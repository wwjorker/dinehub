-- Flash-sale coupons: coupon templates plus per-user holdings.
-- Amounts are stored in cents; uk_user_coupon is the last line of defence
-- against duplicate grabs when Redis state is lost.

CREATE TABLE `coupon` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title` varchar(64) NOT NULL COMMENT '券标题,如 满30减10',
  `type` tinyint NOT NULL DEFAULT '1' COMMENT '类型 1普通券 2秒杀券',
  `discount_amount` int NOT NULL COMMENT '优惠金额,单位分',
  `min_amount` int NOT NULL DEFAULT '0' COMMENT '使用门槛,单位分',
  `total_stock` int NOT NULL COMMENT '发行总量',
  `remaining_stock` int NOT NULL COMMENT '剩余库存,DB侧最终账本',
  `seckill_start_time` datetime DEFAULT NULL COMMENT '秒杀开始时间',
  `seckill_end_time` datetime DEFAULT NULL COMMENT '秒杀结束时间',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态 0未发布 1已发布',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券模板';

CREATE TABLE `user_coupon` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `coupon_id` bigint NOT NULL COMMENT '优惠券id',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态 1未使用 2已使用 3已过期',
  `order_id` bigint DEFAULT NULL COMMENT '核销订单id',
  `create_time` datetime DEFAULT NULL COMMENT '领取时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_coupon` (`user_id`,`coupon_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户持券';
