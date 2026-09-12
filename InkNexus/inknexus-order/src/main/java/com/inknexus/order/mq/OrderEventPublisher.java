package com.inknexus.order.mq;

import com.inknexus.common.mq.InkNexusRabbitMq;
import com.inknexus.common.mq.OrderCloseDelayMessage;
import com.inknexus.common.mq.OrderStockEvent;
import com.inknexus.common.mq.StockItemMessage;
import com.inknexus.order.client.dto.StockOperationItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 发布订单支付/释放库存事件与超时关单延迟消息，库存服务和订单自身订阅处理。
 */
@Component
public class OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public OrderEventPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishOrderPaid(Long orderId, Long userId, List<StockOperationItem> stockItems) throws Exception {
        publish(OrderStockEvent.OPERATION_ORDER_PAID, orderId, userId, stockItems,
                InkNexusRabbitMq.ORDER_PAID_ROUTING_KEY);
    }

    public void publishStockRelease(Long orderId, Long userId, List<StockOperationItem> stockItems) throws Exception {
        publish(OrderStockEvent.OPERATION_ORDER_RELEASE, orderId, userId, stockItems,
                InkNexusRabbitMq.ORDER_STOCK_RELEASE_ROUTING_KEY);
    }

    /**
     * 发布超时关单延迟消息：直接投递到延迟队列，TTL 过期后死信进入关单队列。
     */
    public void publishOrderCloseDelay(Long orderId) throws Exception {
        OrderCloseDelayMessage message = new OrderCloseDelayMessage();
        message.setOrderId(orderId);
        rabbitTemplate.convertAndSend("", InkNexusRabbitMq.ORDER_CLOSE_DELAY_QUEUE,
                objectMapper.writeValueAsString(message));
    }

    private void publish(String operation, Long orderId, Long userId,
                         List<StockOperationItem> stockItems, String routingKey) throws Exception {
        OrderStockEvent event = new OrderStockEvent();
        event.setOrderId(orderId);
        event.setUserId(userId);
        event.setOperation(operation);
        event.setItems(stockItems.stream()
                .map(item -> new StockItemMessage(item.getBookId(), item.getQuantity()))
                .toList());
        String payload = objectMapper.writeValueAsString(event);
        rabbitTemplate.convertAndSend(InkNexusRabbitMq.ORDER_STOCK_EXCHANGE, routingKey, payload);
    }
}
