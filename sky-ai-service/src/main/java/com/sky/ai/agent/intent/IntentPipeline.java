package com.sky.ai.agent.intent;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * IntentPipeline 按顺序执行对应的识别与路由阶段。
 */
@Component
public class IntentPipeline {

    private final RuleIntentRecognizer ruleRecognizer;
    private final ConversationContextBuilder contextBuilder;

    /**
     * 初始化 IntentPipeline，并注入其运行所需的依赖。
     */
    public IntentPipeline(RuleIntentRecognizer ruleRecognizer,
                          ConversationContextBuilder contextBuilder) {
        this.ruleRecognizer = ruleRecognizer;
        this.contextBuilder = contextBuilder;
    }

    /**
     * 根据输入消息和显式参数识别确定性规则结果。
     */
    public RuleMatch recognize(String message, Long explicitOrderId) {
        return ruleRecognizer.recognize(message, explicitOrderId);
    }

    /**
     * 结合规则结果和会话上下文解析最终意图。
     */
    public IntentRoutingResult resolve(long userId,
                                       String conversationId,
                                       long beforeSequence,
                                       String message,
                                       RuleMatch rule) {
        ConversationContext context = contextBuilder.build(
                userId, conversationId, beforeSequence);

        if (rule.intent() == CustomerIntent.REFUND_REQUEST
                || rule.intent() == CustomerIntent.SHOP_STATUS_QUERY) {
            return result(rule.intent(), rule.confidence(),
                    ResolutionSource.RULE, rule.orderId(), context);
        }
        if (rule.intent() == CustomerIntent.ORDER_PROGRESS_QUERY) {
            Long orderId = rule.orderId() != null
                    ? rule.orderId() : context.relatedOrderId();
            ResolutionSource source = rule.orderId() != null
                    ? ResolutionSource.RULE
                    : orderId != null ? ResolutionSource.CONTEXT : ResolutionSource.RULE;
            double confidence = source == ResolutionSource.CONTEXT ? 0.92 : rule.confidence();
            return result(CustomerIntent.ORDER_PROGRESS_QUERY,
                    confidence, source, orderId, context);
        }
        if (context.relatedOrderId() != null && isOrderFollowUp(message)) {
            return result(CustomerIntent.ORDER_PROGRESS_QUERY, 0.88,
                    ResolutionSource.CONTEXT, context.relatedOrderId(), context);
        }

        // 前两层无法确定时交给 LLM；会话订单只作为受控候选槽位，不视为已确认意图。
        return result(CustomerIntent.UNKNOWN, 0.0, ResolutionSource.LLM,
                context.relatedOrderId(), context);
    }

    /**
     * 组装一次意图路由结果。
     */
    private IntentRoutingResult result(CustomerIntent intent,
                                       double confidence,
                                       ResolutionSource source,
                                       Long orderId,
                                       ConversationContext context) {
        return new IntentRoutingResult(
                new IntentResolution(intent, confidence, source, orderId), context);
    }

    /**
     * 判断消息是否属于订单进度追问。
     */
    private boolean isOrderFollowUp(String message) {
        String normalized = message.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        return containsAny(normalized,
                "它怎么样", "它到哪", "这个怎么样", "那单", "这单",
                "什么时候到", "到哪了", "进度呢", "现在呢", "怎么样了");
    }

    /**
     * 判断文本是否包含任一候选关键词。
     */
    private boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
