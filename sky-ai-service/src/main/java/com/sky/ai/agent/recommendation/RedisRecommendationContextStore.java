package com.sky.ai.agent.recommendation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 优先使用 Redis 保存短期推荐上下文，并在 Redis 暂不可用时降级到进程内短期缓存。
 */
@Component
public class RedisRecommendationContextStore implements RecommendationContextStore {

    private static final Logger log = LoggerFactory.getLogger(RedisRecommendationContextStore.class);
    private static final String KEY_PREFIX = "ai:ctx:";
    private static final String KEY_SUFFIX = ":recommendation";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;
    private final Map<String, FallbackEntry> fallback = new ConcurrentHashMap<>();

    /**
     * 创建 Redis 推荐上下文存储。
     */
    public RedisRecommendationContextStore(StringRedisTemplate redisTemplate,
                                           ObjectMapper objectMapper,
                                           @Value("${app.recommendation.context-ttl:30m}") Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    /**
     * 从 Redis 读取上下文，连接失败时读取未过期的进程内副本。
     */
    @Override
    public RecommendationContext load(String conversationId) {
        String key = key(conversationId);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                RecommendationContext context = objectMapper.readValue(json, RecommendationContext.class);
                fallback.put(key, new FallbackEntry(context, Instant.now().plus(ttl)));
                return context;
            }
        } catch (DataAccessException | JacksonException ex) {
            log.warn("读取推荐上下文失败，临时使用进程内缓存，conversationId={}", conversationId);
        }
        FallbackEntry entry = fallback.get(key);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            fallback.remove(key);
            return RecommendationContext.empty();
        }
        return entry.context();
    }

    /**
     * 将上下文写入 Redis 并保留一份同期限的进程内降级副本。
     */
    @Override
    public void save(String conversationId, RecommendationContext context) {
        String key = key(conversationId);
        fallback.put(key, new FallbackEntry(context, Instant.now().plus(ttl)));
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(context), ttl);
        } catch (DataAccessException | JacksonException ex) {
            log.warn("保存推荐上下文失败，已写入进程内临时缓存，conversationId={}", conversationId);
        }
    }

    /**
     * 构造限定在推荐命名空间内的 Redis 键。
     */
    private String key(String conversationId) {
        if (conversationId == null || !conversationId.matches("[A-Za-z0-9._:-]{1,64}")) {
            throw new IllegalArgumentException("invalid conversation id");
        }
        return KEY_PREFIX + conversationId + KEY_SUFFIX;
    }

    /**
     * 保存降级缓存内容及其过期时间。
     */
    private record FallbackEntry(RecommendationContext context, Instant expiresAt) {
    }
}
