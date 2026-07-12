package com.dinehub.seckill;

import com.dinehub.config.RabbitMQConfiguration;
import com.dinehub.mq.SeckillMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Duration;

@Component
@Slf4j
public class SeckillMessagePublisher {

    public static final String HEADER_REQUEST_ID = "dinehub-request-id";
    public static final String HEADER_ATTEMPT = "dinehub-publish-attempt";

    private final RabbitTemplate rabbitTemplate;
    private final SeckillRedisLedger ledger;

    @Value("${dinehub.seckill.confirm-timeout-millis:10000}")
    private long confirmTimeoutMillis;
    @Value("${dinehub.seckill.settlement-timeout-millis:120000}")
    private long settlementTimeoutMillis;
    @Value("${dinehub.seckill.publish-retry-delay-millis:5000}")
    private long retryDelayMillis;

    public SeckillMessagePublisher(RabbitTemplate rabbitTemplate, SeckillRedisLedger ledger) {
        this.rabbitTemplate = rabbitTemplate;
        this.ledger = ledger;
    }

    @PostConstruct
    public void configureCallbacks() {
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            PublishAttempt attempt = PublishAttempt.from(correlationData);
            if (attempt == null) {
                log.error("publisher confirm without seckill correlation data, ack={}", ack);
                return;
            }
            if (ack) {
                ledger.markPublished(attempt.message, attempt.attempt, Duration.ofMillis(settlementTimeoutMillis));
            } else {
                ledger.markPublishFailed(attempt.message, attempt.attempt, Duration.ofMillis(retryDelayMillis),
                        cause == null ? "publisher nack" : cause);
            }
        });
        rabbitTemplate.setReturnsCallback(returned -> {
            Object requestId = returned.getMessage().getMessageProperties().getHeaders().get(HEADER_REQUEST_ID);
            Object attemptValue = returned.getMessage().getMessageProperties().getHeaders().get(HEADER_ATTEMPT);
            Object couponId = returned.getMessage().getMessageProperties().getHeaders().get("couponId");
            Object userId = returned.getMessage().getMessageProperties().getHeaders().get("userId");
            if (requestId == null || attemptValue == null || couponId == null || userId == null) {
                log.error("unroutable seckill message without recovery headers: {}", returned.getReplyText());
                return;
            }
            SeckillMessage message = SeckillMessage.builder()
                    .requestId(String.valueOf(requestId))
                    .couponId(Long.valueOf(String.valueOf(couponId)))
                    .userId(Long.valueOf(String.valueOf(userId)))
                    .build();
            int attempt = Integer.parseInt(String.valueOf(attemptValue));
            ledger.markPublishFailed(message, attempt, Duration.ofMillis(retryDelayMillis),
                    "returned " + returned.getReplyCode() + ": " + returned.getReplyText());
        });
    }

    /**
     * Claims and publishes one attempt. False means another scanner owns it or it is terminal.
     */
    public boolean publish(SeckillMessage message) {
        long claimed = ledger.claimPublish(message, Duration.ofMillis(confirmTimeoutMillis));
        if (claimed < 1 || claimed > Integer.MAX_VALUE) {
            return false;
        }
        int attempt = (int) claimed;
        CorrelationData correlationData = new CorrelationData(correlationId(message, attempt));
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfiguration.SECKILL_EXCHANGE,
                    RabbitMQConfiguration.SECKILL_ROUTING_KEY, message, raw -> {
                        raw.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        raw.getMessageProperties().setHeader(HEADER_REQUEST_ID, message.getRequestId());
                        raw.getMessageProperties().setHeader(HEADER_ATTEMPT, attempt);
                        raw.getMessageProperties().setHeader("couponId", message.getCouponId());
                        raw.getMessageProperties().setHeader("userId", message.getUserId());
                        return raw;
                    }, correlationData);
            return true;
        } catch (RuntimeException ex) {
            ledger.markPublishFailed(message, attempt, Duration.ofMillis(retryDelayMillis), ex.getMessage());
            log.warn("seckill publish attempt {} failed for request {}", attempt, message.getRequestId(), ex);
            return false;
        }
    }

    private String correlationId(SeckillMessage message, int attempt) {
        return message.getRequestId() + ":" + message.getCouponId() + ":" + message.getUserId() + ":" + attempt;
    }

    private static class PublishAttempt {
        private final SeckillMessage message;
        private final int attempt;

        private PublishAttempt(SeckillMessage message, int attempt) {
            this.message = message;
            this.attempt = attempt;
        }

        private static PublishAttempt from(CorrelationData correlationData) {
            if (correlationData == null || correlationData.getId() == null) {
                return null;
            }
            String[] parts = correlationData.getId().split(":", 4);
            if (parts.length != 4) {
                return null;
            }
            try {
                return new PublishAttempt(SeckillMessage.builder()
                        .requestId(parts[0])
                        .couponId(Long.valueOf(parts[1]))
                        .userId(Long.valueOf(parts[2]))
                        .build(), Integer.parseInt(parts[3]));
            } catch (RuntimeException ex) {
                return null;
            }
        }
    }
}
