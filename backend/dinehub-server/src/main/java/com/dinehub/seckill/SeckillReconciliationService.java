package com.dinehub.seckill;

import com.dinehub.constant.MessageConstant;
import com.dinehub.entity.Coupon;
import com.dinehub.entity.SeckillRequest;
import com.dinehub.exception.SeckillBusinessException;
import com.dinehub.mapper.CouponMapper;
import com.dinehub.mapper.SeckillRequestMapper;
import com.dinehub.mapper.UserCouponMapper;
import com.dinehub.seckill.SeckillRedisLedger.PendingRequest;
import com.dinehub.vo.SeckillReconciliationVO;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Read-only comparison of the durable ledger and its Redis projection. */
@Service
public class SeckillReconciliationService {

    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;
    private final SeckillRequestMapper requestMapper;
    private final SeckillRedisLedger ledger;

    public SeckillReconciliationService(CouponMapper couponMapper, UserCouponMapper userCouponMapper,
                                         SeckillRequestMapper requestMapper, SeckillRedisLedger ledger) {
        this.couponMapper = couponMapper;
        this.userCouponMapper = userCouponMapper;
        this.requestMapper = requestMapper;
        this.ledger = ledger;
    }

    public SeckillReconciliationVO reconcile(Long couponId) {
        Coupon coupon = couponMapper.getById(couponId);
        if (coupon == null || !Coupon.TYPE_SECKILL.equals(coupon.getType())) {
            throw new SeckillBusinessException(MessageConstant.COUPON_NOT_FOUND);
        }

        Set<Long> expectedProtectedUsers = new LinkedHashSet<>(
                userCouponMapper.listUserIdsByCouponId(couponId));
        int activeReservations = 0;
        for (String requestId : ledger.allActiveRequestIds(couponId)) {
            PendingRequest pending = ledger.get(requestId);
            if (pending == null || !Objects.equals(couponId, pending.getCouponId())) {
                continue;
            }
            SeckillRequest terminal = requestMapper.getByRequestId(requestId);
            if (terminal != null && (SeckillRequest.SETTLED.equals(terminal.getStatus())
                    || SeckillRequest.COMPENSATED.equals(terminal.getStatus()))) {
                continue;
            }
            if (ledger.isCompensated(requestId)) {
                continue;
            }
            activeReservations++;
            expectedProtectedUsers.add(pending.getUserId());
        }

        int expectedStock = coupon.getRemainingStock() - activeReservations;
        Integer redisStock = ledger.availableStock(couponId);
        Long protectedCount = ledger.protectedUserCount(couponId);
        boolean protectedUsersMatch = protectedCount != null
                && protectedCount.longValue() == expectedProtectedUsers.size();
        if (protectedUsersMatch) {
            for (Long userId : expectedProtectedUsers) {
                if (!ledger.isUserProtected(couponId, userId)) {
                    protectedUsersMatch = false;
                    break;
                }
            }
        }
        Integer drift = redisStock == null ? null : redisStock - expectedStock;
        boolean consistent = expectedStock >= 0 && drift != null && drift == 0 && protectedUsersMatch;

        return SeckillReconciliationVO.builder()
                .couponId(couponId)
                .dbRemainingStock(coupon.getRemainingStock())
                .dbHolderCount(userCouponMapper.countByCouponId(couponId))
                .expectedRedisStock(expectedStock)
                .redisAvailableStock(redisStock)
                .redisProtectedUserCount(protectedCount)
                .activeReservationCount(activeReservations)
                .settledCount(requestMapper.countByCouponAndStatus(couponId, SeckillRequest.SETTLED))
                .compensatingCount(requestMapper.countByCouponAndStatus(couponId, SeckillRequest.COMPENSATING))
                .compensatedCount(requestMapper.countByCouponAndStatus(couponId, SeckillRequest.COMPENSATED))
                .stockDrift(drift)
                .consistent(consistent)
                .build();
    }
}
