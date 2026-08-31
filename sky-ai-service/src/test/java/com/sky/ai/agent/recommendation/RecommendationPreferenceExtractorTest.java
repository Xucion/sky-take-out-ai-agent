package com.sky.ai.agent.recommendation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证推荐偏好可以从多轮自然语言中确定性提取和覆盖。
 */
class RecommendationPreferenceExtractorTest {

    private final RecommendationPreferenceExtractor extractor =
            new RecommendationPreferenceExtractor();

    /**
     * 验证预算、微辣、偏好标签和甜味排除可以同时提取。
     */
    @Test
    void extractsStructuredPreferences() {
        RecommendationResolution resolution = extractor.extract(RecommendationContext.empty(),
                "想吃点微辣的，30元以内，要下饭，不要甜口", 18L);

        assertEquals(new BigDecimal("30"), resolution.context().maxPrice());
        assertEquals(1, resolution.context().spicyLevelMin());
        assertEquals(1, resolution.context().spicyLevelMax());
        assertEquals(0, resolution.context().sweetnessLevelMax());
        assertTrue(resolution.context().preferredTags().contains("下饭"));
        assertTrue(resolution.context().excludedTags().contains("甜"));
        assertEquals(18L, resolution.context().updatedSequence());
    }

    /**
     * 验证后续消息可以将原有辣度偏好明确覆盖为不辣。
     */
    @Test
    void replacesSpicyPreferenceAcrossTurns() {
        RecommendationContext spicy = extractor.extract(RecommendationContext.empty(),
                "想吃重辣的", 1L).context();
        RecommendationContext noSpicy = extractor.extract(spicy,
                "算了，今天不想吃辣", 2L).context();

        assertEquals(0, noSpicy.spicyLevelMin());
        assertEquals(0, noSpicy.spicyLevelMax());
    }

    /**
     * 验证总预算口径不明确时先生成澄清问题。
     */
    @Test
    void asksWhenBudgetScopeIsAmbiguous() {
        RecommendationResolution resolution = extractor.extract(RecommendationContext.empty(),
                "预算50，推荐一下", 1L);

        assertTrue(resolution.requiresClarification());
        assertEquals(RecommendationClarification.BUDGET_SCOPE,
                resolution.context().pendingClarification());
    }

    /**
     * 验证预算澄清后的纯人数回答会被解释为多人总预算。
     */
    @Test
    void treatsPeopleCountAsTotalBudgetAnswerWhenClarificationIsPending() {
        RecommendationContext pending = extractor.extract(RecommendationContext.empty(),
                "预算200，想吃辣，帮我推荐一下", 1L).context();

        RecommendationResolution resolution = extractor.extract(pending, "2人", 2L);

        assertEquals(2, resolution.context().peopleCount());
        assertEquals(false, resolution.context().perDishBudget());
        assertEquals(RecommendationClarification.NONE,
                resolution.context().pendingClarification());
        assertNull(resolution.clarification());
    }

    /**
     * 验证人均预算可以覆盖原总预算并继续进入单菜推荐。
     */
    @Test
    void acceptsPerCapitaBudgetAfterClarification() {
        RecommendationContext pending = extractor.extract(RecommendationContext.empty(),
                "预算200，推荐一下", 1L).context();

        RecommendationResolution resolution = extractor.extract(pending, "人均100", 2L);

        assertEquals(new BigDecimal("100"), resolution.context().maxPrice());
        assertEquals(true, resolution.context().perDishBudget());
        assertEquals(false, resolution.requiresClarification());
    }

    /**
     * 验证口语化预算和辣度表达也能进入结构化偏好。
     */
    @Test
    void extractsConversationalBudgetAndSpicyPreference() {
        RecommendationResolution resolution = extractor.extract(RecommendationContext.empty(),
                "我有200元，想吃点辣的，帮我推荐一下", 1L);

        assertEquals(new BigDecimal("200"), resolution.context().maxPrice());
        assertEquals(1, resolution.context().spicyLevelMin());
        assertEquals(RecommendationClarification.BUDGET_SCOPE,
                resolution.context().pendingClarification());
    }

    /**
     * 验证明确提到的过敏原会进入硬性排除列表。
     */
    @Test
    void extractsExplicitAllergen() {
        RecommendationResolution resolution = extractor.extract(RecommendationContext.empty(),
                "我对花生过敏，推荐点清淡的", 1L);

        assertTrue(resolution.context().allergens().contains("花生"));
        assertTrue(resolution.context().preferredTags().contains("清淡"));
    }

    /**
     * 验证麸质相关表达会归一化为菜品画像使用的过敏原名称。
     */
    @Test
    void normalizesGlutenAllergen() {
        RecommendationResolution resolution = extractor.extract(RecommendationContext.empty(),
                "我对麸质过敏，不要含大麦的饮料", 1L);

        assertTrue(resolution.context().allergens().contains("含麸质谷物"));
    }
}
