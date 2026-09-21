package com.inknexus.order.client.dto;

import lombok.Data;

/**
 * cart 服务“已选购物车条目”接口的返回载荷：购物车下单的图书与数量来源。
 */
@Data
public class CartItemSnapshot {

    private Long id;
    private Long bookId;
    private Integer quantity;
    private Integer selected;
}
