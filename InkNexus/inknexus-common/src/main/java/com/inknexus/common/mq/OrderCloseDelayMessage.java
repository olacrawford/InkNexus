package com.inknexus.common.mq;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 订单超时关单延迟消息载荷，由 order 下单时发布至 ORDER_CLOSE_DELAY_QUEUE，
 * 队列级 TTL（expire-minutes）到期后经死信交换机转投 ORDER_CLOSE_QUEUE，仍由 order 自身消费。
 * 延迟时长由延迟队列的队列级 TTL 统一控制（所有订单过期时间一致）。
 */
@Data
@NoArgsConstructor
public class OrderCloseDelayMessage {

    // 消息唯一编号，仅用于日志追踪；重复投递的幂等由消费端按订单状态条件更新保证（未支付且到期才关单）
    private String eventId = UUID.randomUUID().toString();
    /** 到期需要检查关闭的订单 ID */
    private Long orderId;
}
