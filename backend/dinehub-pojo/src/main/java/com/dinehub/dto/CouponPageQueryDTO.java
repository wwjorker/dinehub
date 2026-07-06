package com.dinehub.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class CouponPageQueryDTO implements Serializable {

    private int page;

    private int pageSize;

    //券标题,模糊查询
    private String title;

    //状态 0未发布 1已发布
    private Integer status;
}
