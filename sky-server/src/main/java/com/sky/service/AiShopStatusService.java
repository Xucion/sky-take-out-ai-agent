package com.sky.service;

import com.sky.vo.ai.ShopStatusVO;

/**
 * 面向 AI 客服的门店状态只读服务。
 */
public interface AiShopStatusService {

    ShopStatusVO getShopStatus();
}
