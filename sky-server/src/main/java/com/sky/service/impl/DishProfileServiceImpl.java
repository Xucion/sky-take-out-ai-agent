package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.sky.dto.DishProfileDTO;
import com.sky.entity.DishProfile;
import com.sky.mapper.DishProfileMapper;
import com.sky.mapper.model.DishProfileRow;
import com.sky.service.DishProfileService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 使用标准化等级和标签持久化菜品推荐画像。
 */
@Service
public class DishProfileServiceImpl implements DishProfileService {

    private static final int MAX_TAG_COUNT = 20;
    private static final int MAX_TAG_LENGTH = 20;

    private final DishProfileMapper dishProfileMapper;

    /**
     * 创建菜品画像服务。
     */
    public DishProfileServiceImpl(DishProfileMapper dishProfileMapper) {
        this.dishProfileMapper = dishProfileMapper;
    }

    /**
     * 校验、规范化并保存菜品画像。
     */
    @Override
    public void save(Long dishId, DishProfileDTO profile) {
        if (profile == null) {
            return;
        }
        if (dishId == null || dishId <= 0) {
            throw new IllegalArgumentException("菜品ID无效");
        }
        Integer spicyLevel = level(profile.getSpicyLevel(), "辣度", false);
        Integer sweetnessLevel = level(profile.getSweetnessLevel(), "甜度", false);
        Integer saltinessLevel = level(profile.getSaltinessLevel(), "咸度", false);
        Integer oilinessLevel = level(profile.getOilinessLevel(), "油腻程度", false);
        Integer calorieLevel = level(profile.getCalorieLevel(), "热量等级", true);
        List<String> tags = normalizeLabels(profile.getTags(), "标签");
        List<String> allergens = normalizeLabels(profile.getAllergens(), "过敏原");
        dishProfileMapper.upsert(dishId, spicyLevel, sweetnessLevel, saltinessLevel,
                oilinessLevel, calorieLevel, JSON.toJSONString(tags), JSON.toJSONString(allergens));
    }

    /**
     * 查询画像并将 JSON 列转换为结构化列表。
     */
    @Override
    public DishProfile getByDishId(Long dishId) {
        DishProfileRow row = dishProfileMapper.getByDishId(dishId);
        if (row == null) {
            return null;
        }
        return DishProfile.builder()
                .dishId(row.getDishId())
                .spicyLevel(row.getSpicyLevel())
                .sweetnessLevel(row.getSweetnessLevel())
                .saltinessLevel(row.getSaltinessLevel())
                .oilinessLevel(row.getOilinessLevel())
                .calorieLevel(row.getCalorieLevel())
                .tags(parseLabels(row.getTagsJson()))
                .allergens(parseLabels(row.getAllergensJson()))
                .updateTime(row.getUpdateTime())
                .build();
    }

    /**
     * 删除指定菜品画像。
     */
    @Override
    public void deleteByDishId(Long dishId) {
        dishProfileMapper.deleteByDishId(dishId);
    }

    /**
     * 校验画像等级处于 0～4 范围。
     */
    private Integer level(Integer value, String name, boolean nullable) {
        if (value == null) {
            if (nullable) {
                return null;
            }
            return 0;
        }
        if (value < 0 || value > 4) {
            throw new IllegalArgumentException(name + "必须在0到4之间");
        }
        return value;
    }

    /**
     * 去除空白和重复标签，并限制数量与单项长度。
     */
    private List<String> normalizeLabels(List<String> values, String name) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                if (!StringUtils.hasText(value)) {
                    continue;
                }
                String label = value.trim();
                if (label.length() > MAX_TAG_LENGTH) {
                    throw new IllegalArgumentException(name + "长度不能超过" + MAX_TAG_LENGTH + "个字符");
                }
                normalized.add(label);
            }
        }
        if (normalized.size() > MAX_TAG_COUNT) {
            throw new IllegalArgumentException(name + "数量不能超过" + MAX_TAG_COUNT + "个");
        }
        return new ArrayList<>(normalized);
    }

    /**
     * 将数据库 JSON 数组转换为标签列表。
     */
    private List<String> parseLabels(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        List<String> values = JSON.parseArray(json, String.class);
        return values == null ? new ArrayList<>() : values;
    }
}
