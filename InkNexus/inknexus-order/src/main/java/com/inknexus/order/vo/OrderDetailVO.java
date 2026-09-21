package com.inknexus.order.vo;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单详情视图：GET /orders/{id} 与下单接口的返回载荷。
 * status 字典：0 待支付 / 1 已支付 / 2 已取消 / 3 已完成。
 */
@Data
public class OrderDetailVO {

    private Long id;
    private String orderNo;
    private Long userId;
    private BigDecimal totalAmount;
    private Integer status;
    private LocalDateTime expireTime;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private List<OrderItemVO> items;

    /**
     * 订单明细项：bookTitle/bookPrice 为下单时刻快照。
     */
    @Setter
    @Getter
    public static class OrderItemVO {
        private Long bookId;
        private String bookTitle;
        private BigDecimal bookPrice;
        private Integer quantity;
        private BigDecimal subtotal;

    }
}
