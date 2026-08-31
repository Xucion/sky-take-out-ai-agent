package com.sky.ai.agent.recommendation;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 使用白名单规则从自然语言中增量提取菜品推荐偏好。
 */
@Component
public class RecommendationPreferenceExtractor {

    private static final Pattern MAX_PRICE_WITH_UNIT = Pattern.compile(
            "(?<!\\d)(\\d{1,5}(?:\\.\\d{1,2})?)\\s*元\\s*(?:以内|以下|封顶)");
    private static final Pattern BUDGET_PRICE = Pattern.compile(
            "(?:预算|控制在|不超过|最多)\\s*(?:是|为|[:：])?\\s*(\\d{1,5}(?:\\.\\d{1,2})?)\\s*元?");
    private static final Pattern AVAILABLE_BUDGET = Pattern.compile(
            "(?:我有|手里有|带了)\\s*(\\d{1,5}(?:\\.\\d{1,2})?)\\s*元");
    private static final Pattern PER_CAPITA_PRICE = Pattern.compile(
            "(?:人均|每人)\\s*(?:预算|最高|不超过|最多)?\\s*(\\d{1,5}(?:\\.\\d{1,2})?)\\s*元?");
    private static final Pattern PER_DISH_PRICE = Pattern.compile(
            "(?:单个菜|每个菜|一道菜|单菜)\\s*(?:预算|最高|不超过|最多)?\\s*(\\d{1,5}(?:\\.\\d{1,2})?)\\s*元?");
    private static final Pattern MIN_PRICE = Pattern.compile(
            "(?:至少|最低)\\s*(\\d{1,5}(?:\\.\\d{1,2})?)\\s*元");
    private static final Pattern PEOPLE_COUNT = Pattern.compile("(?<!\\d)(\\d{1,2})\\s*人(?:份|吃|用餐)?");

    private static final List<String> KNOWN_TAGS = List.of(
            "下饭", "肉类", "素食", "清淡", "川味", "低油", "高蛋白", "低卡", "热菜", "凉菜");
    private static final List<String> KNOWN_ALLERGENS = List.of(
            "花生", "坚果", "牛奶", "乳制品", "鸡蛋", "海鲜", "虾", "蟹", "鱼", "大豆", "小麦", "芝麻");
    private static final String GLUTEN_CEREAL_ALLERGEN = "含麸质谷物";

    /**
     * 将本轮文本偏好合并到已有会话上下文。
     */
    public RecommendationResolution extract(RecommendationContext existing,
                                            String message,
                                            long sequence) {
        RecommendationContext base = existing == null ? RecommendationContext.empty() : existing;
        String text = message == null ? "" : message.replaceAll("\\s+", "");
        BigDecimal minPrice = firstAmount(MIN_PRICE, text, base.minPrice());
        BigDecimal maxPrice = firstAmount(PER_DISH_PRICE, text,
                firstAmount(PER_CAPITA_PRICE, text,
                        firstAmount(MAX_PRICE_WITH_UNIT, text,
                                firstAmount(BUDGET_PRICE, text,
                                        firstAmount(AVAILABLE_BUDGET, text, base.maxPrice())))));
        Integer peopleCount = firstInteger(PEOPLE_COUNT, text, base.peopleCount());
        Boolean perDishBudget = base.perDishBudget();
        boolean answersPendingBudgetWithPeople =
                (base.pendingClarification() == RecommendationClarification.BUDGET_SCOPE
                        || base.maxPrice() != null && base.perDishBudget() == null)
                        && PEOPLE_COUNT.matcher(text).find();
        if (PER_DISH_PRICE.matcher(text).find() || PER_CAPITA_PRICE.matcher(text).find()
                || MAX_PRICE_WITH_UNIT.matcher(text).find()
                || containsAny(text, "单个菜", "每个菜", "一道菜", "单菜", "人均")) {
            perDishBudget = true;
        } else if (containsAny(text, "总预算", "这顿饭预算", "一共", "合计")) {
            perDishBudget = false;
        } else if (answersPendingBudgetWithPeople) {
            perDishBudget = false;
        } else if (BUDGET_PRICE.matcher(text).find() || AVAILABLE_BUDGET.matcher(text).find()) {
            perDishBudget = null;
        }

        Integer spicyMin = base.spicyLevelMin();
        Integer spicyMax = base.spicyLevelMax();
        if (containsAny(text, "不辣", "不要辣", "不吃辣", "不想吃辣", "今天不吃辣")) {
            spicyMin = 0;
            spicyMax = 0;
        } else if (text.contains("微辣")) {
            spicyMin = 1;
            spicyMax = 1;
        } else if (text.contains("中辣")) {
            spicyMin = 2;
            spicyMax = 2;
        } else if (containsAny(text, "重辣", "特辣")) {
            spicyMin = 3;
            spicyMax = 4;
        } else if (containsAny(text, "辣一点", "想吃辣", "吃辣的", "想吃点辣", "吃点辣")) {
            spicyMin = 1;
        }
        if (containsAny(text, "别太辣", "不要太辣")) {
            spicyMax = 2;
        }

        Integer sweetnessMax = base.sweetnessLevelMax();
        Set<String> preferred = new LinkedHashSet<>(base.preferredTags());
        Set<String> excluded = new LinkedHashSet<>(base.excludedTags());
        Set<String> allergens = new LinkedHashSet<>(base.allergens());
        if (containsAny(text, "不要甜", "不甜", "不吃甜", "不想吃甜")) {
            sweetnessMax = 0;
            excluded.add("甜");
        }
        for (String tag : KNOWN_TAGS) {
            if (!text.contains(tag)) {
                continue;
            }
            if (containsAny(text, "不要" + tag, "不吃" + tag, "不想吃" + tag)) {
                preferred.remove(tag);
                excluded.add(tag);
            } else {
                excluded.remove(tag);
                preferred.add(tag);
            }
        }
        for (String allergen : KNOWN_ALLERGENS) {
            if (text.contains(allergen + "过敏") || text.contains("对" + allergen + "过敏")) {
                allergens.add(allergen);
            }
        }
        if (containsAny(text, "麸质过敏", "对麸质过敏", "大麦过敏", "对大麦过敏")) {
            allergens.add(GLUTEN_CEREAL_ALLERGEN);
        }

        RecommendationClarification pendingClarification;
        if (maxPrice != null && perDishBudget == null) {
            pendingClarification = RecommendationClarification.BUDGET_SCOPE;
        } else if (maxPrice != null && Boolean.FALSE.equals(perDishBudget) && peopleCount == null) {
            pendingClarification = RecommendationClarification.PEOPLE_COUNT;
        } else {
            pendingClarification = RecommendationClarification.NONE;
        }
        RecommendationContext merged = new RecommendationContext(minPrice, maxPrice, peopleCount, perDishBudget,
                spicyMin, spicyMax, sweetnessMax, new ArrayList<>(preferred),
                new ArrayList<>(excluded), new ArrayList<>(allergens), base.categoryId(), sequence,
                pendingClarification);
        return new RecommendationResolution(merged, clarification(text, merged));
    }

    /**
     * 对预算口径或互相冲突的偏好生成确定性澄清问题。
     */
    private String clarification(String text, RecommendationContext context) {
        if (context.pendingClarification() == RecommendationClarification.BUDGET_SCOPE) {
            return "这个预算是单个菜品或人均最高预算，还是这顿饭的总预算？"
                    + "如果是总预算，请再告诉我用餐人数。";
        }
        if (context.pendingClarification() == RecommendationClarification.PEOPLE_COUNT) {
            return "明白了，这是整顿饭的总预算。请告诉我用餐人数，我会按人数搭配主菜、蔬菜、汤和主食。";
        }
        if (context.preferredTags().contains("清淡")
                && context.spicyLevelMin() != null && context.spicyLevelMin() >= 3) {
            return "你同时选择了清淡和重辣，这两个条件有冲突。更希望优先清淡，还是优先辣味？";
        }
        return null;
    }

    /**
     * 从文本中读取第一个金额，未命中时保留原值。
     */
    private BigDecimal firstAmount(Pattern pattern, String text, BigDecimal fallback) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? new BigDecimal(matcher.group(1)) : fallback;
    }

    /**
     * 从文本中读取第一个整数，未命中时保留原值。
     */
    private Integer firstInteger(Pattern pattern, String text, Integer fallback) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : fallback;
    }

    /**
     * 判断文本是否包含任一候选短语。
     */
    private boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
