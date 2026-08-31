package com.sky.ai.agent.recommendation;

/**
 * 保存和读取会话级推荐偏好。
 */
public interface RecommendationContextStore {

    /**
     * 读取指定会话的推荐偏好，不存在时返回空上下文。
     */
    RecommendationContext load(String conversationId);

    /**
     * 保存指定会话的推荐偏好并刷新过期时间。
     */
    void save(String conversationId, RecommendationContext context);
}
