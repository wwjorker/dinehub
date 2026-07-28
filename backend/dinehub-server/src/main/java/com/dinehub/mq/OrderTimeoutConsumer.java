package com.dinehub.mq;

import com.dinehub.config.OrderCancelMQConfiguration;
import com.dinehub.mapper.OrderMapper;
import com.dinehub.mapper.UserCouponMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 超时取消消费者:延迟消息到期后重查订单状态,仅"待支付→已取消"合法。
 * 用状态条件更新保证幂等,并天然规避"取消瞬间用户恰好支付"的竞态——
 * 支付回调把状态改走后,这里的条件更新影响0行,直接放过。
 */
@Component
@Slf4j
public class OrderTimeoutConsumer {

    public static final String CANCEL_REASON = "订单超时,自动取消";

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserCouponMapper userCouponMapper;

    @RabbitListener(queues = OrderCancelMQConfiguration.ORDER_CANCEL_QUEUE)
    @Transactional
    public void onOrderTimeout(Long orderId) {
        int rows = orderMapper.cancelIfPending(orderId, CANCEL_REASON, LocalDateTime.now());
        if (rows > 0) {
            userCouponMapper.releaseByOrderId(orderId);
            log.info("order {} cancelled after payment timeout", orderId);
        } else {
            log.info("order {} already paid or cancelled, nothing to do", orderId);
        }
    }
}
