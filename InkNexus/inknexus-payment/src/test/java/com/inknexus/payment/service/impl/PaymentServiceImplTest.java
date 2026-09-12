package com.inknexus.payment.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.inknexus.common.exception.BusinessException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.inknexus.common.mq.PaySuccessMessage;
import com.inknexus.common.result.Result;
import com.inknexus.payment.client.OrderClient;
import com.inknexus.payment.client.dto.OrderSnapshot;
import com.inknexus.payment.dto.PaymentRequest;
import com.inknexus.payment.entity.Payment;
import com.inknexus.payment.mapper.PaymentMapper;
import com.inknexus.payment.mq.PaySuccessPublisher;
import com.inknexus.payment.vo.PaymentVO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentMapper paymentMapper;
    @Mock
    private OrderClient orderClient;
    @Mock
    private PaySuccessPublisher paySuccessPublisher;

    private PaymentServiceImpl paymentService;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, Payment.class);
    }

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(paymentMapper, orderClient, paySuccessPublisher);
    }

    @Test
    void pay_createsPaymentAndPublishesSuccessEvent_whenOrderIsPayable() throws Exception {
        OrderSnapshot order = new OrderSnapshot();
        order.setId(100L);
        order.setOrderNo("OD-TEST-100");
        order.setUserId(1L);
        order.setTotalAmount(new BigDecimal("59.80"));
        order.setStatus(0);
        order.setExpireTime(LocalDateTime.now().plusMinutes(30));

        when(orderClient.getOrderDetail(100L, 1L)).thenReturn(Result.success(order));
        when(paymentMapper.selectOne(any())).thenReturn(null);

        PaymentRequest request = new PaymentRequest();
        request.setOrderId(100L);

        PaymentVO paymentVO = paymentService.pay(1L, request);

        assertEquals(1, paymentVO.getStatus());
        verify(paymentMapper).insert(any(Payment.class));
        verify(paySuccessPublisher).publish(any(PaySuccessMessage.class));
    }

    @Test
    void pay_publishesPaySuccessOnlyOnce_whenSameOrderPaidTwice() throws Exception {
        OrderSnapshot order = new OrderSnapshot();
        order.setId(100L);
        order.setOrderNo("OD-TEST-100");
        order.setUserId(1L);
        order.setTotalAmount(new BigDecimal("59.80"));
        order.setStatus(0);
        order.setExpireTime(LocalDateTime.now().plusMinutes(30));

        Payment paid = new Payment();
        paid.setId(9L);
        paid.setOrderId(100L);
        paid.setOrderNo("OD-TEST-100");
        paid.setUserId(1L);
        paid.setAmount(new BigDecimal("59.80"));
        paid.setPayType("mock");
        paid.setStatus(1);

        // 第一次支付：新建支付单并发布；第二次支付：命中已支付支付单，不得重发
        when(orderClient.getOrderDetail(100L, 1L)).thenReturn(Result.success(order));
        when(paymentMapper.selectOne(any())).thenReturn(null, paid);

        PaymentRequest request = new PaymentRequest();
        request.setOrderId(100L);

        paymentService.pay(1L, request);
        paymentService.pay(1L, request);

        // 不变量：重复支付请求合计只发布一次支付成功消息
        verify(paySuccessPublisher, times(1)).publish(any(PaySuccessMessage.class));
    }

    @Test
    void pay_throwsBusinessException_whenEventPublishFails() throws Exception {
        OrderSnapshot order = new OrderSnapshot();
        order.setId(100L);
        order.setOrderNo("OD-TEST-100");
        order.setUserId(1L);
        order.setTotalAmount(new BigDecimal("59.80"));
        order.setStatus(0);
        order.setExpireTime(LocalDateTime.now().plusMinutes(30));

        when(orderClient.getOrderDetail(100L, 1L)).thenReturn(Result.success(order));
        when(paymentMapper.selectOne(any())).thenReturn(null);
        doThrow(new RuntimeException("rabbitmq unavailable"))
                .when(paySuccessPublisher).publish(any());

        PaymentRequest request = new PaymentRequest();
        request.setOrderId(100L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.pay(1L, request));

        assertEquals(500, exception.getCode());
    }
}