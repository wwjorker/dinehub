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
import com.dinehub.vo.SeckillCampaignVO;
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
import java.time.ZoneId;
import java.time.Instant;
import java.util.stream.Collectors;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class CouponServiceImpl implements CouponService {

    public static final String STOCK_KEY_PREFIX = SeckillRedisLedger.STOCK_KEY_PREFIX;
    public static final String BOUGHT_KEY_PREFIX = SeckillRedisLedger.BOUGHT_KEY_PREFIX;
    //活动结束后再保留一天,避免脏key常驻
    private static final Duration KEY_TTL_MARGIN = Duration.ofDays(1);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    public static final long SECKILL_GRANTED = 0L;
    public static final long SECKILL_SOLD_OUT = 1L;
    public static final long SECKILL_DUPLICATE = 2L;
    public static final long SECKILL_REBUILDING = 3L;

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
        if (!Coupon.TYPE_SECKILL.equals(couponDTO.getType())) {
            throw new SeckillBusinessException("当前版本只支持创建秒杀券");
        }
        if (couponDTO.getMinAmount() > 0
                && couponDTO.getMinAmount() < couponDTO.getDiscountAmount()) {
            throw new SeckillBusinessException("使用门槛为0表示无门槛，否则不能低于优惠金额");
        }
        if (!couponDTO.getSeckillEndTime().isAfter(couponDTO.getSeckillStartTime())) {
            throw new SeckillBusinessException("活动结束时间必须晚于开始时间");
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
     * Rebuilds the Redis view while admissions and terminal transitions are behind
     * a short per-coupon barrier. Nonterminal reservations reduce available stock
     * and their users remain in the one-per-user set.
     */
    public void rebuildSeckillCache(Long id) {
        Coupon coupon = couponMapper.getById(id);
        if (coupon == null || !Coupon.TYPE_SECKILL.equals(coupon.getType())) {
            throw new SeckillBusinessException(MessageConstant.COUPON_NOT_FOUND);
        }
        if (!Coupon.STATUS_PUBLISHED.equals(coupon.getStatus())) {
            throw new SeckillBusinessException(MessageConstant.COUPON_NOT_PUBLISHED);
        }
        Duration rebuildLease = Duration.ofSeconds(60);
        String lockToken = seckillRedisLedger.acquireRebuildLock(id, rebuildLease);
        if (lockToken == null) {
            throw new SeckillBusinessException(MessageConstant.SECKILL_BUSY);
        }
        try {
            if (!seckillRedisLedger.awaitTerminalQuiescence(id, Duration.ofSeconds(5))) {
                throw new SeckillBusinessException(MessageConstant.SECKILL_BUSY);
            }
            Set<String> activeRequestIds = seckillRedisLedger.allActiveRequestIds(id);
            Coupon current = couponMapper.getById(id);
            Set<Long> protectedUsers = new LinkedHashSet<>(userCouponMapper.listUserIdsByCouponId(id));
            Map<String, SeckillRequest> terminals = seckillTerminalService.getAll(activeRequestIds);
            int activeReservations = 0;
            for (String requestId : activeRequestIds) {
                PendingRequest pending = seckillRedisLedger.get(requestId);
                if (pending == null || !Objects.equals(id, pending.getCouponId())) {
                    throw new IllegalStateException("incomplete active ledger for request " + requestId);
                }
                SeckillRequest terminal = terminals.get(requestId);
                if (terminal != null && (SeckillRequest.SETTLED.equals(terminal.getStatus())
                        || SeckillRequest.COMPENSATED.equals(terminal.getStatus()))) {
                    if (!Objects.equals(id, terminal.getCouponId())
                            || !Objects.equals(pending.getUserId(), terminal.getUserId())) {
                        throw new IllegalStateException("terminal identity mismatch for request " + requestId);
                    }
                    seckillRedisLedger.cleanup(pending.toMessage());
                    continue;
                }
                if (seckillRedisLedger.isCompensated(requestId)) {
                    continue;
                }
                activeReservations++;
                protectedUsers.add(pending.getUserId());
            }
            int available = current.getRemainingStock() - activeReservations;
            if (available < 0) {
                throw new IllegalStateException("active reservations exceed DB stock for coupon " + id);
            }
            Duration projectionTtl = ledgerTtl(current);
            if (!seckillRedisLedger.replaceLedgerIfLockOwned(id, lockToken, available,
                    protectedUsers, projectionTtl, rebuildLease)) {
                throw new SeckillBusinessException(MessageConstant.SECKILL_BUSY);
            }
            log.info("seckill cache rebuilt for coupon {}: available={}, active={}",
                    id, available, activeReservations);
        } finally {
            seckillRedisLedger.releaseRebuildLock(id, lockToken);
        }
    }

    /**
     * 以DB为准装载Redis账本:库存=remaining_stock,防重集合=已持券用户,TTL对齐活动周期
     */
    private void preheat(Coupon coupon) {
        writeRedisLedger(coupon, coupon.getRemainingStock(),
                new LinkedHashSet<>(userCouponMapper.listUserIdsByCouponId(coupon.getId())));
    }

    private void writeRedisLedger(Coupon coupon, int available, Set<Long> protectedUsers) {
        Long id = coupon.getId();
        String stockKey = STOCK_KEY_PREFIX + id;
        String boughtKey = BOUGHT_KEY_PREFIX + id;
        stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(available));
        stringRedisTemplate.delete(boughtKey);
        if (!protectedUsers.isEmpty()) {
            String[] members = protectedUsers.stream().map(String::valueOf).toArray(String[]::new);
            stringRedisTemplate.opsForSet().add(boughtKey, members);
        }
        Duration ttl = ledgerTtl(coupon);
        stringRedisTemplate.expire(stockKey, ttl);
        stringRedisTemplate.expire(boughtKey, ttl);
    }

    private Duration ledgerTtl(Coupon coupon) {
        Duration ttl = Duration.between(LocalDateTime.now(BUSINESS_ZONE), coupon.getSeckillEndTime())
                .plus(KEY_TTL_MARGIN);
        return ttl.isNegative() || ttl.isZero() ? Duration.ofHours(1) : ttl;
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
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
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
        Duration ttl = Duration.between(LocalDateTime.now(BUSINESS_ZONE), coupon.getSeckillEndTime())
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
        if (result == SECKILL_REBUILDING) {
            throw new SeckillBusinessException(MessageConstant.SECKILL_BUSY);
        }
        SeckillMessage message = SeckillMessage.builder()
                .userId(userId)
                .couponId(couponId)
                .requestId(requestId)
                .admittedAt(System.currentTimeMillis())
                .build();
        seckillMessagePublisher.publish(message);
        log.debug("seckill admitted, requestId={}", requestId);
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
                        .createTime(LocalDateTime.now(BUSINESS_ZONE))
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

    public List<SeckillCampaignVO> listActiveSeckill() {
        Instant serverNow = Instant.now();
        return couponMapper.listActiveSeckill().stream()
                .map(coupon -> {
                    Instant start = coupon.getSeckillStartTime().atZone(BUSINESS_ZONE).toInstant();
                    Instant end = coupon.getSeckillEndTime().atZone(BUSINESS_ZONE).toInstant();
                    String phase = serverNow.isBefore(start) ? "SCHEDULED"
                            : !serverNow.isBefore(end) ? "ENDED"
                            : coupon.getRemainingStock() <= 0 ? "SOLD_OUT" : "LIVE";
                    return SeckillCampaignVO.builder()
                            .id(coupon.getId())
                            .title(coupon.getTitle())
                            .discountAmount(coupon.getDiscountAmount())
                            .minAmount(coupon.getMinAmount())
                            .totalStock(coupon.getTotalStock())
                            .remainingStock(coupon.getRemainingStock())
                            .serverTimeEpochMs(serverNow.toEpochMilli())
                            .startTimeEpochMs(start.toEpochMilli())
                            .endTimeEpochMs(end.toEpochMilli())
                            .phase(phase)
                            .build();
                })
                .collect(Collectors.toList());
    }
}
