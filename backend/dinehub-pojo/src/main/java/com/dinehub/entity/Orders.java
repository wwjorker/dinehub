package com.dinehub.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Orders implements Serializable {

    /**
     * 订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
     */
    public static final Integer PENDING_PAYMENT = 1;
    public static final Integer TO_BE_CONFIRMED = 2;
    public static final Integer CONFIRMED = 3;
    public static final Integer DELIVERY_IN_PROGRESS = 4;
    public static final Integer COMPLETED = 5;
    public static final Integer CANCELLED = 6;

    /**
     * 履约方式 1外卖配送 2到店堂食
     */
    public static final Integer DELIVERY = 1;
    public static final Integer DINE_IN = 2;

    /**
     * 支付状态 0未支付 1已支付 2已退款 3退款中
     */
    public static final Integer UN_PAID = 0;
    public static final Integer PAID = 1;
    public static final Integer REFUND = 2;
    //已取得退款权但退款尚未确认完成,由兜底扫描重试
    public static final Integer REFUND_PENDING = 3;

    private static final long serialVersionUID = 1L;

    private Long id;

    //订单号
    private String number;

    //订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消；退款由支付状态表示
    private Integer status;

    //履约方式 1外卖 2堂食
    private Integer fulfillmentType;

    //堂食桌台id
    private Long tableId;

    //下单用户id
    private Long userId;

    // Client-generated request id, unique per user.
    private String requestId;

    // Coupon holding reserved for this order.
    private Long userCouponId;

    //地址id
    private Long addressBookId;

    //下单时间
    private LocalDateTime orderTime;

    //结账时间
    private LocalDateTime checkoutTime;

    //支付方式 1微信；2为预留值，当前未接入支付宝
    private Integer payMethod;

    //支付状态 0未支付 1已支付 2已退款 3退款中
    private Integer payStatus;

    //退款兜底调度:仅 payStatus=3 时使用
    private LocalDateTime refundNextRetryAt;

    //退款调用累计尝试次数
    private Integer refundAttemptCount;

    //最近一次退款状态或异常摘要(不保存敏感响应正文)
    private String refundLastError;

    //实收金额
    private BigDecimal amount;

    // Amount before coupon discount.
    private BigDecimal originalAmount;

    // Coupon discount applied by the server.
    private BigDecimal discountAmount;

    //备注
    private String remark;

    //用户名
    private String userName;

    //手机号
    private String phone;

    //地址
    private String address;

    //收货人
    private String consignee;

    //订单取消原因
    private String cancelReason;

    //订单拒绝原因
    private String rejectionReason;

    //订单取消时间
    private LocalDateTime cancelTime;

    //预计送达时间
    private LocalDateTime estimatedDeliveryTime;

    //配送状态  1立即送出  0选择具体时间
    private Integer deliveryStatus;

    //送达时间
    private LocalDateTime deliveryTime;

    //打包费(堂食单为空)
    private Integer packAmount;

    //餐具数量(堂食单为空)
    private Integer tablewareNumber;

    //餐具数量状态  1按餐量提供  0选择具体数量
    private Integer tablewareStatus;
}
