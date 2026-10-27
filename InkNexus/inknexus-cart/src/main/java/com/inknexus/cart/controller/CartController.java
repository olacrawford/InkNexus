package com.inknexus.cart.controller;

import com.inknexus.cart.dto.CartItemCreateRequest;
import com.inknexus.cart.dto.CartItemUpdateRequest;
import com.inknexus.cart.service.CartService;
import com.inknexus.cart.vo.CartItemVO;
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
 * 购物车接口（/cart，需登录）。
 * <p>所有接口的 userId 都取自 {@code X-User-Id} 头（网关校验 JWT 后注入、覆盖客户端同名头），
 * 数据按用户天然隔离；条目级操作由 Service 层再做一次归属校验。
 */
@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping("/hello")
    // 网关将 /hello 结尾的路径视为公开接口，无需 JWT
    public Result<String> hello() {
        return Result.success("inknexus-cart is running");
    }

    /**
     * 查询当前用户全部购物车条目，最近更新的在前
     */
    @GetMapping
    public Result<List<CartItemVO>> list() {
        Long userId = UserContext.requireUserId();
        return Result.success(cartService.list(userId));
    }

    /**
     * 查询当前用户已勾选条目；订单服务通过 Feign 调用此接口取结算商品
     */
    @GetMapping("/selected")
    public Result<List<CartItemVO>> selected() {
        Long userId = UserContext.requireUserId();
        return Result.success(cartService.selected(userId));
    }

    /**
     * 加购；先经图书服务校验图书可购再写入，重复加购同一本书时合并数量而不是新增多行
     * @throws com.inknexus.common.exception.BusinessException 400 图书不存在或已下架
     */
    @PostMapping
    public Result<CartItemVO> add(@Valid @RequestBody CartItemCreateRequest request) {
        Long userId = UserContext.requireUserId();
        return Result.success(cartService.add(userId, request));
    }

    /**
     * 修改数量/勾选状态；数量为覆盖式赋值而非累加，selected=null 保持原勾选状态，非本人条目按不存在处理
     * @throws com.inknexus.common.exception.BusinessException 404 条目不存在或不属于当前用户
     */
    @PutMapping("/{id}")
    public Result<CartItemVO> update(@PathVariable Long id,
                                     @Valid @RequestBody CartItemUpdateRequest request) {
        Long userId = UserContext.requireUserId();
        return Result.success(cartService.update(userId, id, request));
    }

    /**
     * 删除单条购物车条目；非本人条目按不存在处理
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = UserContext.requireUserId();
        cartService.delete(userId, id);
        return Result.success();
    }

    /**
     * 清空当前用户购物车
     */
    @DeleteMapping
    public Result<Void> clear() {
        Long userId = UserContext.requireUserId();
        cartService.clear(userId);
        return Result.success();
    }
}
