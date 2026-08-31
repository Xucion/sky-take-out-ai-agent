package com.sky.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** AI 工具提交的多人整餐组合推荐条件。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MealComboRecommendationRequest implements Serializable {
    /** 整顿饭允许使用的总预算。 */
    private BigDecimal totalBudget;
    /** 用餐人数。 */
    private Integer peopleCount;
    /** 主菜期望的最低辣度。 */
    private Integer spicyLevelMin;
    /** 主菜期望的最高辣度。 */
    private Integer spicyLevelMax;
    /** 所有菜品可接受的最高甜度。 */
    private Integer sweetnessLevelMax;
    /** 偏好的标准化标签。 */
    @Builder.Default
    private List<String> preferredTags = new ArrayList<>();
    /** 明确排除的标准化标签。 */
    @Builder.Default
    private List<String> excludedTags = new ArrayList<>();
    /** 必须硬性排除的过敏原。 */
    @Builder.Default
    private List<String> allergens = new ArrayList<>();
    /** 可选分类编号。 */
    private Long categoryId;
}
