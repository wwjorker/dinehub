package com.dinehub.task;

import com.dinehub.entity.Orders;
import com.dinehub.mapper.OrderMapper;
import com.dinehub.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderTaskTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderService orderService;
    @InjectMocks
    private OrderTask orderTask;

    @Test
    void autoCompleteUsesConditionalTransition() {
        Orders delivering = Orders.builder().id(7L).status(Orders.DELIVERY_IN_PROGRESS).build();
        when(orderMapper.getByStatusAndOrderTimeLT(eq(Orders.DELIVERY_IN_PROGRESS), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(delivering));
        when(orderMapper.transitionStatus(any(Orders.class), eq(Orders.DELIVERY_IN_PROGRESS))).thenReturn(1);

        orderTask.processDeliveryOrder();

        ArgumentCaptor<Orders> target = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).transitionStatus(target.capture(), eq(Orders.DELIVERY_IN_PROGRESS));
        assertEquals(Orders.COMPLETED, target.getValue().getStatus());
        verify(orderMapper, never()).update(any());
    }
}
