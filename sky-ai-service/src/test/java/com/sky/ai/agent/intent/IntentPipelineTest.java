package com.sky.ai.agent.intent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证规则未命中时的订单多轮上下文恢复。 */
class IntentPipelineTest {

    /** 验证没有待补订单 ID 时，孤立数字不会越权触发订单查询。 */
    @Test
    void leavesStandaloneNumberUnknownWithoutPendingQuestion() {
        ConversationContextBuilder builder = mock(ConversationContextBuilder.class);
        when(builder.build(7L, "c2", 1L)).thenReturn(
                new ConversationContext("c2", null, List.of()));
        RuleIntentRecognizer recognizer = new RuleIntentRecognizer();
        IntentPipeline pipeline = new IntentPipeline(recognizer, builder);

        IntentResolution resolution = pipeline.resolve(7L, "c2", 1L,
                "19", recognizer.recognize("19")).resolution();

        assertEquals(CustomerIntent.UNKNOWN, resolution.intent());
        assertEquals(null, resolution.orderId());
    }

}
