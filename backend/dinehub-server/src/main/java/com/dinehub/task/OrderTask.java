package com.dinehub.task;

import com.dinehub.entity.Orders;
import com.dinehub.mapper.OrderMapper;
import com.dinehub.mq.OrderTimeoutConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务类,定时处理订单状态。
 * 超时取消的主链路是MQ延迟消息(见 OrderTimeoutConsumer),本扫描默认降级为
 * 低频兜底,防止消息丢失导致订单永久滞留;cancel-mode=task 时可调高频率作为对照方案。
 */
@Component
@Slf4j
public class OrderTask {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private com.dinehub.service.OrderService orderService;

    @Value("${dinehub.order.pay-timeout-millis:900000}")
    private long payTimeoutMillis;

    /**
     * 兜底扫描超时未支付订单,默认每10分钟一次
     */
    @Scheduled(cron = "${dinehub.order.timeout-scan-cron:0 0/10 * * * ?}")
    public void processTimeoutOrder() {
        LocalDateTime time = LocalDateTime.now().minus(Duration.ofMillis(payTimeoutMillis));
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.PENDING_PAYMENT, time);

        if (ordersList == null || ordersList.isEmpty()) {
            return;
        }
        log.info("兜底扫描发现超时订单 {} 笔", ordersList.size());
        for (Orders orders : ordersList) {
            //条件更新保证与MQ取消/支付回调之间幂等
            orderMapper.cancelIfPending(orders.getId(), OrderTimeoutConsumer.CANCEL_REASON, LocalDateTime.now());
        }
    }

    /**
     * 重试停留在退款中的订单:退款调用失败或结果未知时的兜底,
     * 固定退款单号保证重复调用幂等
     */
    @Scheduled(cron = "${dinehub.order.refund-retry-cron:30 0/10 * * * ?}")
    public void retryPendingRefunds() {
        orderService.retryPendingRefunds();
    }

    /**
     * 处理一直处于派送中状态的订单
     */
    @Scheduled(cron = "0 0 1 * * ?") //每天凌晨1点触发一次
    public void processDeliveryOrder() {
        LocalDateTime time = LocalDateTime.now().plusMinutes(-60);
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.DELIVERY_IN_PROGRESS, time);

        if (ordersList != null && ordersList.size() > 0) {
            for (Orders orders : ordersList) {
                orders.setStatus(Orders.COMPLETED);
                orderMapper.update(orders);
            }
        }
    }
}
