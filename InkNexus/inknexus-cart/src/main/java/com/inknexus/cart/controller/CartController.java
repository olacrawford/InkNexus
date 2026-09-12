package com.inknexus.cart.controller;

import com.inknexus.cart.dto.CartItemCreateRequest;
import com.inknexus.cart.dto.CartItemUpdateRequest;
import com.inknexus.cart.service.CartService;
import com.inknexus.cart.vo.CartItemVO;
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
@RequestMapping("/cart")
public class CartController {

    // 购物车接口只操作当前登录用户自己的数据
    private final CartService cartService;

    public CartController(CartService cartService) {

        this.cartService = cartService;
    }

    @GetMapping("/hello")
    // 健康检查，Gateway 将 /hello 结尾的路径视为公开接口
    public Result<String> hello() {
        return Result.success("inknexus-cart is running");
    }

    @GetMapping
    // userId 由网关校验 JWT 后写入 X-User-Id，这里不接收前端传入的 userId
    public Result<List<CartItemVO>> list(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(cartService.list(userId));
    }

    @GetMapping("/selected")
    // 订单服务通过 Feign 调用此接口获取购物车中已勾选的商品
    public Result<List<CartItemVO>> selected(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(cartService.selected(userId));
    }

    @PostMapping
    // 同一用户同一本书重复加购时，由业务层合并数量而不是新增多行
    public Result<CartItemVO> add(@RequestHeader("X-User-Id") Long userId,
                                  @Valid @RequestBody CartItemCreateRequest request) {
        return Result.success(cartService.add(userId, request));
    }

    @PutMapping("/{id}")
    // 只能修改当前用户购物车中的条目
    public Result<CartItemVO> update(@RequestHeader("X-User-Id") Long userId,
                                     @PathVariable Long id,
                                     @Valid @RequestBody CartItemUpdateRequest request) {
        return Result.success(cartService.update(userId, id, request));
    }

    @DeleteMapping("/{id}")
    // 只能删除当前用户自己的购物车条目，防止越权删除
    public Result<Void> delete(@RequestHeader("X-User-Id") Long userId,
                               @PathVariable Long id) {
        cartService.delete(userId, id);
        return Result.success();
    }

    @DeleteMapping
    // 清空操作同样按当前用户隔离
    public Result<Void> clear(@RequestHeader("X-User-Id") Long userId) {
        cartService.clear(userId);
        return Result.success();
    }
}
