package com.bookmall.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bookmall.common.exception.BusinessException;
import com.bookmall.common.result.Result;
import com.bookmall.order.client.BookClient;
import com.bookmall.order.client.CartClient;
import com.bookmall.order.client.StockClient;
import com.bookmall.order.client.dto.BookSnapshot;
import com.bookmall.order.client.dto.CartItemSnapshot;
import com.bookmall.order.client.dto.StockOperationItem;
import com.bookmall.order.client.dto.StockOperationRequest;
import com.bookmall.order.dto.OrderCreateRequest;
import com.bookmall.order.dto.OrderFromCartRequest;
import com.bookmall.order.entity.Order;
import com.bookmall.order.entity.OrderItem;
import com.bookmall.order.mapper.OrderItemMapper;
import com.bookmall.order.mapper.OrderMapper;
import com.bookmall.order.mq.OrderEventPublisher;
import com.bookmall.order.service.OrderService;
import com.bookmall.order.vo.OrderDetailVO;
import com.bookmall.order.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 订单业务实现
 */
@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final BookClient bookClient;
    private final CartClient cartClient;
    private final StockClient stockClient;
    private final OrderEventPublisher orderEventPublisher;
    private final int orderExpireMinutes;

    // 构造注入 mapper 和 Feign 客户端
    public OrderServiceImpl(OrderMapper orderMapper,
                            OrderItemMapper orderItemMapper,
                            BookClient bookClient,
                            CartClient cartClient,
                            StockClient stockClient,
                            OrderEventPublisher orderEventPublisher,
                            @Value("${bookmall.order.expire-minutes:30}") int orderExpireMinutes) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.bookClient = bookClient;
        this.cartClient = cartClient;
        this.stockClient = stockClient;
        this.orderEventPublisher = orderEventPublisher;
        this.orderExpireMinutes = orderExpireMinutes;
    }

    /**
     * 直接下单：Feign 调图书服务拿价格/标题 → 算总价 → 落订单 + 明细
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderDetailVO createOrder(Long userId, OrderCreateRequest request) {
        // 通过 Feign 调图书服务拿价格与标题（下单即快照，避免后续改价影响历史订单）
        BookSnapshot book = successfulData(bookClient.getBookById(request.getBookId()));
        if (book == null || book.getPrice() == null) {
            return null;
        }

        List<StockOperationItem> stockItems = List.of(
                new StockOperationItem(request.getBookId(), request.getQuantity()));
        // 先预占库存，再创建本地订单，保证下单时有可售库存
        reserveStock(stockItems);
        try {
            BigDecimal totalAmount = book.getPrice().multiply(BigDecimal.valueOf(request.getQuantity()));
            Order order = insertOrderHead(userId, request.getClientRequestId(), totalAmount,
                    request.getReceiverName(), request.getReceiverPhone(), request.getReceiverAddress());
            insertOrderItem(order, book, request.getQuantity());
            return getOrderDetail(order.getId(), userId);
        } catch (DuplicateKeyException ex) {
            // 幂等命中：同一用户同一请求号已下过单，补偿释放本次预占并返回已有订单
            return returnExistingOrder(userId, request.getClientRequestId(), stockItems, ex);
        } catch (Exception ex) {
            // 本地订单落库异常时补偿释放，避免库存被长期占用
            releaseStockQuietly(stockItems);
            throw ex;
        }
    }

    /**
     * 购物车下单：读取购物车已选条目，一次创建订单主表和多条订单明细
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderDetailVO createOrderFromCart(Long userId, OrderFromCartRequest request) {
        List<CartItemSnapshot> cartItems = successfulData(cartClient.selectedItems(userId));
        if (cartItems == null || cartItems.isEmpty()) {
            throw new BusinessException(400, "购物车没有选中的商品");
        }

        // 先远程校验每本书，并计算总价；任一本书异常时不落库
        List<BookSnapshot> books = new ArrayList<>();
        List<Integer> quantities = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (CartItemSnapshot item : cartItems) {
            BookSnapshot book = successfulData(bookClient.getBookById(item.getBookId()));
            if (book == null || book.getPrice() == null) {
                throw new BusinessException(400, "购物车中有图书不存在或已下架");
            }
            books.add(book);
            quantities.add(item.getQuantity());
            totalAmount = totalAmount.add(book.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        List<StockOperationItem> stockItems = buildStockItems(books, quantities);
        // 一次预占所有商品，库存服务内部会整体回滚
        reserveStock(stockItems);
        try {
            Order order = insertOrderHead(userId, request.getClientRequestId(), totalAmount,
                    request.getReceiverName(), request.getReceiverPhone(), request.getReceiverAddress());
            for (int i = 0; i < books.size(); i++) {
                insertOrderItem(order, books.get(i), quantities.get(i));
            }
            return getOrderDetail(order.getId(), userId);
        } catch (DuplicateKeyException ex) {
            // 幂等命中：同一用户同一请求号已下过单，补偿释放本次预占并返回已有订单
            return returnExistingOrder(userId, request.getClientRequestId(), stockItems, ex);
        } catch (RuntimeException ex) {
            // 本地订单落库异常时补偿释放，避免库存被长期占用
            releaseStockQuietly(stockItems);
            throw ex;
        }
    }

    private Order insertOrderHead(Long userId, String clientRequestId, BigDecimal totalAmount,
                                  String receiverName, String receiverPhone, String receiverAddress) {
        // 订单号：OD + 时间戳 + UUID 前6位，保证唯一
        String orderNo = "OD" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6);
        LocalDateTime now = LocalDateTime.now();

        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setClientRequestId(clientRequestId);
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setStatus(0); // 0 待支付
        order.setReceiverName(receiverName);
        order.setReceiverPhone(receiverPhone);
        order.setReceiverAddress(receiverAddress);
        order.setCreateTime(now);
        // 超时未支付订单由定时任务自动取消，过期时间按配置向后计算
        order.setExpireTime(now.plusMinutes(orderExpireMinutes));
        order.setUpdateTime(now);
        orderMapper.insert(order);
        return order;
    }

    /**
     * 下单幂等兜底：唯一键冲突说明同一用户已用相同请求号下过单，
     * 释放本次重复预占的库存后返回已有订单，保证重复提交无副作用。
     */
    private OrderDetailVO returnExistingOrder(Long userId, String clientRequestId,
                                              List<StockOperationItem> stockItems,
                                              DuplicateKeyException original) {
        releaseStockQuietly(stockItems);
        Order existing = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(Order::getClientRequestId, clientRequestId)
                .last("LIMIT 1"));
        if (existing == null) {
            // 查不到说明冲突不是幂等键导致的，按原异常处理
            throw original;
        }
        log.info("下单幂等命中，返回已有订单：orderId={}, clientRequestId={}", existing.getId(), clientRequestId);
        return getOrderDetail(existing.getId(), userId);
    }

    private void insertOrderItem(Order order, BookSnapshot book, Integer quantity) {
        BigDecimal subtotal = book.getPrice().multiply(BigDecimal.valueOf(quantity));
        OrderItem orderItem = new OrderItem();
        orderItem.setOrderId(order.getId());
        orderItem.setBookId(book.getId());
        orderItem.setBookTitle(book.getTitle());
        orderItem.setBookPrice(book.getPrice());
        orderItem.setQuantity(quantity);
        orderItem.setSubtotal(subtotal);
        orderItem.setCreateTime(LocalDateTime.now());
        orderItemMapper.insert(orderItem);
    }

    private List<StockOperationItem> buildStockItems(List<BookSnapshot> books, List<Integer> quantities) {
        // 把订单明细转换成库存服务需要的入参
        List<StockOperationItem> items = new ArrayList<>();
        for (int i = 0; i < books.size(); i++) {
            items.add(new StockOperationItem(books.get(i).getId(), quantities.get(i)));
        }
        return items;
    }

    private void reserveStock(List<StockOperationItem> items) {
        // 远程预占库存，失败时转为本地业务异常
        StockOperationRequest request = new StockOperationRequest();
        request.setItems(items);
        requireSuccess(stockClient.deduct(request), 400, "库存不足，请稍后重试");
    }

    private void releaseStockQuietly(List<StockOperationItem> items) {
        try {
            orderEventPublisher.publishStockRelease(null, null, items);
        } catch (Exception ex) {
            // 补偿释放只记录日志，不掩盖原始的订单异常
            log.warn("订单创建失败后释放库存异常", ex);
        }
    }

    private void requireSuccess(Result<?> result, Integer code, String fallback) {
        // 远程 Result 非 200 时统一转成业务异常
        if (!isSuccess(result)) {
            String message = result != null && result.getMessage() != null ? result.getMessage() : fallback;
            throw new BusinessException(code, message);
        }
    }

    // 判断远程调用是否成功（code == 200）
    private boolean isSuccess(Result<?> result) {
        return result != null && Integer.valueOf(200).equals(result.getCode());
    }

    // 取出远程调用成功时的 data，失败返回 null
    private <T> T successfulData(Result<T> result) {
        return isSuccess(result) ? result.getData() : null;
    }

    /**
     * 查询某用户的订单列表（按创建时间倒序）
     */
    @Override
    public List<OrderVO> listOrdersByUserId(Long userId) {
        List<Order> orders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getUserId, userId)
                        .orderByDesc(Order::getCreateTime)
        );

        return orders.stream()
                .map(order -> {
                    OrderVO vo = new OrderVO();
                    vo.setId(order.getId());
                    vo.setOrderNo(order.getOrderNo());
                    vo.setUserId(order.getUserId());
                    vo.setTotalAmount(order.getTotalAmount());
                    vo.setStatus(order.getStatus());
                    vo.setExpireTime(order.getExpireTime());
                    return vo;
                })
                .collect(Collectors.toList());
    }

    /**
     * 查询订单详情（只能查自己的订单）
     */
    @Override
    public OrderDetailVO getOrderDetail(Long id, Long userId) {
        Order order = orderMapper.selectById(id);
        // 只能查看自己的订单
        if (order == null || !order.getUserId().equals(userId)) {
            return null;
        }

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, id)
        );

        OrderDetailVO vo = new OrderDetailVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setUserId(order.getUserId());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setStatus(order.getStatus());
        vo.setExpireTime(order.getExpireTime());
        vo.setReceiverName(order.getReceiverName());
        vo.setReceiverPhone(order.getReceiverPhone());
        vo.setReceiverAddress(order.getReceiverAddress());

        List<OrderDetailVO.OrderItemVO> itemVOList = items.stream()
                .map(item -> {
                    OrderDetailVO.OrderItemVO itemVO = new OrderDetailVO.OrderItemVO();
                    itemVO.setBookId(item.getBookId());
                    itemVO.setBookTitle(item.getBookTitle());
                    itemVO.setBookPrice(item.getBookPrice());
                    itemVO.setQuantity(item.getQuantity());
                    itemVO.setSubtotal(item.getSubtotal());
                    return itemVO;
                })
                .collect(Collectors.toList());

        vo.setItems(itemVOList);
        return vo;
    }

    /**
     * 取消订单（只能取消自己的待支付订单）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelOrder(Long id, Long userId) {
        // 条件更新保证只有“属于该用户且仍待支付”的订单能被取消，避免和支付/超时任务并发重复释放
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, id)
                .eq(Order::getUserId, userId)
                .eq(Order::getStatus, 0)
                .set(Order::getStatus, 2)
                .set(Order::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            return false;
        }

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, id));
        // 取消订单时按订单明细释放此前预占的库存
        List<StockOperationItem> stockItems = items.stream()
                .map(item -> new StockOperationItem(item.getBookId(), item.getQuantity()))
                .toList();

        if (!stockItems.isEmpty()) {
            publishStockReleaseOrThrow(id, userId, stockItems);
        }
        return true;
    }

    /**
     * 标记订单已支付：支付服务完成内部模拟支付后调用。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markPaid(Long id, Long userId) {
        // 条件更新只允许待支付订单进入已支付，防止取消/超时任务同时更新
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, id)
                .eq(Order::getUserId, userId)
                .eq(Order::getStatus, 0)
                .set(Order::getStatus, 1)
                .set(Order::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            Order existing = orderMapper.selectById(id);
            // 已支付视为幂等成功，避免支付服务重复回调时误判为失败
            if (existing != null && existing.getUserId().equals(userId)
                    && existing.getStatus() != null && existing.getStatus() == 1) {
                List<OrderItem> items = orderItemMapper.selectList(
                        new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, id));
                List<StockOperationItem> stockItems = items.stream()
                        .map(item -> new StockOperationItem(item.getBookId(), item.getQuantity()))
                        .toList();
                if (!stockItems.isEmpty()) {
                    publishOrderPaidOrThrow(id, userId, stockItems);
                }
                return true;
            }
            return false;
        }

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, id));
        List<StockOperationItem> stockItems = items.stream()
                .map(item -> new StockOperationItem(item.getBookId(), item.getQuantity()))
                .toList();
        if (!stockItems.isEmpty()) {
            publishOrderPaidOrThrow(id, userId, stockItems);
        }
        return true;
    }

    /**
     * 确认收货：只允许当前用户把已支付订单更新为已完成。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean completeOrder(Long id, Long userId) {
        // 条件更新只允许已支付订单进入已完成，防止取消/超时状态被错误确认
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, id)
                .eq(Order::getUserId, userId)
                .eq(Order::getStatus, 1)
                .set(Order::getStatus, 3)
                .set(Order::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            // 已完成视为幂等成功；其他状态或非本人订单返回失败
            Order existing = orderMapper.selectById(id);
            return existing != null && existing.getUserId().equals(userId)
                    && existing.getStatus() != null && existing.getStatus() == 3;
        }
        return true;
    }

    /**
     * 关闭超时未支付订单，并释放订单明细对应的预占库存。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean closeExpiredOrder(Long orderId) {
        // 只更新已过期的待支付订单；释放库存失败时事务回滚，下轮定时任务会重试
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, 0)
                .le(Order::getExpireTime, LocalDateTime.now())
                .set(Order::getStatus, 2)
                .set(Order::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            return false;
        }

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        List<StockOperationItem> stockItems = items.stream()
                .map(item -> new StockOperationItem(item.getBookId(), item.getQuantity()))
                .toList();
        if (!stockItems.isEmpty()) {
            publishStockReleaseOrThrow(orderId, null, stockItems);
        }
        return true;
    }

    private void publishOrderPaidOrThrow(Long orderId, Long userId, List<StockOperationItem> items) {
        try {
            orderEventPublisher.publishOrderPaid(orderId, userId, items);
        } catch (Exception ex) {
            throw new BusinessException(500, "订单支付事件发送失败，请稍后重试");
        }
    }

    private void publishStockReleaseOrThrow(Long orderId, Long userId, List<StockOperationItem> items) {
        try {
            orderEventPublisher.publishStockRelease(orderId, userId, items);
        } catch (Exception ex) {
            throw new BusinessException(500, "库存释放事件发送失败，请稍后重试");
        }
    }
}
