package com.sky.service;

import com.sky.vo.ai.OrderProgressVO;

public interface AiOrderProgressService {

    OrderProgressVO getOrderProgress(Long orderId, Long userId);
}
