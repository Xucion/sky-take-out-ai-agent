package com.sky.service.impl;

import com.sky.dto.DishRecommendationRequest;
import com.sky.mapper.DishMapper;
import com.sky.vo.ai.DishRecommendationCandidateVO;
import com.sky.vo.ai.DishRecommendationResultVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证菜品推荐服务的硬过滤、评分和结果解释。
 */
class AiDishRecommendationServiceImplTest {

    /**
     * 验证过敏原属于硬约束且不会出现在推荐结果中。
     */
    @Test
    void excludesAllergenBeforeScoring() {
        DishMapper mapper = mock(DishMapper.class);
        when(mapper.listRecommendationCandidates(any())).thenReturn(List.of(
                candidate(1L, "花生鸡丁", 1L, "28", 1,
                        "[\"下饭\",\"肉类\"]", "[\"花生\"]"),
                candidate(2L, "青椒肉丝", 1L, "26", 1,
                        "[\"下饭\",\"肉类\"]", "[]")));
        AiDishRecommendationServiceImpl service = new AiDishRecommendationServiceImpl(mapper);
        DishRecommendationRequest request = DishRecommendationRequest.builder()
                .maxPrice(new BigDecimal("30"))
                .spicyLevelMin(1)
                .spicyLevelMax(1)
                .preferredTags(List.of("下饭"))
                .allergens(List.of("花生"))
                .limit(5)
                .build();

        DishRecommendationResultVO result = service.recommend(request);

        assertEquals(1, result.getItems().size());
        assertEquals(2L, result.getItems().get(0).getDishId());
    }

    /**
     * 验证推荐结果携带价格、辣度和标签匹配原因码。
     */
    @Test
    void returnsStableReasonCodes() {
        DishMapper mapper = mock(DishMapper.class);
        when(mapper.listRecommendationCandidates(any())).thenReturn(List.of(
                candidate(3L, "川味小炒", 2L, "22", 1,
                        "[\"川味\",\"下饭\"]", "[]")));
        AiDishRecommendationServiceImpl service = new AiDishRecommendationServiceImpl(mapper);
        DishRecommendationRequest request = DishRecommendationRequest.builder()
                .maxPrice(new BigDecimal("30"))
                .spicyLevelMin(1)
                .spicyLevelMax(1)
                .preferredTags(List.of("下饭"))
                .limit(3)
                .build();

        DishRecommendationResultVO result = service.recommend(request);

        List<String> codes = result.getItems().get(0).getReasonCodes();
        assertTrue(codes.contains("PRICE_MATCH"));
        assertTrue(codes.contains("SPICY_MATCH"));
        assertTrue(codes.contains("TAG_MATCH"));
    }

    /**
     * 验证用户未提供预算和辣度时不会生成误导性的匹配原因码。
     */
    @Test
    void omitsReasonCodesForMissingPreferences() {
        DishMapper mapper = mock(DishMapper.class);
        when(mapper.listRecommendationCandidates(any())).thenReturn(List.of(
                candidate(4L, "清炒时蔬", 2L, "18", 0,
                        "[\"素食\",\"清淡\"]", "[]")));
        AiDishRecommendationServiceImpl service = new AiDishRecommendationServiceImpl(mapper);

        DishRecommendationResultVO result = service.recommend(new DishRecommendationRequest());

        List<String> codes = result.getItems().get(0).getReasonCodes();
        assertFalse(codes.contains("PRICE_MATCH"));
        assertFalse(codes.contains("SPICY_MATCH"));
    }

    /**
     * 创建推荐服务测试使用的候选菜品。
     */
    private DishRecommendationCandidateVO candidate(Long id, String name, Long categoryId,
                                                     String price, int spicyLevel,
                                                     String tags, String allergens) {
        DishRecommendationCandidateVO candidate = new DishRecommendationCandidateVO();
        candidate.setDishId(id);
        candidate.setName(name);
        candidate.setCategoryId(categoryId);
        candidate.setPrice(new BigDecimal(price));
        candidate.setSpicyLevel(spicyLevel);
        candidate.setSweetnessLevel(0);
        candidate.setOilinessLevel(1);
        candidate.setTagsJson(tags);
        candidate.setAllergensJson(allergens);
        candidate.setFlavorOptionsJson("[\"不辣\",\"微辣\"]");
        return candidate;
    }
}
