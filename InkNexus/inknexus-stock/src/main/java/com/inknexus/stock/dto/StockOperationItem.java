package com.inknexus.stock.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockOperationItem {

    @NotNull(message = "bookId 不能为空")
    private Long bookId;

    @NotNull(message = "quantity 不能为空")
    @Min(value = 1, message = "quantity 至少为1")
    private Integer quantity;
}
