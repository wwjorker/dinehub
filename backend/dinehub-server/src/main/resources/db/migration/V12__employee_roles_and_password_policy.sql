ALTER TABLE employee
    ADD COLUMN role varchar(16) NOT NULL DEFAULT 'OPERATOR' COMMENT 'ADMIN/OPERATOR' AFTER status,
    ADD COLUMN must_change_password tinyint(1) NOT NULL DEFAULT 1 COMMENT '首次登录必须修改密码' AFTER role;

UPDATE employee
SET role = 'ADMIN', must_change_password = 0
WHERE username = 'admin';
