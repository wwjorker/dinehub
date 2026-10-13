-- Documentation-only correction. Preserve column types, defaults and data.
ALTER TABLE `orders`
  MODIFY COLUMN `status` int NOT NULL DEFAULT '1'
    COMMENT '订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消；退款由支付状态表示',
  MODIFY COLUMN `pay_status` tinyint NOT NULL DEFAULT '0'
    COMMENT '支付状态 0未支付 1已支付 2已退款 3退款中',
  MODIFY COLUMN `pay_method` int NOT NULL DEFAULT '1'
    COMMENT '支付方式 1微信；2为预留值，当前未接入支付宝';
