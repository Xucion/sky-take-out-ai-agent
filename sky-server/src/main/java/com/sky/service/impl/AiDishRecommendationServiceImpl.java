package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.sky.dto.DishRecommendationRequest;
import com.sky.dto.DishRecommendationQuery;
import com.sky.mapper.DishMapper;
import com.sky.service.AiDishRecommendationService;
import com.sky.vo.ai.DishRecommendationCandidateVO;
import com.sky.vo.ai.DishRecommendationItemVO;
import com.sky.vo.ai.DishRecommendationResultVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 通过硬性过滤、加权评分和分类多样性生成可解释的菜品推荐。
 */
@Service
public class AiDishRecommendationServiceImpl implements AiDishRecommendationService {

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 5;

    private final DishMapper dishMapper;

    /**
     * 创建确定性菜品推荐服务。
     */
    public AiDishRecommendationServiceImpl(DishMapper dishMapper) {
        this.dishMapper = dishMapper;
    }

    /**
     * 校验条件、执行硬过滤并按加权得分选择多样化结果。
     */
    @Override
    public DishRecommendationResultVO recommend(DishRecommendationRequest request) {
        DishRecommendationRequest normalized = normalize(request);
        DishRecommendationQuery query = new DishRecommendationQuery(
                normalized.getMinPrice(), normalized.getMaxPrice(), normalized.getCategoryId());

        List<ScoredCandidate> candidates = dishMapper.listRecommendationCandidates(query).stream()
                .filter(candidate -> satisfiesHardConstraints(candidate, normalized))
                .map(candidate -> score(candidate, normalized))
                .sorted(Comparator.comparingDouble(ScoredCandidate::score).reversed()
                        .thenComparing(scored -> scored.candidate().getDishId()))
                .collect(Collectors.toList());

        List<DishRecommendationItemVO> items = selectDiverse(candidates, normalized.getLimit());
        String emptyReason = items.isEmpty()
                ? "没有菜品同时满足当前预算、排除标签和过敏原要求，请放宽一个非安全条件后重试。"
                : null;
        return DishRecommendationResultVO.builder().items(items).emptyReason(emptyReason).build();
    }

    /**
     * 规范化推荐条件并拒绝越界参数。
     */
    private DishRecommendationRequest normalize(DishRecommendationRequest request) {
        if (request == null) {
            request = new DishRecommendationRequest();
        }
        if (request.getMinPrice() != null && request.getMinPrice().signum() < 0
                || request.getMaxPrice() != null && request.getMaxPrice().signum() <= 0
                || request.getMinPrice() != null && request.getMaxPrice() != null
                && request.getMinPrice().compareTo(request.getMaxPrice()) > 0) {
            throw new IllegalArgumentException("价格范围无效");
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
        int limit = request.getLimit() == null ? DEFAULT_LIMIT : request.getLimit();
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("推荐数量必须在1到5之间");
        }
        request.setLimit(limit);
        return request;
    }

    /**
     * 校验口味等级范围。
     */
    private void validateLevel(Integer value, String name) {
        if (value != null && (value < 0 || value > 4)) {
            throw new IllegalArgumentException(name + "必须在0到4之间");
        }
    }

    /**
     * 规范化工具传入的标签并限制数量和长度。
     */
    private List<String> normalizeLabels(List<String> values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                if (!StringUtils.hasText(value)) {
                    continue;
                }
                String label = value.trim();
                if (label.length() > 20) {
                    throw new IllegalArgumentException("标签长度不能超过20个字符");
                }
                normalized.add(label);
            }
        }
        if (normalized.size() > 20) {
            throw new IllegalArgumentException("标签数量不能超过20个");
        }
        return new ArrayList<>(normalized);
    }

    /**
     * 执行过敏原、排除标签和最高甜度等硬性约束。
     */
    private boolean satisfiesHardConstraints(DishRecommendationCandidateVO candidate,
                                             DishRecommendationRequest request) {
        Set<String> tags = new HashSet<>(parseList(candidate.getTagsJson()));
        Set<String> allergens = new HashSet<>(parseList(candidate.getAllergensJson()));
        if (request.getExcludedTags().stream().anyMatch(tags::contains)) {
            return false;
        }
        if (request.getAllergens().stream().anyMatch(allergens::contains)) {
            return false;
        }
        return request.getSweetnessLevelMax() == null
                || candidate.getSweetnessLevel() <= request.getSweetnessLevelMax();
    }

    /**
     * 计算口味、标签和价格匹配的基础分数。
     */
    private ScoredCandidate score(DishRecommendationCandidateVO candidate,
                                  DishRecommendationRequest request) {
        List<String> tags = parseList(candidate.getTagsJson());
        List<String> matchedTags = request.getPreferredTags().stream()
                .filter(tags::contains).collect(Collectors.toList());
        double tasteScore = tasteScore(candidate.getSpicyLevel(), request);
        double tagScore = request.getPreferredTags().isEmpty() ? 25.0
                : 25.0 * matchedTags.size() / request.getPreferredTags().size();
        double priceScore = priceScore(candidate.getPrice(), request);
        List<String> reasons = new ArrayList<>();
        if (request.getMinPrice() != null || request.getMaxPrice() != null) {
            reasons.add("PRICE_MATCH");
        }
        if ((request.getSpicyLevelMin() != null || request.getSpicyLevelMax() != null)
                && tasteScore >= 30.0) {
            reasons.add("SPICY_MATCH");
        }
        if (!matchedTags.isEmpty()) {
            reasons.add("TAG_MATCH");
        }
        if (request.getSweetnessLevelMax() != null) {
            reasons.add("SWEETNESS_MATCH");
        }
        return new ScoredCandidate(candidate, tasteScore + tagScore + priceScore,
                matchedTags, reasons);
    }

    /**
     * 计算辣度匹配分，最大为40分。
     */
    private double tasteScore(int spicyLevel, DishRecommendationRequest request) {
        Integer min = request.getSpicyLevelMin();
        Integer max = request.getSpicyLevelMax();
        if (min == null && max == null) {
            return 40.0;
        }
        int effectiveMin = min == null ? 0 : min;
        int effectiveMax = max == null ? 4 : max;
        if (spicyLevel >= effectiveMin && spicyLevel <= effectiveMax) {
            return 40.0;
        }
        int distance = spicyLevel < effectiveMin
                ? effectiveMin - spicyLevel : spicyLevel - effectiveMax;
        return Math.max(0.0, 40.0 - distance * 15.0);
    }

    /**
     * 计算预算内价格匹配分，最大为25分。
     */
    private double priceScore(BigDecimal price, DishRecommendationRequest request) {
        if (request.getMinPrice() == null && request.getMaxPrice() == null) {
            return 25.0;
        }
        if (request.getMaxPrice() == null) {
            return 25.0;
        }
        BigDecimal min = request.getMinPrice() == null ? BigDecimal.ZERO : request.getMinPrice();
        BigDecimal max = request.getMaxPrice();
        if (max.compareTo(min) == 0) {
            return 25.0;
        }
        BigDecimal center = min.add(max).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
        BigDecimal halfRange = max.subtract(min).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
        double distance = price.subtract(center).abs()
                .divide(halfRange, 6, RoundingMode.HALF_UP).doubleValue();
        return Math.max(0.0, 25.0 * (1.0 - Math.min(1.0, distance)));
    }

    /**
     * 使用贪心策略优先选择尚未出现的分类，避免结果过度集中。
     */
    private List<DishRecommendationItemVO> selectDiverse(List<ScoredCandidate> candidates, int limit) {
        List<DishRecommendationItemVO> selected = new ArrayList<>();
        Set<Long> usedCategories = new HashSet<>();
        List<ScoredCandidate> remaining = new ArrayList<>(candidates);
        while (!remaining.isEmpty() && selected.size() < limit) {
            ScoredCandidate best = remaining.stream().max(Comparator
                    .comparingDouble((ScoredCandidate item) -> item.score()
                            + (usedCategories.contains(item.candidate().getCategoryId()) ? 0.0 : 10.0))
                    .thenComparing(item -> -item.candidate().getDishId()))
                    .orElseThrow(() -> new IllegalStateException("候选菜品列表不应为空"));
            remaining.remove(best);
            boolean diverse = usedCategories.add(best.candidate().getCategoryId());
            List<String> reasons = new ArrayList<>(best.reasonCodes());
            if (diverse) {
                reasons.add("CATEGORY_DIVERSITY");
            }
            selected.add(DishRecommendationItemVO.builder()
                    .dishId(best.candidate().getDishId())
                    .name(best.candidate().getName())
                    .price(best.candidate().getPrice())
                    .matchedTags(best.matchedTags())
                    .flavorOptions(parseList(best.candidate().getFlavorOptionsJson()))
                    .reasonCodes(reasons)
                    .build());
        }
        return selected;
    }

    /**
     * 将数据库 JSON 数组安全转换为字符串列表。
     */
    private List<String> parseList(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        List<String> values = JSON.parseArray(json, String.class);
        return values == null ? new ArrayList<>() : values;
    }

    /**
     * 保存候选菜品的基础分数及可解释信息。
     */
    private static final class ScoredCandidate {
        private final DishRecommendationCandidateVO candidate;
        private final double score;
        private final List<String> matchedTags;
        private final List<String> reasonCodes;

        /**
         * 创建已评分候选菜品。
         */
        private ScoredCandidate(DishRecommendationCandidateVO candidate, double score,
                                List<String> matchedTags, List<String> reasonCodes) {
            this.candidate = candidate;
            this.score = score;
            this.matchedTags = matchedTags;
            this.reasonCodes = reasonCodes;
        }

        /** 返回候选菜品。 */
        private DishRecommendationCandidateVO candidate() { return candidate; }

        /** 返回基础匹配分数。 */
        private double score() { return score; }

        /** 返回命中的偏好标签。 */
        private List<String> matchedTags() { return matchedTags; }

        /** 返回推荐原因码。 */
        private List<String> reasonCodes() { return reasonCodes; }
    }
}
