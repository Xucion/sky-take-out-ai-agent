package com.sky.ai.tool;

public record ToolResponse<T>(boolean success, T data, ToolError error, String traceId) {
}
