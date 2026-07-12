package com.dinehub.mq;

import com.dinehub.seckill.SeckillCompensationService;
import com.dinehub.seckill.SeckillRedisLedger;
import com.dinehub.seckill.SeckillTerminalService;
import com.dinehub.seckill.SeckillTerminalService.SettlementOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeckillOrderConsumerTest {

    @Mock
    private SeckillTerminalService terminalService;
    @Mock
    private SeckillCompensationService compensationService;
    @Mock
    private SeckillRedisLedger ledger;
    @InjectMocks
    private SeckillOrderConsumer consumer;

    private SeckillMessage message() {
        return SeckillMessage.builder().userId(1L).couponId(10L).requestId("req-1").build();
    }

    @Test
    void settledDeliveryCleansTransientLedger() {
        when(terminalService.settle(message())).thenReturn(SettlementOutcome.SETTLED);

        consumer.onMessage(message());

        verify(ledger).cleanup(message());
        verify(compensationService, never()).compensate(message(), "late delivery after compensation claim");
    }

    @Test
    void duplicateSettledDeliveryIsIdempotent() {
        when(terminalService.settle(message())).thenReturn(SettlementOutcome.SETTLED);

        consumer.onMessage(message());
        consumer.onMessage(message());

        verify(terminalService, org.mockito.Mockito.times(2)).settle(message());
        verify(ledger, org.mockito.Mockito.times(2)).cleanup(message());
    }

    @Test
    void lateDeliveryContinuesCompensationInsteadOfGranting() {
        when(terminalService.settle(message())).thenReturn(SettlementOutcome.COMPENSATING);

        consumer.onMessage(message());

        verify(compensationService).compensate(message(), "late delivery after compensation claim");
        verify(ledger, never()).cleanup(message());
    }

    @Test
    void persistenceFailurePropagatesForBrokerRetry() {
        when(terminalService.settle(message())).thenThrow(new IllegalStateException("db down"));

        assertThrows(IllegalStateException.class, () -> consumer.onMessage(message()));
        verify(ledger, never()).cleanup(message());
    }
}
