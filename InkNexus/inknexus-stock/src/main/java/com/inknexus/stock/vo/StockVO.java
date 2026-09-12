package com.inknexus.stock.vo;

import lombok.Data;

@Data
public class StockVO {

    private Long bookId;
    private Integer stock;
    private Integer lockedStock;
    private Integer availableStock;
}
