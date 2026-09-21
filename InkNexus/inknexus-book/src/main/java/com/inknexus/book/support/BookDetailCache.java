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
 * 图书详情缓存：手写 Cache-Aside，{@code BookServiceImpl.getBookById} 的详情读取全部经此（存 BookDetailVO 的 JSON 串）。
 * 防击穿：未命中先 {@code SETNX} 抢互斥锁（带 TTL 防死锁），单线程回源重建、其余自旋等新缓存，释放用 Lua「校验锁值再删」防误删；
 * 防穿透：不存在的 id 写短 TTL 空值标记；防雪崩：数据 TTL 叠 ±10% 抖动；键布局：数据 {@code cache:book:detail:{id}}、锁 {@code cache:book:detail:lock:{id}}。
 */
@Slf4j
@Component
public class BookDetailCache {

    /** 详情数据键前缀，完整键为 cache:book:detail:{id} */
    static final String KEY_PREFIX = "cache:book:detail:";
    /** 互斥锁键前缀，完整键为 cache:book:detail:lock:{id}，与数据键一一对应 */
    static final String LOCK_PREFIX = "cache:book:detail:lock:";
    /** 空值标记内容：命中即代表「图书确认不存在」，防穿透 */
    static final String NULL_MARK = "__NULL__";

    /** 正常数据的基准 TTL，写入时叠加 ±10% 抖动 */
    static final Duration DATA_TTL = Duration.ofMinutes(30);
    /** 空值标记 TTL：比数据短，给「稍后新增该书」留恢复窗口 */
    static final Duration NULL_TTL = Duration.ofMinutes(2);
    /** 互斥锁 TTL：持锁线程崩溃后锁最多残留这么久，防死锁 */
    static final Duration LOCK_TTL = Duration.ofSeconds(10);

    /** TTL 随机抖动比例：±10%，避免同批 key 同时到期引发雪崩 */
    private static final double JITTER_RATIO = 0.1;
    /** 未抢到锁后的自旋间隔（毫秒） */
    private static final long SPIN_INTERVAL_MS = 50;
    /** 自旋上限次数：50ms × 20 ≈ 1s，超过则放弃等待直接回源 */
    private static final int SPIN_MAX_TIMES = 20;

    /** 校验锁值后删除：锁值不匹配（锁已过期被他人持有）时不删除 */
    private static final RedisScript<Long> UNLOCK_SCRIPT = RedisScript.of(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    private final Random random = new Random();
    /** 自旋参数默认值，包内可见仅供单元测试缩短等待 */
    private long spinIntervalMs = SPIN_INTERVAL_MS;
    private int spinMaxTimes = SPIN_MAX_TIMES;

    /** 读详情缓存：未命中抢锁回源重建，命中空值标记返回 null，自旋等不到新缓存时直接回源兜底保证可用性 */
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
        // 锁值用随机 UUID 标识本次持有者，解锁时校验防止误删他人的锁
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
                // 正常数据写入，TTL 叠加 ±10% 抖动让同批重建的 key 过期时间错开（防雪崩）
                redisTemplate.opsForValue().set(key, toJson(vo), jitteredTtl(DATA_TTL));
                log.info("详情缓存未命中，互斥重建完成：bookId={}", id);
                return vo;
            } finally {
                // Lua 原子「校验锁值再删」：只删自己持有的锁
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

    /** 序列化为 JSON；失败属于严重错误，直接抛出中断本次重建（不影响已写入的数据） */
    private String toJson(BookDetailVO vo) {
        try {
            return objectMapper.writeValueAsString(vo);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("详情缓存序列化失败：bookId=" + vo.getId(), ex);
        }
    }

    /** TTL 随机抖动：基准 ±JITTER_RATIO，同批写入的 key 过期时间错开 */
    private Duration jitteredTtl(Duration base) {
        // 均匀取值于 [1-ratio, 1+ratio) 倍基准毫秒数，下限钳到 1ms 防止出现 0（Redis 视为永久键）
        long ms = base.toMillis();
        long jittered = (long) (ms * (1 - JITTER_RATIO + random.nextDouble() * 2 * JITTER_RATIO));
        return Duration.ofMillis(Math.max(jittered, 1));
    }
}
