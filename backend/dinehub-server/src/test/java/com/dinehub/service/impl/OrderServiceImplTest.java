package com.dinehub.service.impl;

import com.dinehub.constant.StatusConstant;
import com.dinehub.context.BaseContext;
import com.dinehub.dto.OrdersSubmitDTO;
import com.dinehub.entity.AddressBook;
import com.dinehub.entity.DiningTable;
import com.dinehub.entity.Orders;
import com.dinehub.entity.ShoppingCart;
import com.dinehub.exception.AddressBookBusinessException;
import com.dinehub.exception.OrderBusinessException;
import com.dinehub.exception.ShoppingCartBusinessException;
import com.dinehub.mapper.AddressBookMapper;
import com.dinehub.mapper.DiningTableMapper;
import com.dinehub.mapper.OrderDetailMapper;
import com.dinehub.mapper.OrderMapper;
import com.dinehub.mapper.ShoppingCartMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    private static final Long USER_ID = 42L;

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderDetailMapper orderDetailMapper;
    @Mock
    private AddressBookMapper addressBookMapper;
    @Mock
    private DiningTableMapper diningTableMapper;
    @Mock
    private ShoppingCartMapper shoppingCartMapper;
    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        BaseContext.setCurrentId(USER_ID);
        ReflectionTestUtils.setField(orderService, "cancelMode", "mq");
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    private void stubCartWithOneItem() {
        ShoppingCart item = new ShoppingCart();
        item.setDishId(1L);
        item.setNumber(2);
        item.setAmount(new BigDecimal("18.00"));
        when(shoppingCartMapper.list(any())).thenReturn(Collections.singletonList(item));
    }

    @Test
    void dineInOrderIsBoundToTableAndSkipsAddressBook() {
        DiningTable table = DiningTable.builder().id(7L).name("A07").status(StatusConstant.ENABLE).build();
        when(diningTableMapper.getByToken("tok-7")).thenReturn(table);
        stubCartWithOneItem();

        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setTableToken("tok-7");
        dto.setAmount(new BigDecimal("36.00"));

        orderService.submitOrder(dto);

        ArgumentCaptor<Orders> captor = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).insert(captor.capture());
        Orders saved = captor.getValue();
        assertEquals(Orders.DINE_IN, saved.getFulfillmentType());
        assertEquals(7L, saved.getTableId());
        assertEquals("A07", saved.getAddress());
        assertEquals(USER_ID, saved.getUserId());
        assertNull(saved.getAddressBookId());
        //dine-in clients don't send delivery/tableware options; NOT NULL columns need defaults
        assertEquals(1, saved.getDeliveryStatus());
        assertEquals(1, saved.getTablewareStatus());
        verify(addressBookMapper, never()).getById(anyLong());
        verify(shoppingCartMapper).deleteByUserId(USER_ID);
        //mq mode schedules the payment-timeout cancellation
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), (Object) any());
    }

    @Test
    void taskModeDoesNotSendDelayMessage() {
        ReflectionTestUtils.setField(orderService, "cancelMode", "task");
        DiningTable table = DiningTable.builder().id(7L).name("A07").status(StatusConstant.ENABLE).build();
        when(diningTableMapper.getByToken("tok-7")).thenReturn(table);
        stubCartWithOneItem();

        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setTableToken("tok-7");
        dto.setAmount(new BigDecimal("36.00"));

        orderService.submitOrder(dto);

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), (Object) any());
    }

    @Test
    void deliveryOrderStillUsesAddressBook() {
        AddressBook addressBook = new AddressBook();
        addressBook.setId(3L);
        addressBook.setDetail("中山路100号");
        addressBook.setPhone("13800000000");
        addressBook.setConsignee("张三");
        when(addressBookMapper.getById(3L)).thenReturn(addressBook);
        stubCartWithOneItem();

        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setAddressBookId(3L);
        dto.setAmount(new BigDecimal("36.00"));

        orderService.submitOrder(dto);

        ArgumentCaptor<Orders> captor = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).insert(captor.capture());
        Orders saved = captor.getValue();
        assertEquals(Orders.DELIVERY, saved.getFulfillmentType());
        assertNull(saved.getTableId());
        assertEquals("中山路100号", saved.getAddress());
        assertEquals("张三", saved.getConsignee());
    }

    @Test
    void dineInOrderRejectsInvalidToken() {
        when(diningTableMapper.getByToken("bad")).thenReturn(null);

        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setTableToken("bad");

        assertThrows(OrderBusinessException.class, () -> orderService.submitOrder(dto));
        verify(orderMapper, never()).insert(any());
    }

    @Test
    void dineInOrderRejectsDisabledTable() {
        DiningTable table = DiningTable.builder().id(7L).name("A07").status(StatusConstant.DISABLE).build();
        when(diningTableMapper.getByToken("tok-7")).thenReturn(table);

        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setTableToken("tok-7");

        assertThrows(OrderBusinessException.class, () -> orderService.submitOrder(dto));
        verify(orderMapper, never()).insert(any());
    }

    @Test
    void deliveryOrderRejectsMissingAddress() {
        when(addressBookMapper.getById(anyLong())).thenReturn(null);

        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setAddressBookId(99L);

        assertThrows(AddressBookBusinessException.class, () -> orderService.submitOrder(dto));
    }

    @Test
    void emptyCartRejectsSubmission() {
        DiningTable table = DiningTable.builder().id(7L).name("A07").status(StatusConstant.ENABLE).build();
        when(diningTableMapper.getByToken("tok-7")).thenReturn(table);
        when(shoppingCartMapper.list(any())).thenReturn(Collections.emptyList());

        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setTableToken("tok-7");

        assertThrows(ShoppingCartBusinessException.class, () -> orderService.submitOrder(dto));
    }
}
