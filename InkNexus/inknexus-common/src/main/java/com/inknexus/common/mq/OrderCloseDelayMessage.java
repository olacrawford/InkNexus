package com.inknexus.common.mq;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 订单超时关单延迟消息载荷，由 order 下单时发布，TTL 过期后由 order 自身消费。
 * 延迟时长由延迟队列的队列级 TTL 统一控制（所有订单过期时间一致）。
 */
@Data
@NoArgsConstructor
public class OrderCloseDelayMessage {

    // 关单消费按 eventId 幂等，防 TTL 重投导致重复关单
    private String eventId = UUID.randomUUID().toString();
    private Long orderId;
}
