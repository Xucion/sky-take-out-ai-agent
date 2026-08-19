package com.sky.vo.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 提供给模型的订单进度白名单字段，刻意不包含完整订单实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderProgressVO implements Serializable {

    /** 订单主键，用于生成订单详情页链接；使用前已经完成归属校验。 */
    private Long orderId;

    /** 稳定英文状态枚举，供 Agent 确定性判断。 */
    private String status;

    /** 与当前状态对应的用户可读文案。 */
    private String statusText;

    /** 用户下单时保存的预计送达时间，不代表实时骑手 ETA。 */
    private LocalDateTime estimatedDeliveryTime;

    /** 订单完成时记录的实际送达时间。 */
    private LocalDateTime deliveredAt;

    /** 由业务代码计算的操作提示，不代表 Agent 已获得对应写工具。 */
    private List<String> allowedUserActions;

    /** 当前项目无骑手轨迹能力，因此 P0 固定为 false。 */
    private boolean trackingAvailable;

    /** 用户端订单详情路由，不是后端内部 URL。 */
    private String detailPath;
}
