package com.dinehub.seckill;

import com.dinehub.entity.SeckillRequest;
import com.dinehub.entity.UserCoupon;
import com.dinehub.mapper.CouponMapper;
import com.dinehub.mapper.SeckillRequestMapper;
import com.dinehub.mapper.UserCouponMapper;
import com.dinehub.mq.SeckillMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class SeckillTerminalService {

    private final SeckillRequestMapper requestMapper;
    private final UserCouponMapper userCouponMapper;
    private final CouponMapper couponMapper;
    private final SeckillMetrics metrics;

    public SeckillTerminalService(SeckillRequestMapper requestMapper, UserCouponMapper userCouponMapper,
                                  CouponMapper couponMapper, SeckillMetrics metrics) {
        this.requestMapper = requestMapper;
        this.userCouponMapper = userCouponMapper;
        this.couponMapper = couponMapper;
        this.metrics = metrics;
    }

    /**
     * The terminal row, holding row and DB stock deduction commit as one unit.
     */
    @Transactional
    public SettlementOutcome settle(SeckillMessage message) {
        int inserted = requestMapper.insertIgnore(newRequest(message, SeckillRequest.SETTLED, null));
        if (inserted == 0) {
            return outcome(existingAndVerified(message));
        }

        userCouponMapper.insert(UserCoupon.builder()
                .userId(message.getUserId())
                .couponId(message.getCouponId())
                .status(UserCoupon.UNUSED)
                .createTime(LocalDateTime.now())
                .build());
        if (couponMapper.deductStock(message.getCouponId()) == 0) {
            throw new IllegalStateException("DB stock exhausted for request " + message.getRequestId());
        }
        metrics.settled(message.getAdmittedAt());
        return SettlementOutcome.SETTLED;
    }

    /**
     * Commits compensation ownership before any Redis mutation.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompensationClaim claimCompensation(SeckillMessage message, String error) {
        int inserted = requestMapper.insertIgnore(newRequest(message, SeckillRequest.COMPENSATING, trim(error)));
        if (inserted == 0) {
            SeckillRequest existing = existingAndVerified(message);
            if (SeckillRequest.SETTLED.equals(existing.getStatus())) {
                return new CompensationClaim(SettlementOutcome.SETTLED, false);
            }
            if (SeckillRequest.COMPENSATED.equals(existing.getStatus())) {
                return new CompensationClaim(SettlementOutcome.COMPENSATED, false);
            }
        }
        boolean keepBought = userCouponMapper.countByUserAndCoupon(message.getUserId(), message.getCouponId()) > 0;
        return new CompensationClaim(SettlementOutcome.COMPENSATING, keepBought);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finishCompensation(String requestId, String error) {
        int rows = requestMapper.markCompensated(requestId, trim(error));
        if (rows == 0) {
            SeckillRequest existing = requestMapper.getByRequestId(requestId);
            if (existing == null || !SeckillRequest.COMPENSATED.equals(existing.getStatus())) {
                throw new IllegalStateException("compensation terminal transition failed for " + requestId);
            }
        }
    }

    public SeckillRequest get(String requestId) {
        return requestMapper.getByRequestId(requestId);
    }

    private SeckillRequest newRequest(SeckillMessage message, Integer status, String error) {
        LocalDateTime now = LocalDateTime.now();
        return SeckillRequest.builder()
                .requestId(message.getRequestId())
                .couponId(message.getCouponId())
                .userId(message.getUserId())
                .status(status)
                .lastError(error)
                .createTime(now)
                .updateTime(now)
                .build();
    }

    private SeckillRequest existingAndVerified(SeckillMessage message) {
        SeckillRequest existing = requestMapper.getByRequestId(message.getRequestId());
        if (existing == null) {
            throw new IllegalStateException("terminal request disappeared: " + message.getRequestId());
        }
        if (!Objects.equals(existing.getCouponId(), message.getCouponId())
                || !Objects.equals(existing.getUserId(), message.getUserId())) {
            throw new IllegalStateException("request identity conflict: " + message.getRequestId());
        }
        return existing;
    }

    private SettlementOutcome outcome(SeckillRequest request) {
        if (SeckillRequest.SETTLED.equals(request.getStatus())) {
            return SettlementOutcome.SETTLED;
        }
        if (SeckillRequest.COMPENSATING.equals(request.getStatus())) {
            return SettlementOutcome.COMPENSATING;
        }
        if (SeckillRequest.COMPENSATED.equals(request.getStatus())) {
            return SettlementOutcome.COMPENSATED;
        }
        throw new IllegalStateException("unknown terminal status for " + request.getRequestId());
    }

    private String trim(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= 255 ? error : error.substring(0, 255);
    }

    public enum SettlementOutcome {
        SETTLED, COMPENSATING, COMPENSATED
    }

    @lombok.Value
    public static class CompensationClaim {
        SettlementOutcome outcome;
        boolean keepBought;
    }
}
