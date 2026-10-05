package com.inknexus.order.client;

import com.inknexus.common.result.Result;
import com.inknexus.order.client.dto.StockOperationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 库存服务 Feign 客户端：下单主链第一步，远程预占库存。
 * 调用发生在 {@code @GlobalTransactional} 全局事务内，Seata 的 Feign 拦截器自动透传 XID，
 * stock 侧据此把本次扣减注册为分支事务；订单侧落库失败时由 TC 协调自动回滚预占。
 */
@FeignClient(name = "stock")
public interface StockClient {

    @PostMapping("/stock/deduct")
    Result<Void> deduct(@RequestBody StockOperationRequest request);

}
