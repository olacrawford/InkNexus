package com.inknexus.payment.mq;

import com.inknexus.common.mq.InkNexusRabbitMq;
import com.inknexus.common.mq.PaySuccessMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 支付成功事件发布器，订单服务消费后异步更新订单状态。
 * 发布时携带 CorrelationData（eventId），publisher confirm 失败的日志能对应到具体订单。
 */
@Component
public class PaySuccessPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public PaySuccessPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(PaySuccessMessage message) throws Exception {
        String payload = objectMapper.writeValueAsString(message);
        rabbitTemplate.convertAndSend(
                InkNexusRabbitMq.PAY_SUCCESS_EXCHANGE,
                InkNexusRabbitMq.PAY_SUCCESS_ROUTING_KEY,
                payload,
                new CorrelationData(message.getEventId()));
    }
}
