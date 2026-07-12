package com.dinehub.seckill;

import com.dinehub.entity.SeckillRequest;
import com.dinehub.mapper.CouponMapper;
import com.dinehub.mapper.SeckillRequestMapper;
import com.dinehub.mapper.UserCouponMapper;
import com.dinehub.mq.SeckillMessage;
import com.dinehub.seckill.SeckillTerminalService.CompensationClaim;
import com.dinehub.seckill.SeckillTerminalService.SettlementOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeckillTerminalServiceTest {

    @Mock
    private SeckillRequestMapper requestMapper;
    @Mock
    private UserCouponMapper userCouponMapper;
    @Mock
    private CouponMapper couponMapper;
    @Mock
    private SeckillMetrics metrics;
    @InjectMocks
    private SeckillTerminalService service;

    private SeckillMessage message() {
        return SeckillMessage.builder().requestId("request-1").couponId(10L).userId(20L).build();
    }

    private SeckillRequest terminal(int status) {
        return SeckillRequest.builder().requestId("request-1").couponId(10L).userId(20L).status(status).build();
    }

    @Test
    void settlementCommitsCouponAndDbStockBehindOneTerminalClaim() {
        when(requestMapper.insertIgnore(any())).thenReturn(1);
        when(couponMapper.deductStock(10L)).thenReturn(1);

        assertEquals(SettlementOutcome.SETTLED, service.settle(message()));

        verify(userCouponMapper).insert(any());
        verify(couponMapper).deductStock(10L);
    }

    @Test
    void duplicateDeliveryReadsSettledTerminalWithoutSecondDeduction() {
        when(requestMapper.insertIgnore(any())).thenReturn(0);
        when(requestMapper.getByRequestId("request-1")).thenReturn(terminal(SeckillRequest.SETTLED));

        assertEquals(SettlementOutcome.SETTLED, service.settle(message()));
        verify(userCouponMapper, never()).insert(any());
        verify(couponMapper, never()).deductStock(10L);
    }

    @Test
    void userCouponConflictRollsOutToBrokerRetry() {
        when(requestMapper.insertIgnore(any())).thenReturn(1);
        org.mockito.Mockito.doThrow(new DuplicateKeyException("uk_user_coupon"))
                .when(userCouponMapper).insert(any());

        assertThrows(DuplicateKeyException.class, () -> service.settle(message()));
        verify(couponMapper, never()).deductStock(10L);
    }

    @Test
    void compensationClaimKeepsBoughtWhenAnotherRequestAlreadyGrantedUser() {
        when(requestMapper.insertIgnore(any())).thenReturn(1);
        when(userCouponMapper.countByUserAndCoupon(20L, 10L)).thenReturn(1);

        CompensationClaim claim = service.claimCompensation(message(), "failed");

        assertEquals(SettlementOutcome.COMPENSATING, claim.getOutcome());
        assertTrue(claim.isKeepBought());
    }

    @Test
    void settledRequestCannotBeCompensated() {
        when(requestMapper.insertIgnore(any())).thenReturn(0);
        when(requestMapper.getByRequestId("request-1")).thenReturn(terminal(SeckillRequest.SETTLED));

        assertEquals(SettlementOutcome.SETTLED,
                service.claimCompensation(message(), "late dead letter").getOutcome());
        verify(userCouponMapper, never()).countByUserAndCoupon(20L, 10L);
    }

    @Test
    void conflictingRequestIdentityIsRejected() {
        when(requestMapper.insertIgnore(any())).thenReturn(0);
        SeckillRequest conflict = terminal(SeckillRequest.SETTLED);
        conflict.setUserId(99L);
        when(requestMapper.getByRequestId("request-1")).thenReturn(conflict);

        assertThrows(IllegalStateException.class, () -> service.settle(message()));
    }
}
