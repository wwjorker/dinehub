-- Dine-in ordering: tables with QR tokens, and a fulfillment dimension on orders.

CREATE TABLE `dining_table` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) NOT NULL COMMENT '桌台名称',
  `qr_token` varchar(64) NOT NULL COMMENT '扫码点单令牌',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态 0停用 1启用',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dining_table_name` (`name`),
  UNIQUE KEY `uk_dining_table_qr_token` (`qr_token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='桌台';

ALTER TABLE `orders`
  ADD COLUMN `fulfillment_type` int NOT NULL DEFAULT '1' COMMENT '履约方式 1外卖 2堂食' AFTER `status`,
  ADD COLUMN `table_id` bigint DEFAULT NULL COMMENT '堂食桌台id' AFTER `fulfillment_type`,
  MODIFY COLUMN `address_book_id` bigint DEFAULT NULL COMMENT '地址id(堂食订单为空)';
