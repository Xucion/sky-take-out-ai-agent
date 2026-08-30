package com.sky.ai.agent.tool;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
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
