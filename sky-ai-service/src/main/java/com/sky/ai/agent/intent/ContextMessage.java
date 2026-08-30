package com.sky.ai.agent.intent;

/**
 * 表示用于意图识别上下文的一条精简历史消息。
 */
public record ContextMessage(String role, String content) {
}
