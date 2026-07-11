-- Every user identity maps to exactly one row; concurrent first-time logins
-- (WeChat or demo) rely on this key to collapse into a single account.

ALTER TABLE `user`
  ADD UNIQUE KEY `uk_user_openid` (`openid`);
