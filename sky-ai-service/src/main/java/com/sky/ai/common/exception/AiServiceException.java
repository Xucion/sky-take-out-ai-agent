package com.sky.ai.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 对外错误使用稳定 code；message 只给用户可理解的信息，不携带密钥或下游响应正文。
 */
public class AiServiceException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    /**
     * 初始化 AiServiceException，并注入其运行所需的依赖。
     */
    public AiServiceException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    /**
     * 返回异常对应的 HTTP 状态。
     */
    public HttpStatus getStatus() {
        return status;
    }

    /**
     * 返回异常对应的稳定业务错误码。
     */
    public String getCode() {
        return code;
    }
}
