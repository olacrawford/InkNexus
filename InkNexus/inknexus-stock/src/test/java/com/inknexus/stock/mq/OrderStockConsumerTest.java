package com.inknexus.stock.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inknexus.common.mq.OrderStockEvent;
import com.inknexus.common.mq.StockItemMessage;
import com.inknexus.stock.dto.StockOperationItem;
import com.inknexus.stock.service.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderStockConsumerTest {

    @Mock
    private StockService stockService;

    private OrderStockConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new OrderStockConsumer(objectMapper, stockService);
    }

    @Test
    void onOrderStockEvent_dropsPayloadWithoutCallingService_whenJsonInvalid() {
        assertDoesNotThrow(() -> consumer.onOrderStockEvent("not-a-json"));

        verifyNoInteractions(stockService);
    }

    @Test
    void onOrderStockEvent_skipsStockOperation_whenMessageAlreadyConsumed() throws Exception {
        OrderStockEvent event = paidEvent("event-1");
        when(stockService.confirm(eq("event-1"), anyList())).thenReturn(false);

        assertDoesNotThrow(() -> consumer.onOrderStockEvent(objectMapper.writeValueAsString(event)));

        verify(stockService).confirm(eq("event-1"), anyList());
    }

    @Test
    void onOrderStockEvent_confirmsStock_whenPaidEventFirstSeen() throws Exception {
        OrderStockEvent event = paidEvent("event-1");
        when(stockService.confirm(eq("event-1"), anyList())).thenReturn(true);

        consumer.onOrderStockEvent(objectMapper.writeValueAsString(event));

        verify(stockService).confirm(eq("event-1"),
                eq(List.of(new StockOperationItem(1L, 2), new StockOperationItem(2L, 3))));
    }

    @Test
    void onOrderStockEvent_releasesStock_whenReleaseEventFirstSeen() throws Exception {
        OrderStockEvent event = paidEvent("event-2");
        event.setOperation(OrderStockEvent.OPERATION_ORDER_RELEASE);
        when(stockService.release(eq("event-2"), anyList())).thenReturn(true);

        consumer.onOrderStockEvent(objectMapper.writeValueAsString(event));

        verify(stockService).release(eq("event-2"), anyList());
    }

    @Test
    void onOrderStockEvent_ignoresEvent_whenItemsMissing() throws Exception {
        OrderStockEvent event = paidEvent("event-3");
        event.setItems(null);

        consumer.onOrderStockEvent(objectMapper.writeValueAsString(event));

        verifyNoInteractions(stockService);
    }

    private OrderStockEvent paidEvent(String eventId) {
        OrderStockEvent event = new OrderStockEvent();
        event.setEventId(eventId);
        event.setOrderId(100L);
        event.setUserId(1L);
        event.setOperation(OrderStockEvent.OPERATION_ORDER_PAID);
        event.setItems(List.of(new StockItemMessage(1L, 2), new StockItemMessage(2L, 3)));
        return event;
    }
}
