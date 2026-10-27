package com.inknexus.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.inknexus.auth.dto.AddressRequest;
import com.inknexus.auth.entity.UserAddress;
import com.inknexus.auth.mapper.UserAddressMapper;
import com.inknexus.auth.service.AddressService;
import com.inknexus.auth.vo.AddressVO;
import com.inknexus.common.constant.ErrorCode;
import com.inknexus.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户收货地址业务实现
 * 业务规则：一个用户最多1条默认地址；新增/修改/删除维护默认地址；校验地址归属防止越权
 * <p>"只能操作自己的地址"由两层保证：userId 取自网关注入的 {@code X-User-Id} 头（不信任客户端），
 * 且 {@link #getOwnedAddress} 的查询条件同时匹配 id 与 user_id，非本人地址查不到、按 404 处理。
 * <p>默认地址的唯一性靠"先清空再置位/补位"的多步写保证，这些写操作都在同一事务内完成。
 */
@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private final UserAddressMapper addressMapper;

    /**
     * 查询用户全部地址，默认地址优先，其次按更新时间倒序
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
     * 新增收货地址；第一条地址自动为默认，设为默认时清空用户其他地址的默认标记
     */
    @Override
    @Transactional
    public AddressVO create(Long userId, AddressRequest request) {
        long count = addressMapper.selectCount(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId));

        boolean defaultAddress = Boolean.TRUE.equals(request.getIsDefault()) || count == 0;
        if (defaultAddress) {
            clearDefault(userId);
        }

        UserAddress address = new UserAddress();
        address.setUserId(userId);
        fillRequest(address, request);
        address.setIsDefault(defaultAddress ? 1 : 0);
        addressMapper.insert(address);
        return toVO(address);
    }

    /**
     * 修改地址；取消原默认地址时兜底保证至少存在一条默认地址；isDefault=null 时不触碰默认标记
     * @throws BusinessException 404 地址不存在或不属于当前用户
     */
    @Override
    @Transactional
    public AddressVO update(Long userId, Long addressId, AddressRequest request) {
        UserAddress address = getOwnedAddress(userId, addressId);
        Integer oldDefault = address.getIsDefault();//保存修改前默认状态

        fillRequest(address, request);
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            clearDefault(userId);
            address.setIsDefault(1);
        }
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
     * 设置某条地址为默认地址；先清空该用户全部默认标记再置位
     */
    @Override
    @Transactional
    public void setDefault(Long userId, Long addressId) {
        getOwnedAddress(userId, addressId);
        clearDefault(userId);

        UserAddress address = new UserAddress();
        address.setId(addressId);
        address.setIsDefault(1);
        //只设置 id 与 isDefault，MyBatis-Plus updateById 跳过 null 字段，其余列不会被覆盖
        addressMapper.updateById(address);
    }

    /**
     * 删除地址；如果删除的是默认地址，自动选一条作为新默认
     */
    @Override
    @Transactional
    public void delete(Long userId, Long addressId) {
        UserAddress address = getOwnedAddress(userId, addressId);
        addressMapper.deleteById(addressId);

        //先删后补：若补位失败整个事务回滚，不会出现"无默认地址"的中间状态对外可见
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
     * 归属校验：查询条件同时匹配 id 与 userId，非本人地址统一按"不存在"处理，避免泄露他人地址的存在性
     * @throws BusinessException 404 地址不存在或不属于当前用户
     */
    private UserAddress getOwnedAddress(Long userId, Long addressId) {
        UserAddress address = addressMapper.selectOne(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getId, addressId)
                .eq(UserAddress::getUserId, userId));
        if (address == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "地址不存在");
        }
        return address;
    }

    private void clearDefault(Long userId) {
        addressMapper.update(null, new LambdaUpdateWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .set(UserAddress::getIsDefault, 0));
    }

    /**
     * 兜底：保证用户至少有一条默认地址（排除指定地址）
     */
    private void ensureOneDefaultRemains(Long userId, Long excludeAddressId) {
        long defaultCount = addressMapper.selectCount(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .eq(UserAddress::getIsDefault, 1));
        if (defaultCount > 0) {
            return;
        }

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

    private void fillRequest(UserAddress address, AddressRequest request) {
        address.setReceiverName(request.getReceiverName());
        address.setReceiverPhone(request.getReceiverPhone());
        address.setProvince(request.getProvince());
        address.setCity(request.getCity());
        address.setDistrict(request.getDistrict());
        address.setDetailAddress(request.getDetailAddress());
    }

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
