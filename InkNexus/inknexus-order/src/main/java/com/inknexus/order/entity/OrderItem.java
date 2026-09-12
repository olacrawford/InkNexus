package com.inknexus.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_order_item")
public class OrderItem {

    private Long id;
    private Long orderId;
    private Long bookId;
    private String bookTitle;
    private BigDecimal bookPrice;
    private Integer quantity;
    private BigDecimal subtotal;
    private LocalDateTime createTime;

}