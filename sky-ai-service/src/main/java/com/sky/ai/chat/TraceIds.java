package com.sky.ai.chat;

import java.util.UUID;

/**
 * 只接受有限字符集的上游 Trace ID，防止把换行或超长值传播到日志和下游请求。
 */
public final class TraceIds {

    /**
     * 初始化 TraceIds，并注入其运行所需的依赖。
     */
    private TraceIds() {
    }

    /**
     * 返回合法追踪编号，缺失或非法时生成新编号。
     */
    public static String validOrRandom(String supplied) {
        if (supplied != null && supplied.matches("[A-Za-z0-9_-]{8,64}")) {
            return supplied;
        }
        return UUID.randomUUID().toString();
    }
}
