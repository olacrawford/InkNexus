package com.inknexus.cart.client.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BookSnapshot {

    // 只接收购物车需要校验/展示的字段，避免依赖图书模块内部 VO
    private Long id;
    private String title;
    private BigDecimal price;
    private Integer status;
}
