package com.inknexus.payment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付单，对应表 t_payment（order_id 唯一键，一个订单至多一条支付记录）。
 * 唯一键是支付幂等的数据基础：pay 以“是否已有已支付记录”判断重复支付。
 */
@Data
@TableName("t_payment")
public class Payment {

    private Long id;
    /**
     * 支付单号：时间戳 + 随机串生成，唯一键
     */
    private String paymentNo;
    private Long orderId;
    private String orderNo;
    private Long userId;
    /**
     * 支付金额：取订单服务返回的订单总额，不采信前端
     */
    private BigDecimal amount;
    /**
     * 支付渠道：mock/wechat/alipay，当前恒为 mock（内部模拟支付）
     */
    private String payType;
    /**
     * 支付状态：0待支付 1已支付 2失败；模拟支付落单即置 1
     */
    private Integer status;
    private LocalDateTime payTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
