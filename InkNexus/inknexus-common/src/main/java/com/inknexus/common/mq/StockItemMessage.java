package com.inknexus.common.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 跨服务库存操作中的商品条目：order 侧由订单条目组装进 {@link OrderStockEvent} 的 items，
 * stock 侧逐条执行确认扣减/释放的条件更新。quantity 始终为正数，动作语义由外层 operation 决定。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockItemMessage {

    /** 图书 ID，对应库存表的图书维度 */
    private Long bookId;
    /** 正数数量：确认扣减或释放的库存数量，具体动作由 OrderStockEvent.operation 决定 */
    private Integer quantity;
}
