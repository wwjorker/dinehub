-- Upgrade the seeded admin account from unsalted MD5 to BCrypt (still the
-- well-known dev default password). Legacy MD5 accounts created elsewhere
-- migrate transparently on their next successful login.

UPDATE `employee`
SET `password` = '$2a$10$guUgfBracBiCkF7VvcNm6OWu0PE./m22dBTxW2g.XfWO6zsS4VaIS'
WHERE `username` = 'admin'
  AND `password` = 'e10adc3949ba59abbe56e057f20f883e';
