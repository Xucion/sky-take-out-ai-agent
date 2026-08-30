package com.sky.ai.agent.intent;

/**
 * 表示规则识别是否命中或需要补充上下文。
 */
public enum RuleMatchStatus {
    MATCHED,
    NEED_CONTEXT,
    NO_MATCH
}
