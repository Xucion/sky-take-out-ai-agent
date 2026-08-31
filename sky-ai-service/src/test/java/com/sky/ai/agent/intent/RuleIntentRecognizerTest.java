package com.sky.ai.agent.intent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证菜品推荐的高精度预算表达不会掉入未知意图。
 */
class RuleIntentRecognizerTest {

    private final RuleIntentRecognizer recognizer = new RuleIntentRecognizer();

    /**
     * 验证人均和单菜预算回答会继续识别为菜品推荐。
     */
    @Test
    void recognizesPerCapitaAndPerDishBudgetAnswers() {
        assertEquals(CustomerIntent.DISH_RECOMMENDATION,
                recognizer.recognize("人均100元").intent());
        assertEquals(CustomerIntent.DISH_RECOMMENDATION,
                recognizer.recognize("单个菜最高80元").intent());
    }

    /**
     * 验证孤立人数仍需依赖待澄清上下文，不能由全局规则直接判定。
     */
    @Test
    void leavesStandalonePeopleCountUnmatched() {
        assertEquals(CustomerIntent.UNKNOWN,
                recognizer.recognize("2人").intent());
    }
}
