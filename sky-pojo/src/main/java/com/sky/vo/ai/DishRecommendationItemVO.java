package com.sky.vo.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 提供给 AI 工具的最小菜品推荐结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DishRecommendationItemVO implements Serializable {

    /** 菜品主键。 */
    private Long dishId;

    /** 菜品名称。 */
    private String name;

    /** 推荐时再次确认的实际价格。 */
    private BigDecimal price;

    /** 与用户偏好命中的标签。 */
    @Builder.Default
    private List<String> matchedTags = new ArrayList<>();

    /** 当前菜品允许选择的口味选项。 */
    @Builder.Default
    private List<String> flavorOptions = new ArrayList<>();

    /** 供 Agent 解释推荐理由的稳定原因码。 */
    @Builder.Default
    private List<String> reasonCodes = new ArrayList<>();
}
