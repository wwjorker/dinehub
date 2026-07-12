package com.dinehub.seckill;

import com.dinehub.mq.SeckillMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeckillMessagePublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private SeckillRedisLedger ledger;
    @Mock
    private SeckillMetrics metrics;
    private SeckillMessagePublisher publisher;

    private SeckillMessage message() {
        return SeckillMessage.builder().requestId("request-1").couponId(10L).userId(20L).build();
    }

    @BeforeEach
    void setUp() {
        publisher = new SeckillMessagePublisher(rabbitTemplate, ledger, metrics);
        ReflectionTestUtils.setField(publisher, "confirmTimeoutMillis", 10000L);
        ReflectionTestUtils.setField(publisher, "settlementTimeoutMillis", 120000L);
        ReflectionTestUtils.setField(publisher, "retryDelayMillis", 5000L);
        publisher.configureCallbacks();
    }

    @Test
    void confirmedPublishAdvancesLedgerToPublished() {
        when(ledger.claimPublish(eq(message()), any(Duration.class))).thenReturn(3L);
        ArgumentCaptor<RabbitTemplate.ConfirmCallback> callback =
                ArgumentCaptor.forClass(RabbitTemplate.ConfirmCallback.class);

        assertTrue(publisher.publish(message()));
        verify(rabbitTemplate).setConfirmCallback(callback.capture());
        callback.getValue().confirm(new CorrelationData("request-1:10:20:3"), true, null);

        verify(ledger).markPublished(eq(message()), eq(3), any(Duration.class));
    }

    @Test
    void synchronousSendFailureStaysRetryable() {
        when(ledger.claimPublish(eq(message()), any(Duration.class))).thenReturn(1L);
        doThrow(new AmqpException("broker down")).when(rabbitTemplate)
                .convertAndSend(any(String.class), any(String.class), eq(message()),
                        any(MessagePostProcessor.class), any(CorrelationData.class));

        assertFalse(publisher.publish(message()));

        verify(ledger).markPublishFailed(eq(message()), eq(1), any(Duration.class), eq("broker down"));
    }

    @Test
    void unclaimedAttemptIsNotPublished() {
        when(ledger.claimPublish(eq(message()), any(Duration.class))).thenReturn(-1L);
        assertFalse(publisher.publish(message()));
    }
}
