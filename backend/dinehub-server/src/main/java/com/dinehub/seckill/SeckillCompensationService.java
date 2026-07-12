package com.dinehub.seckill;

import com.dinehub.mq.SeckillMessage;
import com.dinehub.seckill.SeckillTerminalService.CompensationClaim;
import com.dinehub.seckill.SeckillTerminalService.SettlementOutcome;
import com.dinehub.seckill.SeckillRedisLedger.CompensationOutcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Three-stage compensation: DB claim, Redis refund, DB completion.
 */
@Service
@Slf4j
public class SeckillCompensationService {

    private static final Duration MARKER_TTL = Duration.ofDays(1);

    private final SeckillTerminalService terminalService;
    private final SeckillRedisLedger ledger;
    private final SeckillMetrics metrics;

    public SeckillCompensationService(SeckillTerminalService terminalService, SeckillRedisLedger ledger,
                                      SeckillMetrics metrics) {
        this.terminalService = terminalService;
        this.ledger = ledger;
        this.metrics = metrics;
    }

    public SettlementOutcome compensate(SeckillMessage message, String error) {
        if (!ledger.beginTerminalTransition(message.getCouponId())) {
            throw new IllegalStateException("coupon ledger rebuild in progress");
        }
        try {
            CompensationClaim claim = terminalService.claimCompensation(message, error);
            if (claim.getOutcome() == SettlementOutcome.SETTLED) {
                ledger.cleanup(message);
                return SettlementOutcome.SETTLED;
            }
            if (claim.getOutcome() == SettlementOutcome.COMPENSATED) {
                ledger.cleanup(message);
                return SettlementOutcome.COMPENSATED;
            }

            ledger.markCompensating(message, error);
            CompensationOutcome refund = ledger.compensate(message, claim.isKeepBought(), MARKER_TTL);
            if (refund == CompensationOutcome.DEFERRED) {
                throw new IllegalStateException("coupon ledger rebuild started during compensation");
            }
            terminalService.finishCompensation(message.getRequestId(), error);
            ledger.cleanup(message);
            if (refund == CompensationOutcome.APPLIED) {
                metrics.compensated();
                log.error("seckill request compensated, requestId={}, reason={}", message.getRequestId(), error);
            } else {
                log.info("seckill compensation continued idempotently, requestId={}", message.getRequestId());
            }
            return SettlementOutcome.COMPENSATED;
        } finally {
            ledger.endTerminalTransition(message.getCouponId());
        }
    }
}
