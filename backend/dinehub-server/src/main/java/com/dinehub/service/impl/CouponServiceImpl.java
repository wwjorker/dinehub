package com.dinehub.service.impl;

import com.dinehub.constant.MessageConstant;
import com.dinehub.dto.CouponDTO;
import com.dinehub.dto.CouponPageQueryDTO;
import com.dinehub.entity.Coupon;
import com.dinehub.entity.UserCoupon;
import com.dinehub.entity.SeckillRequest;
import com.dinehub.exception.SeckillBusinessException;
import com.dinehub.mapper.CouponMapper;
import com.dinehub.mapper.UserCouponMapper;
import com.dinehub.mq.SeckillMessage;
import com.dinehub.result.PageResult;
import com.dinehub.service.CouponService;
import com.dinehub.seckill.SeckillMessagePublisher;
import com.dinehub.seckill.SeckillRedisLedger;
import com.dinehub.seckill.SeckillRedisLedger.PendingRequest;
import com.dinehub.seckill.SeckillTerminalService;
import com.dinehub.vo.SeckillResultVO;
import com.dinehub.vo.SeckillTicketVO;
import com.dinehub.vo.UserCouponVO;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
public class CouponServiceImpl implements CouponService {

    public static final String STOCK_KEY_PREFIX = SeckillRedisLedger.STOCK_KEY_PREFIX;
    public static final String BOUGHT_KEY_PREFIX = SeckillRedisLedger.BOUGHT_KEY_PREFIX;
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
    private SeckillRedisLedger seckillRedisLedger;
    @Autowired
    private SeckillMessagePublisher seckillMessagePublisher;
    @Autowired
    private SeckillTerminalService seckillTerminalService;
    //编程式事务:seckillByDb 由本类方法内部调用,注解事务会因自调用绕过代理而失效
    @Autowired
    private TransactionTemplate transactionTemplate;

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
            preheat(coupon);
        }
        log.info("coupon {} published, stock preheated: {}", id, coupon.getRemainingStock());
    }

    /**
     * 重建秒杀缓存:幂等,可反复调用,用DB的剩余库存与持券记录重建资格账本与防重集合。
     * 边界:以DB为准,尚未落库的在途异步准入不在账本内——活动进行且队列有积压时重建
     * 会放大库存并漏掉在途用户,因此仅应在无在途请求时使用(活动未开始、已结束或
     * 队列确认排空);活动中的安全重建需要可靠消息账本(seckill_request)支持。
     */
    public void rebuildSeckillCache(Long id) {
        Coupon coupon = couponMapper.getById(id);
        if (coupon == null || !Coupon.TYPE_SECKILL.equals(coupon.getType())) {
            throw new SeckillBusinessException(MessageConstant.COUPON_NOT_FOUND);
        }
        if (!Coupon.STATUS_PUBLISHED.equals(coupon.getStatus())) {
            throw new SeckillBusinessException(MessageConstant.COUPON_NOT_PUBLISHED);
        }
        preheat(coupon);
        log.info("seckill cache rebuilt for coupon {}: stock {}", id, coupon.getRemainingStock());
    }

    /**
     * 以DB为准装载Redis账本:库存=remaining_stock,防重集合=已持券用户,TTL对齐活动周期
     */
    private void preheat(Coupon coupon) {
        Long id = coupon.getId();
        String stockKey = STOCK_KEY_PREFIX + id;
        String boughtKey = BOUGHT_KEY_PREFIX + id;
        stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(coupon.getRemainingStock()));
        //防重集合以DB持券记录为准重建,Redis数据丢失后重建也不会破坏一人一单
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

    public SeckillTicketVO seckill(Long couponId, Long userId) {
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
            return SeckillTicketVO.builder().status("SUCCESS").build();
        } else {
            return seckillByRedis(coupon, userId);
        }
    }

    /**
     * Redis预扣模式:Lua原子判定+扣减,成功后发MQ异步落库,响应时间与DB解耦
     */
    private SeckillTicketVO seckillByRedis(Coupon coupon, Long userId) {
        Long couponId = coupon.getId();
        String requestId = UUID.randomUUID().toString();
        Duration ttl = Duration.between(LocalDateTime.now(), coupon.getSeckillEndTime())
                .plus(KEY_TTL_MARGIN);
        if (ttl.isNegative() || ttl.isZero()) {
            ttl = KEY_TTL_MARGIN;
        }
        Long result = seckillRedisLedger.admit(couponId, userId, requestId, ttl);
        if (result == null || result == SECKILL_SOLD_OUT) {
            throw new SeckillBusinessException(MessageConstant.SECKILL_SOLD_OUT);
        }
        if (result == SECKILL_DUPLICATE) {
            throw new SeckillBusinessException(MessageConstant.SECKILL_REPEAT);
        }
        SeckillMessage message = SeckillMessage.builder()
                .userId(userId)
                .couponId(couponId)
                .requestId(requestId)
                .build();
        seckillMessagePublisher.publish(message);
        log.info("seckill admitted, requestId={}", requestId);
        return SeckillTicketVO.builder().requestId(requestId).status("PENDING").build();
    }

    public SeckillResultVO getSeckillResult(String requestId, Long userId) {
        SeckillRequest terminal = seckillTerminalService.get(requestId);
        if (terminal != null) {
            verifyResultOwner(terminal.getUserId(), userId);
            String status = SeckillRequest.SETTLED.equals(terminal.getStatus()) ? "SUCCESS"
                    : SeckillRequest.COMPENSATED.equals(terminal.getStatus()) ? "FAILED" : "PENDING";
            return SeckillResultVO.builder().requestId(requestId).status(status).build();
        }
        PendingRequest pending = seckillRedisLedger.get(requestId);
        if (pending == null) {
            return SeckillResultVO.builder().requestId(requestId).status("UNKNOWN").build();
        }
        verifyResultOwner(pending.getUserId(), userId);
        return SeckillResultVO.builder().requestId(requestId).status("PENDING").build();
    }

    private void verifyResultOwner(Long ownerId, Long userId) {
        if (!Objects.equals(ownerId, userId)) {
            throw new SeckillBusinessException(MessageConstant.COUPON_NOT_FOUND);
        }
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
