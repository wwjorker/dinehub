package com.dinehub.mq;

import com.dinehub.config.RabbitMQConfiguration;
import com.dinehub.service.CouponService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 死信消费者:落库重试耗尽的消息在这里做补偿——
 * 回补Redis库存并把用户移出已购集合,让名额重新可抢,同时留下告警日志供人工核查。
 * 回补走单个Lua脚本保证原子性,并以requestId做幂等标记,重复投递不会重复加库存。
 */
@Component
@Slf4j
public class SeckillDeadLetterConsumer {

    @Autowired
    private CouponService couponService;

    @RabbitListener(queues = RabbitMQConfiguration.SECKILL_DEAD_QUEUE)
    public void onDeadLetter(SeckillMessage message) {
        boolean compensated = couponService.compensate(
                message.getCouponId(), message.getUserId(), message.getRequestId());
        if (compensated) {
            log.error("seckill order permanently failed, stock compensated: {}", message);
        } else {
            log.warn("duplicate dead letter ignored, already compensated: {}", message);
        }
    }
}
