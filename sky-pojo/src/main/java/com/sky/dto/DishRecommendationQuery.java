package com.sky.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * MyBatis 查询可售推荐候选时使用的白名单条件。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DishRecommendationQuery implements Serializable {

    /** 最低价格。 */
    private BigDecimal minPrice;

    /** 最高价格。 */
    private BigDecimal maxPrice;

    /** 可选分类编号。 */
    private Long categoryId;
}
