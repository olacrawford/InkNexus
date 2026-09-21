package com.inknexus.ai.feign.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 订单列表数据快照：镜像 order 服务返回的订单概要字段。 */
@Data
public class OrderSnapshot {
    private Long id;
    private String orderNo;
    private Long userId;
    private BigDecimal totalAmount;
    private Integer status;       // 订单状态：0待支付 1已支付 2已取消 3已完成
    private LocalDateTime createTime;
    private LocalDateTime expireTime;
}