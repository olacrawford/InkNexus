package com.inknexus.payment.mq;

import com.inknexus.common.mq.InkNexusRabbitMq;
import com.inknexus.common.mq.PaySuccessMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 支付成功事件发布器，订单服务消费后异步更新订单状态。
 * 发布时携带 CorrelationData（eventId），publisher confirm 失败的日志能对应到具体订单。
 */
@Component
public class PaySuccessPublisher {

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 发布支付成功事件到 pay.success 交换机，订单服务消费后把订单置为已支付并触发库存确认。
     * 由调用方在支付事务内调用：发布失败抛出的异常会回滚支付单，避免“已支付但订单未更新”。
     *
     * @param message 支付成功事件载荷（eventId 由调用方填充，用于 confirm 回调定位与消费去重）
     * @throws Exception JSON 序列化或 AMQP 发送失败，由调用方决定回滚
     */
    public void publish(PaySuccessMessage message) throws Exception {
        String payload = objectMapper.writeValueAsString(message);
        rabbitTemplate.convertAndSend(
                InkNexusRabbitMq.PAY_SUCCESS_EXCHANGE,
                InkNexusRabbitMq.PAY_SUCCESS_ROUTING_KEY,
                payload,
                new CorrelationData(message.getEventId()));
    }
}
