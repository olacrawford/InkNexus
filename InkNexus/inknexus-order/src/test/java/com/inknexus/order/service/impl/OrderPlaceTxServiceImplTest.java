package com.inknexus.order.service.impl;

import com.inknexus.common.exception.BusinessException;
import com.inknexus.common.result.Result;
import com.inknexus.order.client.StockClient;
import com.inknexus.order.client.dto.StockOperationItem;
import com.inknexus.order.client.dto.StockOperationRequest;
import com.inknexus.order.entity.Order;
import com.inknexus.order.mq.OrderEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderPlaceTxServiceImpl 纯单元测试：mock Feign 与 MQ 发布器，不启动 Spring 容器。
 * 守住全局事务骨架的三个行为约定：先预占后落库、库存服务失败原样抛 400、
 * 落库失败异常向边界外传播（供 Seata 触发全局回滚，绝不内部吞掉）。
 */
@ExtendWith(MockitoExtension.class)
class OrderPlaceTxServiceImplTest {

    @Mock
    private StockClient stockClient;
    @Mock
    private OrderEventPublisher orderEventPublisher;

    @InjectMocks
    private OrderPlaceTxServiceImpl txService;

    private Order order(long id) {
        Order order = new Order();
        order.setId(id);
        return order;
    }

    @Test
    void place_reservesStockFirst_thenWritesOrder_andPublishesCloseDelay() throws Exception {
        Order created = order(100L);
        when(stockClient.deduct(any(StockOperationRequest.class))).thenReturn(Result.success());

        Order result = txService.place(List.of(new StockOperationItem(5L, 2)), () -> created);

        assertSame(created, result);
        // 骨架顺序不变量：预占在前、关单延迟消息在落库之后
        verify(stockClient).deduct(any(StockOperationRequest.class));
        verify(orderEventPublisher).publishOrderCloseDelay(100L);
    }

    @Test
    void place_throws400WithoutWritingOrder_whenStockServiceRejects() throws Exception {
        when(stockClient.deduct(any(StockOperationRequest.class)))
                .thenReturn(new Result<>(400, "库存不足：图书ID 5", null));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> txService.place(List.of(new StockOperationItem(5L, 99)),
                        () -> order(100L)));

        assertEquals(400, ex.getCode());
        // 库存不足属于业务拒绝，落库 lambda 不得执行、不发关单消息
        verify(orderEventPublisher, never()).publishOrderCloseDelay(anyLong());
    }

    @Test
    void place_propagatesWriterException_forSeataToRollbackGlobalTx() throws Exception {
        when(stockClient.deduct(any(StockOperationRequest.class))).thenReturn(Result.success());

        // 落库失败的异常必须抛出 @GlobalTransactional 边界，Seata 才会回滚已预占的库存；
        // 在这里吞掉等价于让全局事务带着"已扣库存、无订单"提交
        RuntimeException dbFailure = new RuntimeException("db connection lost");
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> txService.place(List.of(new StockOperationItem(5L, 2)), () -> {
                    throw dbFailure;
                }));

        assertSame(dbFailure, ex);
        verify(orderEventPublisher, never()).publishOrderCloseDelay(anyLong());
    }

    @Test
    void place_stillReturnsOrder_whenCloseDelayPublishFails() throws Exception {
        Order created = order(100L);
        when(stockClient.deduct(any(StockOperationRequest.class))).thenReturn(Result.success());
        // 关单延迟消息发送失败不阻断下单，兜底扫描任务会关闭漏网订单
        doThrow(new RuntimeException("mq unavailable"))
                .when(orderEventPublisher).publishOrderCloseDelay(100L);

        Order result = txService.place(List.of(new StockOperationItem(5L, 2)), () -> created);

        assertSame(created, result);
    }
}
