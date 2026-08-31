package com.sky.service;

import com.sky.dto.DishProfileDTO;
import com.sky.entity.DishProfile;

/**
 * 管理菜品推荐画像并执行输入规范化。
 */
public interface DishProfileService {

    /**
     * 保存指定菜品的推荐画像；画像为空时保持现有数据不变。
     */
    void save(Long dishId, DishProfileDTO profile);

    /**
     * 查询指定菜品的推荐画像。
     */
    DishProfile getByDishId(Long dishId);

    /**
     * 删除指定菜品的推荐画像。
     */
    void deleteByDishId(Long dishId);
}
