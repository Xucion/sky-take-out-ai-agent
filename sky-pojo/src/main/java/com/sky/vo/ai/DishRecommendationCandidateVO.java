package com.sky.vo.ai;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 推荐服务从数据库读取的候选菜品内部视图。
 */
@Data
public class DishRecommendationCandidateVO implements Serializable {

    /** 菜品主键。 */
    private Long dishId;

    /** 菜品名称。 */
    private String name;

    /** 分类主键。 */
    private Long categoryId;

    /** 当前实际价格。 */
    private BigDecimal price;

    /** 辣度等级。 */
    private Integer spicyLevel;

    /** 甜度等级。 */
    private Integer sweetnessLevel;

    /** 油腻程度。 */
    private Integer oilinessLevel;

    /** 数据库中的标签 JSON。 */
    private String tagsJson;

    /** 数据库中的过敏原 JSON。 */
    private String allergensJson;

    /** 数据库中的辣度选项 JSON。 */
    private String flavorOptionsJson;
}
