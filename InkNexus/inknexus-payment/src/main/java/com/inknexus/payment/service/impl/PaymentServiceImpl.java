package com.inknexus.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.inknexus.common.exception.BusinessException;
import com.inknexus.common.mq.PaySuccessMessage;
import com.inknexus.common.result.Result;
import com.inknexus.payment.client.OrderClient;
import com.inknexus.payment.client.dto.OrderSnapshot;
import com.inknexus.payment.dto.PaymentRequest;
import com.inknexus.payment.entity.Payment;
import com.inknexus.payment.mapper.PaymentMapper;
import com.inknexus.payment.mq.PaySuccessPublisher;
import com.inknexus.payment.service.PaymentService;
import com.inknexus.payment.vo.PaymentVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 支付业务：当前使用内部模拟支付，先落支付单，再发布支付成功事件由订单服务异步更新订单。
 * 关键不变量：
 * 金额与订单状态以 order 服务返回为准，绝不信任前端；
 * 支付单幂等（已存在且已支付直接返回），PaySuccess 只在首次支付时发布一次；
 * PaySuccess 在支付事务内发布，发送失败回滚支付单，避免“已支付但订单未更新”。
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentMapper paymentMapper;
    private final OrderClient orderClient;
    private final PaySuccessPublisher paySuccessPublisher;

    public PaymentServiceImpl(PaymentMapper paymentMapper, OrderClient orderClient, PaySuccessPublisher paySuccessPublisher) {
        this.paymentMapper = paymentMapper;
        this.orderClient = orderClient;
        this.paySuccessPublisher = paySuccessPublisher;
    }

    /**
     * 发起（模拟）支付。整个方法在一个事务内：远程校验订单 → 查/建支付单 → 发布 PaySuccess。
     * 幂等语义：已存在已支付支付单时直接返回既有记录，PaySuccess 不重发——
     * 否则订单服务会对同一订单二次确认库存。发送失败抛 500，事务回滚支付单。
     *
     * @throws com.inknexus.common.exception.BusinessException 404 订单不存在；403 非本人订单；
     *                                                         400 订单不可支付或已超时；500 事件发布失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentVO pay(Long userId, PaymentRequest request) {
        // 先通过订单服务校验订单归属，金额以订单服务返回为准
        OrderSnapshot order = getPayableOrder(userId, request.getOrderId());
        Payment payment = findByOrderId(userId, request.getOrderId());

        // 幂等处理：已存在支付单且已支付时直接返回，避免重复扣款；
        // 支付成功消息只在首次支付时发布，重复支付请求不再重发，
        // 否则订单服务会收到第二条 PaySuccess，对同一订单重复确认库存
        if (payment != null && Integer.valueOf(1).equals(payment.getStatus())) {
            return toVO(payment);
        }

        // 没有支付单就新建；已有支付单但未成功时更新为已支付
        if (payment == null) {
            payment = createPayment(order, userId);
        } else {
            payment.setStatus(1);
            payment.setPayTime(LocalDateTime.now());
            payment.setUpdateTime(LocalDateTime.now());
            paymentMapper.updateById(payment);
        }

        // 调用方事务内发布支付成功事件，发送失败会回滚支付单，避免“已支付但订单未更新”
        publishPaySuccess(order, payment);
        return toVO(payment);
    }

    @Override
    public PaymentVO getByOrderId(Long userId, Long orderId) {
        // 只允许当前用户查询自己的支付单
        Payment payment = findByOrderId(userId, orderId);
        if (payment == null) {
            throw new BusinessException(404, "支付单不存在");
        }
        return toVO(payment);
    }

    /**
     * 支付前校验订单：归属、状态、时效三道关口，全部通过才返回订单快照。
     * 这是支付服务的信任边界——金额、状态都以这次远程调用的结果为准。
     *
     * @throws com.inknexus.common.exception.BusinessException 404 订单不存在；403 非本人订单；400 不可支付
     */
    private OrderSnapshot getPayableOrder(Long userId, Long orderId) {
        // 远程调用订单服务，避免支付服务直接信任前端传入的订单信息
        Result<OrderSnapshot> result = orderClient.getOrderDetail(orderId, userId);
        if (result == null || !Integer.valueOf(200).equals(result.getCode()) || result.getData() == null) {
            throw new BusinessException(404, "订单不存在");
        }
        OrderSnapshot order = result.getData();
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权限支付该订单");
        }
        // 状态 2=已取消 3=已完成，均已是终态，拒绝支付
        if (order.getStatus() != null && (order.getStatus() == 2 || order.getStatus() == 3)) {
            throw new BusinessException(400, "订单当前状态不可支付");
        }
        // 即使超时任务还没执行，也不允许继续支付已过期订单
        if (order.getExpireTime() != null && order.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(400, "订单已超时，请重新下单");
        }
        return order;
    }

    private Payment findByOrderId(Long userId, Long orderId) {
        // 一个订单只保留一条支付记录，用于幂等判断
        return paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getOrderId, orderId)
                .eq(Payment::getUserId, userId));
    }

    /**
     * 新建支付单。模拟支付不接真实渠道，落单即标记已支付（status=1）并记录支付时间；
     * 金额取订单快照总额。payment_no 由时间戳 + 随机串拼出，满足唯一键要求。
     */
    private Payment createPayment(OrderSnapshot order, Long userId) {
        String paymentNo = "PAY" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6);
        LocalDateTime now = LocalDateTime.now();

        Payment payment = new Payment();
        payment.setPaymentNo(paymentNo);
        payment.setOrderId(order.getId());
        payment.setOrderNo(order.getOrderNo());
        payment.setUserId(userId);
        payment.setAmount(order.getTotalAmount());
        payment.setPayType("mock");
        payment.setStatus(1);
        payment.setPayTime(now);
        payment.setCreateTime(now);
        payment.setUpdateTime(now);
        paymentMapper.insert(payment);
        return payment;
    }

    /**
     * 发布 PaySuccess 事件（仅在支付单首次到达已支付状态的路径上调用）。
     * 发送失败转成业务异常向上抛，由外层事务回滚支付单，保证“支付单已支付”
     * 与“事件已投递”尽量同步；投递后的丢失由 publisher confirm 日志与消费侧幂等兜底。
     */
    private void publishPaySuccess(OrderSnapshot order, Payment payment) {
        try {
            PaySuccessMessage message = new PaySuccessMessage();
            message.setOrderId(order.getId());
            message.setUserId(order.getUserId());
            message.setOrderNo(order.getOrderNo());
            message.setAmount(order.getTotalAmount());
            message.setPaymentNo(payment.getPaymentNo());
            message.setPayTime(payment.getPayTime());
            paySuccessPublisher.publish(message);
        } catch (Exception ex) {
            throw new BusinessException(500, "支付确认消息发送失败，请稍后重试");
        }
    }

    private PaymentVO toVO(Payment payment) {
        PaymentVO vo = new PaymentVO();
        vo.setId(payment.getId());
        vo.setPaymentNo(payment.getPaymentNo());
        vo.setOrderId(payment.getOrderId());
        vo.setOrderNo(payment.getOrderNo());
        vo.setAmount(payment.getAmount());
        vo.setPayType(payment.getPayType());
        vo.setStatus(payment.getStatus());
        vo.setPayTime(payment.getPayTime());
        return vo;
    }
}
