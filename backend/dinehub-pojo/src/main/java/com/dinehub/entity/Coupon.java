package com.dinehub.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 优惠券模板
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Coupon implements Serializable {

    /**
     * 类型 1普通券 2秒杀券
     */
    public static final Integer TYPE_NORMAL = 1;
    public static final Integer TYPE_SECKILL = 2;

    /**
     * 状态 0未发布 1已发布
     */
    public static final Integer STATUS_DRAFT = 0;
    public static final Integer STATUS_PUBLISHED = 1;

    private static final long serialVersionUID = 1L;

    private Long id;

    //券标题,如 满30减10
    private String title;

    //类型 1普通券 2秒杀券
    private Integer type;

    //优惠金额,单位分
    private Integer discountAmount;

    //使用门槛,单位分
    private Integer minAmount;

    //发行总量
    private Integer totalStock;

    //剩余库存,DB侧最终账本
    private Integer remainingStock;

    private LocalDateTime seckillStartTime;

    private LocalDateTime seckillEndTime;

    //状态 0未发布 1已发布
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long createUser;

    private Long updateUser;
}
