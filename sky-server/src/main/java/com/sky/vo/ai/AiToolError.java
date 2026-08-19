package com.sky.vo.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 稳定的内部工具错误结构。message 面向上层 Agent，不能包含堆栈、SQL 或内部地址。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiToolError implements Serializable {

    /** 可供程序分支判断的稳定错误码。 */
    private String code;

    /** 脱敏后的错误说明。 */
    private String message;

    /** 是否允许上层在没有副作用的前提下重试。 */
    private boolean retryable;
}
