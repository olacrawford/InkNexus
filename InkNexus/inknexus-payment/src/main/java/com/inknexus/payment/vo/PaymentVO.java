package com.inknexus.payment.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付单视图（POST /payment/pay 与 GET /payment/order/{orderId} 的 Result 载荷）。
 * 字段为 t_payment 的只读投影，不含审计时间列。
 */
@Data
public class PaymentVO {

    private Long id;
    private String paymentNo;
    private Long orderId;
    private String orderNo;
    private BigDecimal amount;
    private String payType;
    /**
     * 支付状态：0待支付 1已支付 2失败（模拟支付场景恒为 1）
     */
    private Integer status;
    private LocalDateTime payTime;
}
