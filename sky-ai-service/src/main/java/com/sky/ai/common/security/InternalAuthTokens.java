package com.sky.ai.common.security;

/**
 * 保存 AI 服务调用主服务时使用的服务令牌和用户上下文令牌。
 */
public record InternalAuthTokens(String serviceToken, String userContextToken) {
}
