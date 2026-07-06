package com.dinehub.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 秒杀异步落库的队列拓扑:业务队列 + 死信队列。
 * 消费重试耗尽后消息进入死信队列,由死信消费者做补偿(回补Redis库存)。
 */
@Configuration
public class RabbitMQConfiguration {

    public static final String SECKILL_EXCHANGE = "seckill.exchange";
    public static final String SECKILL_QUEUE = "seckill.queue";
    public static final String SECKILL_ROUTING_KEY = "seckill.order";

    public static final String SECKILL_DLX = "seckill.dlx";
    public static final String SECKILL_DEAD_QUEUE = "seckill.dead.queue";
    public static final String SECKILL_DEAD_ROUTING_KEY = "seckill.dead";

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public DirectExchange seckillExchange() {
        return new DirectExchange(SECKILL_EXCHANGE);
    }

    @Bean
    public Queue seckillQueue() {
        return QueueBuilder.durable(SECKILL_QUEUE)
                .withArgument("x-dead-letter-exchange", SECKILL_DLX)
                .withArgument("x-dead-letter-routing-key", SECKILL_DEAD_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding seckillBinding() {
        return BindingBuilder.bind(seckillQueue()).to(seckillExchange()).with(SECKILL_ROUTING_KEY);
    }

    @Bean
    public DirectExchange seckillDlx() {
        return new DirectExchange(SECKILL_DLX);
    }

    @Bean
    public Queue seckillDeadQueue() {
        return QueueBuilder.durable(SECKILL_DEAD_QUEUE).build();
    }

    @Bean
    public Binding seckillDeadBinding() {
        return BindingBuilder.bind(seckillDeadQueue()).to(seckillDlx()).with(SECKILL_DEAD_ROUTING_KEY);
    }
}
