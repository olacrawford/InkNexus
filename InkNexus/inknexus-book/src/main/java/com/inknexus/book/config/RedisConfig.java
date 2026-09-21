package com.inknexus.book.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Redis 缓存配置：JSON 序列化 + 分级 TTL + 随机抖动（缓存雪崩防护）。
 *
 * <p>SpringCache 默认 JDK 序列化会出现二进制乱码，这里统一改成 JSON 序列化；
 * 不同 cacheName 配不同基准 TTL（分类变化少给更长），写入时再经 {@link JitterRedisCacheWriter}
 * 加 ±10% 随机抖动，避免同批 key 同时到期把请求压到 MySQL。
 * 缓存穿透与击穿的防护在 {@link com.inknexus.book.support.BookDetailCache} 中实现。
 */
@Configuration
public class RedisConfig {

    /** 非锁 writer（不做缓存级互斥，读多写少吞吐优先），外套抖动装饰器 ratio=0.1，与 BookDetailCache 手写抖动保持同比例 */
    @Bean
    public RedisCacheWriter redisCacheWriter(RedisConnectionFactory factory) {
        return new JitterRedisCacheWriter(
                RedisCacheWriter.nonLockingRedisCacheWriter(factory), 0.1);
    }

    /** CacheManager 装配：默认基准 TTL 30 分钟，按 cacheName 覆盖差异化基准 TTL，落盘前统一再经抖动 writer */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory, RedisCacheWriter writer) {
        return RedisCacheManager.builder(writer)
                .cacheDefaults(baseConfig().entryTtl(Duration.ofMinutes(30)))
                // 分类数据变化少：基准 TTL 更长
                .withCacheConfiguration("category", baseConfig().entryTtl(Duration.ofMinutes(60)))
                .withCacheConfiguration("books", baseConfig().entryTtl(Duration.ofMinutes(30)))
                .build();
    }

    /** 公共缓存配置：JSON 序列化（写入 @class 类型信息，读出可还原 PageResult 等原类型）；null 不落缓存 */
    private RedisCacheConfiguration baseConfig() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));
    }
}
