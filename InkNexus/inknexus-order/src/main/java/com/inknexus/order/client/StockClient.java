package com.inknexus.order.client;

import com.inknexus.common.result.Result;
import com.inknexus.order.client.dto.StockOperationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "stock")
public interface StockClient {

    // 下单前预占库存
    @PostMapping("/stock/deduct")
    Result<Void> deduct(@RequestBody StockOperationRequest request);

}
