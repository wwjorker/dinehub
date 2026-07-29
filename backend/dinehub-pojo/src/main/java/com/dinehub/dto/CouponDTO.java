package com.dinehub.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
public class CouponDTO implements Serializable {

    private Long id;

    //券标题
    @NotBlank(message = "活动标题不能为空")
    @Size(max = 50, message = "活动标题不能超过50个字符")
    private String title;

    //类型 1普通券 2秒杀券
    @NotNull(message = "优惠券类型不能为空")
    private Integer type;

    //优惠金额,单位分
    @NotNull(message = "优惠金额不能为空")
    @Min(value = 1, message = "优惠金额必须大于0")
    private Integer discountAmount;

    //使用门槛,单位分
    @NotNull(message = "使用门槛不能为空")
    @Min(value = 0, message = "使用门槛不能小于0")
    private Integer minAmount;

    //发行总量
    @NotNull(message = "发行库存不能为空")
    @Min(value = 1, message = "发行库存至少为1")
    @Max(value = 1000000, message = "发行库存不能超过1000000")
    private Integer totalStock;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime seckillStartTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime seckillEndTime;
}
