package com.sky.service;

import com.sky.dto.MealComboRecommendationRequest;
import com.sky.vo.ai.MealComboRecommendationResultVO;

/** 为 AI 提供确定性的多人整餐组合推荐。 */
public interface AiMealComboRecommendationService {
    /** 根据总预算、人数和饮食约束生成一个可解释组合。 */
    MealComboRecommendationResultVO recommend(MealComboRecommendationRequest request);
}
