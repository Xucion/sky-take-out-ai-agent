package com.sky.ai.common.exception;

import com.sky.ai.chat.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一处理控制器异常，并将其转换为安全、稳定的 API 错误响应。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 将已知业务异常转换为稳定 API 错误响应。
     */
    @ExceptionHandler(AiServiceException.class)
    ResponseEntity<ApiError> handleKnown(AiServiceException ex, HttpServletRequest request) {
        // 已知错误保留稳定 code 和预期 HTTP 状态，供前端决定重试、提示或转人工。
        return ResponseEntity.status(ex.getStatus())
                .body(new ApiError(ex.getCode(), ex.getMessage(), traceId(request)));
    }

    /**
     * 将请求体校验异常转换为参数错误响应。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                               HttpServletRequest request) {
        // 不回显具体字段值，避免恶意输入或敏感内容被写入统一错误响应。
        return ResponseEntity.badRequest()
                .body(new ApiError("INVALID_ARGUMENT", "请求参数不完整或格式不正确", traceId(request)));
    }

    /**
     * 将方法参数校验异常转换为参数错误响应。
     */
    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class})
    ResponseEntity<ApiError> handleConstraintValidation(Exception ex,
                                                        HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(new ApiError("INVALID_ARGUMENT", "请求参数不完整或格式不正确", traceId(request)));
    }

    /**
     * 记录未知异常并返回脱敏后的服务错误。
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        String traceId = traceId(request);
        // 完整异常只进入服务端日志；用户响应使用固定文案并依靠 traceId 排查。
        log.error("Unhandled AI service error, traceId={}", traceId, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "客服暂时不可用，请稍后重试", traceId));
    }

    /**
     * 从请求中读取或生成追踪编号。
     */
    private String traceId(HttpServletRequest request) {
        return TraceIds.validOrRandom(request.getHeader("X-Trace-Id"));
    }
}
