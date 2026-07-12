package com.dinehub.seckill;

import com.dinehub.mq.SeckillMessage;
import com.dinehub.seckill.SeckillTerminalService.CompensationClaim;
import com.dinehub.seckill.SeckillTerminalService.SettlementOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeckillCompensationServiceTest {

    @Mock
    private SeckillTerminalService terminalService;
    @Mock
    private SeckillRedisLedger ledger;
    @InjectMocks
    private SeckillCompensationService service;

    private SeckillMessage message() {
        return SeckillMessage.builder().requestId("request-1").couponId(10L).userId(20L).build();
    }

    @Test
    void compensationRunsClaimThenRedisThenTerminalCompletion() {
        when(terminalService.claimCompensation(message(), "failed"))
                .thenReturn(new CompensationClaim(SettlementOutcome.COMPENSATING, false));
        when(ledger.compensate(eq(message()), eq(false), any(Duration.class))).thenReturn(true);

        assertEquals(SettlementOutcome.COMPENSATED, service.compensate(message(), "failed"));

        org.mockito.InOrder order = inOrder(terminalService, ledger);
        order.verify(terminalService).claimCompensation(message(), "failed");
        order.verify(ledger).markCompensating(message(), "failed");
        order.verify(ledger).compensate(eq(message()), eq(false), any(Duration.class));
        order.verify(terminalService).finishCompensation("request-1", "failed");
        order.verify(ledger).cleanup(message());
    }

    @Test
    void settledWinnerPreventsRedisRefund() {
        when(terminalService.claimCompensation(message(), "late"))
                .thenReturn(new CompensationClaim(SettlementOutcome.SETTLED, false));

        assertEquals(SettlementOutcome.SETTLED, service.compensate(message(), "late"));

        verify(ledger, never()).compensate(any(), eq(false), any(Duration.class));
        verify(ledger).cleanup(message());
    }

    @Test
    void repeatedCompensationDoesNotRefundAgain() {
        when(terminalService.claimCompensation(message(), "duplicate"))
                .thenReturn(new CompensationClaim(SettlementOutcome.COMPENSATED, false));

        assertEquals(SettlementOutcome.COMPENSATED, service.compensate(message(), "duplicate"));
        verify(ledger, never()).compensate(any(), eq(false), any(Duration.class));
    }
}
