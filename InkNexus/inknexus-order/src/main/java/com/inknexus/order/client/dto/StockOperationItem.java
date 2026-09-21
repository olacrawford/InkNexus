package com.inknexus.order.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单本图书的库存操作明细：Feign 预占请求与 MQ 库存事件共用的最小粒度载荷。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockOperationItem {

    private Long bookId;
    private Integer quantity;
}
