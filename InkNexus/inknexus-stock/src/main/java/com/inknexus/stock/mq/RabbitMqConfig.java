package com.inknexus.stock.mq;

import com.inknexus.common.mq.InkNexusRabbitMq;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 库存服务声明订单状态事件消费队列。
 * 业务队列挂死信交换机：消费重试耗尽的消息进入 {@code inknexus.dlx.queue} 等待人工处理。
 */
@Configuration
@EnableRabbit
public class RabbitMqConfig {

    @Bean
    public TopicExchange orderStockExchange() {
        return new TopicExchange(InkNexusRabbitMq.ORDER_STOCK_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderPaidQueue() {
        // order 服务声明同名队列，死信参数必须与这里完全一致（RabbitMQ 等价声明校验）
        return QueueBuilder.durable(InkNexusRabbitMq.ORDER_PAID_QUEUE)
                .deadLetterExchange(InkNexusRabbitMq.DLX_EXCHANGE)
                .deadLetterRoutingKey(InkNexusRabbitMq.DLX_KEY_ORDER_PAID)
                .build();
    }

    @Bean
    public Queue orderStockReleaseQueue() {
        return QueueBuilder.durable(InkNexusRabbitMq.ORDER_STOCK_RELEASE_QUEUE)
                .deadLetterExchange(InkNexusRabbitMq.DLX_EXCHANGE)
                .deadLetterRoutingKey(InkNexusRabbitMq.DLX_KEY_STOCK_RELEASE)
                .build();
    }

    @Bean
    public Binding orderPaidBinding(TopicExchange orderStockExchange, Queue orderPaidQueue) {
        return BindingBuilder.bind(orderPaidQueue)
                .to(orderStockExchange)
                .with(InkNexusRabbitMq.ORDER_PAID_ROUTING_KEY);
    }

    @Bean
    public Binding orderStockReleaseBinding(TopicExchange orderStockExchange, Queue orderStockReleaseQueue) {
        return BindingBuilder.bind(orderStockReleaseQueue)
                .to(orderStockExchange)
                .with(InkNexusRabbitMq.ORDER_STOCK_RELEASE_ROUTING_KEY);
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
    public Binding dlxOrderPaidBinding(DirectExchange dlxExchange, Queue dlxQueue) {
        return BindingBuilder.bind(dlxQueue)
                .to(dlxExchange)
                .with(InkNexusRabbitMq.DLX_KEY_ORDER_PAID);
    }

    @Bean
    public Binding dlxStockReleaseBinding(DirectExchange dlxExchange, Queue dlxQueue) {
        return BindingBuilder.bind(dlxQueue)
                .to(dlxExchange)
                .with(InkNexusRabbitMq.DLX_KEY_STOCK_RELEASE);
    }
}
