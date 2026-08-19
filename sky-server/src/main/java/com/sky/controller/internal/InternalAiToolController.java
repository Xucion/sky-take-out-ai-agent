package com.sky.controller.internal;

import com.sky.context.AiRequestContext;
import com.sky.service.AiOrderProgressService;
import com.sky.service.AiShopStatusService;
import com.sky.vo.ai.AiToolResponse;
import com.sky.vo.ai.OrderProgressVO;
import com.sky.vo.ai.ShopStatusVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 只供 sky-ai-service 调用的窄接口，不直接向用户前端开放。
 */
@RestController
@RequestMapping("/internal/ai-tools")
@RequiredArgsConstructor
public class InternalAiToolController {

    private final AiOrderProgressService orderProgressService;
    private final AiShopStatusService shopStatusService;

    /**
     * 查询当前门店营业状态。即使该信息与用户无关，也沿用双 JWT 鉴权，避免形成公开旁路。
     */
    @GetMapping("/shop/status")
    public ResponseEntity<AiToolResponse<ShopStatusVO>> getShopStatus() {
        String traceId = AiRequestContext.getTraceId();
        return ResponseEntity.ok(AiToolResponse.success(shopStatusService.getShopStatus(), traceId));
    }

    /**
     * 查询当前认证用户的一笔订单进度。
     * 路径中的 me 强调用户身份来自认证上下文，而不是调用方可填写的 userId。
     */
    @GetMapping("/users/me/orders/{orderId}/progress")
    public ResponseEntity<AiToolResponse<OrderProgressVO>> getOrderProgress(@PathVariable Long orderId) {
        String traceId = AiRequestContext.getTraceId();
        // userId 来自已验证的内部上下文；接口故意不接收 userId 参数。
        Long userId = AiRequestContext.getCurrentUserId();

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AiToolResponse.error("UNAUTHENTICATED", "用户上下文不可用", false, traceId));
        }
        if (orderId == null || orderId <= 0) {
            // 在进入数据库前拒绝无效 ID，避免无意义查询并保持错误语义稳定。
            return ResponseEntity.badRequest()
                    .body(AiToolResponse.error("INVALID_ARGUMENT", "订单ID无效", false, traceId));
        }

        OrderProgressVO progress = orderProgressService.getOrderProgress(orderId, userId);
        if (progress == null) {
            // 不区分“订单不存在”和“订单属于其他用户”，避免泄露订单是否存在。
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(AiToolResponse.error("ORDER_NOT_FOUND", "未找到当前账号下的该订单", false, traceId));
        }
        // 200 只表示工具成功拿到当前用户的订单进度；业务回答仍由上层 Agent 组织。
        return ResponseEntity.ok(AiToolResponse.success(progress, traceId));
    }
}
