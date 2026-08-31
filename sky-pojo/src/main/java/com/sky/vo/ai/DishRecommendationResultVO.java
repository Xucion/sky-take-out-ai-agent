package com.sky.vo.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 菜品推荐工具的确定性结果集合。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DishRecommendationResultVO implements Serializable {

    /** 按综合得分和分类多样性排序后的菜品。 */
    @Builder.Default
    private List<DishRecommendationItemVO> items = new ArrayList<>();

    /** 没有候选结果时可供 Agent 直接使用的安全提示。 */
    private String emptyReason;
}
