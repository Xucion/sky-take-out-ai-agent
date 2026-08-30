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
        return new AiChatResult("我是本地 Fake 客服，当前可以查询门店营业状态和指定订单进度。",
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
