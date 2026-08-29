package com.sky.ai.model;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * 厂商无关的单次请求工具。工具参数全部由服务端闭包绑定，不接受模型提供身份或资源 ID。
 */
public final class AiChatTool {

    private final String name;
    private final String description;
    private final Supplier<?> action;
    private boolean executed;
    private Object result;
    private RuntimeException failure;

    public AiChatTool(String name, String description, Supplier<?> action) {
        this.name = requireText(name, "name");
        this.description = requireText(description, "description");
        this.action = Objects.requireNonNull(action, "action must not be null");
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    /**
     * 模型可能在一轮中重复请求同一无参工具；缓存结果以保证业务调用和审计只发生一次。
     */
    public synchronized Object execute() {
        if (!executed) {
            executed = true;
            try {
                result = action.get();
            } catch (RuntimeException ex) {
                failure = ex;
            }
        }
        if (failure != null) {
            throw failure;
        }
        return result;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
