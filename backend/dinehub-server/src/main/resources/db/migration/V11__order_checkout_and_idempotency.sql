-- Complete the order checkout loop:
-- 1. user-scoped request ids make ordinary order submission idempotent;
-- 2. original/discount amounts keep settlement auditable;
-- 3. user_coupon_id links the coupon reservation to its order.

ALTER TABLE `orders`
  ADD COLUMN `request_id` varchar(64) DEFAULT NULL COMMENT '客户端请求幂等键' AFTER `user_id`,
  ADD COLUMN `user_coupon_id` bigint DEFAULT NULL COMMENT '本单使用的用户优惠券id' AFTER `request_id`,
  ADD COLUMN `original_amount` decimal(10,2) DEFAULT NULL COMMENT '优惠前金额' AFTER `pay_status`,
  ADD COLUMN `discount_amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '优惠金额' AFTER `original_amount`,
  ADD UNIQUE KEY `uk_orders_user_request` (`user_id`, `request_id`),
  ADD KEY `idx_orders_user_coupon` (`user_coupon_id`);

UPDATE `orders`
SET `original_amount` = `amount`
WHERE `original_amount` IS NULL;

ALTER TABLE `orders`
  MODIFY COLUMN `original_amount` decimal(10,2) NOT NULL COMMENT '优惠前金额';

ALTER TABLE `user_coupon`
  MODIFY COLUMN `status` tinyint NOT NULL DEFAULT '1'
    COMMENT '状态 1未使用 2已使用 3已过期 4已占用',
  ADD KEY `idx_user_coupon_order` (`order_id`);
