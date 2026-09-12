package com.inknexus.stock.mq;

import com.inknexus.common.mq.InkNexusRabbitMq;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 库存服务声明订单状态事件消费队列。
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
}
