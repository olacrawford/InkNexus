package com.inknexus.ai.feign.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 订单详情数据快照：镜像 order 服务返回的订单详情（含商品明细与收货信息）。 */
@Data
public class OrderDetailSnapshot {
    private Long id;
    private String orderNo;
    private Long userId;
    private BigDecimal totalAmount;
    private Integer status;     // 订单状态：0待支付 1已支付 2已取消 3已完成
    private LocalDateTime expireTime; // 超时关单时间
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private List<OrderItemSnapshot> items;
}