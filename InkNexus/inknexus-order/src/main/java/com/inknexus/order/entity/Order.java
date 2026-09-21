package com.inknexus.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单主表实体，对应 t_order。
 * 状态字典：0 待支付 / 1 已支付 / 2 已取消（用户取消或超时关单）/ 3 已完成。
 * user_id + client_request_id 上有唯一键，是下单幂等的落点。
 */
@Data
@TableName("t_order")
public class Order {

    private Long id;
    /** 对外订单号：OD + 时间戳 + 随机串，仅用于展示与检索 */
    private String orderNo;
    /** 客户端请求号（可选），与用户ID联合唯一，支撑下单幂等 */
    private String clientRequestId;
    private Long userId;
    /** 订单总价：下单时刻按 book 服务返回的单价与数量计算并快照 */
    private BigDecimal totalAmount;
    private Integer status;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private LocalDateTime createTime;
    /** 支付截止时间（按秒截断），超过即由延迟消息/兜底任务关单 */
    private LocalDateTime expireTime;
    private LocalDateTime updateTime;

}
