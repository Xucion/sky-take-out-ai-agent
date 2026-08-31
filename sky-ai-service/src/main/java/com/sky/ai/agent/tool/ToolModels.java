package com.sky.ai.agent.tool;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

/**
 * 调用业务服务工具时使用的最小数据模型集合。
 */
public final class ToolModels {

    /**
     * 禁止实例化仅用于归类模型的工具类。
     */
    private ToolModels() {
    }

    /**
     * 业务工具返回的门店营业状态。
     */
    public record ShopStatus(
            String status,
            String statusText,
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm") LocalDateTime checkedAt) {
    }

    /**
     * 业务工具主动公开的最小订单进度视图。
     */
    public record OrderProgress(
            Long orderId,
            String status,
            String statusText,
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm") LocalDateTime estimatedDeliveryTime,
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm") LocalDateTime deliveredAt,
            List<String> allowedUserActions,
            boolean trackingAvailable,
            String detailPath) {
    }

    /**
     * 发送给业务推荐服务的结构化偏好，不包含原始文本、SQL 或用户身份。
     */
    public record DishRecommendationRequest(
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Integer spicyLevelMin,
            Integer spicyLevelMax,
            Integer sweetnessLevelMax,
            List<String> preferredTags,
            List<String> excludedTags,
            List<String> allergens,
            Long categoryId,
            int limit) {
    }

    /**
     * 业务服务返回的一项可解释菜品推荐。
     */
    public record DishRecommendationItem(
            Long dishId,
            String name,
            BigDecimal price,
            List<String> matchedTags,
            List<String> flavorOptions,
            List<String> reasonCodes) {
    }

    /**
     * 菜品推荐结果及无候选时的安全原因。
     */
    public record DishRecommendationResult(
            List<DishRecommendationItem> items,
            String emptyReason) {
    }

    /** 发送给业务服务的多人整餐组合条件。 */
    public record MealComboRecommendationRequest(
            BigDecimal totalBudget,
            int peopleCount,
            Integer spicyLevelMin,
            Integer spicyLevelMax,
            Integer sweetnessLevelMax,
            List<String> preferredTags,
            List<String> excludedTags,
            List<String> allergens,
            Long categoryId) {
    }

    /** 多人整餐组合中的一项菜品。 */
    public record MealComboItem(
            Long dishId,
            String name,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal subtotal,
            String role,
            List<String> matchedTags,
            List<String> flavorOptions,
            List<String> reasonCodes) {
    }

    /** 业务服务返回的确定性多人整餐组合。 */
    public record MealComboRecommendationResult(
            List<MealComboItem> items,
            BigDecimal totalPrice,
            BigDecimal budget,
            BigDecimal remainingBudget,
            int peopleCount,
            List<String> reasonCodes,
            String emptyReason) {
    }

    /**
     * 业务工具失败时返回的安全错误信息。
     */
    public record ToolError(String code, String message, boolean retryable) {
    }

    /**
     * 业务工具调用的统一响应包装。
     */
    public record ToolResponse<T>(boolean success, T data, ToolError error, String traceId) {
    }
}
