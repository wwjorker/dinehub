package com.dinehub.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 订单超时取消的延迟队列拓扑(TTL+死信,不依赖延迟插件):
 * 下单时投递到延迟队列(无消费者),消息滞留至TTL过期后经死信交换机
 * 进入取消队列,由消费者校验订单状态后取消。
 * 同一队列的TTL必须一致(队头阻塞),本场景支付超时固定,正好适用;
 * 若需要多种TTL应改用延迟插件或时间轮。注意修改TTL需删除重建队列。
 */
@Configuration
public class OrderCancelMQConfiguration {

    public static final String ORDER_DELAY_EXCHANGE = "order.delay.exchange";
    public static final String ORDER_DELAY_QUEUE = "order.delay.queue";
    public static final String ORDER_DELAY_ROUTING_KEY = "order.create";

    public static final String ORDER_CANCEL_EXCHANGE = "order.cancel.exchange";
    public static final String ORDER_CANCEL_QUEUE = "order.cancel.queue";
    public static final String ORDER_CANCEL_ROUTING_KEY = "order.cancel";

    /**
     * 待支付订单的超时时长,同时作为延迟队列TTL与兜底扫描的判定阈值
     */
    @Value("${dinehub.order.pay-timeout-millis:900000}")
    private long payTimeoutMillis;

    @Bean
    public DirectExchange orderDelayExchange() {
        return new DirectExchange(ORDER_DELAY_EXCHANGE);
    }

    @Bean
    public Queue orderDelayQueue() {
        return QueueBuilder.durable(ORDER_DELAY_QUEUE)
                .withArgument("x-message-ttl", payTimeoutMillis)
                .withArgument("x-dead-letter-exchange", ORDER_CANCEL_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ORDER_CANCEL_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding orderDelayBinding() {
        return BindingBuilder.bind(orderDelayQueue()).to(orderDelayExchange()).with(ORDER_DELAY_ROUTING_KEY);
    }

    @Bean
    public DirectExchange orderCancelExchange() {
        return new DirectExchange(ORDER_CANCEL_EXCHANGE);
    }

    @Bean
    public Queue orderCancelQueue() {
        return QueueBuilder.durable(ORDER_CANCEL_QUEUE).build();
    }

    @Bean
    public Binding orderCancelBinding() {
        return BindingBuilder.bind(orderCancelQueue()).to(orderCancelExchange()).with(ORDER_CANCEL_ROUTING_KEY);
    }
}
