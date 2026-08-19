package com.sky.ai.error;

import org.springframework.http.HttpStatus;

/**
 * 对外错误使用稳定 code；message 只给用户可理解的信息，不携带密钥或下游响应正文。
 */
public class AiServiceException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public AiServiceException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
