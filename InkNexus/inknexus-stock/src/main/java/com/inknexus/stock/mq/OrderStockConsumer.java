package com.inknexus.stock.mq;

import com.inknexus.common.mq.InkNexusRabbitMq;
import com.inknexus.common.mq.OrderStockEvent;
import com.inknexus.stock.dto.StockOperationItem;
import com.inknexus.stock.service.StockService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 消费订单服务发布的库存确认/释放事件。
 * 幂等分两层：消息层按 eventId 去重（见 StockServiceImpl），业务层由库存 SQL 的条件更新兜底。
 */
@Slf4j
@Component
public class OrderStockConsumer {

    private final ObjectMapper objectMapper;
    private final StockService stockService;

    public OrderStockConsumer(ObjectMapper objectMapper, StockService stockService) {
        this.objectMapper = objectMapper;
        this.stockService = stockService;
    }

    @RabbitListener(queues = {InkNexusRabbitMq.ORDER_PAID_QUEUE, InkNexusRabbitMq.ORDER_STOCK_RELEASE_QUEUE})
    public void onOrderStockEvent(String payload) {
        OrderStockEvent event;
        try {
            event = objectMapper.readValue(payload, OrderStockEvent.class);
        } catch (Exception ex) {
            // 解析失败的毒消息重试也无法修复，直接丢弃避免无限重投；业务侧有对账兜底
            log.warn("订单库存事件解析失败，丢弃：{}", payload, ex);
            return;
        }
        if (event.getItems() == null || event.getItems().isEmpty()) {
            log.warn("订单库存事件缺少需要确认或释放的商品明细：{}", payload);
            return;
        }
        if (OrderStockEvent.OPERATION_ORDER_PAID.equals(event.getOperation())
                && event.getOrderId() == null) {
            log.warn("订单支付库存确认事件缺少订单号：{}", payload);
            return;
        }

        List<StockOperationItem> items = event.getItems().stream()
                .map(item -> new StockOperationItem(item.getBookId(), item.getQuantity()))
                .toList();
        if (OrderStockEvent.OPERATION_ORDER_PAID.equals(event.getOperation())) {
            boolean executed = stockService.confirm(event.getEventId(), items);
            log.info(executed ? "订单支付库存确认完成：orderId={}, eventId={}"
                    : "订单支付库存确认消息重复，跳过：orderId={}, eventId={}",
                    event.getOrderId(), event.getEventId());
        } else {
            boolean executed = stockService.release(event.getEventId(), items);
            log.info(executed ? "订单库存释放完成：orderId={}, eventId={}"
                    : "订单库存释放消息重复，跳过：orderId={}, eventId={}",
                    event.getOrderId(), event.getEventId());
        }
    }
}
