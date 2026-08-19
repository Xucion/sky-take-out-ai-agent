package com.sky.ai.error;

public record ApiError(String code, String message, String traceId) {
}
