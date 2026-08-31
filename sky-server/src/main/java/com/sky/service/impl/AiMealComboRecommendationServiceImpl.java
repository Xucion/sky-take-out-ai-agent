package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.sky.dto.DishRecommendationQuery;
import com.sky.dto.MealComboRecommendationRequest;
import com.sky.mapper.DishMapper;
import com.sky.service.AiMealComboRecommendationService;
import com.sky.vo.ai.DishRecommendationCandidateVO;
import com.sky.vo.ai.MealComboItemVO;
import com.sky.vo.ai.MealComboRecommendationResultVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 使用确定性规则生成不超过总预算的多人整餐组合。 */
@Service
public class AiMealComboRecommendationServiceImpl implements AiMealComboRecommendationService {

    private static final int MAX_PEOPLE = 20;
    private static final Set<String> VEGETABLE_TAGS = Set.of("蔬菜", "素食");
    private static final Set<String> SOUP_TAGS = Set.of("汤类", "汤品");
    private static final Set<String> STAPLE_TAGS = Set.of("主食");
    private static final Set<String> BEVERAGE_TAGS = Set.of("饮料", "饮品");

    private final DishMapper dishMapper;

    /** 创建整餐组合推荐服务。 */
    public AiMealComboRecommendationServiceImpl(DishMapper dishMapper) {
        this.dishMapper = dishMapper;
    }

    /** 校验条件、过滤安全约束并按主菜、蔬菜、汤和主食组装整餐。 */
    @Override
    public MealComboRecommendationResultVO recommend(MealComboRecommendationRequest request) {
        MealComboRecommendationRequest normalized = normalize(request);
        List<DishRecommendationCandidateVO> candidates = dishMapper.listRecommendationCandidates(
                        new DishRecommendationQuery(null, normalized.getTotalBudget(), normalized.getCategoryId()))
                .stream().filter(candidate -> satisfiesHardConstraints(candidate, normalized))
                .collect(Collectors.toList());

        List<DishRecommendationCandidateVO> mains = candidates.stream()
                .filter(candidate -> role(candidate).equals("MAIN"))
                .filter(candidate -> satisfiesMainTaste(candidate, normalized))
                .sorted(mainComparator(normalized))
                .collect(Collectors.toList());
        if (mains.isEmpty()) {
            return empty(normalized, "没有找到同时满足辣度、预算和过敏原要求的主菜，请调整一个非安全条件后重试。");
        }

        List<MealComboItemVO> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal reservedSideBudget = cheapestRoleCost(candidates, "VEGETABLE", 1)
                .add(cheapestRoleCost(candidates, "SOUP", 1))
                .add(cheapestRoleCost(candidates, "STAPLE", normalized.getPeopleCount()));
        BigDecimal mainBudget = normalized.getTotalBudget().subtract(reservedSideBudget);
        int mainTarget = Math.max(1, (normalized.getPeopleCount() + 1) / 2);
        for (DishRecommendationCandidateVO main : mains) {
            if (itemsForRole(items, "MAIN") >= mainTarget) {
                break;
            }
            if (fits(total, main.getPrice(), mainBudget)) {
                items.add(toItem(main, 1, "MAIN", normalized));
                total = total.add(main.getPrice());
            }
        }
        if (items.isEmpty()) {
            return empty(normalized, "当前总预算不足以选择满足口味要求的主菜。");
        }

        total = addCheapestRole(candidates, items, total, normalized, "VEGETABLE", 1);
        total = addCheapestRole(candidates, items, total, normalized, "SOUP", 1);
        total = addCheapestRole(candidates, items, total, normalized, "STAPLE", normalized.getPeopleCount());

        List<String> reasons = new ArrayList<>(List.of("TOTAL_BUDGET_MATCH", "PEOPLE_COUNT_MATCH", "CATEGORY_DIVERSITY"));
        if (normalized.getSpicyLevelMin() != null || normalized.getSpicyLevelMax() != null) {
            reasons.add("SPICY_MAIN_MATCH");
        }
        return MealComboRecommendationResultVO.builder()
                .items(items).totalPrice(total).budget(normalized.getTotalBudget())
                .remainingBudget(normalized.getTotalBudget().subtract(total))
                .peopleCount(normalized.getPeopleCount()).reasonCodes(reasons).build();
    }

    /** 规范化请求并拒绝可能导致越权或无意义计算的参数。 */
    private MealComboRecommendationRequest normalize(MealComboRecommendationRequest request) {
        if (request == null || request.getTotalBudget() == null || request.getTotalBudget().signum() <= 0) {
            throw new IllegalArgumentException("总预算必须大于0");
        }
        if (request.getPeopleCount() == null || request.getPeopleCount() < 1
                || request.getPeopleCount() > MAX_PEOPLE) {
            throw new IllegalArgumentException("用餐人数必须在1到20之间");
        }
        validateLevel(request.getSpicyLevelMin(), "最低辣度");
        validateLevel(request.getSpicyLevelMax(), "最高辣度");
        validateLevel(request.getSweetnessLevelMax(), "最高甜度");
        if (request.getSpicyLevelMin() != null && request.getSpicyLevelMax() != null
                && request.getSpicyLevelMin() > request.getSpicyLevelMax()) {
            throw new IllegalArgumentException("辣度范围无效");
        }
        if (request.getCategoryId() != null && request.getCategoryId() <= 0) {
            throw new IllegalArgumentException("分类ID无效");
        }
        request.setPreferredTags(normalizeLabels(request.getPreferredTags()));
        request.setExcludedTags(normalizeLabels(request.getExcludedTags()));
        request.setAllergens(normalizeLabels(request.getAllergens()));
        return request;
    }

    /** 校验口味等级范围。 */
    private void validateLevel(Integer value, String name) {
        if (value != null && (value < 0 || value > 4)) {
            throw new IllegalArgumentException(name + "必须在0到4之间");
        }
    }

    /** 去重并限制标签的数量与长度。 */
    private List<String> normalizeLabels(List<String> values) {
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                if (!StringUtils.hasText(value)) continue;
                String label = value.trim();
                if (label.length() > 20) throw new IllegalArgumentException("标签长度不能超过20个字符");
                labels.add(label);
            }
        }
        if (labels.size() > 20) throw new IllegalArgumentException("标签数量不能超过20个");
        return new ArrayList<>(labels);
    }

    /** 应用过敏原、排除标签和甜度上限等硬约束。 */
    private boolean satisfiesHardConstraints(DishRecommendationCandidateVO candidate,
                                             MealComboRecommendationRequest request) {
        Set<String> tags = new HashSet<>(parseList(candidate.getTagsJson()));
        Set<String> allergens = new HashSet<>(parseList(candidate.getAllergensJson()));
        return request.getExcludedTags().stream().noneMatch(tags::contains)
                && request.getAllergens().stream().noneMatch(allergens::contains)
                && (request.getSweetnessLevelMax() == null
                || candidate.getSweetnessLevel() != null
                && candidate.getSweetnessLevel() <= request.getSweetnessLevelMax());
    }

    /** 仅要求主菜满足用户的辣度区间，避免米饭和汤被错误排除。 */
    private boolean satisfiesMainTaste(DishRecommendationCandidateVO candidate,
                                       MealComboRecommendationRequest request) {
        int spicy = candidate.getSpicyLevel() == null ? 0 : candidate.getSpicyLevel();
        return (request.getSpicyLevelMin() == null || spicy >= request.getSpicyLevelMin())
                && (request.getSpicyLevelMax() == null || spicy <= request.getSpicyLevelMax());
    }

    /** 对主菜按偏好命中数、辣度和价格稳定排序。 */
    private Comparator<DishRecommendationCandidateVO> mainComparator(MealComboRecommendationRequest request) {
        return Comparator.comparingInt((DishRecommendationCandidateVO candidate) ->
                        matchedTags(candidate, request).size()).reversed()
                .thenComparing(candidate -> candidate.getSpicyLevel() == null ? 0 : candidate.getSpicyLevel(),
                        Comparator.reverseOrder())
                .thenComparing(DishRecommendationCandidateVO::getPrice, Comparator.reverseOrder())
                .thenComparing(DishRecommendationCandidateVO::getDishId);
    }

    /** 在预算允许时加入指定角色中最便宜的一项。 */
    private BigDecimal addCheapestRole(List<DishRecommendationCandidateVO> candidates,
                                      List<MealComboItemVO> items, BigDecimal total,
                                      MealComboRecommendationRequest request, String targetRole, int quantity) {
        DishRecommendationCandidateVO selected = candidates.stream()
                .filter(candidate -> role(candidate).equals(targetRole))
                .filter(candidate -> items.stream().noneMatch(item -> item.getDishId().equals(candidate.getDishId())))
                .filter(candidate -> fits(total, candidate.getPrice().multiply(BigDecimal.valueOf(quantity)),
                        request.getTotalBudget()))
                .min(Comparator.comparing(DishRecommendationCandidateVO::getPrice)
                        .thenComparing(DishRecommendationCandidateVO::getDishId))
                .orElse(null);
        if (selected == null) return total;
        MealComboItemVO item = toItem(selected, quantity, targetRole, request);
        items.add(item);
        return total.add(item.getSubtotal());
    }

    /** 计算指定角色最便宜菜品所需金额；没有该角色时不预留预算。 */
    private BigDecimal cheapestRoleCost(List<DishRecommendationCandidateVO> candidates,
                                        String targetRole, int quantity) {
        return candidates.stream().filter(candidate -> role(candidate).equals(targetRole))
                .map(DishRecommendationCandidateVO::getPrice).min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO).multiply(BigDecimal.valueOf(quantity));
    }

    /** 判断增加金额后是否仍处于总预算内。 */
    private boolean fits(BigDecimal total, BigDecimal addition, BigDecimal budget) {
        return total.add(addition).compareTo(budget) <= 0;
    }

    /** 统计组合中指定角色的数量。 */
    private int itemsForRole(List<MealComboItemVO> items, String targetRole) {
        return (int) items.stream().filter(item -> targetRole.equals(item.getRole())).count();
    }

    /** 根据画像标签确定菜品在整餐中的角色。 */
    private String role(DishRecommendationCandidateVO candidate) {
        Set<String> tags = new HashSet<>(parseList(candidate.getTagsJson()));
        if (tags.stream().anyMatch(STAPLE_TAGS::contains)) return "STAPLE";
        if (tags.stream().anyMatch(SOUP_TAGS::contains)) return "SOUP";
        if (tags.stream().anyMatch(VEGETABLE_TAGS::contains)) return "VEGETABLE";
        if (tags.stream().anyMatch(BEVERAGE_TAGS::contains)) return "BEVERAGE";
        return "MAIN";
    }

    /** 将候选菜品转换为对 AI 暴露的最小组合项。 */
    private MealComboItemVO toItem(DishRecommendationCandidateVO candidate, int quantity,
                                   String itemRole, MealComboRecommendationRequest request) {
        List<String> reasons = new ArrayList<>(List.of("AVAILABLE", "TOTAL_BUDGET_MATCH"));
        if ("MAIN".equals(itemRole) && (request.getSpicyLevelMin() != null
                || request.getSpicyLevelMax() != null)) reasons.add("SPICY_MATCH");
        return MealComboItemVO.builder().dishId(candidate.getDishId()).name(candidate.getName())
                .unitPrice(candidate.getPrice()).quantity(quantity)
                .subtotal(candidate.getPrice().multiply(BigDecimal.valueOf(quantity))).role(itemRole)
                .matchedTags(matchedTags(candidate, request))
                .flavorOptions(parseList(candidate.getFlavorOptionsJson())).reasonCodes(reasons).build();
    }

    /** 返回候选菜品命中的用户偏好标签。 */
    private List<String> matchedTags(DishRecommendationCandidateVO candidate,
                                     MealComboRecommendationRequest request) {
        List<String> tags = parseList(candidate.getTagsJson());
        return request.getPreferredTags().stream().filter(tags::contains).collect(Collectors.toList());
    }

    /** 构造没有可用组合时的稳定返回结构。 */
    private MealComboRecommendationResultVO empty(MealComboRecommendationRequest request, String reason) {
        return MealComboRecommendationResultVO.builder().items(new ArrayList<>())
                .totalPrice(BigDecimal.ZERO).budget(request.getTotalBudget())
                .remainingBudget(request.getTotalBudget()).peopleCount(request.getPeopleCount())
                .emptyReason(reason).build();
    }

    /** 将数据库 JSON 数组安全转换为字符串列表。 */
    private List<String> parseList(String json) {
        if (!StringUtils.hasText(json)) return new ArrayList<>();
        List<String> values = JSON.parseArray(json, String.class);
        return values == null ? new ArrayList<>() : values;
    }
}
