package com.inknexus.order.vo;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
