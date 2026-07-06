package com.dinehub.mq;

import com.dinehub.config.RabbitMQConfiguration;
import com.dinehub.service.impl.CouponServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 死信消费者:落库重试耗尽的消息在这里做补偿——
 * 回补Redis库存并把用户移出已购集合,让名额重新可抢,同时留下告警日志供人工核查。
 */
@Component
@Slf4j
public class SeckillDeadLetterConsumer {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @RabbitListener(queues = RabbitMQConfiguration.SECKILL_DEAD_QUEUE)
    public void onDeadLetter(SeckillMessage message) {
        String stockKey = CouponServiceImpl.STOCK_KEY_PREFIX + message.getCouponId();
        String boughtKey = CouponServiceImpl.BOUGHT_KEY_PREFIX + message.getCouponId();
        stringRedisTemplate.opsForValue().increment(stockKey);
        stringRedisTemplate.opsForSet().remove(boughtKey, String.valueOf(message.getUserId()));
        log.error("seckill order permanently failed, stock compensated: {}", message);
    }
}
