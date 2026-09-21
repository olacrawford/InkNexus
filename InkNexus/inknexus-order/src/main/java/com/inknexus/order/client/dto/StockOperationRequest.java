package com.inknexus.order.client.dto;

import lombok.Data;

import java.util.List;

/**
 * 库存服务 /stock/deduct 预占接口的请求体：一次携带多本图书，库存服务内部整体成功或整体回滚。
 */
@Data
public class StockOperationRequest {

    private List<StockOperationItem> items;
}
