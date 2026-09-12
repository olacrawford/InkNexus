package com.inknexus.book.support;

import com.inknexus.book.vo.BookDetailVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 图书详情缓存：手写 Cache-Aside，落地缓存击穿与穿透的防护。
 *
 * <p>防击穿（互斥重建）：缓存未命中时先 {@code SETNX} 抢互斥锁，只有一个线程回源 MySQL 重建，
 * 其余线程自旋等待后读新缓存；抢到锁的线程重建前再查一次缓存（双重检查）。
 * 锁带 TTL 防死锁，释放用 Lua 脚本「校验锁值再删除」原子完成，避免误删其它持有者的锁。
 *
 * <p>防穿透（空值缓存）：数据库里不存在的 id 也写入短 TTL 的空值标记，
 * 恶意反复查询不存在的图书时直接命中空值标记，不再打到 MySQL。
 */
@Slf4j
@Component
public class BookDetailCache {

    static final String KEY_PREFIX = "cache:book:detail:";
    static final String LOCK_PREFIX = "cache:book:detail:lock:";
    static final String NULL_MARK = "__NULL__";

    static final Duration DATA_TTL = Duration.ofMinutes(30);
    static final Duration NULL_TTL = Duration.ofMinutes(2);
    static final Duration LOCK_TTL = Duration.ofSeconds(10);

    /** TTL 随机抖动比例：±10%，避免同批 key 同时到期引发雪崩 */
    private static final double JITTER_RATIO = 0.1;
    private static final long SPIN_INTERVAL_MS = 50;
    private static final int SPIN_MAX_TIMES = 20;

    /** 校验锁值后删除：锁值不匹配（锁已过期被他人持有）时不删除 */
    private static final RedisScript<Long> UNLOCK_SCRIPT = RedisScript.of(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();
    private final long spinIntervalMs;
    private final int spinMaxTimes;

    /** 存在多个构造器时用 @Autowired 标注 Spring 应使用的入口 */
    @Autowired
    public BookDetailCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this(redisTemplate, objectMapper, SPIN_INTERVAL_MS, SPIN_MAX_TIMES);
    }

    /** 自旋参数可注入，仅供单元测试缩短等待 */
    BookDetailCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                    long spinIntervalMs, int spinMaxTimes) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.spinIntervalMs = spinIntervalMs;
        this.spinMaxTimes = spinMaxTimes;
    }

    /**
     * 按详情缓存读取，未命中时用 loader 回源并重建缓存。
     *
     * @param id     图书 id
     * @param loader 缓存未命中时的回源逻辑（查 MySQL）
     * @return 图书详情，图书不存在返回 null
     */
    public BookDetailVO load(Long id, Supplier<BookDetailVO> loader) {
        String key = KEY_PREFIX + id;

        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            if (NULL_MARK.equals(cached)) {
                return null;
            }
            BookDetailVO hit = fromJson(key, cached);
            if (hit != null) {
                return hit;
            }
            // JSON 损坏：坏数据已被删除，继续走重建流程
        }

        String lockKey = LOCK_PREFIX + id;
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, LOCK_TTL);
        if (Boolean.TRUE.equals(locked)) {
            try {
                // 双重检查：抢到锁前可能其它线程刚好重建完成
                cached = redisTemplate.opsForValue().get(key);
                if (cached != null) {
                    BookDetailVO hit = readEntry(key, cached);
                    if (hit != null || NULL_MARK.equals(cached)) {
                        return hit;
                    }
                    // 损坏数据：已被删除，继续回源重建
                }

                BookDetailVO vo = loader.get();
                if (vo == null) {
                    // 防穿透：不存在的数据也缓存空值标记，短 TTL
                    redisTemplate.opsForValue().set(key, NULL_MARK, NULL_TTL);
                    return null;
                }
                redisTemplate.opsForValue().set(key, toJson(vo), jitteredTtl(DATA_TTL));
                log.info("详情缓存未命中，互斥重建完成：bookId={}", id);
                return vo;
            } finally {
                // Lua 原子「校验锁值再删除」：只删自己持有的锁
                redisTemplate.execute(UNLOCK_SCRIPT, List.of(lockKey), lockValue);
            }
        }

        // 未抢到锁：自旋等待持锁线程完成重建
        for (int i = 0; i < spinMaxTimes; i++) {
            try {
                Thread.sleep(spinIntervalMs);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
            cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                BookDetailVO hit = readEntry(key, cached);
                if (hit != null || NULL_MARK.equals(cached)) {
                    return hit;
                }
            }
        }
        // 自旋超时兜底：极小概率（持锁线程崩溃且锁未到期）直接回源，保证可用性
        log.warn("详情缓存重建等待超时，直接回源：bookId={}", id);
        return loader.get();
    }

    /** 精确驱逐某本图书的详情缓存（写操作后调用，先更库后删缓存） */
    public void evict(Long id) {
        redisTemplate.delete(KEY_PREFIX + id);
    }

    /** 读缓存条目：null 标记返回 null（命中不存在）；损坏数据删除后返回 null（由调用方重建） */
    private BookDetailVO readEntry(String key, String cached) {
        if (NULL_MARK.equals(cached)) {
            return null;
        }
        return fromJson(key, cached);
    }

    /** 反序列化失败视为缓存损坏：删除坏数据并返回 null 让调用方重建 */
    private BookDetailVO fromJson(String key, String json) {
        try {
            return objectMapper.readValue(json, BookDetailVO.class);
        } catch (JsonProcessingException ex) {
            log.warn("详情缓存反序列化失败，删除重建：{}", key, ex);
            redisTemplate.delete(key);
            return null;
        }
    }

    private String toJson(BookDetailVO vo) {
        try {
            return objectMapper.writeValueAsString(vo);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("详情缓存序列化失败：bookId=" + vo.getId(), ex);
        }
    }

    /** TTL 随机抖动：基准 ±JITTER_RATIO，同批写入的 key 过期时间错开 */
    private Duration jitteredTtl(Duration base) {
        long ms = base.toMillis();
        long jittered = (long) (ms * (1 - JITTER_RATIO + random.nextDouble() * 2 * JITTER_RATIO));
        return Duration.ofMillis(Math.max(jittered, 1));
    }
}
