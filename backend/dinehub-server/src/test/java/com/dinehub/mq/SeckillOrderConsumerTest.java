package com.dinehub.mq;

import com.dinehub.mapper.CouponMapper;
import com.dinehub.mapper.UserCouponMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeckillOrderConsumerTest {

    @Mock
    private UserCouponMapper userCouponMapper;
    @Mock
    private CouponMapper couponMapper;

    @InjectMocks
    private SeckillOrderConsumer consumer;

    private SeckillMessage message() {
        return SeckillMessage.builder().userId(1L).couponId(10L).requestId("req-1").build();
    }

    @Test
    void persistsCouponAndDeductsDbStock() {
        when(couponMapper.deductStock(10L)).thenReturn(1);

        consumer.onMessage(message());

        verify(userCouponMapper).insert(any());
        verify(couponMapper).deductStock(10L);
    }

    @Test
    void duplicateDeliveryIsIdempotent() {
        //same message delivered twice: unique key fires, message is acked without a second deduction
        doThrow(new DuplicateKeyException("uk_user_coupon")).when(userCouponMapper).insert(any());

        assertDoesNotThrow(() -> consumer.onMessage(message()));
        verify(couponMapper, never()).deductStock(any());
    }

    @Test
    void unexpectedFailurePropagatesForRetry() {
        doThrow(new RuntimeException("db down")).when(userCouponMapper).insert(any());

        assertThrows(RuntimeException.class, () -> consumer.onMessage(message()));
    }
}
