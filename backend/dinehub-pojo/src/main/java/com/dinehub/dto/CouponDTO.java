package com.dinehub.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class CouponDTO implements Serializable {

    private Long id;

    //券标题
    private String title;

    //类型 1普通券 2秒杀券
    private Integer type;

    //优惠金额,单位分
    private Integer discountAmount;

    //使用门槛,单位分
    private Integer minAmount;

    //发行总量
    private Integer totalStock;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime seckillStartTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime seckillEndTime;
}
