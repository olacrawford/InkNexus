package com.inknexus.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.inknexus.order.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单明细表 Mapper：仅 BaseMapper 通用 CRUD。
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {
}