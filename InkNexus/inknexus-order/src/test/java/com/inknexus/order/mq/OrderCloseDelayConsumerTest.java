package com.inknexus.order.mq;

import com.inknexus.common.mq.OrderCloseDelayMessage;
import com.inknexus.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderCloseDelayConsumer 纯单元测试：覆盖正常关单、缺订单ID跳过、毒消息丢弃三个分支。
 */
@ExtendWith(MockitoExtension.class)
class OrderCloseDelayConsumerTest {

    @Mock
    private OrderService orderService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OrderCloseDelayConsumer consumer;

    @BeforeEach
    void setUp() {
        // bean 依赖走构造器注入，由测试直接传入
        consumer = new OrderCloseDelayConsumer(objectMapper, orderService);
    }

    @Test
    void onOrderCloseDue_closesExpiredOrder_whenOrderIdPresent() throws Exception {
        when(orderService.closeExpiredOrder(100L)).thenReturn(true);

        consumer.onOrderCloseDue(objectMapper.writeValueAsString(message(100L)));

        verify(orderService).closeExpiredOrder(eq(100L));
    }

    @Test
    void onOrderCloseDue_skipsClose_whenOrderIdMissing() throws Exception {
        consumer.onOrderCloseDue("{\"eventId\":\"e1\"}");

        verify(orderService, never()).closeExpiredOrder(any());
    }

    @Test
    void onOrderCloseDue_dropsMessage_whenPayloadIsNotJson() {
        consumer.onOrderCloseDue("not-a-json");

        verify(orderService, never()).closeExpiredOrder(any());
    }

    private OrderCloseDelayMessage message(Long orderId) {
        OrderCloseDelayMessage message = new OrderCloseDelayMessage();
        message.setOrderId(orderId);
        return message;
    }
}
