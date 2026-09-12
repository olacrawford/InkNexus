package com.inknexus.ai.feign.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 订单详情数据快照：镜像 order 服务返回的订单详情（含商品明细与收货信息）。 */
@Data
public class OrderDetailSnapshot {
    private Long id;            // 订单 ID
    private String orderNo;     // 订单编号
    private Long userId;        // 下单用户 ID
    private BigDecimal totalAmount; // 订单总金额
    private Integer status;     // 订单状态：0待支付 1已支付 2已取消 3已完成
    private LocalDateTime expireTime; // 超时关单时间
    private String receiverName;    // 收货人
    private String receiverPhone;   // 收货电话
    private String receiverAddress; // 收货地址
    private List<OrderItemSnapshot> items; // 商品明细分列表
}