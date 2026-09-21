package com.inknexus.common.mq;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 支付成功事件载荷，由 payment(PaySuccessPublisher) 发布、order(PaySuccessConsumer) 消费：
 * order 收到后把待支付订单标记为已支付，并触发库存确认。
 *
 * <p>幂等约定：orderId 是消费幂等依据——markPaid 按订单状态条件更新（仅待支付可改为已支付），
 * MQ 重复投递时状态已变更、更新 0 行即跳过；eventId 仅用于日志追踪，不参与去重。
 * 以 JSON 收发，字段名即两端反序列化契约，删改字段需两侧同步。</p>
 */
@Data
@NoArgsConstructor
public class PaySuccessMessage {

    /** 消息唯一编号，发布端生成 */
    private String eventId = UUID.randomUUID().toString();
    /** 目标订单 ID，消费幂等依据 */
    private Long orderId;
    /** 下单用户 ID，消费端校验非空后才处理 */
    private Long userId;
    /** 订单业务编号，便于人工对账排查 */
    private String orderNo;
    /** 实付金额（元） */
    private BigDecimal amount;
    /** 支付流水号，对应支付服务侧的支付记录 */
    private String paymentNo;
    /** 支付完成时间 */
    private LocalDateTime payTime;
}
