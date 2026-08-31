package com.sky.ai.agent.recommendation;

/**
 * 标识菜品推荐会话当前正在等待用户补充的信息。
 */
public enum RecommendationClarification {

    /** 当前没有待回答的澄清问题。 */
    NONE,

    /** 等待用户明确预算是单菜、人均还是整顿饭总预算。 */
    BUDGET_SCOPE,

    /** 已确认整餐总预算，等待用户提供用餐人数。 */
    PEOPLE_COUNT,

    /** 等待用户处理互相冲突的口味偏好。 */
    PREFERENCE_CONFLICT
}
