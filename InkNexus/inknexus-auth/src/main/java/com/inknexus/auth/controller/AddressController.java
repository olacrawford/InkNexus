package com.inknexus.auth.controller;

import com.inknexus.auth.dto.AddressRequest;
import com.inknexus.auth.service.AddressService;
import com.inknexus.auth.vo.AddressVO;
import com.inknexus.common.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/auth/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    // 查询当前用户地址
    @GetMapping
    public Result<List<AddressVO>> list(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(addressService.list(userId));
    }

    // 新增地址
    @PostMapping
    public Result<AddressVO> create(@RequestHeader("X-User-Id") Long userId,
                                    @Valid @RequestBody AddressRequest request) {
        return Result.success(addressService.create(userId, request));
    }

    // 修改地址
    @PutMapping("/{id}")
    public Result<AddressVO> update(@RequestHeader("X-User-Id") Long userId,
                                    @PathVariable Long id,
                                    @Valid @RequestBody AddressRequest request) {
        return Result.success(addressService.update(userId, id, request));
    }

    // 设置为默认地址
    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@RequestHeader("X-User-Id") Long userId,
                                   @PathVariable Long id) {
        addressService.setDefault(userId, id);
        return Result.success();
    }

    // 删除地址
    @DeleteMapping("/{id}")
    public Result<Void> delete(@RequestHeader("X-User-Id") Long userId,
                               @PathVariable Long id) {
        addressService.delete(userId, id);
        return Result.success();
    }
}