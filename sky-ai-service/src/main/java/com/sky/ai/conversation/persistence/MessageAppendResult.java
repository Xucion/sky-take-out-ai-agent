package com.sky.ai.conversation.persistence;

/**
 * 表示消息追加结果及其是否为本次新建，用于支持幂等写入。
 */
public record MessageAppendResult(AiMessage message, boolean created) {
}
