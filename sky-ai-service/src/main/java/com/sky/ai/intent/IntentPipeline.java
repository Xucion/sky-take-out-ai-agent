package com.sky.ai.intent;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class IntentPipeline {

    private final RuleIntentRecognizer ruleRecognizer;
    private final ConversationContextBuilder contextBuilder;

    public IntentPipeline(RuleIntentRecognizer ruleRecognizer,
                          ConversationContextBuilder contextBuilder) {
        this.ruleRecognizer = ruleRecognizer;
        this.contextBuilder = contextBuilder;
    }

    public RuleMatch recognize(String message, Long explicitOrderId) {
        return ruleRecognizer.recognize(message, explicitOrderId);
    }

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

    private IntentRoutingResult result(CustomerIntent intent,
                                       double confidence,
                                       ResolutionSource source,
                                       Long orderId,
                                       ConversationContext context) {
        return new IntentRoutingResult(
                new IntentResolution(intent, confidence, source, orderId), context);
    }

    private boolean isOrderFollowUp(String message) {
        String normalized = message.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        return containsAny(normalized,
                "它怎么样", "它到哪", "这个怎么样", "那单", "这单",
                "什么时候到", "到哪了", "进度呢", "现在呢", "怎么样了");
    }

    private boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
