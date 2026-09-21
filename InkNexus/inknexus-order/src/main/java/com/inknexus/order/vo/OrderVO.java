package com.inknexus.order.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单列表项视图（GET /orders 返回载荷）：不含明细的订单概要。
 * status 字典：0 待支付 / 1 已支付 / 2 已取消 / 3 已完成。
 */
@Data
public class OrderVO {

    private Long id;
    private String orderNo;
    private Long userId;
    private BigDecimal totalAmount;
    private Integer status;
    private LocalDateTime expireTime;

}
