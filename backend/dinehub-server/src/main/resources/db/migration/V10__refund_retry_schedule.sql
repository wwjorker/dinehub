-- Refund retries must advance through the backlog instead of repeatedly selecting
-- the same unordered first 50 rows.
ALTER TABLE `orders`
  ADD COLUMN `refund_next_retry_at` datetime DEFAULT NULL COMMENT 'next automatic refund query time',
  ADD COLUMN `refund_attempt_count` int NOT NULL DEFAULT 0 COMMENT 'automatic refund attempts',
  ADD COLUMN `refund_last_error` varchar(255) DEFAULT NULL COMMENT 'last sanitized refund status';

UPDATE `orders`
SET `refund_next_retry_at` = NOW()
WHERE `pay_status` = 3;

CREATE INDEX `idx_orders_refund_schedule`
  ON `orders` (`pay_status`, `refund_next_retry_at`, `id`);
