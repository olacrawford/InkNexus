package com.inknexus.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 购物车下单请求体（POST /orders/from-cart）：只携带收货信息，商品与数量来自购物车选中条目。
 */
@Data
public class OrderFromCartRequest {

    @NotBlank(message = "收货人不能为空")
    private String receiverName;

    @NotBlank(message = "收货电话不能为空")
    private String receiverPhone;

    @NotBlank(message = "收货地址不能为空")
    private String receiverAddress;

    /** 客户端请求号（可选）：同一用户重复提交返回同一笔订单（下单幂等） */
    @Size(max = 64, message = "客户端请求号最长64位")
    private String clientRequestId;
}
