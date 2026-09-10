package com.bookmall.book.support;

import com.bookmall.book.vo.BookDetailVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookDetailCacheTest {

    private static final String KEY = BookDetailCache.KEY_PREFIX + "1";
    private static final String LOCK_KEY = BookDetailCache.LOCK_PREFIX + "1";

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private BookDetailCache cache;

    @BeforeEach
    void setUp() {
        // 自旋间隔 1ms、最多 3 次，缩短测试耗时
        cache = new BookDetailCache(redisTemplate, objectMapper, 1, 3);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void load_returnsNull_whenNullMarkCached() {
        when(valueOps.get(KEY)).thenReturn(BookDetailCache.NULL_MARK);

        assertNull(cache.load(1L, failingLoader()));

        // 命中空值标记时不应回源、不应抢锁（防穿透生效）
        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any());
        verify(valueOps, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void load_returnsParsedValue_onCacheHit() throws Exception {
        when(valueOps.get(KEY)).thenReturn(objectMapper.writeValueAsString(vo()));

        BookDetailVO result = cache.load(1L, failingLoader());

        assertEquals("Java核心技术", result.getTitle());
        assertEquals(new BigDecimal("149.00"), result.getPrice());
        verify(valueOps, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void load_rebuildsUnderLock_whenMissed() {
        when(valueOps.get(KEY)).thenReturn(null, (String) null);
        when(valueOps.setIfAbsent(eq(LOCK_KEY), anyString(), eq(BookDetailCache.LOCK_TTL))).thenReturn(true);

        BookDetailVO result = cache.load(1L, this::vo);

        assertEquals("Java核心技术", result.getTitle());
        // 双重检查后回源写入带抖动的 TTL（30 分钟 ±10%）
        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(valueOps).set(eq(KEY), anyString(), ttl.capture());
        assertTrue(ttl.getValue().toMillis() >= BookDetailCache.DATA_TTL.toMillis() * 9 / 10);
        assertTrue(ttl.getValue().toMillis() <= BookDetailCache.DATA_TTL.toMillis() * 11 / 10);
        // 释放锁：Lua 脚本校验锁值后删除
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(LOCK_KEY)), anyString());
    }

    @Test
    void load_cachesNullMark_whenBookMissing() {
        when(valueOps.get(KEY)).thenReturn(null, (String) null);
        when(valueOps.setIfAbsent(eq(LOCK_KEY), anyString(), eq(BookDetailCache.LOCK_TTL))).thenReturn(true);

        assertNull(cache.load(1L, () -> null));

        // 防穿透：不存在的数据写入短 TTL 空值标记
        verify(valueOps).set(eq(KEY), eq(BookDetailCache.NULL_MARK), eq(BookDetailCache.NULL_TTL));
    }

    @Test
    void load_waitsAndReadsRebuiltCache_whenLockHeldByOther() throws Exception {
        // 第一次读未命中；抢锁失败；自旋两次后读到其它线程重建好的缓存
        when(valueOps.get(KEY))
                .thenReturn(null, (String) null, null, objectMapper.writeValueAsString(vo()));
        when(valueOps.setIfAbsent(eq(LOCK_KEY), anyString(), eq(BookDetailCache.LOCK_TTL))).thenReturn(false);

        BookDetailVO result = cache.load(1L, failingLoader());

        assertEquals("Java核心技术", result.getTitle());
        // 未持有锁不应执行释放锁脚本
        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any());
    }

    @Test
    void load_fallsBackToLoader_whenSpinExhausted() {
        when(valueOps.get(KEY)).thenReturn(null, (String) null, null, null, null);
        when(valueOps.setIfAbsent(eq(LOCK_KEY), anyString(), eq(BookDetailCache.LOCK_TTL))).thenReturn(false);

        BookDetailVO result = cache.load(1L, this::vo);

        // 自旋超时后直接回源兜底，保证可用性
        assertEquals("Java核心技术", result.getTitle());
    }

    @Test
    void evict_deletesDetailKey() {
        cache.evict(1L);

        verify(redisTemplate).delete(KEY);
    }

    private BookDetailVO vo() {
        BookDetailVO vo = new BookDetailVO();
        vo.setId(1L);
        vo.setTitle("Java核心技术");
        vo.setAuthor("凯·霍斯特曼");
        vo.setPrice(new BigDecimal("149.00"));
        vo.setCategoryId(2L);
        vo.setStatus(1);
        return vo;
    }

    private Supplier<BookDetailVO> failingLoader() {
        return () -> {
            throw new IllegalStateException("命中缓存时不应回源");
        };
    }
}
