package com.bookmall.book.config;

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
 * 缓存穿透与击穿的防护在 {@link com.bookmall.book.support.BookDetailCache} 中实现。
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisCacheWriter redisCacheWriter(RedisConnectionFactory factory) {
        return new JitterRedisCacheWriter(
                RedisCacheWriter.nonLockingRedisCacheWriter(factory), 0.1);
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory, RedisCacheWriter writer) {
        return RedisCacheManager.builder(writer)
                .cacheDefaults(baseConfig().entryTtl(Duration.ofMinutes(30)))
                // 分类数据变化少：基准 TTL 更长
                .withCacheConfiguration("category", baseConfig().entryTtl(Duration.ofMinutes(60)))
                // 图书列表 / 分页
                .withCacheConfiguration("books", baseConfig().entryTtl(Duration.ofMinutes(30)))
                .build();
    }

    private RedisCacheConfiguration baseConfig() {
        return RedisCacheConfiguration.defaultCacheConfig()
                // 列表/分页缓存不存 null（穿透防护由详情空值缓存负责）
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));
    }
}
