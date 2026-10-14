package com.dinehub.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class OrdersConfirmDTO implements Serializable {

    private Long id;
    //订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消；退款由支付状态表示
    private Integer status;

}
