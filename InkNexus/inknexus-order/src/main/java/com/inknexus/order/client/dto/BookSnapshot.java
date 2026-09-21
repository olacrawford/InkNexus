package com.inknexus.order.client.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * book 服务图书查询接口的返回载荷：下单取 title/price 做订单价格快照。
 */
@Data
public class BookSnapshot {

    private Long id;
    private String title;
    /** 下单时刻的图书单价（快照来源），为空视为不可售 */
    private BigDecimal price;
    private Integer status;

}
