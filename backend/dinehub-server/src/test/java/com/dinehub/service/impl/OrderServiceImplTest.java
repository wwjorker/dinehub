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
import com.dinehub.mapper.UserCouponMapper;
import com.dinehub.utils.WeChatPayUtil;
import com.dinehub.vo.OrderSubmitVO;
import com.dinehub.vo.UserCouponVO;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
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
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doAnswer;
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
    private UserCouponMapper userCouponMapper;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private RedisTemplate redisTemplate;
    @Mock
    private ValueOperations valueOperations;
    @Mock
    private com.dinehub.properties.WeChatProperties weChatProperties;
    @Mock
    private WeChatPayUtil weChatPayUtil;

    @InjectMocks
    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        BaseContext.setCurrentId(USER_ID);
        ReflectionTestUtils.setField(orderService, "cancelMode", "mq");
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get("SHOP_STATUS")).thenReturn(1);
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
        dto.setRequestId("req-dine-in");
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
        dto.setRequestId("req-task-mode");
        dto.setTableToken("tok-7");
        dto.setAmount(new BigDecimal("36.00"));

        orderService.submitOrder(dto);

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), (Object) any());
    }

    @Test
    void deliveryOrderStillUsesAddressBook() {
        AddressBook addressBook = new AddressBook();
        addressBook.setId(3L);
        addressBook.setUserId(USER_ID);
        addressBook.setProvinceName("江苏省");
        addressBook.setCityName("南京市");
        addressBook.setDistrictName("玄武区");
        addressBook.setDetail("中山路100号");
        addressBook.setPhone("13800000000");
        addressBook.setConsignee("张三");
        when(addressBookMapper.getById(3L)).thenReturn(addressBook);
        stubCartWithOneItem();

        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setRequestId("req-delivery");
        dto.setAddressBookId(3L);
        dto.setAmount(new BigDecimal("36.00"));

        orderService.submitOrder(dto);

        ArgumentCaptor<Orders> captor = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).insert(captor.capture());
        Orders saved = captor.getValue();
        assertEquals(Orders.DELIVERY, saved.getFulfillmentType());
        assertNull(saved.getTableId());
        assertEquals("江苏省 南京市 玄武区 中山路100号", saved.getAddress());
        assertEquals("张三", saved.getConsignee());
    }

    @Test
    void dineInOrderRejectsInvalidToken() {
        when(diningTableMapper.getByToken("bad")).thenReturn(null);

        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setRequestId("req-invalid-table");
        dto.setTableToken("bad");

        assertThrows(OrderBusinessException.class, () -> orderService.submitOrder(dto));
        verify(orderMapper, never()).insert(any());
    }

    @Test
    void dineInOrderRejectsDisabledTable() {
        DiningTable table = DiningTable.builder().id(7L).name("A07").status(StatusConstant.DISABLE).build();
        when(diningTableMapper.getByToken("tok-7")).thenReturn(table);

        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setRequestId("req-disabled-table");
        dto.setTableToken("tok-7");

        assertThrows(OrderBusinessException.class, () -> orderService.submitOrder(dto));
        verify(orderMapper, never()).insert(any());
    }

    @Test
    void deliveryOrderRejectsMissingAddress() {
        when(addressBookMapper.getById(anyLong())).thenReturn(null);

        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setRequestId("req-missing-address");
        dto.setAddressBookId(99L);

        assertThrows(AddressBookBusinessException.class, () -> orderService.submitOrder(dto));
    }

    @Test
    void refundOnlyHappensAfterWinningTheCancelTransition() throws Exception {
        //已支付待接单订单,用户取消:必须先赢得状态迁移才允许退款
        Orders paid = Orders.builder().id(9L).userId(USER_ID).number("N9")
                .status(Orders.TO_BE_CONFIRMED).payStatus(Orders.PAID)
                .amount(new BigDecimal("36.00")).build();
        when(orderMapper.getById(9L)).thenReturn(paid);
        when(orderMapper.transitionStatus(any(), org.mockito.ArgumentMatchers.eq(Orders.TO_BE_CONFIRMED))).thenReturn(1);

        orderService.userCancelById(9L);

        //演示环境(无商户配置)退款闭环 = 标记已退款
        verify(orderMapper).markRefunded(9L);
    }

    @Test
    void losingTheCancelRaceNeverRefunds() {
        //并发接单抢先:迁移0行,取消失败,绝不发生退款
        Orders paid = Orders.builder().id(9L).userId(USER_ID).number("N9")
                .status(Orders.TO_BE_CONFIRMED).payStatus(Orders.PAID)
                .amount(new BigDecimal("36.00")).build();
        when(orderMapper.getById(9L)).thenReturn(paid);
        when(orderMapper.transitionStatus(any(), org.mockito.ArgumentMatchers.eq(Orders.TO_BE_CONFIRMED))).thenReturn(0);

        assertThrows(OrderBusinessException.class, () -> orderService.userCancelById(9L));
        verify(orderMapper, never()).markRefunded(anyLong());
    }

    @Test
    void payCallbackRejectsAmountMismatch() {
        Orders order = Orders.builder().id(9L).number("N9").status(Orders.PENDING_PAYMENT)
                .payStatus(Orders.UN_PAID).amount(new BigDecimal("36.00")).build();
        when(orderMapper.getByNumber("N9")).thenReturn(order);

        assertEquals(com.dinehub.service.OrderService.PayCallbackResult.REJECTED,
                orderService.paySuccess("N9", 100));
        verify(orderMapper, never()).markPaidIfPending(anyLong(), any());
    }

    @Test
    void latePaymentOnCancelledOrderTriggersRefund() {
        Orders cancelled = Orders.builder().id(9L).number("N9").status(Orders.CANCELLED)
                .payStatus(Orders.UN_PAID).amount(new BigDecimal("36.00")).build();
        when(orderMapper.getByNumber("N9")).thenReturn(cancelled);
        when(orderMapper.markPaidIfPending(anyLong(), any())).thenReturn(0);
        when(orderMapper.markRefundPendingIfCancelled(9L)).thenReturn(1);

        assertEquals(com.dinehub.service.OrderService.PayCallbackResult.LATE_REFUND_PENDING,
                orderService.paySuccess("N9", 3600));
        verify(orderMapper).markRefunded(9L);
    }

    @Test
    void processingRefundRemainsPending() throws Exception {
        Orders paid = Orders.builder().id(9L).number("N9").payStatus(Orders.REFUND_PENDING)
                .amount(new BigDecimal("36.00")).build();
        when(weChatProperties.getApiV3Key()).thenReturn("configured");
        when(weChatPayUtil.queryRefund("RN9")).thenReturn(WeChatPayUtil.RefundStatus.NOT_FOUND);
        when(weChatPayUtil.refund("N9", "RN9", paid.getAmount(), paid.getAmount()))
                .thenReturn(WeChatPayUtil.RefundStatus.PROCESSING);

        orderService.executeRefund(paid);

        verify(orderMapper, never()).markRefunded(anyLong());
        verify(orderMapper).scheduleRefundRetry(eq(9L), any(LocalDateTime.class), eq("PROCESSING"));
    }

    @Test
    void onlySuccessfulRefundIsMarkedCompleted() throws Exception {
        Orders paid = Orders.builder().id(9L).number("N9").payStatus(Orders.REFUND_PENDING)
                .amount(new BigDecimal("36.00")).build();
        when(weChatProperties.getApiV3Key()).thenReturn("configured");
        when(weChatPayUtil.queryRefund("RN9")).thenReturn(WeChatPayUtil.RefundStatus.SUCCESS);

        orderService.executeRefund(paid);

        verify(orderMapper).markRefunded(9L);
        verify(weChatPayUtil, never()).refund(anyString(), anyString(), any(), any());
    }

    @Test
    void unknownRefundResultRemainsPending() throws Exception {
        Orders paid = Orders.builder().id(9L).number("N9").payStatus(Orders.REFUND_PENDING)
                .amount(new BigDecimal("36.00")).build();
        when(weChatProperties.getApiV3Key()).thenReturn("configured");
        when(weChatPayUtil.queryRefund("RN9")).thenThrow(new java.io.IOException("timeout"));

        orderService.executeRefund(paid);

        verify(orderMapper, never()).markRefunded(anyLong());
        verify(orderMapper).scheduleRefundRetry(eq(9L), any(LocalDateTime.class),
                eq("IOException:timeout"));
    }

    @Test
    void terminalRefundFailureMovesToManualIntervention() throws Exception {
        Orders paid = Orders.builder().id(9L).number("N9").payStatus(Orders.REFUND_PENDING)
                .amount(new BigDecimal("36.00")).build();
        when(weChatProperties.getApiV3Key()).thenReturn("configured");
        when(weChatPayUtil.queryRefund("RN9")).thenReturn(WeChatPayUtil.RefundStatus.ABNORMAL);

        orderService.executeRefund(paid);

        verify(orderMapper).markRefundIntervention(9L, "STATUS:ABNORMAL");
        verify(orderMapper, never()).scheduleRefundRetry(anyLong(), any(), anyString());
    }

    @Test
    void emptyCartRejectsSubmission() {
        DiningTable table = DiningTable.builder().id(7L).name("A07").status(StatusConstant.ENABLE).build();
        when(diningTableMapper.getByToken("tok-7")).thenReturn(table);
        when(shoppingCartMapper.list(any())).thenReturn(Collections.emptyList());

        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setRequestId("req-empty-cart");
        dto.setTableToken("tok-7");

        assertThrows(ShoppingCartBusinessException.class, () -> orderService.submitOrder(dto));
    }

    @Test
    void repeatedRequestReturnsTheExistingOrderWithoutWritingAgain() {
        Orders existing = Orders.builder()
                .id(88L)
                .userId(USER_ID)
                .requestId("req-replay")
                .number("N88")
                .amount(new BigDecimal("31.00"))
                .originalAmount(new BigDecimal("36.00"))
                .discountAmount(new BigDecimal("5.00"))
                .orderTime(LocalDateTime.now())
                .build();
        when(orderMapper.getByUserIdAndRequestId(USER_ID, "req-replay")).thenReturn(existing);

        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setRequestId("req-replay");
        OrderSubmitVO result = orderService.submitOrder(dto);

        assertEquals(88L, result.getId());
        assertEquals(new BigDecimal("31.00"), result.getOrderAmount());
        verify(orderMapper, never()).insert(any());
        verify(shoppingCartMapper, never()).deleteByUserId(anyLong());
    }

    @Test
    void closedShopIsEnforcedByTheServiceLayer() {
        when(valueOperations.get("SHOP_STATUS")).thenReturn(0);
        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setRequestId("req-closed");

        assertThrows(OrderBusinessException.class, () -> orderService.submitOrder(dto));
        verify(orderMapper, never()).insert(any());
    }

    @Test
    void missingShopStatusFailsClosed() {
        when(valueOperations.get("SHOP_STATUS")).thenReturn(null);
        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setRequestId("req-status-missing");

        assertThrows(OrderBusinessException.class, () -> orderService.submitOrder(dto));
        verify(orderMapper, never()).insert(any());
    }

    @Test
    void couponDiscountIsCalculatedAndReservedByTheServer() {
        DiningTable table = DiningTable.builder().id(7L).name("A07")
                .status(StatusConstant.ENABLE).build();
        when(diningTableMapper.getByToken("tok-7")).thenReturn(table);
        stubCartWithOneItem();

        UserCouponVO coupon = UserCouponVO.builder()
                .id(10L)
                .couponId(5L)
                .discountAmount(500)
                .minAmount(3000)
                .status(com.dinehub.entity.UserCoupon.UNUSED)
                .seckillEndTime(LocalDateTime.now().plusDays(1))
                .build();
        when(userCouponMapper.getByIdAndUserId(10L, USER_ID)).thenReturn(coupon);
        doAnswer(invocation -> {
            Orders saved = invocation.getArgument(0);
            saved.setId(91L);
            return null;
        }).when(orderMapper).insert(any(Orders.class));
        when(userCouponMapper.reserveIfUnused(10L, USER_ID, 91L)).thenReturn(1);

        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setRequestId("req-coupon");
        dto.setTableToken("tok-7");
        dto.setUserCouponId(10L);

        OrderSubmitVO result = orderService.submitOrder(dto);

        assertEquals(new BigDecimal("36.00"), result.getOriginalAmount());
        assertEquals(new BigDecimal("5.00"), result.getDiscountAmount());
        assertEquals(new BigDecimal("31.00"), result.getOrderAmount());
        verify(userCouponMapper).reserveIfUnused(10L, USER_ID, 91L);
    }

    @Test
    void adminDetailsDoNotReuseUserOwnershipChecks() {
        Orders anotherUsersOrder = Orders.builder().id(77L).userId(999L).build();
        when(orderMapper.getById(77L)).thenReturn(anotherUsersOrder);
        when(orderDetailMapper.getByOrderId(77L)).thenReturn(Collections.emptyList());

        assertEquals(77L, orderService.detailsForAdmin(77L).getId());
        assertThrows(OrderBusinessException.class,
                () -> orderService.detailsForCurrentUser(77L));
    }
}
