package com.inknexus.cart.service;

import com.inknexus.cart.dto.CartItemCreateRequest;
import com.inknexus.cart.dto.CartItemUpdateRequest;
import com.inknexus.cart.vo.CartItemVO;

import java.util.List;

/**
 * 购物车业务接口：条目增删改查与已选清单。
 * <p>数据边界：所有方法都不接收前端传入的 userId，一律使用网关注入的 {@code X-User-Id}，
 * 跨用户数据天然隔离。
 */
public interface CartService {

    /**
     * 查询当前用户全部购物车条目，最近更新的在前
     */
    List<CartItemVO> list(Long userId);

    /**
     * 查询已勾选条目，供订单服务结算时取数
     */
    List<CartItemVO> selected(Long userId);

    /**
     * 加购：先跨服务校验图书可购，再按唯一键合并数量
     * @throws com.inknexus.common.exception.BusinessException 400 图书不存在或已下架
     */
    CartItemVO add(Long userId, CartItemCreateRequest request);

    /**
     * 修改数量/勾选状态；非本人条目按不存在处理
     * @throws com.inknexus.common.exception.BusinessException 404 条目不存在或不属于当前用户
     */
    CartItemVO update(Long userId, Long cartItemId, CartItemUpdateRequest request);

    /**
     * 删除单条条目；非本人条目按不存在处理
     */
    void delete(Long userId, Long cartItemId);

    /**
     * 清空当前用户全部条目（只删自己的数据）
     */
    void clear(Long userId);
}
