package com.sky.vo.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * AI 内部工具统一响应。成功时 data 有值，失败时 error 有值，两者不同时出现。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiToolResponse<T> implements Serializable {

    private boolean success;
    private T data;
    private AiToolError error;
    private String traceId;

    /** 构造成功响应并保留链路追踪 ID。 */
    public static <T> AiToolResponse<T> success(T data, String traceId) {
        return new AiToolResponse<>(true, data, null, traceId);
    }

    /** 构造失败响应；调用方应优先根据 code 而不是 message 做逻辑判断。 */
    public static <T> AiToolResponse<T> error(String code, String message, boolean retryable, String traceId) {
        return new AiToolResponse<>(false, null, new AiToolError(code, message, retryable), traceId);
    }
}
