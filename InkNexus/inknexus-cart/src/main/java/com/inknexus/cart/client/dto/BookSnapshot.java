package com.inknexus.cart.client.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 图书服务 {@code GET /books/{id}} 响应的裁剪版，仅作为 BookClient 的反序列化载体。
 */
@Data
public class BookSnapshot {

    // 只接收购物车需要校验/展示的字段，避免依赖图书模块内部 VO
    private Long id;
    private String title;
    private BigDecimal price;

    // 图书状态字典：1 上架 0 下架；可购性由图书服务查询条件过滤（下架书查不到），购物车侧不重复判断
    private Integer status;
}
