package com.inknexus.ai.feign.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 订单商品明细快照：镜像 order 服务返回的单个订单商品字段。 */
@Data
public class OrderItemSnapshot {
    private Long bookId;       // 商品（图书）ID
    private String bookTitle;  // 下单时的书名快照
    private BigDecimal bookPrice; // 下单时的单价快照
    private Integer quantity;  // 购买数量
    private BigDecimal subtotal; // 小计 = 单价 * 数量
}