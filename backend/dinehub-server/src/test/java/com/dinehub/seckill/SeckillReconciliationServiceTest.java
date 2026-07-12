package com.dinehub.seckill;

import com.dinehub.entity.Coupon;
import com.dinehub.mapper.CouponMapper;
import com.dinehub.mapper.SeckillRequestMapper;
import com.dinehub.mapper.UserCouponMapper;
import com.dinehub.seckill.SeckillRedisLedger.PendingRequest;
import com.dinehub.vo.SeckillReconciliationVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeckillReconciliationServiceTest {

    @Mock
    private CouponMapper couponMapper;
    @Mock
    private UserCouponMapper userCouponMapper;
    @Mock
    private SeckillRequestMapper requestMapper;
    @Mock
    private SeckillRedisLedger ledger;
    @InjectMocks
    private SeckillReconciliationService service;

    @Test
    void activeReservationIsSubtractedFromRedisAvailableStock() {
        when(couponMapper.getById(10L)).thenReturn(Coupon.builder()
                .id(10L).type(Coupon.TYPE_SECKILL).remainingStock(100).build());
        when(userCouponMapper.listUserIdsByCouponId(10L)).thenReturn(Collections.singletonList(1L));
        when(userCouponMapper.countByCouponId(10L)).thenReturn(1);
        when(ledger.allActiveRequestIds(10L)).thenReturn(Collections.singleton("request-1"));
        when(ledger.get("request-1")).thenReturn(PendingRequest.builder()
                .requestId("request-1").couponId(10L).userId(2L).status("PUBLISHED")
                .attempt(1).createdAt(1L).build());
        when(ledger.availableStock(10L)).thenReturn(99);
        when(ledger.protectedUserCount(10L)).thenReturn(2L);
        when(ledger.isUserProtected(10L, 1L)).thenReturn(true);
        when(ledger.isUserProtected(10L, 2L)).thenReturn(true);

        SeckillReconciliationVO result = service.reconcile(10L);

        assertEquals(1, result.getActiveReservationCount());
        assertEquals(99, result.getExpectedRedisStock());
        assertEquals(0, result.getStockDrift());
        assertTrue(result.getConsistent());
    }
}
