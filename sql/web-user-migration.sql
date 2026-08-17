-- 网页用户端登录改造。执行前请备份 sky_take_out 数据库。
ALTER TABLE `user`
    ADD COLUMN `password` VARCHAR(100) NULL COMMENT 'BCrypt密码哈希' AFTER `phone`,
    ADD UNIQUE KEY `uk_user_phone` (`phone`);
