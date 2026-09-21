package com.inknexus.order.client;

import com.inknexus.common.result.Result;
import com.inknexus.order.client.dto.StockOperationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 库存服务 Feign 客户端：下单主链第一步，远程预占库存。
 * 该调用在订单本地事务之外执行、不可回滚，失败后由调用方决定补偿或终止。
 */
@FeignClient(name = "stock")
public interface StockClient {

    @PostMapping("/stock/deduct")
    Result<Void> deduct(@RequestBody StockOperationRequest request);

}
