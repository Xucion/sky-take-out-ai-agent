package com.sky.ai.agent.intent;

import com.sky.ai.common.exception.AiServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RuleIntentRecognizer 使用确定性规则识别用户意图。
 */
@Component
public class RuleIntentRecognizer {

    private static final List<Pattern> ORDER_ID_PATTERNS = List.of(
            Pattern.compile(
                    "(?iu)订单\\s*(?:id|编号|号)?\\s*(?:为|是|[:：#])?\\s*(\\d{1,19})(?!\\d)"),
            Pattern.compile("(?iu)(?<!\\d)(\\d{1,19})\\s*号?\\s*订单")
    );

    /**
     * 根据输入消息和显式参数识别确定性规则结果。
     */
    public RuleMatch recognize(String message, Long explicitOrderId) {
        Long orderId = resolveOrderId(message, explicitOrderId);
        String normalized = message.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");

        // 规则层只覆盖高精度表达；未命中交给上下文和 LLM 层。
        if (containsAny(normalized, "退款", "退钱", "退费", "refund")) {
            return new RuleMatch(RuleMatchStatus.MATCHED,
                    CustomerIntent.REFUND_REQUEST, 1.0, orderId);
        }
        if (containsAny(normalized, "营业", "开门", "打烊", "关门")) {
            return new RuleMatch(RuleMatchStatus.MATCHED,
                    CustomerIntent.SHOP_STATUS_QUERY, 0.99, orderId);
        }
        if (orderId != null || containsAny(normalized,
                "订单", "配送", "送到", "进度", "到哪")) {
            return new RuleMatch(orderId == null
                    ? RuleMatchStatus.NEED_CONTEXT : RuleMatchStatus.MATCHED,
                    CustomerIntent.ORDER_PROGRESS_QUERY, 0.98, orderId);
        }
        return new RuleMatch(RuleMatchStatus.NO_MATCH,
                CustomerIntent.UNKNOWN, 0.0, null);
    }

    /**
     * 合并显式订单编号与文本中提取的订单编号。
     */
    private Long resolveOrderId(String message, Long explicitOrderId) {
        Set<Long> extractedIds = new LinkedHashSet<>();
        for (Pattern pattern : ORDER_ID_PATTERNS) {
            Matcher matcher = pattern.matcher(message);
            while (matcher.find()) {
                try {
                    long parsed = Long.parseLong(matcher.group(1));
                    if (parsed <= 0) {
                        throw invalidOrderId("订单 ID 必须是正整数");
                    }
                    extractedIds.add(parsed);
                } catch (NumberFormatException ex) {
                    throw invalidOrderId("订单 ID 超出有效范围");
                }
            }
        }
        if (extractedIds.size() > 1) {
            throw new AiServiceException(HttpStatus.BAD_REQUEST,
                    "AMBIGUOUS_ORDER_ID", "检测到多个订单 ID，请明确指定需要查询的一个订单");
        }
        Long extractedOrderId = extractedIds.stream().findFirst().orElse(null);
        if (explicitOrderId != null && extractedOrderId != null
                && !explicitOrderId.equals(extractedOrderId)) {
            throw new AiServiceException(HttpStatus.BAD_REQUEST,
                    "ORDER_ID_CONFLICT", "填写的订单 ID 与问题中的订单 ID 不一致");
        }
        return explicitOrderId != null ? explicitOrderId : extractedOrderId;
    }

    /**
     * 创建订单编号不合法的业务异常。
     */
    private AiServiceException invalidOrderId(String message) {
        return new AiServiceException(HttpStatus.BAD_REQUEST, "INVALID_ORDER_ID", message);
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
