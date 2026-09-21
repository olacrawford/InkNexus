package com.inknexus.stock.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单本图书的库存操作条目：/stock/deduct 请求体的明细项，
 * 同时也是订单侧库存确认/释放事件（OrderStockEvent.items）转成服务内部入参的载体。
 * quantity 为正整数；扣减/释放/确认共用同一结构。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockOperationItem {

    @NotNull(message = "bookId 不能为空")
    private Long bookId;

    @NotNull(message = "quantity 不能为空")
    @Min(value = 1, message = "quantity 至少为1")
    private Integer quantity;
}
