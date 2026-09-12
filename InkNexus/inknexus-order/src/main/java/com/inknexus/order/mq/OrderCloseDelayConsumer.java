package com.inknexus.order.mq;

import com.inknexus.common.mq.InkNexusRabbitMq;
import com.inknexus.common.mq.OrderCloseDelayMessage;
import com.inknexus.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 订单服务消费超时关单延迟消息。
 * closeExpiredOrder 按订单状态条件更新，天然幂等：已支付/已取消/未到期的订单直接跳过。
 */
@Slf4j
@Component
public class OrderCloseDelayConsumer {

    private final ObjectMapper objectMapper;
    private final OrderService orderService;

    public OrderCloseDelayConsumer(ObjectMapper objectMapper, OrderService orderService) {
        this.objectMapper = objectMapper;
        this.orderService = orderService;
    }

    @RabbitListener(queues = InkNexusRabbitMq.ORDER_CLOSE_QUEUE)
    public void onOrderCloseDue(String payload) {
        OrderCloseDelayMessage event;
        try {
            event = objectMapper.readValue(payload, OrderCloseDelayMessage.class);
        } catch (Exception ex) {
            // 无法解析的毒消息直接丢弃，关单兜底扫描会覆盖漏网订单，避免无限重入队列
            log.warn("关单延迟消息解析失败，丢弃：{}", payload, ex);
            return;
        }
        if (event.getOrderId() == null) {
            log.warn("关单延迟消息缺少订单ID：{}", payload);
            return;
        }

        boolean closed = orderService.closeExpiredOrder(event.getOrderId());
        if (closed) {
            log.info("延迟消息关闭超时订单：orderId={}, eventId={}", event.getOrderId(), event.getEventId());
        } else {
            log.info("订单无需关闭（已支付/已取消/未到期）：orderId={}", event.getOrderId());
        }
    }
}
