package com.inknexus.payment.controller;

import com.inknexus.common.result.Result;
import com.inknexus.payment.dto.PaymentRequest;
import com.inknexus.payment.service.PaymentService;
import com.inknexus.payment.vo.PaymentVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付对外 HTTP 入口：发起支付与查询支付单。
 * 金额、订单状态不在接口入参中，一律以订单服务返回为准（见 PaymentServiceImpl）。
 */
@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/hello")
    public Result<String> hello() {
        return Result.success("inknexus-payment is running");
    }

    /**
     * 对指定订单发起（模拟）支付。幂等：重复请求返回既有支付单，不会重复扣款或重发事件。
     * @throws com.inknexus.common.exception.BusinessException 404 订单/支付单不存在；403 非本人订单；400 订单状态或时效不允许支付
     */
    @PostMapping("/pay")
    public Result<PaymentVO> pay(@RequestHeader("X-User-Id") Long userId,
                                 @Valid @RequestBody PaymentRequest request) {
        // userId 由网关解析 JWT 后写入请求头，不信任前端传参
        return Result.success(paymentService.pay(userId, request));
    }

    /**
     * 按订单查询当前用户的支付单。
     * @throws com.inknexus.common.exception.BusinessException 404 支付单不存在
     */
    @GetMapping("/order/{orderId}")
    public Result<PaymentVO> getByOrderId(@RequestHeader("X-User-Id") Long userId,
                                          @PathVariable Long orderId) {
        return Result.success(paymentService.getByOrderId(userId, orderId));
    }
}
