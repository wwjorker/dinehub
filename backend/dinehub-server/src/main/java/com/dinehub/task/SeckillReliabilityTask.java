package com.dinehub.task;

import com.dinehub.entity.SeckillRequest;
import com.dinehub.mapper.CouponMapper;
import com.dinehub.mq.SeckillMessage;
import com.dinehub.seckill.SeckillCompensationService;
import com.dinehub.seckill.SeckillMessagePublisher;
import com.dinehub.seckill.SeckillRedisLedger;
import com.dinehub.seckill.SeckillRedisLedger.PendingRequest;
import com.dinehub.seckill.SeckillTerminalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Low-frequency recovery for unknown confirms, lost callbacks and consumer stalls.
 */
@Component
@Slf4j
public class SeckillReliabilityTask {

    private final CouponMapper couponMapper;
    private final SeckillRedisLedger ledger;
    private final SeckillTerminalService terminalService;
    private final SeckillMessagePublisher publisher;
    private final SeckillCompensationService compensationService;

    @Value("${dinehub.seckill.max-publish-attempts:10}")
    private int maxAttempts;
    @Value("${dinehub.seckill.max-pending-millis:1800000}")
    private long maxPendingMillis;

    public SeckillReliabilityTask(CouponMapper couponMapper, SeckillRedisLedger ledger,
                                   SeckillTerminalService terminalService,
                                   SeckillMessagePublisher publisher,
                                   SeckillCompensationService compensationService) {
        this.couponMapper = couponMapper;
        this.ledger = ledger;
        this.terminalService = terminalService;
        this.publisher = publisher;
        this.compensationService = compensationService;
    }

    @Scheduled(fixedDelayString = "${dinehub.seckill.scan-interval-millis:60000}")
    public void recoverDueRequests() {
        long now = System.currentTimeMillis();
        List<Long> couponIds = couponMapper.listReliableScanCouponIds();
        for (Long couponId : couponIds) {
            Set<String> due = ledger.dueRequestIds(couponId, now, 200);
            for (String requestId : due) {
                try {
                    recoverOne(couponId, requestId, now);
                } catch (RuntimeException ex) {
                    log.error("seckill recovery failed, requestId={}", requestId, ex);
                }
            }
        }
    }

    private void recoverOne(Long couponId, String requestId, long now) {
        PendingRequest pending = ledger.get(requestId);
        if (pending == null) {
            ledger.removeOrphan(couponId, requestId);
            return;
        }
        if (!Objects.equals(couponId, pending.getCouponId())) {
            throw new IllegalStateException("active ledger coupon mismatch for " + requestId);
        }
        SeckillMessage message = pending.toMessage();
        SeckillRequest terminal = terminalService.get(requestId);
        if (terminal != null) {
            verifyIdentity(message, terminal);
            if (SeckillRequest.SETTLED.equals(terminal.getStatus())
                    || SeckillRequest.COMPENSATED.equals(terminal.getStatus())) {
                ledger.cleanup(message);
                return;
            }
            compensationService.compensate(message, "continue interrupted compensation");
            return;
        }

        if ("COMPENSATING".equals(pending.getStatus())) {
            compensationService.compensate(message, "continue Redis compensation state");
            return;
        }
        if (pending.getAttempt() >= maxAttempts || now - pending.getCreatedAt() >= maxPendingMillis) {
            compensationService.compensate(message, "delivery deadline exceeded");
            return;
        }
        publisher.publish(message);
    }

    private void verifyIdentity(SeckillMessage message, SeckillRequest terminal) {
        if (!Objects.equals(message.getCouponId(), terminal.getCouponId())
                || !Objects.equals(message.getUserId(), terminal.getUserId())) {
            throw new IllegalStateException("terminal ledger identity mismatch for " + message.getRequestId());
        }
    }
}
