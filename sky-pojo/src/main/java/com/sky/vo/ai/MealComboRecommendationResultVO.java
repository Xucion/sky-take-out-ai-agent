package com.sky.vo.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 经过预算和安全约束校验的多人整餐组合。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MealComboRecommendationResultVO implements Serializable {
    /** 组合中的菜品。 */
    @Builder.Default
    private List<MealComboItemVO> items = new ArrayList<>();
    /** 组合总价。 */
    private BigDecimal totalPrice;
    /** 用户提供的总预算。 */
    private BigDecimal budget;
    /** 推荐后剩余预算。 */
    private BigDecimal remainingBudget;
    /** 用餐人数。 */
    private Integer peopleCount;
    /** 组合层面的推荐原因码。 */
    @Builder.Default
    private List<String> reasonCodes = new ArrayList<>();
    /** 无法组成整餐时的安全说明。 */
    private String emptyReason;
}
