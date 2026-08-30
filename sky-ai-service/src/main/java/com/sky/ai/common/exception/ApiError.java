package com.sky.ai.common.exception;

/**
 * 表示返回给客户端的统一错误信息。
 */
public record ApiError(String code, String message, String traceId) {
}
