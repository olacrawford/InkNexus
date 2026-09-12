package com.inknexus.stock.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class StockOperationRequest {

    @Valid
    @NotEmpty(message = "库存操作条目不能为空")
    private List<StockOperationItem> items;
}
