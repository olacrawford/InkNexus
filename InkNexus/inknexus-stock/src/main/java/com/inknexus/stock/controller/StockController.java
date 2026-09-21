package com.inknexus.stock.controller;

import com.inknexus.common.result.Result;
import com.inknexus.stock.dto.StockOperationRequest;
import com.inknexus.stock.service.StockService;
import com.inknexus.stock.vo.StockVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库存对外 HTTP 入口。
 * 查询与下单预占走同步接口（deduct 由订单服务在创建订单时调用）；
 * 支付确认与取消释放不提供 HTTP 入口，统一由 MQ 事件驱动（见 OrderStockConsumer），
 * 避免绕过订单状态直接篡改库存账目。
 */
@RestController
@RequestMapping("/stock")
public class StockController {

    @Autowired
    private StockService stockService;

    @GetMapping("/hello")
    public Result<String> hello() {
        return Result.success("inknexus-stock is running");
    }

    /**
     * 查询单本图书的库存账目。
     * @throws com.inknexus.common.exception.BusinessException 404 库存记录不存在
     */
    @GetMapping("/{bookId}")
    public Result<StockVO> getByBookId(@PathVariable Long bookId) {
        // 供前端展示和订单侧校验使用
        return Result.success(stockService.getByBookId(bookId));
    }

    /**
     * 批量预占库存。订单服务在创建订单的事务里同步调用；
     * 任一图书库存不足时整体失败，订单侧据此回滚订单创建。
     */
    @PostMapping("/deduct")
    public Result<Void> deduct(@Valid @RequestBody StockOperationRequest request) {
        stockService.deduct(request.getItems());
        return Result.success();
    }

    // 说明：release/confirm 曾有 HTTP 调试入口，但订单服务实际只通过 MQ 事件触发库存释放与确认；
    // 这两个入口允许登录用户绕过订单状态直接篡改库存账目，已下线，与 /orders/{id}/paid 的处理保持一致。
}
