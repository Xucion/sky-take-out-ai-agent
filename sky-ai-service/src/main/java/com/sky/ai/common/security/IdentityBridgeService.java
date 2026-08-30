package com.sky.ai.common.security;

import com.sky.ai.common.config.AiServiceProperties;
import org.springframework.stereotype.Service;

/**
 * 把外部用户身份转换为两个短时内部令牌。调用方不能自行传 userId。
 */
@Service
public class IdentityBridgeService {

    private final HmacJwtService jwtService;
    private final AiServiceProperties.Auth auth;

    /**
     * 初始化 IdentityBridgeService，并注入其运行所需的依赖。
     */
    public IdentityBridgeService(HmacJwtService jwtService, AiServiceProperties properties) {
        this.jwtService = jwtService;
        this.auth = properties.auth();
    }

    /**
     * 验证用户请求头中的令牌。
     */
    public UserIdentity verifyUser(String userToken) {
        // 兼容现有前端直接传 JWT，以及未来网关传标准 Bearer JWT 两种格式。
        return jwtService.verifyUserToken(stripBearer(userToken), auth.userJwtSecret());
    }

    /**
     * 为访问业务服务签发双内部令牌。
     */
    public InternalAuthTokens issueInternalTokens(UserIdentity identity, String conversationId) {
        // 每次工具调用重新签发短时令牌，P0 不做跨请求缓存，优先降低重放窗口。
        String serviceToken = jwtService.createServiceToken(
                auth.serviceJwtSecret(), auth.serviceTokenTtl());
        String contextToken = jwtService.createUserContextToken(identity.userId(), conversationId,
                auth.userContextJwtSecret(), auth.userContextTokenTtl());
        return new InternalAuthTokens(serviceToken, contextToken);
    }

    /**
     * 移除可选的 Bearer 前缀并规范化令牌。
     */
    private String stripBearer(String value) {
        // regionMatches(ignoreCase=true) 只处理前缀，不修改 JWT 本体的大小写。
        if (value != null && value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return value.substring(7).trim();
        }
        return value;
    }
}
