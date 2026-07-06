package com.dinehub.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 抢券成功后投递的异步落库消息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeckillMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;

    private Long couponId;

    //请求标识,用于日志追踪;幂等由 uk_user_coupon 兜底
    private String requestId;
}
