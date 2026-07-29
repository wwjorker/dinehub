package com.dinehub.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户持券
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCoupon implements Serializable {

    /**
     * 状态 1未使用 2已使用 3已过期
     */
    public static final Integer UNUSED = 1;
    public static final Integer USED = 2;
    public static final Integer EXPIRED = 3;
    public static final Integer RESERVED = 4;

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long userId;

    private Long couponId;

    //状态 1未使用 2已使用 3已过期
    private Integer status;

    //核销订单id
    private Long orderId;

    //领取时间
    private LocalDateTime createTime;
}
