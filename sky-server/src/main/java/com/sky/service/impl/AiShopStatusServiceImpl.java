package com.sky.service.impl;

import com.sky.service.AiShopStatusService;
import com.sky.vo.ai.ShopStatusVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 只读取业务系统维护的 SHOP_STATUS，不允许 AI 服务直接访问 Redis。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiShopStatusServiceImpl implements AiShopStatusService {

    private static final String SHOP_STATUS_KEY = "SHOP_STATUS";

    private final RedisTemplate redisTemplate;

    @Override
    public ShopStatusVO getShopStatus() {
        // 默认先设为 UNKNOWN，只有拿到明确合法值后才改成 OPEN/CLOSED。
        String status = "UNKNOWN";
        String statusText = "门店状态暂时未知";
        try {
            Object value = redisTemplate.opsForValue().get(SHOP_STATUS_KEY);
            // 兼容 Redis 反序列化出的 Integer/Long，但拒绝字符串等意外类型。
            if (value instanceof Number) {
                int numericStatus = ((Number) value).intValue();
                if (numericStatus == 1) {
                    status = "OPEN";
                    statusText = "营业中";
                } else if (numericStatus == 0) {
                    status = "CLOSED";
                    statusText = "已打烊";
                }
                // 其他数字保持 UNKNOWN，避免新增业务状态被旧 AI 代码错误解释。
            }
        } catch (RuntimeException ex) {
            // Redis 故障不能泄露给模型或用户；日志中也不记录连接串等配置。
            log.warn("AI tool failed to read shop status from Redis", ex);
        }
        // checkedAt 表示实际读取时间，后续可用于缓存新鲜度或回答“截至何时”。
        return ShopStatusVO.builder()
                .status(status)
                .statusText(statusText)
                .checkedAt(LocalDateTime.now())
                .build();
    }
}
