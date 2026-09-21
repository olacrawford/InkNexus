package com.inknexus.order.mq;

import com.inknexus.common.mq.InkNexusRabbitMq;
import com.inknexus.common.mq.OrderCloseDelayMessage;
import com.inknexus.common.mq.OrderStockEvent;
import com.inknexus.common.mq.StockItemMessage;
import com.inknexus.order.client.dto.StockOperationItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 发布订单支付/释放库存事件与超时关单延迟消息，库存服务和订单自身订阅处理。
 * 发布时携带 CorrelationData（eventId），publisher confirm 失败的日志能对应到具体订单。
 */
@Component
public class OrderEventPublisher {

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 发布库存确认事件（订单已支付）：由 markPaid 在 0→1 迁移后的事务内调用；
     * 序列化或发送失败抛异常，由调用方决定是否回滚事务。
     */
    public void publishOrderPaid(Long orderId, Long userId, List<StockOperationItem> stockItems) throws Exception {
        publish(OrderStockEvent.OPERATION_ORDER_PAID, orderId, userId, stockItems,
                InkNexusRabbitMq.ORDER_PAID_ROUTING_KEY);
    }

    /**
     * 发布库存释放事件（取消/超时关单/下单补偿）；下单补偿时 orderId/userId 允许传 null。
     * 序列化或发送失败抛异常，由调用方决定回滚事务或仅记日志。
     */
    public void publishStockRelease(Long orderId, Long userId, List<StockOperationItem> stockItems) throws Exception {
        publish(OrderStockEvent.OPERATION_ORDER_RELEASE, orderId, userId, stockItems,
                InkNexusRabbitMq.ORDER_STOCK_RELEASE_ROUTING_KEY);
    }

    /**
     * 发布超时关单延迟消息：走默认交换机按队列名直投延迟队列，TTL 过期后死信进入关单队列。
     */
    public void publishOrderCloseDelay(Long orderId) throws Exception {
        OrderCloseDelayMessage message = new OrderCloseDelayMessage();
        message.setOrderId(orderId);
        rabbitTemplate.convertAndSend("", InkNexusRabbitMq.ORDER_CLOSE_DELAY_QUEUE,
                objectMapper.writeValueAsString(message), new CorrelationData(message.getEventId()));
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
        rabbitTemplate.convertAndSend(InkNexusRabbitMq.ORDER_STOCK_EXCHANGE, routingKey, payload,
                new CorrelationData(event.getEventId()));
    }
}
