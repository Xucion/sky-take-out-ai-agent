package com.sky.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理端维护菜品推荐画像时使用的数据对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DishProfileDTO implements Serializable {

    /** 辣度等级，取值范围为 0～4。 */
    private Integer spicyLevel;

    /** 甜度等级，取值范围为 0～4。 */
    private Integer sweetnessLevel;

    /** 咸度等级，取值范围为 0～4。 */
    private Integer saltinessLevel;

    /** 油腻程度，取值范围为 0～4。 */
    private Integer oilinessLevel;

    /** 可选热量等级，取值范围为 0～4。 */
    private Integer calorieLevel;

    /** 用于推荐匹配的标准化标签。 */
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    /** 菜品包含的标准化过敏原。 */
    @Builder.Default
    private List<String> allergens = new ArrayList<>();
}
