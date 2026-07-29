package com.dinehub.service;

import com.dinehub.dto.*;
import com.dinehub.result.PageResult;
import com.dinehub.vo.OrderPaymentVO;
import com.dinehub.vo.OrderStatisticsVO;
import com.dinehub.vo.OrderSubmitVO;
import com.dinehub.vo.OrderVO;

public interface OrderService {
    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO);

    /**
     * 订单支付
     * @param ordersPaymentDTO
     * @return
     */
    OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception;

    /**
     * 支付成功，修改订单状态
     * @param outTradeNo
     */
    /**
     * 支付回调处理结果,决定给微信的应答:
     * CONFIRMED/LATE_REFUND_PENDING 确认消费;REJECTED 拒绝,等待微信重发并人工介入
     */
    enum PayCallbackResult { CONFIRMED, LATE_REFUND_PENDING, REJECTED }

    PayCallbackResult paySuccess(String outTradeNo, Integer payerTotalFen);

    /**
     * 重试停留在退款中的订单(兜底扫描调用)
     */
    void retryPendingRefunds();

    /**
     * 用户端订单分页查询
     * @param page
     * @param pageSize
     * @param status
     * @return
     */
    PageResult pageQuery4User(int page, int pageSize, Integer status);

    /**
     * 查询订单详情
     * @param id
     * @return
     */
    OrderVO detailsForCurrentUser(Long id);

    OrderVO detailsForAdmin(Long id);

    /**
     * Development-only simulated payment. The exposing controller is only
     * loaded under the dev profile.
     */
    void demoPay(Long id);

    /**
     * 用户取消订单
     * @param id
     */
    void userCancelById(Long id) throws Exception;

    /**
     * 再来一单
     * @param id
     */
    void repetition(Long id);

    /**
     * 条件搜索订单
     * @param ordersPageQueryDTO
     * @return
     */
    PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 各个状态的订单数量统计
     * @return
     */
    OrderStatisticsVO statistics();

    /**
     * 接单
     *
     * @param ordersConfirmDTO
     */
    void confirm(OrdersConfirmDTO ordersConfirmDTO);

    /**
     * 拒单
     *
     * @param ordersRejectionDTO
     */
    void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception;

    /**
     * 商家取消订单
     *
     * @param ordersCancelDTO
     */
    void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception;

    /**
     * 派送订单
     *
     * @param id
     */
    void delivery(Long id);

    /**
     * 完成订单
     *
     * @param id
     */
    void complete(Long id);

    /**
     * 客户催单
     * @param id
     */
    void reminder(Long id);
}
