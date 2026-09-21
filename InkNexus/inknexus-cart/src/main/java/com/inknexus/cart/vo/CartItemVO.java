package com.inknexus.cart.vo;

import lombok.Data;

/**
 * 购物车条目响应体：/cart 系列接口的返回载荷。
 * <p>刻意不含书名/价格——购物车不落价格快照，前端按 bookId 另查图书详情展示，
 * 结算价格由订单服务在下单时实时校验并快照。
 */
@Data
public class CartItemVO {

    private Long id;
    private Long userId;
    private Long bookId;
    private Integer quantity;

    // 是否勾选（结算范围）：0 否 1 是
    private Integer selected;
}
