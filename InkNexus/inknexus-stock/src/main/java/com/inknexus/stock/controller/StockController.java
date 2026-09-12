package com.inknexus.stock.controller;

import com.inknexus.common.result.Result;
import com.inknexus.stock.dto.StockOperationRequest;
import com.inknexus.stock.service.StockService;
import com.inknexus.stock.vo.StockVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stock")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping("/hello")
    public Result<String> hello() {
        return Result.success("inknexus-stock is running");
    }

    @GetMapping("/{bookId}")
    public Result<StockVO> getByBookId(@PathVariable Long bookId) {
        // 供前端展示和订单侧校验使用
        return Result.success(stockService.getByBookId(bookId));
    }

    // 订单服务下单前调用，预占库存；支持一次操作多本书
    @PostMapping("/deduct")
    public Result<Void> deduct(@Valid @RequestBody StockOperationRequest request) {
        stockService.deduct(request.getItems());
        return Result.success();
    }

    // 说明：release/confirm 曾有 HTTP 调试入口，但订单服务实际只通过 MQ 事件触发库存释放与确认；
    // 这两个入口允许登录用户绕过订单状态直接篡改库存账目，已下线，与 /orders/{id}/paid 的处理保持一致。
}
