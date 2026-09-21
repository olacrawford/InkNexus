package com.inknexus.payment.client.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单详情快照，对应订单服务 GET /orders/{id} 返回的 Result 载荷。
 * 只取支付校验关心的字段；金额与状态以下方快照值为准，绝不采信前端传参。
 */
@Data
public class OrderSnapshot {

    private Long id;
    private String orderNo;
    /**
     * 归属用户ID：必须与当前登录用户一致才允许支付
     */
    private Long userId;
    /**
     * 订单应付总额：支付单金额以此为准
     */
    private BigDecimal totalAmount;
    /**
     * 订单状态：0待支付 1已支付 2已取消 3已完成；2/3 为终态拒绝支付
     */
    private Integer status;
    /**
     * 支付截止时间：已过期待支付也拒绝，不等超时关闭任务执行
     */
    private LocalDateTime expireTime;
}
