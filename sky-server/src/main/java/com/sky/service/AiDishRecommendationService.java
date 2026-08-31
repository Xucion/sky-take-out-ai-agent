package com.sky.service;

import com.sky.dto.DishRecommendationRequest;
import com.sky.vo.ai.DishRecommendationResultVO;

/**
 * 根据结构化偏好确定性筛选并排序可售菜品。
 */
public interface AiDishRecommendationService {

    /**
     * 返回满足硬约束且按匹配度排序的菜品推荐。
     */
    DishRecommendationResultVO recommend(DishRecommendationRequest request);
}
