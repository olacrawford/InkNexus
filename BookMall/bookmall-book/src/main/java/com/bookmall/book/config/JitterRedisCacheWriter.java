package com.bookmall.book.config;

import org.springframework.data.redis.cache.CacheStatistics;
import org.springframework.data.redis.cache.CacheStatisticsCollector;
import org.springframework.data.redis.cache.RedisCacheWriter;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * 给缓存写入加 TTL 随机抖动的 RedisCacheWriter 装饰器（缓存雪崩防护）。
 *
 * <p>所有 key 使用统一 TTL 时，同一批写入的 key 会同时到期，过期瞬间请求全部打到 MySQL；
 * 本装饰器在委托的 writer 落盘前（同步 {@link #put} 与异步 {@link #store}），
 * 把 TTL 缩放到基准的 {@code [1-ratio, 1+ratio]} 区间随机取值，让同批 key 的过期时间自然错开。
 * 读、删、清等其余操作全部原样委托。
 */
public class JitterRedisCacheWriter implements RedisCacheWriter {

    private final RedisCacheWriter delegate;
    private final double ratio;
    private final Random random = new Random();

    public JitterRedisCacheWriter(RedisCacheWriter delegate, double ratio) {
        this.delegate = delegate;
        this.ratio = ratio;
    }

    @Override
    public byte[] get(String name, byte[] key) {
        return delegate.get(name, key);
    }

    @Override
    public CompletableFuture<byte[]> retrieve(String name, byte[] key, Duration ttl) {
        return delegate.retrieve(name, key, ttl);
    }

    @Override
    public void put(String name, byte[] key, byte[] value, Duration ttl) {
        delegate.put(name, key, value, jitter(ttl));
    }

    @Override
    public CompletableFuture<Void> store(String name, byte[] key, byte[] value, Duration ttl) {
        return delegate.store(name, key, value, jitter(ttl));
    }

    @Override
    public byte[] putIfAbsent(String name, byte[] key, byte[] value, Duration ttl) {
        return delegate.putIfAbsent(name, key, value, jitter(ttl));
    }

    @Override
    public void remove(String name, byte[] key) {
        delegate.remove(name, key);
    }

    @Override
    public void clean(String name, byte[] pattern) {
        delegate.clean(name, pattern);
    }

    @Override
    public void clearStatistics(String cacheName) {
        delegate.clearStatistics(cacheName);
    }

    @Override
    public RedisCacheWriter withStatisticsCollector(CacheStatisticsCollector cacheStatisticsCollector) {
        return new JitterRedisCacheWriter(
                delegate.withStatisticsCollector(cacheStatisticsCollector), ratio);
    }

    @Override
    public CacheStatistics getCacheStatistics(String cacheName) {
        return delegate.getCacheStatistics(cacheName);
    }

    /** 基准 TTL 缩放到 [1-ratio, 1+ratio] 随机取值；无 TTL（永久）或零 TTL 原样返回 */
    private Duration jitter(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return ttl;
        }
        long ms = (long) (ttl.toMillis() * (1 - ratio + random.nextDouble() * 2 * ratio));
        return Duration.ofMillis(Math.max(ms, 1));
    }
}
