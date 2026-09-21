package com.inknexus.cart.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 购物车条目实体，对应表 {@code t_cart_item}。
 * <p>设计意图：只存"谁、买了哪本书、几本、勾没勾"这一引用信息，
 * 不落书名/价格快照——价格以图书服务当前值为准，下单时由订单服务实时校验并快照，
 * 避免改价后购物车数据过期误导用户。
 */
@Data
@TableName("t_cart_item")
public class CartItem {

    // 对应 t_cart_item 表，userId + bookId 唯一，重复加购时复用同一行
    private Long id;
    private Long userId;
    private Long bookId;

    // 购买数量，覆盖式修改；加购重复书时在原值上累加
    private Integer quantity;

    // 是否勾选（结算范围）：0 否 1 是
    private Integer selected;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
