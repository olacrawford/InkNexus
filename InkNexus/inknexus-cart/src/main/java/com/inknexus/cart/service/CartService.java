package com.inknexus.cart.service;

import com.inknexus.cart.dto.CartItemCreateRequest;
import com.inknexus.cart.dto.CartItemUpdateRequest;
import com.inknexus.cart.vo.CartItemVO;

import java.util.List;

public interface CartService {

    // 购物车服务统一按当前用户隔离数据，所有方法都不接收前端传入的 userId
    List<CartItemVO> list(Long userId);

    List<CartItemVO> selected(Long userId);

    CartItemVO add(Long userId, CartItemCreateRequest request);

    CartItemVO update(Long userId, Long cartItemId, CartItemUpdateRequest request);

    void delete(Long userId, Long cartItemId);

    void clear(Long userId);
}
