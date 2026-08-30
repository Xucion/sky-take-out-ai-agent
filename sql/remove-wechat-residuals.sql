-- 移除网页端不再使用的微信用户标识。执行前请备份 sky_take_out 数据库。
ALTER TABLE `user`
    DROP COLUMN `openid`;
