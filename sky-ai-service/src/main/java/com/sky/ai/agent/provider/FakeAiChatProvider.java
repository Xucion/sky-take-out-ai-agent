package com.sky.ai.agent.provider;

import com.sky.ai.agent.tool.ToolModels;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 默认本地 Provider：不联网、不产生模型费用，便于先验证鉴权和工具链路。
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "fake", matchIfMissing = true)
public class FakeAiChatProvider implements AiChatProvider {

    /**
     * 验证请求并执行一次客服对话。
     */
    @Override
    public AiChatResult chat(String systemPrompt, String userPrompt, List<AiChatTool> tools) {
        // Fake 用确定性规则模拟“模型选择工具”，便于离线验证 Function Calling 编排。
        String normalized = userPrompt.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "推荐", "吃什么", "想吃", "吃点", "来点", "预算", "微辣", "中辣", "重辣")) {
            AiChatTool comboTool = findTool(tools, "recommend_meal_combo");
            if (comboTool != null) {
                ToolModels.MealComboRecommendationResult result =
                        (ToolModels.MealComboRecommendationResult) comboTool.execute();
                if (result.items() == null || result.items().isEmpty()) {
                    String reason = result.emptyReason() == null
                            ? "当前条件下暂时无法组成合适的一餐。" : result.emptyReason();
                    return new AiChatResult(reason, List.of(comboTool.name()));
                }
                StringBuilder answer = new StringBuilder("按").append(result.peopleCount())
                        .append("人、总预算").append(result.budget()).append("元，为你搭配：");
                for (int index = 0; index < result.items().size(); index++) {
                    ToolModels.MealComboItem item = result.items().get(index);
                    if (index > 0) answer.append("；");
                    answer.append(item.name()).append("×").append(item.quantity())
                            .append("，").append(item.subtotal()).append("元");
                    if (item.flavorOptions() != null && !item.flavorOptions().isEmpty()) {
                        answer.append("，可选").append(String.join("、", item.flavorOptions()));
                    }
                }
                answer.append("。合计").append(result.totalPrice()).append("元，剩余预算")
                        .append(result.remainingBudget()).append("元。价格、起售状态和约束已由业务服务复核。");
                return new AiChatResult(answer.toString(), List.of(comboTool.name()));
            }
            AiChatTool tool = findTool(tools, "recommend_dishes");
            if (tool != null) {
                ToolModels.DishRecommendationResult result =
                        (ToolModels.DishRecommendationResult) tool.execute();
                if (result.items() == null || result.items().isEmpty()) {
                    String reason = result.emptyReason() == null
                            ? "暂时没有满足全部条件的菜品。" : result.emptyReason();
                    return new AiChatResult(reason, List.of(tool.name()));
                }
                StringBuilder answer = new StringBuilder("为你推荐：");
                for (int index = 0; index < result.items().size(); index++) {
                    ToolModels.DishRecommendationItem item = result.items().get(index);
                    if (index > 0) {
                        answer.append("；");
                    }
                    answer.append(item.name()).append(" ").append(item.price()).append("元");
                    if (item.matchedTags() != null && !item.matchedTags().isEmpty()) {
                        answer.append("，符合").append(String.join("、", item.matchedTags()));
                    }
                    if (item.flavorOptions() != null && !item.flavorOptions().isEmpty()) {
                        answer.append("，可选").append(String.join("、", item.flavorOptions()));
                    }
                }
                answer.append("。以上价格和起售状态已经业务服务确认。");
                return new AiChatResult(answer.toString(), List.of(tool.name()));
            }
        }
        if (containsAny(normalized, "营业", "开门", "打烊", "关门")) {
            AiChatTool tool = findTool(tools, "get_shop_status");
            if (tool != null) {
                ToolModels.ShopStatus status = (ToolModels.ShopStatus) tool.execute();
                String answer = switch (status.status()) {
                    case "OPEN" -> "门店目前正在营业，可以正常下单。";
                    case "CLOSED" -> "门店目前已经打烊，请留意稍后的营业状态。";
                    default -> "暂时无法确认门店营业状态，建议稍后再试。";
                };
                return new AiChatResult(answer, List.of(tool.name()));
            }
        }
        if (containsAny(normalized, "订单", "配送", "送到", "进度", "到哪")) {
            AiChatTool tool = findTool(tools, "get_order_progress");
            if (tool == null) {
                return new AiChatResult("请提供需要查询的订单 ID，我只会查询当前登录账号下的订单。",
                        List.of());
            }
            ToolModels.OrderProgress progress = (ToolModels.OrderProgress) tool.execute();
            return new AiChatResult("您的订单当前状态是：“" + progress.statusText() + "”。",
                    List.of(tool.name()));
        }
        return new AiChatResult("我是本地 Fake 客服，当前可以推荐菜品、查询门店营业状态和指定订单进度。",
                List.of());
    }

    /**
     * 按名称查找本轮可用工具。
     */
    private AiChatTool findTool(List<AiChatTool> tools, String name) {
        return tools.stream().filter(tool -> tool.name().equals(name)).findFirst().orElse(null);
    }

    /**
     * 判断文本是否包含任一候选关键词。
     */
    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
