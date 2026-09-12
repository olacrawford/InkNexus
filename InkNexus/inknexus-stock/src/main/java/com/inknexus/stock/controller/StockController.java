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

    // 订单服务取消订单或补偿失败时调用，释放之前预占的库存
    @PostMapping("/release")
    public Result<Void> release(@Valid @RequestBody StockOperationRequest request) {
        stockService.release(request.getItems());
        return Result.success();
    }

    // 订单服务支付成功后调用，把预占库存确认成真实扣减
    @PostMapping("/confirm")
    public Result<Void> confirm(@Valid @RequestBody StockOperationRequest request) {
        stockService.confirm(request.getItems());
        return Result.success();
    }
}
