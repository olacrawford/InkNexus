package com.inknexus.cart.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.inknexus.cart.client.BookClient;
import com.inknexus.cart.client.dto.BookSnapshot;
import com.inknexus.cart.dto.CartItemCreateRequest;
import com.inknexus.cart.dto.CartItemUpdateRequest;
import com.inknexus.cart.entity.CartItem;
import com.inknexus.cart.mapper.CartItemMapper;
import com.inknexus.cart.service.CartService;
import com.inknexus.cart.vo.CartItemVO;
import com.inknexus.common.constant.ErrorCode;
import com.inknexus.common.exception.BusinessException;
import com.inknexus.common.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 购物车业务实现。
 * <p>核心设计：
 * <ul>
 *   <li>跨服务校验时序：加购先经 Feign 校验图书可购（校验在写库之前），失败直接抛业务异常，
 *       事务回滚且不产生任何写操作；</li>
 *   <li>并发安全：重复加购依赖数据库唯一键 uk_user_book + ON DUPLICATE KEY UPDATE 原子累加
 *       （见 {@code CartItemMapper#insertOrUpdate}），不做"先查后插"；</li>
 *   <li>不存价格快照：购物车只存 bookId 引用，价格以图书服务当前值为准，
 *       下单时由订单服务实时取价并快照到订单条目，避免改价后购物车数据过期。</li>
 * </ul>
 */
@Service
public class CartServiceImpl implements CartService {

    @Autowired
    private CartItemMapper cartItemMapper;
    @Autowired
    private BookClient bookClient;

    /**
     * 查询当前用户全部购物车条目，最近更新的在前
     */
    @Override
    public List<CartItemVO> list(Long userId) {
        List<CartItem> items = cartItemMapper.selectList(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .orderByDesc(CartItem::getUpdateTime));
        return items.stream().map(this::toVO).toList();
    }

    @Override
    // 提供给订单服务使用的已选条目，只返回 selected=1 的数据
    public List<CartItemVO> selected(Long userId) {
        List<CartItem> items = cartItemMapper.selectList(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getSelected, 1)
                .orderByDesc(CartItem::getUpdateTime));
        return items.stream().map(this::toVO).toList();
    }

    /**
     * 加购：先远程校验图书可购，校验通过才写库，远程失败不会留下脏数据；
     * 事务同时覆盖 upsert 与回查，保证返回的是已合并数量后的最新行
     * @throws BusinessException 400 图书不存在或已下架
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CartItemVO add(Long userId, CartItemCreateRequest request) {
        // 通过 Feign 调用图书服务，避免购物车服务直接信任前端传来的 bookId
        validateBook(request.getBookId());

        // 由数据库唯一键原子累加数量和勾选状态，并发加购同一本书时不会重复插入
        Integer selected = request.getSelected() == null ? null : (request.getSelected() ? 1 : 0);
        cartItemMapper.insertOrUpdate(userId, request.getBookId(), request.getQuantity(), selected);

        CartItem item = cartItemMapper.selectOne(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getBookId, request.getBookId()));
        return toVO(item);
    }

    /**
     * 修改数量/勾选状态；数量为覆盖式赋值，selected 为 null 时保持原勾选状态不变
     * @throws BusinessException 404 条目不存在或不属于当前用户
     */
    @Override
    public CartItemVO update(Long userId, Long cartItemId, CartItemUpdateRequest request) {
        CartItem item = getOwnedItem(userId, cartItemId);
        item.setQuantity(request.getQuantity());
        if (request.getSelected() != null) {
            item.setSelected(request.getSelected() ? 1 : 0);
        }
        cartItemMapper.updateById(item);
        return toVO(item);
    }

    /**
     * 删除单条购物车条目
     * @throws BusinessException 404 条目不存在或不属于当前用户
     */
    @Override
    public void delete(Long userId, Long cartItemId) {
        CartItem item = getOwnedItem(userId, cartItemId);
        cartItemMapper.deleteById(item.getId());
    }

    /**
     * 清空当前用户购物车（删除条件带 userId，天然不会误删他人数据）
     */
    @Override
    public void clear(Long userId) {
        cartItemMapper.delete(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId));
    }

    // 同时匹配 id 和 userId，确保用户只能操作自己的购物车数据
    private CartItem getOwnedItem(Long userId, Long cartItemId) {
        CartItem item = cartItemMapper.selectOne(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getId, cartItemId)
                .eq(CartItem::getUserId, userId));
        if (item == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "购物车商品不存在");
        }
        return item;
    }

    /**
     * 校验加购的图书可购：上架过滤（status=1）由图书服务查询条件保证，下架书查不到返回 404；
     * code!=200、data 为空或整个响应为 null 一律视为不可购，统一抛 400；
     * Feign 调用本身异常（超时/服务不可用）不会被吞掉，会直接上抛中断加购
     */
    private void validateBook(Long bookId) {
        Result<BookSnapshot> result = bookClient.getBookById(bookId);
        if (result == null || !Integer.valueOf(200).equals(result.getCode()) || result.getData() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "图书不存在或已下架");
        }
    }

    /**
     * 实体 → VO；不含书名/价格，前端按 bookId 另查图书详情展示，
     * 下单价格由订单服务在下单时实时校验并快照
     */
    private CartItemVO toVO(CartItem item) {
        CartItemVO vo = new CartItemVO();
        vo.setId(item.getId());
        vo.setUserId(item.getUserId());
        vo.setBookId(item.getBookId());
        vo.setQuantity(item.getQuantity());
        vo.setSelected(item.getSelected());
        return vo;
    }
}
