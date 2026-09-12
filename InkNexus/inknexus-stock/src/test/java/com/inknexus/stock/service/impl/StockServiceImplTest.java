package com.inknexus.stock.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.inknexus.common.exception.BusinessException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.inknexus.stock.dto.StockOperationItem;
import com.inknexus.stock.entity.BookStock;
import com.inknexus.stock.mapper.MqConsumedLogMapper;
import com.inknexus.stock.mapper.StockMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockServiceImplTest {

    private static final String MESSAGE_ID = "event-1";

    @Mock
    private StockMapper stockMapper;

    @Mock
    private MqConsumedLogMapper mqConsumedLogMapper;

    private StockServiceImpl stockService;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, BookStock.class);
    }

    @BeforeEach
    void setUp() {
        stockService = new StockServiceImpl(stockMapper, mqConsumedLogMapper);
    }

    @Test
    void confirm_isIdempotent_whenLockedStockIsAlreadyZero() {
        BookStock row = new BookStock();
        row.setLockedStock(0);

        when(mqConsumedLogMapper.insertOnce(MESSAGE_ID, "stock-confirm")).thenReturn(1);
        when(stockMapper.confirmStock(1L, 2)).thenReturn(0);
        when(stockMapper.selectOne(any())).thenReturn(row);

        assertTrue(stockService.confirm(MESSAGE_ID, List.of(new StockOperationItem(1L, 2))));

        verify(stockMapper).confirmStock(1L, 2);
    }

    @Test
    void confirm_throws_whenLockedStockIsNotEnough() {
        BookStock row = new BookStock();
        row.setLockedStock(5);

        when(mqConsumedLogMapper.insertOnce(anyString(), anyString())).thenReturn(1);
        when(stockMapper.confirmStock(1L, 2)).thenReturn(0);
        when(stockMapper.selectOne(any())).thenReturn(row);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> stockService.confirm(MESSAGE_ID, List.of(new StockOperationItem(1L, 2))));

        assertEquals(500, exception.getCode());
    }

    @Test
    void confirm_skipsStockOperation_whenMessageAlreadyConsumed() {
        when(mqConsumedLogMapper.insertOnce(MESSAGE_ID, "stock-confirm")).thenReturn(0);

        assertFalse(stockService.confirm(MESSAGE_ID, List.of(new StockOperationItem(1L, 2))));

        verifyNoInteractions(stockMapper);
    }

    @Test
    void confirm_executes_whenMessageIdMissing() {
        // 兼容缺失 eventId 的旧消息：不去重，幂等仍由库存条件更新兜底
        when(stockMapper.confirmStock(1L, 2)).thenReturn(1);

        assertTrue(stockService.confirm(null, List.of(new StockOperationItem(1L, 2))));

        verify(mqConsumedLogMapper, never()).insertOnce(anyString(), anyString());
        verify(stockMapper).confirmStock(1L, 2);
    }

    @Test
    void release_isIdempotent_whenLockedStockIsAlreadyReleased() {
        when(mqConsumedLogMapper.insertOnce(MESSAGE_ID, "stock-release")).thenReturn(1);
        when(stockMapper.releaseStock(1L, 2)).thenReturn(0);

        assertTrue(stockService.release(MESSAGE_ID, List.of(new StockOperationItem(1L, 2))));

        verify(stockMapper).releaseStock(1L, 2);
    }

    @Test
    void release_skipsStockOperation_whenMessageAlreadyConsumed() {
        when(mqConsumedLogMapper.insertOnce(MESSAGE_ID, "stock-release")).thenReturn(0);

        assertFalse(stockService.release(MESSAGE_ID, List.of(new StockOperationItem(1L, 2))));

        verifyNoInteractions(stockMapper);
    }

    @Test
    void deduct_throws_whenStockIsInsufficient() {
        when(stockMapper.deductStock(1L, 2)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> stockService.deduct(List.of(new StockOperationItem(1L, 2))));

        assertEquals(400, exception.getCode());
    }

    @Test
    void deduct_succeeds_whenStockIsAvailable() {
        when(stockMapper.deductStock(1L, 2)).thenReturn(1);

        assertDoesNotThrow(() -> stockService.deduct(List.of(new StockOperationItem(1L, 2))));

        verify(stockMapper).deductStock(1L, 2);
    }
}
