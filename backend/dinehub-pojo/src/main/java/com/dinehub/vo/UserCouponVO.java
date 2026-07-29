package com.dinehub.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户持券视图,含券模板信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCouponVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long couponId;

    private String title;

    //优惠金额,单位分
    private Integer discountAmount;

    //使用门槛,单位分
    private Integer minAmount;

    private Integer type;

    private LocalDateTime seckillEndTime;

    //状态 1未使用 2已使用 3已过期
    private Integer status;

    private LocalDateTime createTime;
}
