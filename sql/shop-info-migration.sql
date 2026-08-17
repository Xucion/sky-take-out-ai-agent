-- 店铺基础信息。执行前请备份 sky_take_out 数据库。
CREATE TABLE IF NOT EXISTS `shop_info` (
    `id` BIGINT NOT NULL COMMENT '主键，单店模式固定为1',
    `address` VARCHAR(200) NOT NULL COMMENT '店铺完整地址',
    `update_time` DATETIME NOT NULL COMMENT '更新时间',
    `update_user` BIGINT NULL COMMENT '最后修改的管理员ID',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店铺基础信息';

INSERT INTO `shop_info` (`id`, `address`, `update_time`, `update_user`)
VALUES (1, '北京市海淀区上地十街10号', NOW(), NULL)
ON DUPLICATE KEY UPDATE `id` = `id`;
