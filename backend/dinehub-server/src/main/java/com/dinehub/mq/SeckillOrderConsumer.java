package com.dinehub.mq;

import com.dinehub.config.RabbitMQConfiguration;
import com.dinehub.entity.UserCoupon;
import com.dinehub.mapper.CouponMapper;
import com.dinehub.mapper.UserCouponMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 抢券成功消息的落库消费者。
 * 幂等性由 uk_user_coupon 唯一键兜底:同一消息重复投递时插入冲突,视为已处理,正常ACK。
 * 未预期异常向外抛出,交给重试机制;重试耗尽后进入死信队列做补偿。
 */
@Component
@Slf4j
public class SeckillOrderConsumer {

    @Autowired
    private UserCouponMapper userCouponMapper;
    @Autowired
    private CouponMapper couponMapper;

    @RabbitListener(queues = RabbitMQConfiguration.SECKILL_QUEUE)
    @Transactional
    public void onMessage(SeckillMessage message) {
        log.info("seckill order received: {}", message);
        try {
            userCouponMapper.insert(UserCoupon.builder()
                    .userId(message.getUserId())
                    .couponId(message.getCouponId())
                    .status(UserCoupon.UNUSED)
                    .createTime(LocalDateTime.now())
                    .build());
        } catch (DuplicateKeyException e) {
            log.warn("duplicate seckill message ignored: {}", message);
            return;
        }
        int rows = couponMapper.deductStock(message.getCouponId());
        if (rows == 0) {
            //DB库存扣不动说明账本漂移:整个事务(含已插入的券)回滚,
            //经重试进入死信后原子回补Redis名额,保持两侧账本守恒
            throw new IllegalStateException("db stock exhausted for coupon " + message.getCouponId()
                    + ", requestId " + message.getRequestId());
        }
    }
}
