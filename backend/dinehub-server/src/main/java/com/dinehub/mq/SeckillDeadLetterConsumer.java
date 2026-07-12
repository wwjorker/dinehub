package com.dinehub.mq;

import com.dinehub.config.RabbitMQConfiguration;
import com.dinehub.seckill.SeckillCompensationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.slf4j.MDC;

@Component
@Slf4j
public class SeckillDeadLetterConsumer {

    private final SeckillCompensationService compensationService;

    public SeckillDeadLetterConsumer(SeckillCompensationService compensationService) {
        this.compensationService = compensationService;
    }

    @RabbitListener(queues = RabbitMQConfiguration.SECKILL_DEAD_QUEUE)
    public void onDeadLetter(SeckillMessage message) {
        MDC.put("requestId", message.getRequestId());
        try {
            compensationService.compensate(message, "consumer retries exhausted");
            log.warn("seckill dead letter resolved, requestId={}", message.getRequestId());
        } finally {
            MDC.remove("requestId");
        }
    }
}
