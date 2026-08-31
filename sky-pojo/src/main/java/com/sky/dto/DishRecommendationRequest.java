package com.sky.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 工具提交给业务服务的结构化菜品推荐条件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DishRecommendationRequest implements Serializable {

    /** 单个菜品最低价格。 */
    private BigDecimal minPrice;

    /** 单个菜品最高价格。 */
    private BigDecimal maxPrice;

    /** 期望的最低辣度。 */
    private Integer spicyLevelMin;

    /** 期望的最高辣度。 */
    private Integer spicyLevelMax;

    /** 可接受的最高甜度。 */
    private Integer sweetnessLevelMax;

    /** 偏好的标准化标签。 */
    @Builder.Default
    private List<String> preferredTags = new ArrayList<>();

    /** 明确排除的标准化标签。 */
    @Builder.Default
    private List<String> excludedTags = new ArrayList<>();

    /** 必须硬性排除的过敏原。 */
    @Builder.Default
    private List<String> allergens = new ArrayList<>();

    /** 可选菜品分类编号。 */
    private Long categoryId;

    /** 返回结果数量，范围为 1～5。 */
    private Integer limit;
}
