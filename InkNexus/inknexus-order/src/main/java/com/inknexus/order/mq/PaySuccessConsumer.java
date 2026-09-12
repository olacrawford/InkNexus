package com.inknexus.order.mq;

import com.inknexus.common.mq.InkNexusRabbitMq;
import com.inknexus.common.mq.PaySuccessMessage;
import com.inknexus.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 订单服务消费支付成功消息。
 * markPaid 已按订单状态做幂等，重复消息不会重复确认库存。
 */
@Slf4j
@Component
public class PaySuccessConsumer {

    private final ObjectMapper objectMapper;
    private final OrderService orderService;

    public PaySuccessConsumer(ObjectMapper objectMapper, OrderService orderService) {
        this.objectMapper = objectMapper;
        this.orderService = orderService;
    }

    @RabbitListener(queues = InkNexusRabbitMq.PAY_SUCCESS_QUEUE)
    public void onPaySuccess(String payload) throws Exception {
        log.info("收到支付成功消息：{}", payload);
        PaySuccessMessage event = objectMapper.readValue(payload, PaySuccessMessage.class);
        if (event.getOrderId() == null || event.getUserId() == null) {
            log.warn("支付成功消息缺少必要字段：{}", payload);
            return;
        }

        boolean updated = orderService.markPaid(event.getOrderId(), event.getUserId());
        if (!updated) {
            log.warn("支付消息对应的订单状态不可更新：orderId={}", event.getOrderId());
            return;
        }
        log.info("支付成功消息处理完成：orderId={}, eventId={}", event.getOrderId(), event.getEventId());
    }
}
