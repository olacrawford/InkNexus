package com.inknexus.payment.controller;

import com.inknexus.common.result.Result;
import com.inknexus.payment.dto.PaymentRequest;
import com.inknexus.payment.service.PaymentService;
import com.inknexus.payment.vo.PaymentVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payment")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/hello")
    public Result<String> hello() {
        return Result.success("inknexus-payment is running");
    }

    @PostMapping("/pay")
    public Result<PaymentVO> pay(@RequestHeader("X-User-Id") Long userId,
                                 @Valid @RequestBody PaymentRequest request) {
        // userId 由网关解析 JWT 后写入请求头，不信任前端传参
        return Result.success(paymentService.pay(userId, request));
    }

    @GetMapping("/order/{orderId}")
    public Result<PaymentVO> getByOrderId(@RequestHeader("X-User-Id") Long userId,
                                          @PathVariable Long orderId) {
        // 查询订单支付单，只能查询当前用户自己的数据
        return Result.success(paymentService.getByOrderId(userId, orderId));
    }
}
