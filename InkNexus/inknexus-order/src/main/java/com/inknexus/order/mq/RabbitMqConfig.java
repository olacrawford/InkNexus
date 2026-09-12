package com.inknexus.order.mq;

import com.inknexus.common.mq.InkNexusRabbitMq;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 订单服务声明支付成功消费队列、订单状态事件队列和超时关单延迟队列。
 */
@Configuration
@EnableRabbit
public class RabbitMqConfig {

    @Bean
    public TopicExchange paySuccessExchange() {
        return new TopicExchange(InkNexusRabbitMq.PAY_SUCCESS_EXCHANGE, true, false);
    }

    @Bean
    public Queue paySuccessQueue() {
        return new Queue(InkNexusRabbitMq.PAY_SUCCESS_QUEUE, true);
    }

    @Bean
    public Binding paySuccessBinding(TopicExchange paySuccessExchange, Queue paySuccessQueue) {
        return BindingBuilder.bind(paySuccessQueue)
                .to(paySuccessExchange)
                .with(InkNexusRabbitMq.PAY_SUCCESS_ROUTING_KEY);
    }

    @Bean
    public TopicExchange orderStockExchange() {
        return new TopicExchange(InkNexusRabbitMq.ORDER_STOCK_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderPaidQueue() {
        return new Queue(InkNexusRabbitMq.ORDER_PAID_QUEUE, true);
    }

    @Bean
    public Queue orderStockReleaseQueue() {
        return new Queue(InkNexusRabbitMq.ORDER_STOCK_RELEASE_QUEUE, true);
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

    /**
     * 超时关单拓扑：订单创建后发往延迟队列，TTL 过期后死信投递到关单队列。
     * 所有订单过期时间统一为 expire-minutes，因此用队列级 TTL 而非按消息 TTL，
     * 规避单队列内按消息 TTL 的队头阻塞问题。
     * 注意：RabbitMQ 队列参数一经声明不可更改，调整 expire-minutes 后需删除旧队列让其重建。
     */
    @Bean
    public Queue orderCloseDelayQueue(
            @Value("${inknexus.order.expire-minutes:30}") int expireMinutes) {
        return QueueBuilder.durable(InkNexusRabbitMq.ORDER_CLOSE_DELAY_QUEUE)
                .withArgument("x-message-ttl", expireMinutes * 60 * 1000)
                .withArgument("x-dead-letter-exchange", InkNexusRabbitMq.ORDER_CLOSE_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", InkNexusRabbitMq.ORDER_CLOSE_ROUTING_KEY)
                .build();
    }

    @Bean
    public DirectExchange orderCloseExchange() {
        return new DirectExchange(InkNexusRabbitMq.ORDER_CLOSE_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderCloseQueue() {
        return new Queue(InkNexusRabbitMq.ORDER_CLOSE_QUEUE, true);
    }

    @Bean
    public Binding orderCloseBinding(DirectExchange orderCloseExchange, Queue orderCloseQueue) {
        return BindingBuilder.bind(orderCloseQueue)
                .to(orderCloseExchange)
                .with(InkNexusRabbitMq.ORDER_CLOSE_ROUTING_KEY);
    }
}
