package com.dinehub.mq;

import com.dinehub.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderTimeoutConsumerTest {

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderTimeoutConsumer consumer;

    @Test
    void cancelsOrderStillPendingPayment() {
        when(orderMapper.cancelIfPending(eq(99L), anyString(), any(LocalDateTime.class))).thenReturn(1);

        consumer.onOrderTimeout(99L);

        verify(orderMapper).cancelIfPending(eq(99L), eq(OrderTimeoutConsumer.CANCEL_REASON), any(LocalDateTime.class));
    }

    @Test
    void paidOrderIsLeftAloneWithoutError() {
        //conditional update touches 0 rows when the order was paid meanwhile
        when(orderMapper.cancelIfPending(eq(99L), anyString(), any(LocalDateTime.class))).thenReturn(0);

        assertDoesNotThrow(() -> consumer.onOrderTimeout(99L));
    }
}
