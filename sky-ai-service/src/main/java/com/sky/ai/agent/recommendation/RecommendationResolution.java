package com.sky.ai.agent.recommendation;

/**
 * 汇总本轮合并后的推荐偏好及必要的澄清问题。
 */
public record RecommendationResolution(
        RecommendationContext context,
        String clarification) {

    /**
     * 判断当前条件是否必须先向用户澄清。
     */
    public boolean requiresClarification() {
        return clarification != null && !clarification.isBlank();
    }
}
