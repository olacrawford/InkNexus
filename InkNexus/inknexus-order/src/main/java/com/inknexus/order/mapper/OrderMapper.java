package com.inknexus.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.inknexus.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单主表 Mapper：仅 BaseMapper 通用 CRUD，状态机迁移由服务层 UpdateWrapper 条件更新实现。
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {
}