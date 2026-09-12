package com.inknexus.payment.service;

import com.inknexus.payment.dto.PaymentRequest;
import com.inknexus.payment.vo.PaymentVO;

public interface PaymentService {

    PaymentVO pay(Long userId, PaymentRequest request);

    PaymentVO getByOrderId(Long userId, Long orderId);
}
