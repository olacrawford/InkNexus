package com.inknexus.stock.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.inknexus.common.exception.BusinessException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.inknexus.stock.dto.StockOperationItem;
import com.inknexus.stock.entity.BookStock;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockServiceImplTest {

    @Mock
    private StockMapper stockMapper;

    private StockServiceImpl stockService;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, BookStock.class);
    }

    @BeforeEach
    void setUp() {
        stockService = new StockServiceImpl(stockMapper);
    }

    @Test
    void confirm_isIdempotent_whenLockedStockIsAlreadyZero() {
        BookStock row = new BookStock();
        row.setLockedStock(0);

        when(stockMapper.confirmStock(1L, 2)).thenReturn(0);
        when(stockMapper.selectOne(any())).thenReturn(row);

        assertDoesNotThrow(() -> stockService.confirm(List.of(new StockOperationItem(1L, 2))));

        verify(stockMapper).confirmStock(1L, 2);
    }

    @Test
    void confirm_throws_whenLockedStockIsNotEnough() {
        BookStock row = new BookStock();
        row.setLockedStock(5);

        when(stockMapper.confirmStock(1L, 2)).thenReturn(0);
        when(stockMapper.selectOne(any())).thenReturn(row);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> stockService.confirm(List.of(new StockOperationItem(1L, 2))));

        assertEquals(500, exception.getCode());
    }

    @Test
    void release_isIdempotent_whenLockedStockIsAlreadyReleased() {
        when(stockMapper.releaseStock(1L, 2)).thenReturn(0);

        assertDoesNotThrow(() -> stockService.release(List.of(new StockOperationItem(1L, 2))));

        verify(stockMapper).releaseStock(1L, 2);
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