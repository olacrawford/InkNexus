package com.inknexus.auth.service;

import com.inknexus.auth.dto.AddressRequest;
import com.inknexus.auth.vo.AddressVO;

import java.util.List;

/**
 * 收货地址簿业务接口：围绕"一个用户最多一条默认地址"的不变量提供 CRUD。
 * <p>所有方法都以 userId 为数据边界，userId 来自网关注入的 {@code X-User-Id} 头。
 */
public interface AddressService {

    /**
     * 查询用户全部地址，默认地址在前，其余按更新时间倒序
     */
    List<AddressVO> list(Long userId);

    /**
     * 新增地址；用户的第一条地址自动设为默认
     */
    AddressVO create(Long userId, AddressRequest request);

    /**
     * 修改地址；isDefault=null 保持原默认状态，非本人地址按不存在处理
     * @throws com.inknexus.common.exception.BusinessException 404 地址不存在或不属于当前用户
     */
    AddressVO update(Long userId, Long addressId, AddressRequest request);

    /**
     * 设为默认地址；先清空旧默认再置位，保证默认唯一
     */
    void setDefault(Long userId, Long addressId);

    /**
     * 删除地址；删除默认地址时自动补一条新默认
     */
    void delete(Long userId, Long addressId);
}