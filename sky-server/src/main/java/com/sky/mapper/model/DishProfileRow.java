package com.sky.mapper.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * MyBatis 读取菜品画像 JSON 列时使用的内部数据行。
 */
@Data
public class DishProfileRow {

    /** 关联菜品主键。 */
    private Long dishId;

    /** 辣度等级。 */
    private Integer spicyLevel;

    /** 甜度等级。 */
    private Integer sweetnessLevel;

    /** 咸度等级。 */
    private Integer saltinessLevel;

    /** 油腻程度。 */
    private Integer oilinessLevel;

    /** 可选热量等级。 */
    private Integer calorieLevel;

    /** 标签 JSON 文本。 */
    private String tagsJson;

    /** 过敏原 JSON 文本。 */
    private String allergensJson;

    /** 最后更新时间。 */
    private LocalDateTime updateTime;
}
