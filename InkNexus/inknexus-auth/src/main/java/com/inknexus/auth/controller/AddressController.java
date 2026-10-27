package com.inknexus.auth.controller;

import com.inknexus.auth.dto.AddressRequest;
import com.inknexus.auth.service.AddressService;
import com.inknexus.auth.vo.AddressVO;
import com.inknexus.common.context.UserContext;
import com.inknexus.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 收货地址簿接口（/auth/addresses，需登录）。
 * <p>userId 一律取自 {@code X-User-Id} 请求头——该头由网关校验 JWT 后注入，
 * 客户端自带的同名头会被网关覆盖，因此这里不存在越权传参入口；
 * "只能操作自己的地址"由 Service 层查询条件附带 user_id 保证（见 {@code AddressServiceImpl#getOwnedAddress}）。
 */
@RestController
@RequestMapping("/auth/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    /**
     * 查询当前用户地址，默认地址在前，其余按更新时间倒序
     */
    @GetMapping
    public Result<List<AddressVO>> list() {
        Long userId = UserContext.requireUserId();
        return Result.success(addressService.list(userId));
    }

    /**
     * 新增地址；首条自动设为默认，指定 isDefault=true 时会清掉其他地址的默认标记
     */
    @PostMapping
    public Result<AddressVO> create(@Valid @RequestBody AddressRequest request) {
        Long userId = UserContext.requireUserId();
        return Result.success(addressService.create(userId, request));
    }

    /**
     * 修改地址；isDefault=null 表示保持原默认状态不变；非本人地址按不存在处理
     * @throws com.inknexus.common.exception.BusinessException 404 地址不存在或不属于当前用户
     */
    @PutMapping("/{id}")
    public Result<AddressVO> update(@PathVariable Long id,
                                    @Valid @RequestBody AddressRequest request) {
        Long userId = UserContext.requireUserId();
        return Result.success(addressService.update(userId, id, request));
    }

    /**
     * 设置为默认地址；先清空该用户全部默认标记再置位，保证默认地址唯一；非本人地址按不存在处理
     */
    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@PathVariable Long id) {
        Long userId = UserContext.requireUserId();
        addressService.setDefault(userId, id);
        return Result.success();
    }

    /**
     * 删除地址；若删除的是默认地址，服务层会自动把剩余地址中的一条补为默认；非本人地址按不存在处理
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = UserContext.requireUserId();
        addressService.delete(userId, id);
        return Result.success();
    }
}