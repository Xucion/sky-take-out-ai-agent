package com.sky.ai.agent.recommendation;

import org.springframework.stereotype.Service;

/**
 * 负责读取、增量更新并持久化会话级推荐偏好。
 */
@Service
public class RecommendationContextService {

    private static final String BUDGET_SCOPE_FOLLOW_UP =
            "^(?:总预算|这顿饭|整顿饭|一共|合计|单个菜|每个菜|一道菜|单菜|人均|每人|"
                    + "\\d{1,2}(?:个)?人(?:吃|用餐)?)[，。！？,.!?]?$";

    private final RecommendationContextStore contextStore;
    private final RecommendationPreferenceExtractor preferenceExtractor;

    /**
     * 创建推荐上下文服务。
     */
    public RecommendationContextService(RecommendationContextStore contextStore,
                                        RecommendationPreferenceExtractor preferenceExtractor) {
        this.contextStore = contextStore;
        this.preferenceExtractor = preferenceExtractor;
    }

    /**
     * 合并本轮偏好并刷新会话上下文的过期时间。
     */
    public RecommendationResolution resolve(String conversationId,
                                            String message,
                                            long sequence) {
        RecommendationContext existing = contextStore.load(conversationId);
        // 幂等重放或乱序消息不能覆盖更新版本的结构化偏好。
        if (existing.updatedSequence() > sequence) {
            return new RecommendationResolution(existing, null);
        }
        RecommendationResolution resolution = preferenceExtractor.extract(existing, message, sequence);
        contextStore.save(conversationId, resolution.context());
        return resolution;
    }

    /**
     * 判断本轮短消息是否正在回答同一会话中的推荐澄清问题。
     */
    public boolean isExpectedFollowUp(String conversationId, String message) {
        RecommendationContext context = contextStore.load(conversationId);
        String normalized = message == null ? "" : message.replaceAll("\\s+", "");
        boolean waitingForBudgetScope =
                context.pendingClarification() == RecommendationClarification.BUDGET_SCOPE
                        || context.maxPrice() != null && context.perDishBudget() == null;
        if (waitingForBudgetScope) {
            return normalized.matches(BUDGET_SCOPE_FOLLOW_UP)
                    || containsAny(normalized, "总预算", "这顿饭预算", "整顿饭预算",
                    "单个菜", "每个菜", "一道菜", "单菜", "人均", "每人");
        }
        if (context.pendingClarification() == RecommendationClarification.PEOPLE_COUNT) {
            return normalized.matches(".*\\d{1,2}人(?:份|吃|用餐)?.*");
        }
        if (context.pendingClarification() == RecommendationClarification.PREFERENCE_CONFLICT) {
            return containsAny(normalized, "清淡", "优先辣", "重辣", "不要辣", "不辣");
        }
        return false;
    }

    /**
     * 判断文本是否包含任一候选短语。
     */
    private boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
