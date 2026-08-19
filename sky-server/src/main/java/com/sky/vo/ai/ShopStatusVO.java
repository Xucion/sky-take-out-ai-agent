package com.sky.vo.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 提供给 AI 的稳定门店状态，UNKNOWN 表示不能可靠判断，不能等同于 CLOSED。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopStatusVO implements Serializable {

    private String status;
    private String statusText;
    private LocalDateTime checkedAt;
}
