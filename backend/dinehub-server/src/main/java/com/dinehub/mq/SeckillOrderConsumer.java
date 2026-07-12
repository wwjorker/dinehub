package com.dinehub.mq;

import com.dinehub.config.RabbitMQConfiguration;
import com.dinehub.seckill.SeckillCompensationService;
import com.dinehub.seckill.SeckillRedisLedger;
import com.dinehub.seckill.SeckillTerminalService;
import com.dinehub.seckill.SeckillTerminalService.SettlementOutcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.slf4j.MDC;

/**
 * Persists a granted coupon. MySQL terminal arbitration makes duplicate broker
 * deliveries and late messages after compensation deterministic.
 */
@Component
@Slf4j
public class SeckillOrderConsumer {

    private final SeckillTerminalService terminalService;
    private final SeckillCompensationService compensationService;
    private final SeckillRedisLedger ledger;

    public SeckillOrderConsumer(SeckillTerminalService terminalService,
                                SeckillCompensationService compensationService,
                                SeckillRedisLedger ledger) {
        this.terminalService = terminalService;
        this.compensationService = compensationService;
        this.ledger = ledger;
    }

    @RabbitListener(queues = RabbitMQConfiguration.SECKILL_QUEUE)
    public void onMessage(SeckillMessage message) {
        MDC.put("requestId", message.getRequestId());
        try {
            if (!ledger.beginTerminalTransition(message.getCouponId())) {
                throw new IllegalStateException("coupon ledger rebuild in progress");
            }
            try {
                SettlementOutcome outcome = terminalService.settle(message);
                if (outcome == SettlementOutcome.COMPENSATING) {
                    compensationService.compensate(message, "late delivery after compensation claim");
                    return;
                }
                ledger.cleanup(message);
                log.info("seckill request terminal outcome={}, requestId={}", outcome, message.getRequestId());
            } finally {
                ledger.endTerminalTransition(message.getCouponId());
            }
        } finally {
            MDC.remove("requestId");
        }
    }
}
