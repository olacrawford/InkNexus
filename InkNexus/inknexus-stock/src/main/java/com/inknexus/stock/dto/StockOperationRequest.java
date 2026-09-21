package com.inknexus.stock.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 库存批量操作请求体（POST /stock/deduct）。
 * items 不允许为空：一次下单至少预占一本，空批次没有业务意义。
 */
@Data
public class StockOperationRequest {

    @Valid
    @NotEmpty(message = "库存操作条目不能为空")
    private List<StockOperationItem> items;
}
