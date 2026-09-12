package com.inknexus.payment.mq;

import com.inknexus.common.mq.InkNexusRabbitMq;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 支付服务声明支付成功事件交换机、队列和绑定。
 * 业务队列挂死信交换机：消费重试耗尽的消息进入 {@code inknexus.dlx.queue} 等待人工处理。
 */
@Configuration
public class RabbitMqConfig {

    @Bean
    public TopicExchange paySuccessExchange() {
        return new TopicExchange(InkNexusRabbitMq.PAY_SUCCESS_EXCHANGE, true, false);
    }

    @Bean
    public Queue paySuccessQueue() {
        // order 服务声明同名队列，死信参数必须与这里完全一致（RabbitMQ 等价声明校验）
        return QueueBuilder.durable(InkNexusRabbitMq.PAY_SUCCESS_QUEUE)
                .deadLetterExchange(InkNexusRabbitMq.DLX_EXCHANGE)
                .deadLetterRoutingKey(InkNexusRabbitMq.DLX_KEY_PAY_SUCCESS)
                .build();
    }

    @Bean
    public Binding paySuccessBinding(TopicExchange paySuccessExchange, Queue paySuccessQueue) {
        return BindingBuilder.bind(paySuccessQueue)
                .to(paySuccessExchange)
                .with(InkNexusRabbitMq.PAY_SUCCESS_ROUTING_KEY);
    }

    // ===== 死信拓扑（与 order 服务声明等价，RabbitMQ 幂等声明）=====

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(InkNexusRabbitMq.DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue dlxQueue() {
        return new Queue(InkNexusRabbitMq.DLX_QUEUE, true);
    }

    @Bean
    public Binding dlxPaySuccessBinding(DirectExchange dlxExchange, Queue dlxQueue) {
        return BindingBuilder.bind(dlxQueue)
                .to(dlxExchange)
                .with(InkNexusRabbitMq.DLX_KEY_PAY_SUCCESS);
    }
}
