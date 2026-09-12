package com.inknexus.order.client.dto;

import lombok.Data;

import java.util.List;

@Data
public class StockOperationRequest {

    private List<StockOperationItem> items;
}
