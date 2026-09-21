package com.inknexus.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.inknexus.auth.entity.UserAddress;
import org.apache.ibatis.annotations.Mapper;

/**
 * 收货地址表 Mapper，对应 {@code t_user_address}。
 * <p>无自定义 SQL，全部 CRUD 走 MyBatis-Plus BaseMapper 通用方法；
 * "只查自己的地址"由调用方在 QueryWrapper 中附加 user_id 条件实现。
 */
@Mapper
public interface UserAddressMapper extends BaseMapper<UserAddress> {
}