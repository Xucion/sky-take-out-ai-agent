package com.sky.ai.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * P0 请求保持最小化；userId 绝不能作为请求字段出现。
 */
public record ChatRequest(
        @NotBlank @Size(max = 64) String conversationId,
        @NotBlank @Size(max = 2000) String message,
        @Positive Long orderId) {
}
