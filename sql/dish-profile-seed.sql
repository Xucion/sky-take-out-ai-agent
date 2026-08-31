-- 当前菜单的菜品推荐画像初始化数据。
-- 口味、油腻度和热量等级均为 0-4；未知信息不作为“无过敏原”的医学承诺。
-- 菜品配方或菜单发生变化时，应同步复核并更新本文件。
INSERT INTO dish_profile (
    dish_id,
    spicy_level,
    sweetness_level,
    saltiness_level,
    oiliness_level,
    calorie_level,
    tags,
    allergens,
    update_time
) VALUES
    (46, 0, 3, 0, 0, 2, JSON_ARRAY('饮料', '甜口', '凉饮'), JSON_ARRAY(), NOW()),
    (47, 0, 3, 0, 0, 3, JSON_ARRAY('饮料', '甜口', '碳酸饮料', '凉饮'), JSON_ARRAY(), NOW()),
    (48, 0, 0, 0, 0, 2, JSON_ARRAY('饮料', '酒类', '凉饮'), JSON_ARRAY('含麸质谷物'), NOW()),
    (49, 0, 0, 0, 0, 2, JSON_ARRAY('主食', '素食', '清淡', '低油'), JSON_ARRAY(), NOW()),
    (50, 0, 0, 0, 0, 2, JSON_ARRAY('主食', '素食', '清淡', '低油'), JSON_ARRAY('小麦'), NOW()),
    (51, 2, 0, 3, 3, 3, JSON_ARRAY('川味', '酸辣', '下饭', '鱼类', '热菜'), JSON_ARRAY('鱼'), NOW()),
    (52, 2, 0, 3, 3, 3, JSON_ARRAY('川味', '酸辣', '下饭', '鱼类', '热菜'), JSON_ARRAY('鱼'), NOW()),
    (53, 3, 0, 3, 4, 3, JSON_ARRAY('川味', '重辣', '下饭', '鱼类', '热菜'), JSON_ARRAY('鱼'), NOW()),
    (54, 0, 0, 1, 1, 1, JSON_ARRAY('素食', '清淡', '低油', '蔬菜', '热菜'), JSON_ARRAY(), NOW()),
    (55, 0, 0, 1, 1, 1, JSON_ARRAY('素食', '清淡', '低油', '蔬菜', '蒜香', '热菜'), JSON_ARRAY(), NOW()),
    (56, 0, 0, 1, 1, 1, JSON_ARRAY('素食', '清淡', '低油', '蔬菜', '热菜'), JSON_ARRAY(), NOW()),
    (57, 1, 0, 2, 2, 1, JSON_ARRAY('素食', '微辣', '蔬菜', '热菜'), JSON_ARRAY(), NOW()),
    (58, 0, 0, 2, 1, 2, JSON_ARRAY('鱼类', '清淡', '低油', '高蛋白', '蒸菜', '热菜'), JSON_ARRAY('鱼'), NOW()),
    (59, 0, 2, 2, 4, 4, JSON_ARRAY('肉类', '下饭', '软糯', '高油', '热菜'), JSON_ARRAY(), NOW()),
    (60, 0, 1, 3, 4, 4, JSON_ARRAY('肉类', '下饭', '咸香', '高油', '热菜'), JSON_ARRAY(), NOW()),
    (61, 3, 0, 3, 3, 3, JSON_ARRAY('鱼类', '重辣', '下饭', '蒸菜', '热菜'), JSON_ARRAY('鱼'), NOW()),
    (62, 2, 0, 3, 3, 3, JSON_ARRAY('川味', '酸辣', '牛蛙', '下饭', '热菜'), JSON_ARRAY(), NOW()),
    (63, 3, 0, 3, 4, 4, JSON_ARRAY('川味', '重辣', '牛蛙', '香锅', '下饭', '高油', '热菜'), JSON_ARRAY(), NOW()),
    (64, 3, 0, 3, 4, 4, JSON_ARRAY('川味', '重辣', '牛蛙', '下饭', '高油', '热菜'), JSON_ARRAY(), NOW()),
    (65, 2, 0, 3, 4, 4, JSON_ARRAY('川味', '烤鱼', '鱼类', '下饭', '热菜'), JSON_ARRAY('鱼', '大豆'), NOW()),
    (66, 2, 0, 3, 4, 4, JSON_ARRAY('川味', '烤鱼', '鱼类', '下饭', '热菜'), JSON_ARRAY('鱼', '大豆'), NOW()),
    (67, 2, 0, 3, 4, 4, JSON_ARRAY('川味', '烤鱼', '鱼类', '下饭', '热菜'), JSON_ARRAY('鱼', '大豆'), NOW()),
    (68, 0, 0, 1, 1, 1, JSON_ARRAY('汤类', '清淡', '低油', '鸡蛋', '热菜'), JSON_ARRAY('鸡蛋'), NOW()),
    (69, 0, 0, 1, 1, 1, JSON_ARRAY('汤类', '素食', '清淡', '低油', '豆制品', '热菜'), JSON_ARRAY('大豆'), NOW()),
    (70, 0, 0, 0, 0, NULL, JSON_ARRAY('测试菜品', '画像待复核'), JSON_ARRAY(), NOW())
ON DUPLICATE KEY UPDATE
    spicy_level = VALUES(spicy_level),
    sweetness_level = VALUES(sweetness_level),
    saltiness_level = VALUES(saltiness_level),
    oiliness_level = VALUES(oiliness_level),
    calorie_level = VALUES(calorie_level),
    tags = VALUES(tags),
    allergens = VALUES(allergens),
    update_time = VALUES(update_time);
