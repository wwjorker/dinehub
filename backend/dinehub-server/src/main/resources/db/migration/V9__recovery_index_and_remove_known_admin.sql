-- Support durable recovery without scanning the whole terminal ledger.
CREATE INDEX `idx_seckill_request_status_update`
  ON `seckill_request` (`status`, `update_time`);

-- Historical migrations created a public demo administrator. Keep the
-- immutable migration history, but remove only the untouched known account.
-- The dev profile recreates its demo administrator after Flyway completes.
DELETE FROM `employee`
WHERE `username` = 'admin'
  AND `password` IN (
    'e10adc3949ba59abbe56e057f20f883e',
    '$2a$10$guUgfBracBiCkF7VvcNm6OWu0PE./m22dBTxW2g.XfWO6zsS4VaIS'
  );
