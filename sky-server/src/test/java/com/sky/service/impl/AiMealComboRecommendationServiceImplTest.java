package com.sky.service.impl;

import com.sky.dto.MealComboRecommendationRequest;
import com.sky.mapper.DishMapper;
import com.sky.vo.ai.DishRecommendationCandidateVO;
import com.sky.vo.ai.MealComboRecommendationResultVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证多人整餐组合的预算、辣度、数量和过敏原硬约束。 */
class AiMealComboRecommendationServiceImplTest {

    /** 验证两人辣味套餐包含主菜和配餐且总价不超过二百元。 */
    @Test
    void buildsTwoPeopleSpicyComboWithinTotalBudget() {
        DishMapper mapper = mapperWithDefaultCandidates();
        AiMealComboRecommendationServiceImpl service = new AiMealComboRecommendationServiceImpl(mapper);

        MealComboRecommendationResultVO result = service.recommend(baseRequest().build());

        assertFalse(result.getItems().isEmpty());
        assertTrue(result.getItems().stream().anyMatch(item -> "MAIN".equals(item.getRole())));
        assertTrue(result.getItems().stream().anyMatch(item -> "STAPLE".equals(item.getRole())
                && item.getQuantity() == 2));
        assertTrue(result.getTotalPrice().compareTo(new BigDecimal("200")) <= 0);
        assertEquals(new BigDecimal("200").subtract(result.getTotalPrice()), result.getRemainingBudget());
    }

    /** 验证声明鱼过敏后含鱼候选在组合前被硬性排除。 */
    @Test
    void excludesAllergenFromEveryComboItem() {
        DishMapper mapper = mapperWithDefaultCandidates();
        AiMealComboRecommendationServiceImpl service = new AiMealComboRecommendationServiceImpl(mapper);

        MealComboRecommendationResultVO result = service.recommend(
                baseRequest().allergens(List.of("鱼")).build());

        assertFalse(result.getItems().stream().anyMatch(item -> item.getDishId().equals(1L)));
        assertTrue(result.getItems().stream().anyMatch(item -> item.getDishId().equals(2L)));
    }

    /** 验证没有预算内辣味主菜时返回稳定空结果而不是超支。 */
    @Test
    void returnsEmptyWhenBudgetCannotCoverSpicyMain() {
        DishMapper mapper = mapperWithDefaultCandidates();
        AiMealComboRecommendationServiceImpl service = new AiMealComboRecommendationServiceImpl(mapper);

        MealComboRecommendationResultVO result = service.recommend(
                baseRequest().totalBudget(new BigDecimal("20")).build());

        assertTrue(result.getItems().isEmpty());
        assertEquals(BigDecimal.ZERO, result.getTotalPrice());
        assertTrue(result.getEmptyReason().contains("主菜"));
    }

    /** 创建包含辣味主菜、蔬菜、汤和主食的候选集合。 */
    private DishMapper mapperWithDefaultCandidates() {
        DishMapper mapper = mock(DishMapper.class);
        when(mapper.listRecommendationCandidates(any())).thenReturn(List.of(
                candidate(1L, "老坛酸菜鱼", "56", 2, "[\"鱼类\",\"川味\"]", "[\"鱼\"]"),
                candidate(2L, "香辣牛蛙", "68", 2, "[\"牛蛙\",\"川味\"]", "[]"),
                candidate(3L, "清炒时蔬", "18", 0, "[\"蔬菜\",\"素食\"]", "[]"),
                candidate(4L, "紫菜汤", "8", 0, "[\"汤类\"]", "[]"),
                candidate(5L, "米饭", "2", 0, "[\"主食\"]", "[]")));
        return mapper;
    }

    /** 创建两人二百元且至少微辣的基础请求。 */
    private MealComboRecommendationRequest.MealComboRecommendationRequestBuilder baseRequest() {
        return MealComboRecommendationRequest.builder().totalBudget(new BigDecimal("200"))
                .peopleCount(2).spicyLevelMin(1);
    }

    /** 创建组合推荐测试使用的菜品画像候选。 */
    private DishRecommendationCandidateVO candidate(Long id, String name, String price,
                                                     int spicy, String tags, String allergens) {
        DishRecommendationCandidateVO candidate = new DishRecommendationCandidateVO();
        candidate.setDishId(id);
        candidate.setName(name);
        candidate.setCategoryId(id);
        candidate.setPrice(new BigDecimal(price));
        candidate.setSpicyLevel(spicy);
        candidate.setSweetnessLevel(0);
        candidate.setOilinessLevel(1);
        candidate.setTagsJson(tags);
        candidate.setAllergensJson(allergens);
        candidate.setFlavorOptionsJson(spicy > 0 ? "[\"微辣\",\"中辣\"]" : "[]");
        return candidate;
    }
}
