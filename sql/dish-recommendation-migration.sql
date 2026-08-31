-- 菜品推荐画像表。执行前请先确认 dish 表已经存在且使用 InnoDB。
CREATE TABLE IF NOT EXISTS dish_profile (
    dish_id BIGINT NOT NULL COMMENT '菜品ID',
    spicy_level TINYINT NOT NULL DEFAULT 0 COMMENT '辣度等级0-4',
    sweetness_level TINYINT NOT NULL DEFAULT 0 COMMENT '甜度等级0-4',
    saltiness_level TINYINT NOT NULL DEFAULT 0 COMMENT '咸度等级0-4',
    oiliness_level TINYINT NOT NULL DEFAULT 0 COMMENT '油腻程度0-4',
    calorie_level TINYINT NULL COMMENT '热量等级0-4',
    tags JSON NOT NULL COMMENT '标准化推荐标签',
    allergens JSON NOT NULL COMMENT '标准化过敏原',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (dish_id),
    CONSTRAINT fk_dish_profile_dish
        FOREIGN KEY (dish_id) REFERENCES dish(id) ON DELETE CASCADE,
    CONSTRAINT chk_dish_profile_spicy CHECK (spicy_level BETWEEN 0 AND 4),
    CONSTRAINT chk_dish_profile_sweetness CHECK (sweetness_level BETWEEN 0 AND 4),
    CONSTRAINT chk_dish_profile_saltiness CHECK (saltiness_level BETWEEN 0 AND 4),
    CONSTRAINT chk_dish_profile_oiliness CHECK (oiliness_level BETWEEN 0 AND 4),
    CONSTRAINT chk_dish_profile_calorie CHECK (calorie_level IS NULL OR calorie_level BETWEEN 0 AND 4)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品AI推荐画像';
