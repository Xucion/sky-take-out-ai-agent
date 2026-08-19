package com.sky.ai.model;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 默认本地 Provider：不联网、不产生模型费用，便于先验证鉴权和工具链路。
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "fake", matchIfMissing = true)
public class FakeAiChatProvider implements AiChatProvider {

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        // Fake 只识别编排层生成的结构化标记，不尝试模拟一个不可预测的大模型。
        if (userPrompt.contains("status=OPEN")) {
            return "门店目前正在营业，可以正常下单。";
        }
        if (userPrompt.contains("status=CLOSED")) {
            return "门店目前已经打烊，请留意稍后的营业状态。";
        }
        if (userPrompt.contains("status=UNKNOWN")) {
            return "暂时无法确认门店营业状态，建议稍后再试。";
        }
        if (userPrompt.contains("orderStatusText=")) {
            // 从工具生成的受控行中取状态文案，使本地联调仍能看到有意义的业务回答。
            String marker = "orderStatusText=";
            int start = userPrompt.indexOf(marker) + marker.length();
            int end = userPrompt.indexOf('\n', start);
            String statusText = end < 0 ? userPrompt.substring(start) : userPrompt.substring(start, end);
            return "您的订单当前状态是：“" + statusText + "”。";
        }
        // 未命中工具结果时返回能力边界，避免开发阶段把固定文案误认为真实知识回答。
        return "我是本地 Fake 客服，当前可以查询门店营业状态和指定订单进度。";
    }
}
