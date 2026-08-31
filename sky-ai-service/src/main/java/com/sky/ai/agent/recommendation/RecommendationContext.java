package com.sky.ai.agent.recommendation;

import java.math.BigDecimal;
import java.util.List;

/**
 * 保存单次会话中可增量更新的结构化菜品推荐偏好。
 */
public record RecommendationContext(
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Integer peopleCount,
        Boolean perDishBudget,
        Integer spicyLevelMin,
        Integer spicyLevelMax,
        Integer sweetnessLevelMax,
        List<String> preferredTags,
        List<String> excludedTags,
        List<String> allergens,
        Long categoryId,
        long updatedSequence,
        RecommendationClarification pendingClarification) {

    /**
     * 将所有集合规范化为不可变非空列表。
     */
    public RecommendationContext {
        preferredTags = preferredTags == null ? List.of() : List.copyOf(preferredTags);
        excludedTags = excludedTags == null ? List.of() : List.copyOf(excludedTags);
        allergens = allergens == null ? List.of() : List.copyOf(allergens);
        pendingClarification = pendingClarification == null
                ? RecommendationClarification.NONE : pendingClarification;
    }

    /**
     * 创建尚未包含任何偏好的空上下文。
     */
    public static RecommendationContext empty() {
        return new RecommendationContext(null, null, null, null, null, null,
                null, List.of(), List.of(), List.of(), null, 0L,
                RecommendationClarification.NONE);
    }
}
