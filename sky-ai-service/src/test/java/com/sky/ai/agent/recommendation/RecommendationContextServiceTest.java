package com.sky.ai.agent.recommendation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证推荐上下文服务只接续当前会话真正等待的澄清答案。
 */
class RecommendationContextServiceTest {

    /**
     * 验证等待预算口径时可以识别人数、总预算和人均预算回答。
     */
    @Test
    void recognizesBudgetScopeFollowUps() {
        RecommendationContextStore store = mock(RecommendationContextStore.class);
        RecommendationContext pending = new RecommendationContext(null, new BigDecimal("200"),
                null, null, 1, null, null, List.of(), List.of(), List.of(),
                null, 1L, RecommendationClarification.BUDGET_SCOPE);
        when(store.load("conversation-1")).thenReturn(pending);
        RecommendationContextService service = new RecommendationContextService(
                store, new RecommendationPreferenceExtractor());

        assertTrue(service.isExpectedFollowUp("conversation-1", "2人"));
        assertTrue(service.isExpectedFollowUp("conversation-1", "这是总预算"));
        assertTrue(service.isExpectedFollowUp("conversation-1", "人均100元"));
        assertFalse(service.isExpectedFollowUp("conversation-1", "今天天气怎么样"));
    }

    /**
     * 验证没有待澄清状态时不会把孤立人数误判为推荐追问。
     */
    @Test
    void rejectsPeopleCountWithoutPendingClarification() {
        RecommendationContextStore store = mock(RecommendationContextStore.class);
        when(store.load("conversation-2")).thenReturn(RecommendationContext.empty());
        RecommendationContextService service = new RecommendationContextService(
                store, new RecommendationPreferenceExtractor());

        assertFalse(service.isExpectedFollowUp("conversation-2", "2人"));
    }

    /**
     * 验证升级前没有显式澄清枚举的歧义预算上下文仍可继续回答。
     */
    @Test
    void supportsLegacyAmbiguousBudgetContext() {
        RecommendationContextStore store = mock(RecommendationContextStore.class);
        RecommendationContext legacy = new RecommendationContext(null, new BigDecimal("200"),
                null, null, 1, null, null, List.of(), List.of(), List.of(),
                null, 1L, RecommendationClarification.NONE);
        when(store.load("conversation-legacy")).thenReturn(legacy);
        RecommendationContextService service = new RecommendationContextService(
                store, new RecommendationPreferenceExtractor());

        assertTrue(service.isExpectedFollowUp("conversation-legacy", "2人"));
    }
}
