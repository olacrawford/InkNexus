package com.inknexus.common.mq;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 支付成功事件载荷，由 payment 发布、order 消费。
 * eventId 用于消息追踪，orderId 用于消费幂等。
 */
@Data
@NoArgsConstructor
public class PaySuccessMessage {

    private String eventId = UUID.randomUUID().toString();
    private Long orderId;
    private Long userId;
    private String orderNo;
    private BigDecimal amount;
    private String paymentNo;
    private LocalDateTime payTime;
}
