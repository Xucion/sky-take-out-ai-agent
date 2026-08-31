package com.sky.ai.agent.order;

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

/** 优先使用 Redis 保存订单查询状态，故障时降级到同期限进程内缓存。 */
@Component
public class RedisOrderQueryContextStore implements OrderQueryContextStore {

    private static final Logger log = LoggerFactory.getLogger(RedisOrderQueryContextStore.class);
    private static final String KEY_PREFIX = "ai:ctx:";
    private static final String KEY_SUFFIX = ":order";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;
    private final Map<String, FallbackEntry> fallback = new ConcurrentHashMap<>();

    /** 创建 Redis 订单查询上下文存储。 */
    public RedisOrderQueryContextStore(StringRedisTemplate redisTemplate,
                                       ObjectMapper objectMapper,
                                       @Value("${app.order.context-ttl:30m}") Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    /** 从 Redis 读取上下文，连接失败时读取未过期的进程内副本。 */
    @Override
    public OrderQueryContext load(String conversationId) {
        String key = key(conversationId);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                OrderQueryContext context = objectMapper.readValue(json, OrderQueryContext.class);
                fallback.put(key, new FallbackEntry(context, Instant.now().plus(ttl)));
                return context;
            }
        } catch (DataAccessException | JacksonException ex) {
            log.warn("读取订单查询上下文失败，临时使用进程内缓存，conversationId={}", conversationId);
        }
        FallbackEntry entry = fallback.get(key);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            fallback.remove(key);
            return OrderQueryContext.empty();
        }
        return entry.context();
    }

    /** 将上下文写入 Redis 并保留一份同期限的进程内降级副本。 */
    @Override
    public void save(String conversationId, OrderQueryContext context) {
        String key = key(conversationId);
        fallback.put(key, new FallbackEntry(context, Instant.now().plus(ttl)));
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(context), ttl);
        } catch (DataAccessException | JacksonException ex) {
            log.warn("保存订单查询上下文失败，已写入进程内临时缓存，conversationId={}", conversationId);
        }
    }

    /** 从 Redis 和进程内降级缓存中清除已消费状态。 */
    @Override
    public void clear(String conversationId) {
        String key = key(conversationId);
        fallback.remove(key);
        try {
            redisTemplate.delete(key);
        } catch (DataAccessException ex) {
            log.warn("清除订单查询上下文失败，Redis 中的状态将等待过期，conversationId={}", conversationId);
        }
    }

    /** 构造限定在订单上下文命名空间内的 Redis 键。 */
    private String key(String conversationId) {
        if (conversationId == null || !conversationId.matches("[A-Za-z0-9._:-]{1,64}")) {
            throw new IllegalArgumentException("invalid conversation id");
        }
        return KEY_PREFIX + conversationId + KEY_SUFFIX;
    }

    /** 保存降级缓存内容及其过期时间。 */
    private record FallbackEntry(OrderQueryContext context, Instant expiresAt) {
    }
}
