package com.inknexus.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.inknexus.auth.dto.AddressRequest;
import com.inknexus.auth.entity.UserAddress;
import com.inknexus.auth.mapper.UserAddressMapper;
import com.inknexus.auth.service.AddressService;
import com.inknexus.auth.vo.AddressVO;
import com.inknexus.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户收货地址业务实现
 * 业务规则：一个用户最多1条默认地址；新增/修改/删除维护默认地址；校验地址归属防止越权
 */
@Service
public class AddressServiceImpl implements AddressService {

    private final UserAddressMapper addressMapper;

    //构造器注入mapper
    public AddressServiceImpl(UserAddressMapper addressMapper) {
        this.addressMapper = addressMapper;
    }

    /**
     * 查询用户全部地址，默认地址优先，其次按更新时间倒序
     * @param userId 用户id
     * @return 地址VO集合
     */
    @Override
    public List<AddressVO> list(Long userId) {
        List<UserAddress> addresses = addressMapper.selectList(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .orderByDesc(UserAddress::getIsDefault)
                .orderByDesc(UserAddress::getUpdateTime));
        return addresses.stream().map(this::toVO).toList();
    }

    /**
     * 新增收货地址
     * 第一条地址自动为默认；设为默认时清空用户其他地址默认标记
     * @param userId 用户id
     * @param request 前端地址入参
     * @return 新增后的地址VO
     */
    @Override
    @Transactional
    public AddressVO create(Long userId, AddressRequest request) {
        //统计用户已有地址数量
        long count = addressMapper.selectCount(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId));

        //第一条地址 || 前端指定默认 → 当前为默认地址
        boolean defaultAddress = Boolean.TRUE.equals(request.getIsDefault()) || count == 0;
        if (defaultAddress) {
            clearDefault(userId);
        }

        UserAddress address = new UserAddress();
        address.setUserId(userId);
        fillRequest(address, request);//复制前端请求数据到实体
        address.setIsDefault(defaultAddress ? 1 : 0);
        addressMapper.insert(address);
        return toVO(address);
    }

    /**
     * 修改地址
     * 取消原默认地址时兜底保证至少存在一条默认地址
     * @param userId 用户id
     * @param addressId 地址id
     * @param request 修改入参
     * @return 修改后地址VO
     */
    @Override
    @Transactional
    public AddressVO update(Long userId, Long addressId, AddressRequest request) {
        //校验地址属于当前用户
        UserAddress address = getOwnedAddress(userId, addressId);
        Integer oldDefault = address.getIsDefault();//保存修改前默认状态

        fillRequest(address, request);
        //设置为默认：清空其他默认
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            clearDefault(userId);
            address.setIsDefault(1);
        }
        //取消默认，如果原本是默认，需要兜底生成新默认
        else if (Boolean.FALSE.equals(request.getIsDefault())) {
            address.setIsDefault(0);
            if (oldDefault != null && oldDefault == 1) {
                ensureOneDefaultRemains(userId, addressId);
            }
        }

        addressMapper.updateById(address);
        return toVO(address);
    }

    /**
     * 设置某条地址为默认地址
     * @param userId 用户id
     * @param addressId 地址id
     */
    @Override
    @Transactional
    public void setDefault(Long userId, Long addressId) {
        getOwnedAddress(userId, addressId);//归属校验
        clearDefault(userId);//清空全部默认

        UserAddress address = new UserAddress();
        address.setId(addressId);
        address.setIsDefault(1);
        addressMapper.updateById(address);
    }

    /**
     * 删除地址
     * 如果删除的是默认地址，自动选一条作为新默认
     * @param userId 用户id
     * @param addressId 地址id
     */
    @Override
    @Transactional
    public void delete(Long userId, Long addressId) {
        UserAddress address = getOwnedAddress(userId, addressId);
        addressMapper.deleteById(addressId);

        //删除的是默认地址，自动指定下一条为默认
        if (address.getIsDefault() != null && address.getIsDefault() == 1) {
            UserAddress next = addressMapper.selectOne(new LambdaQueryWrapper<UserAddress>()
                    .eq(UserAddress::getUserId, userId)
                    .orderByAsc(UserAddress::getId)
                    .last("LIMIT 1"));
            if (next != null) {
                next.setIsDefault(1);
                addressMapper.updateById(next);
            }
        }
    }

    /**
     * 校验地址归属：防止越权操作别人地址
     * @param userId 当前用户
     * @param addressId 地址id
     * @return 地址实体
     */
    private UserAddress getOwnedAddress(Long userId, Long addressId) {
        UserAddress address = addressMapper.selectOne(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getId, addressId)
                .eq(UserAddress::getUserId, userId));
        if (address == null) {
            throw new BusinessException(404, "地址不存在");
        }
        return address;
    }

    /**
     * 清空该用户所有地址的默认标记 is_default=0
     * @param userId 用户id
     */
    private void clearDefault(Long userId) {
        addressMapper.update(null, new LambdaUpdateWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .set(UserAddress::getIsDefault, 0));
    }

    /**
     * 兜底：保证用户至少有一条默认地址（排除指定地址）
     * @param userId 用户id
     * @param excludeAddressId 需要排除的地址id
     */
    private void ensureOneDefaultRemains(Long userId, Long excludeAddressId) {
        long defaultCount = addressMapper.selectCount(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .eq(UserAddress::getIsDefault, 1));
        if (defaultCount > 0) {
            return;//已有默认直接返回
        }

        //选除当前外第一条地址设为默认
        UserAddress next = addressMapper.selectOne(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .ne(UserAddress::getId, excludeAddressId)
                .orderByAsc(UserAddress::getId)
                .last("LIMIT 1"));
        if (next != null) {
            next.setIsDefault(1);
            addressMapper.updateById(next);
        }
    }

    /**
     * 请求DTO → 数据库实体，拷贝地址字段
     * @param address 实体
     * @param request 入参DTO
     */
    private void fillRequest(UserAddress address, AddressRequest request) {
        address.setReceiverName(request.getReceiverName());
        address.setReceiverPhone(request.getReceiverPhone());
        address.setProvince(request.getProvince());
        address.setCity(request.getCity());
        address.setDistrict(request.getDistrict());
        address.setDetailAddress(request.getDetailAddress());
    }

    /**
     * 数据库实体 → 返回前端VO
     * @param address 数据库实体
     * @return 前端VO
     */
    private AddressVO toVO(UserAddress address) {
        AddressVO vo = new AddressVO();
        vo.setId(address.getId());
        vo.setUserId(address.getUserId());
        vo.setReceiverName(address.getReceiverName());
        vo.setReceiverPhone(address.getReceiverPhone());
        vo.setProvince(address.getProvince());
        vo.setCity(address.getCity());
        vo.setDistrict(address.getDistrict());
        vo.setDetailAddress(address.getDetailAddress());
        vo.setIsDefault(address.getIsDefault());
        return vo;
    }
}
