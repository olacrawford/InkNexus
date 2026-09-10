package com.bookmall.order.mq;

import com.bookmall.common.mq.OrderCloseDelayMessage;
import com.bookmall.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCloseDelayConsumerTest {

    @Mock
    private OrderService orderService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OrderCloseDelayConsumer consumer;

    @BeforeEach
    void setUp() {
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
