package com.inknexus.common.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 跨服务库存操作中的商品条目。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockItemMessage {

    private Long bookId;
    private Integer quantity;
}
