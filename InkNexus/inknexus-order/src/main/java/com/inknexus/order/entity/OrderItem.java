package com.inknexus.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单明细实体，对应 t_order_item。
 * book_title / book_price / subtotal 为下单时刻的价格快照，后续图书改价不影响历史明细；
 * 取消/超时关单时按明细的 bookId + quantity 释放预占库存。
 */
@Data
@TableName("t_order_item")
public class OrderItem {

    private Long id;
    private Long orderId;
    private Long bookId;
    private String bookTitle;
    private BigDecimal bookPrice;
    private Integer quantity;
    /** 小计 = bookPrice × quantity */
    private BigDecimal subtotal;
    private LocalDateTime createTime;

}