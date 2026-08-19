package com.sky.service.impl;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.service.AiOrderProgressService;
import com.sky.vo.ai.OrderProgressVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 为 AI 客服提供最小化的订单进度视图，不返回电话、地址、备注等非必要字段。
 */
@Service
@RequiredArgsConstructor
public class AiOrderProgressServiceImpl implements AiOrderProgressService {

    private final OrderMapper orderMapper;

    @Override
    public OrderProgressVO getOrderProgress(Long orderId, Long userId) {
        // 必须在数据库查询条件中同时限定订单和用户，不能查出后再在 AI 层过滤。
        Orders order = orderMapper.getProgressByIdAndUserId(orderId, userId);
        if (order == null) {
            return null;
        }

        List<String> allowedActions = new ArrayList<>();
        // 查看详情和请求人工对所有已找到的订单开放，但最终仍由对应接口再次鉴权。
        allowedActions.add("VIEW_DETAIL");
        allowedActions.add("REQUEST_HANDOFF");
        // 允许操作由确定性业务规则计算；模型只能读取结果，不能自行授权取消订单。
        if (Orders.PENDING_PAYMENT.equals(order.getStatus())
                || Orders.TO_BE_CONFIRMED.equals(order.getStatus())) {
            allowedActions.add("REQUEST_CANCEL");
        }

        return OrderProgressVO.builder()
                .orderId(order.getId())
                .status(statusCode(order.getStatus()))
                .statusText(statusText(order.getStatus()))
                .estimatedDeliveryTime(order.getEstimatedDeliveryTime())
                .deliveredAt(order.getDeliveryTime())
                .allowedUserActions(allowedActions)
                // 当前系统没有骑手位置数据，必须显式告诉模型不能生成实时轨迹描述。
                .trackingAvailable(false)
                .detailPath("/orders/" + order.getId())
                .build();
    }

    private String statusCode(Integer status) {
        // 对模型暴露稳定英文枚举，避免把数据库数字状态直接写入 Prompt。
        if (Orders.PENDING_PAYMENT.equals(status)) {
            return "PENDING_PAYMENT";
        }
        if (Orders.TO_BE_CONFIRMED.equals(status)) {
            return "TO_BE_CONFIRMED";
        }
        if (Orders.CONFIRMED.equals(status)) {
            return "CONFIRMED";
        }
        if (Orders.DELIVERY_IN_PROGRESS.equals(status)) {
            return "DELIVERY_IN_PROGRESS";
        }
        if (Orders.COMPLETED.equals(status)) {
            return "COMPLETED";
        }
        if (Orders.CANCELLED.equals(status)) {
            return "CANCELLED";
        }
        return "UNKNOWN";
    }

    private String statusText(Integer status) {
        // 中文文案由服务端统一映射，Agent 可以组织语气，但不能改变状态事实。
        if (Orders.PENDING_PAYMENT.equals(status)) {
            return "等待支付";
        }
        if (Orders.TO_BE_CONFIRMED.equals(status)) {
            return "等待商家接单";
        }
        if (Orders.CONFIRMED.equals(status)) {
            return "商家已接单，正在准备";
        }
        if (Orders.DELIVERY_IN_PROGRESS.equals(status)) {
            return "订单正在派送";
        }
        if (Orders.COMPLETED.equals(status)) {
            return "订单已完成";
        }
        if (Orders.CANCELLED.equals(status)) {
            return "订单已取消";
        }
        return "订单状态未知";
    }
}
