-- One durable terminal decision per successful Redis admission.
-- Redis owns transient delivery state; this table arbitrates settlement versus compensation.
CREATE TABLE `seckill_request` (
  `request_id` varchar(36) NOT NULL,
  `coupon_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `status` tinyint NOT NULL COMMENT '1 settled, 2 compensating, 3 compensated',
  `last_error` varchar(255) DEFAULT NULL,
  `create_time` datetime NOT NULL,
  `update_time` datetime NOT NULL,
  PRIMARY KEY (`request_id`),
  KEY `idx_seckill_request_coupon_status` (`coupon_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='seckill terminal ledger';
