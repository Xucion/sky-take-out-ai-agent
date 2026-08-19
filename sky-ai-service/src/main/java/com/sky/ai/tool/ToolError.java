package com.sky.ai.tool;

public record ToolError(String code, String message, boolean retryable) {
}
