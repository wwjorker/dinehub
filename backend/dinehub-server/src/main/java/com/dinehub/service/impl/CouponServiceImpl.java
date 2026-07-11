package com.dinehub.service.impl;

import com.dinehub.config.RabbitMQConfiguration;
import com.dinehub.constant.MessageConstant;
import com.dinehub.dto.CouponDTO;
import com.dinehub.dto.CouponPageQueryDTO;
import com.dinehub.entity.Coupon;
import com.dinehub.entity.UserCoupon;
import com.dinehub.exception.SeckillBusinessException;
import com.dinehub.mapper.CouponMapper;
import com.dinehub.mapper.UserCouponMapper;
import com.dinehub.mq.SeckillMessage;
import com.dinehub.result.PageResult;
import com.dinehub.service.CouponService;
import com.dinehub.vo.UserCouponVO;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class CouponServiceImpl implements CouponService {

    public static final String STOCK_KEY_PREFIX = "seckill:stock:";
    public static final String BOUGHT_KEY_PREFIX = "seckill:bought:";
    public static final String COMPENSATED_KEY_PREFIX = "seckill:compensated:";
    //活动结束后再保留一天,避免脏key常驻
    private static final Duration KEY_TTL_MARGIN = Duration.ofDays(1);

    public static final long SECKILL_GRANTED = 0L;
    public static final long SECKILL_SOLD_OUT = 1L;
    public static final long SECKILL_DUPLICATE = 2L;

    /**
     * 抢券实现模式:redis = Redis预扣+Lua+MQ异步落库(默认);db = 直接DB行锁扣减(压测对照组)
     */
    @Value("${dinehub.seckill.mode:redis}")
    private String seckillMode;

    @Autowired
    private CouponMapper couponMapper;
    @Autowired
    private UserCouponMapper userCouponMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    //编程式事务:seckillByDb 由本类方法内部调用,注解事务会因自调用绕过代理而失效
    @Autowired
    private TransactionTemplate transactionTemplate;

    private final DefaultRedisScript<Long> seckillScript;
    private final DefaultRedisScript<Long> compensateScript;

    public CouponServiceImpl() {
        seckillScript = new DefaultRedisScript<>();
        seckillScript.setLocation(new org.springframework.core.io.ClassPathResource("lua/seckill.lua"));
        seckillScript.setResultType(Long.class);
        compensateScript = new DefaultRedisScript<>();
        compensateScript.setLocation(new org.springframework.core.io.ClassPathResource("lua/seckill_compensate.lua"));
        compensateScript.setResultType(Long.class);
    }

    /**
     * 原子且幂等地回补一个抢购名额:同一requestId只会补偿一次。
     * 用于消息发送失败的即时回滚与死信队列的最终补偿。
     */
    public boolean compensate(Long couponId, Long userId, String requestId) {
        List<String> keys = Arrays.asList(
                COMPENSATED_KEY_PREFIX + requestId,
                STOCK_KEY_PREFIX + couponId,
                BOUGHT_KEY_PREFIX + couponId);
        Long result = stringRedisTemplate.execute(compensateScript, keys,
                String.valueOf(userId), String.valueOf(Duration.ofDays(1).getSeconds()));
        return result != null && result == 1L;
    }

    public Coupon save(CouponDTO couponDTO) {
        if (Coupon.TYPE_SECKILL.equals(couponDTO.getType())
                && (couponDTO.getSeckillStartTime() == null || couponDTO.getSeckillEndTime() == null)) {
            throw new SeckillBusinessException(MessageConstant.COUPON_TIME_REQUIRED);
        }
        Coupon coupon = new Coupon();
        BeanUtils.copyProperties(couponDTO, coupon);
        coupon.setRemainingStock(coupon.getTotalStock());
        coupon.setStatus(Coupon.STATUS_DRAFT);
        couponMapper.insert(coupon);
        return coupon;
    }

    public PageResult pageQuery(CouponPageQueryDTO couponPageQueryDTO) {
        PageHelper.startPage(couponPageQueryDTO.getPage(), couponPageQueryDTO.getPageSize());
        Page<Coupon> page = couponMapper.pageQuery(couponPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    public void publish(Long id) {
        Coupon coupon = couponMapper.getById(id);
        if (coupon == null) {
            throw new SeckillBusinessException(MessageConstant.COUPON_NOT_FOUND);
        }
        //条件更新防重复发布:重复发布会重置Redis防重账本,让已领用户再次通过Lua
        int rows = couponMapper.publishIfDraft(id);
        if (rows == 0) {
            throw new SeckillBusinessException(MessageConstant.COUPON_ALREADY_PUBLISHED);
        }
        //预热:把DB剩余库存装载进Redis,作为抢购期间的资格账本
        if (Coupon.TYPE_SECKILL.equals(coupon.getType())) {
            String stockKey = STOCK_KEY_PREFIX + id;
            String boughtKey = BOUGHT_KEY_PREFIX + id;
            stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(coupon.getRemainingStock()));
            //防重集合以DB持券记录为准重建,Redis数据丢失后重新发布也不会破坏一人一单
            stringRedisTemplate.delete(boughtKey);
            List<Long> holders = userCouponMapper.listUserIdsByCouponId(id);
            if (!holders.isEmpty()) {
                String[] members = holders.stream().map(String::valueOf).toArray(String[]::new);
                stringRedisTemplate.opsForSet().add(boughtKey, members);
            }
            Duration ttl = Duration.between(LocalDateTime.now(), coupon.getSeckillEndTime()).plus(KEY_TTL_MARGIN);
            if (!ttl.isNegative()) {
                stringRedisTemplate.expire(stockKey, ttl);
                stringRedisTemplate.expire(boughtKey, ttl);
            }
        }
        log.info("coupon {} published, stock preheated: {}", id, coupon.getRemainingStock());
    }

    public void seckill(Long couponId, Long userId) {
        Coupon coupon = couponMapper.getById(couponId);
        //前置校验:券存在、已发布、在时间窗口内
        if (coupon == null) {
            throw new SeckillBusinessException(MessageConstant.COUPON_NOT_FOUND);
        }
        if (!Coupon.STATUS_PUBLISHED.equals(coupon.getStatus())) {
            throw new SeckillBusinessException(MessageConstant.COUPON_NOT_PUBLISHED);
        }
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getSeckillStartTime() != null && now.isBefore(coupon.getSeckillStartTime())) {
            throw new SeckillBusinessException(MessageConstant.SECKILL_NOT_STARTED);
        }
        if (coupon.getSeckillEndTime() != null && now.isAfter(coupon.getSeckillEndTime())) {
            throw new SeckillBusinessException(MessageConstant.SECKILL_ENDED);
        }

        if ("db".equals(seckillMode)) {
            seckillByDb(couponId, userId);
        } else {
            seckillByRedis(couponId, userId);
        }
    }

    /**
     * Redis预扣模式:Lua原子判定+扣减,成功后发MQ异步落库,响应时间与DB解耦
     */
    private void seckillByRedis(Long couponId, Long userId) {
        List<String> keys = Arrays.asList(STOCK_KEY_PREFIX + couponId, BOUGHT_KEY_PREFIX + couponId);
        Long result = stringRedisTemplate.execute(seckillScript, keys, String.valueOf(userId));
        if (result == null || result == SECKILL_SOLD_OUT) {
            throw new SeckillBusinessException(MessageConstant.SECKILL_SOLD_OUT);
        }
        if (result == SECKILL_DUPLICATE) {
            throw new SeckillBusinessException(MessageConstant.SECKILL_REPEAT);
        }
        SeckillMessage message = SeckillMessage.builder()
                .userId(userId)
                .couponId(couponId)
                .requestId(UUID.randomUUID().toString())
                .build();
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfiguration.SECKILL_EXCHANGE,
                    RabbitMQConfiguration.SECKILL_ROUTING_KEY, message);
        } catch (Exception e) {
            //Redis已扣但消息没发出去:立刻把名额还回去,否则名额被永久占用
            compensate(couponId, userId, message.getRequestId());
            log.error("seckill message send failed, slot compensated: {}", message, e);
            throw new SeckillBusinessException(MessageConstant.SECKILL_BUSY);
        }
        log.info("seckill granted, message sent: {}", message);
    }

    /**
     * DB行锁模式(压测对照组):条件更新扣库存,行锁串行化天然防超卖,吞吐受限于DB
     */
    private void seckillByDb(Long couponId, Long userId) {
        transactionTemplate.executeWithoutResult(status -> {
            int rows = couponMapper.deductStock(couponId);
            if (rows == 0) {
                throw new SeckillBusinessException(MessageConstant.SECKILL_SOLD_OUT);
            }
            try {
                userCouponMapper.insert(UserCoupon.builder()
                        .userId(userId)
                        .couponId(couponId)
                        .status(UserCoupon.UNUSED)
                        .createTime(LocalDateTime.now())
                        .build());
            } catch (DuplicateKeyException e) {
                //唯一键兜底一人一单;异常触发回滚,恢复已扣库存
                throw new SeckillBusinessException(MessageConstant.SECKILL_REPEAT);
            }
        });
    }

    public List<UserCouponVO> listByUserId(Long userId) {
        return userCouponMapper.listByUserId(userId);
    }

    public List<Coupon> listActiveSeckill() {
        return couponMapper.listActiveSeckill();
    }
}
