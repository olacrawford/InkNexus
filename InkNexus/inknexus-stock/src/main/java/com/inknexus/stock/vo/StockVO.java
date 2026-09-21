package com.inknexus.stock.vo;

import lombok.Data;

/**
 * 库存查询响应（GET /stock/{bookId} 的 Result 载荷）。
 */
@Data
public class StockVO {

    private Long bookId;
    /**
     * 可售库存：预占时已同步扣减，代表当前真实可买数量
     */
    private Integer stock;
    /**
     * 预占库存：订单未终态期间冻结的数量
     */
    private Integer lockedStock;
    /**
     * 可售数量：恒等于 stock（预占时 stock 已扣减），单独字段仅为语义显式
     */
    private Integer availableStock;
}
