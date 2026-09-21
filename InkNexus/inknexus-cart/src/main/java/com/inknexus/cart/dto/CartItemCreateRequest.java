package com.inknexus.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 加购请求体（POST /cart）。
 * <p>只带 bookId 与数量，不带书名/价格——购物车不落价格快照，展示与计价均以图书服务当前值为准。
 */
@Data
public class CartItemCreateRequest {

    @NotNull(message = "图书ID不能为空")
    private Long bookId;

    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量至少为1")
    private Integer quantity;

    // 可选；不传时由 Service 默认设为勾选
    private Boolean selected;
}
