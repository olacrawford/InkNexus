package com.inknexus.order.client.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BookSnapshot {

    private Long id;
    private String title;
    private BigDecimal price;
    private Integer status;

}
