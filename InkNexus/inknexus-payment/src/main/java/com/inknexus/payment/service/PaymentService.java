package com.inknexus.payment.service;

import com.inknexus.payment.dto.PaymentRequest;
import com.inknexus.payment.vo.PaymentVO;

/**
 * 支付业务接口：模拟支付 + 支付单查询。
 * pay 内部完成订单校验、支付单落库与 PaySuccess 事件发布，对 Controller 屏蔽跨服务细节。
 */
public interface PaymentService {

    /**
     * 对指定订单发起（模拟）支付。
     * 幂等：订单已有已支付支付单时直接返回，不重复扣款、不重发 PaySuccess 事件。
     * @throws com.inknexus.common.exception.BusinessException 404 订单/支付单不存在；403 非本人订单；400 状态/时效不允许支付；500 事件发布失败
     */
    PaymentVO pay(Long userId, PaymentRequest request);

    /**
     * 按订单查询当前用户的支付单。
     * @throws com.inknexus.common.exception.BusinessException 404 支付单不存在
     */
    PaymentVO getByOrderId(Long userId, Long orderId);
}
