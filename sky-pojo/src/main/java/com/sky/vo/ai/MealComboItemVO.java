package com.sky.vo.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 多人整餐组合中的一项菜品。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MealComboItemVO implements Serializable {
    /** 菜品主键。 */
    private Long dishId;
    /** 菜品名称。 */
    private String name;
    /** 推荐时的单价。 */
    private BigDecimal unitPrice;
    /** 推荐数量。 */
    private Integer quantity;
    /** 单价乘以数量后的金额。 */
    private BigDecimal subtotal;
    /** 菜品在组合中的角色。 */
    private String role;
    /** 命中的偏好标签。 */
    @Builder.Default
    private List<String> matchedTags = new ArrayList<>();
    /** 可选择的口味选项。 */
    @Builder.Default
    private List<String> flavorOptions = new ArrayList<>();
    /** 稳定的推荐原因码。 */
    @Builder.Default
    private List<String> reasonCodes = new ArrayList<>();
}
