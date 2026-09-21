package com.inknexus.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.inknexus.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表 Mapper，对应 {@code t_user}。
 * <p>无自定义 SQL，注册查重/登录查询均通过 BaseMapper + QueryWrapper 完成。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}