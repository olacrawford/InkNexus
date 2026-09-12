package com.inknexus.ai.feign.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 订单列表数据快照：镜像 order 服务返回的订单概要字段。 */
@Data
public class OrderSnapshot {
    private Long id;              // 订单 ID
    private String orderNo;       // 订单编号
    private Long userId;          // 下单用户 ID
    private BigDecimal totalAmount;// 订单总金额
    private Integer status;       // 订单状态：0待支付 1已支付 2已取消 3已完成
    private LocalDateTime createTime; // 下单时间
    private LocalDateTime expireTime; // 超时关单时间
}