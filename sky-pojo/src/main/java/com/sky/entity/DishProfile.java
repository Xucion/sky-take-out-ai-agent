package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 菜品用于确定性推荐的标准化画像。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DishProfile implements Serializable {

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

    /** 标准化推荐标签。 */
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    /** 标准化过敏原。 */
    @Builder.Default
    private List<String> allergens = new ArrayList<>();

    /** 画像最后更新时间。 */
    private LocalDateTime updateTime;
}
