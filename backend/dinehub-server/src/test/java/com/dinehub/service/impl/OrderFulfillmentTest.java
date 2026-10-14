package com.dinehub.service.impl;

import com.dinehub.entity.Orders;
import com.dinehub.exception.OrderBusinessException;
import com.dinehub.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderFulfillmentTest {
    @Mock private OrderMapper orderMapper;
    @InjectMocks private OrderServiceImpl orderService;

    private void stubOrder(Integer fulfillment, Integer status) {
        when(orderMapper.getById(9L)).thenReturn(Orders.builder().id(9L)
                .fulfillmentType(fulfillment).status(status).payStatus(Orders.PAID).build());
    }

    @ParameterizedTest
    @CsvSource({"2,3", "1,4", "2,4"})
    void completesOnlyFromTheCorrectFulfillmentState(int fulfillment, int status) {
        stubOrder(fulfillment, status);
        when(orderMapper.transitionStatus(any(), eq(status))).thenReturn(1);
        orderService.complete(9L);
        ArgumentCaptor<Orders> target = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).transitionStatus(target.capture(), eq(status));
        assertEquals(Orders.COMPLETED, target.getValue().getStatus());
        assertEquals(9L, target.getValue().getId());
        assertNotNull(target.getValue().getDeliveryTime());
        assertNull(target.getValue().getPayStatus());
    }

    @ParameterizedTest
    @CsvSource({"2,1", "2,2", "2,5", "2,6", "1,1", "1,2", "1,3", "1,5", "1,6", "9,4", ",4", "2,"})
    void rejectsEarlyRepeatedCancelledAndUnknownCompletions(Integer fulfillment, Integer status) {
        stubOrder(fulfillment, status);
        assertThrows(OrderBusinessException.class, () -> orderService.complete(9L));
        verify(orderMapper, never()).transitionStatus(any(), any());
    }

    @ParameterizedTest
    @CsvSource({"2,3", "1,4", "2,4"})
    void completionCannotOverwriteAConcurrentCancellation(int fulfillment, int status) {
        stubOrder(fulfillment, status);
        when(orderMapper.transitionStatus(any(), eq(status))).thenReturn(0);
        assertThrows(OrderBusinessException.class, () -> orderService.complete(9L));
        verify(orderMapper).transitionStatus(any(), eq(status));
        verifyNoMoreInteractions(orderMapper);
    }

    @Test
    void missingOrderCannotBeCompleted() {
        assertThrows(OrderBusinessException.class, () -> orderService.complete(9L));
        verify(orderMapper, never()).transitionStatus(any(), any());
    }

    @Test
    void deliveryStillMovesConfirmedDeliveryOrderToDispatch() {
        stubOrder(Orders.DELIVERY, Orders.CONFIRMED);
        when(orderMapper.transitionStatus(any(), eq(Orders.CONFIRMED))).thenReturn(1);
        orderService.delivery(9L);
        ArgumentCaptor<Orders> target = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).transitionStatus(target.capture(), eq(Orders.CONFIRMED));
        assertEquals(Orders.DELIVERY_IN_PROGRESS, target.getValue().getStatus());
        assertNull(target.getValue().getPayStatus());
    }

    @ParameterizedTest
    @CsvSource({"2,3", "1,1", "1,2", "1,4", "1,5", "1,6", ",3", "9,3"})
    void dineInAndInvalidStatesCannotEnterDelivery(Integer fulfillment, Integer status) {
        stubOrder(fulfillment, status);
        assertThrows(OrderBusinessException.class, () -> orderService.delivery(9L));
        verify(orderMapper, never()).transitionStatus(any(), any());
    }

    @Test
    void dispatchCannotOverwriteAConcurrentCancellation() {
        stubOrder(Orders.DELIVERY, Orders.CONFIRMED);
        when(orderMapper.transitionStatus(any(), eq(Orders.CONFIRMED))).thenReturn(0);
        assertThrows(OrderBusinessException.class, () -> orderService.delivery(9L));
    }
}
