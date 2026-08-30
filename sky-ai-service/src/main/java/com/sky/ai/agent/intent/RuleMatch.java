package com.sky.ai.agent.intent;

/**
 * 表示规则识别阶段得到的匹配状态和业务参数。
 */
public record RuleMatch(RuleMatchStatus status,
                        CustomerIntent intent,
                        double confidence,
                        Long orderId) {
}
