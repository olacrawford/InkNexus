package com.inknexus.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.inknexus.common.exception.BusinessException;
import com.inknexus.common.result.Result;
import com.inknexus.order.client.BookClient;
import com.inknexus.order.client.CartClient;
import com.inknexus.order.client.dto.BookSnapshot;
import com.inknexus.order.client.dto.CartItemSnapshot;
import com.inknexus.order.client.dto.StockOperationItem;
import com.inknexus.order.dto.OrderCreateRequest;
import com.inknexus.order.dto.OrderFromCartRequest;
import com.inknexus.order.entity.Order;
import com.inknexus.order.entity.OrderItem;
import com.inknexus.order.mapper.OrderItemMapper;
import com.inknexus.order.mapper.OrderMapper;
import com.inknexus.order.mq.OrderEventPublisher;
import com.inknexus.order.service.OrderPlaceTxService;
import com.inknexus.order.service.OrderService;
import com.inknexus.order.vo.OrderDetailVO;
import com.inknexus.order.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 订单业务实现：下单编排与订单状态机（0 待支付 / 1 已支付 / 2 已取消 / 3 已完成）。
 * 下单的"预占库存 + 落库"骨架收敛在 {@link OrderPlaceTxService} 的 Seata 全局事务内。
 *
 * <p>关键不变量：下单幂等靠 t_order 上"用户ID + clientRequestId"唯一键，预检前移到预占库存之前，
 * 并发竞态残余由插入冲突触发全局事务回滚（Seata 基于 undo_log 自动还原预占）后返回已有订单兜底；
 * 价格以 book 服务下单时刻返回为快照；状态迁移一律走 WHERE status 条件更新，防并发重复确认/释放；
 * 状态迁移后的库存事件在事务内发送、失败即抛异常回滚，避免"状态已改但下游不知道"。
 */
@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderItemMapper orderItemMapper;
    @Autowired
    private BookClient bookClient;
    @Autowired
    private CartClient cartClient;
    @Autowired
    private OrderPlaceTxService orderPlaceTxService;
    @Autowired
    private OrderEventPublisher orderEventPublisher;

    /** 订单超时关单时长（分钟），来自 Nacos 配置 */
    @Value("${inknexus.order.expire-minutes:30}")
    private int orderExpireMinutes;

    /**
     * 直接下单：价格以 book 服务下单时刻返回为快照；图书不存在或价格为空时返回 null（控制器转 400）。
     * 幂等预检前移到预占库存之前，重复请求不触碰库存；库存预占与落库在
     * {@link OrderPlaceTxService#place} 的全局事务内，落库失败由 Seata 自动回滚预占。
     */
    @Override
    public OrderDetailVO createOrder(Long userId, OrderCreateRequest request) {
        OrderDetailVO existing = findExistingOrder(userId, request.getClientRequestId());
        if (existing != null) {
            log.info("下单幂等命中，返回已有订单：userId={}, clientRequestId={}",
                    userId, request.getClientRequestId());
            return existing;
        }

        BookSnapshot book = successfulData(bookClient.getBookById(request.getBookId()));
        if (book == null || book.getPrice() == null) {
            return null;
        }

        List<StockOperationItem> stockItems = List.of(
                new StockOperationItem(request.getBookId(), request.getQuantity()));
        BigDecimal totalAmount = book.getPrice().multiply(BigDecimal.valueOf(request.getQuantity()));

        try {
            Order order = orderPlaceTxService.place(stockItems, () -> {
                Order created = insertOrderHead(userId, request.getClientRequestId(), totalAmount,
                        request.getReceiverName(), request.getReceiverPhone(), request.getReceiverAddress());
                insertOrderItem(created, book, request.getQuantity());
                return created;
            });
            return getOrderDetail(order.getId(), userId);
        } catch (DuplicateKeyException ex) {
            // 并发幂等竞态：全局事务已回滚、预占库存已由 undo_log 自动还原，返回已有订单即可
            return returnExistingOrder(userId, request.getClientRequestId(), ex);
        }
    }

    /**
     * 购物车下单：读取购物车已选条目创建多明细订单；幂等预检与商品校验全部发生在
     * 远程预占库存之前，失败抛 {@link BusinessException} 且不触碰库存。
     */
    @Override
    public OrderDetailVO createOrderFromCart(Long userId, OrderFromCartRequest request) {
        OrderDetailVO existing = findExistingOrder(userId, request.getClientRequestId());
        if (existing != null) {
            log.info("下单幂等命中，返回已有订单：userId={}, clientRequestId={}",
                    userId, request.getClientRequestId());
            return existing;
        }

        List<CartItemSnapshot> cartItems = successfulData(cartClient.selectedItems(userId));
        if (cartItems == null || cartItems.isEmpty()) {
            throw new BusinessException(400, "购物车没有选中的商品");
        }

        // 一次批量拉取所有图书，避免逐本 Feign 调用使下单延迟随商品数线性增长
        List<Long> bookIds = cartItems.stream().map(CartItemSnapshot::getBookId).toList();
        List<BookSnapshot> fetchedBooks = successfulData(bookClient.listBooksByIds(bookIds));
        if (fetchedBooks == null) {
            throw new BusinessException(400, "购物车中有图书不存在或已下架");
        }
        Map<Long, BookSnapshot> bookMap = fetchedBooks.stream()
                .collect(Collectors.toMap(BookSnapshot::getId, b -> b));

        List<BookSnapshot> books = new ArrayList<>();
        List<Integer> quantities = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (CartItemSnapshot item : cartItems) {
            BookSnapshot book = bookMap.get(item.getBookId());
            if (book == null || book.getPrice() == null) {
                throw new BusinessException(400, "购物车中有图书不存在或已下架");
            }
            books.add(book);
            quantities.add(item.getQuantity());
            totalAmount = totalAmount.add(book.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        List<StockOperationItem> stockItems = buildStockItems(books, quantities);
        BigDecimal finalTotalAmount = totalAmount;
        // 一次预占所有商品，库存服务内部会整体回滚；预占与落库同处一个全局事务
        try {
            Order order = orderPlaceTxService.place(stockItems, () -> {
                Order created = insertOrderHead(userId, request.getClientRequestId(), finalTotalAmount,
                        request.getReceiverName(), request.getReceiverPhone(), request.getReceiverAddress());
                for (int i = 0; i < books.size(); i++) {
                    insertOrderItem(created, books.get(i), quantities.get(i));
                }
                return created;
            });
            return getOrderDetail(order.getId(), userId);
        } catch (DuplicateKeyException ex) {
            // 并发幂等竞态：全局事务已回滚、预占库存已由 undo_log 自动还原，返回已有订单即可
            return returnExistingOrder(userId, request.getClientRequestId(), ex);
        }
    }

    /**
     * 下单幂等预检：命中"用户ID + clientRequestId"唯一键说明重复提交，直接返回已有订单，
     * 不触碰库存。并发窗口（预检时对方事务尚未提交）由插入冲突触发全局回滚后走
     * {@link #returnExistingOrder} 兜底。
     */
    private OrderDetailVO findExistingOrder(Long userId, String clientRequestId) {
        Order existing = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(Order::getClientRequestId, clientRequestId)
                .last("LIMIT 1"));
        return existing == null ? null : getOrderDetail(existing.getId(), userId);
    }

    /**
     * 并发幂等兜底：DuplicateKeyException 已触发全局事务回滚（预占库存由 Seata 基于 undo_log
     * 自动还原），这里只负责查回并发请求创建的订单；查不到说明冲突另有原因，按原异常抛出。
     */
    private OrderDetailVO returnExistingOrder(Long userId, String clientRequestId,
                                              DuplicateKeyException original) {
        Order existing = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(Order::getClientRequestId, clientRequestId)
                .last("LIMIT 1"));
        if (existing == null) {
            throw original;
        }
        log.info("下单并发幂等命中，返回已有订单：orderId={}, clientRequestId={}",
                existing.getId(), clientRequestId);
        return getOrderDetail(existing.getId(), userId);
    }

    /**
     * 插入订单主表；clientRequestId 命中唯一键时 insert 抛 DuplicateKeyException，由 placeOrder 走幂等分支。
     */
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
        // 过期时间必须按秒截断：MySQL DATETIME 四舍五入会让延迟消息比存的过期时间早约 1 秒到达，
        // 导致 closeExpiredOrder 的 expire_time <= now 条件误判“未到期”
        order.setExpireTime(now.plusMinutes(orderExpireMinutes).truncatedTo(ChronoUnit.SECONDS));
        order.setUpdateTime(now);
        orderMapper.insert(order);
        return order;
    }

    /**
     * 插入订单明细：书名/单价取 book 服务下单时刻返回值做快照，之后图书改价不影响本明细。
     */
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
        List<StockOperationItem> items = new ArrayList<>();
        for (int i = 0; i < books.size(); i++) {
            items.add(new StockOperationItem(books.get(i).getId(), quantities.get(i)));
        }
        return items;
    }

    private boolean isSuccess(Result<?> result) {
        return result != null && Integer.valueOf(200).equals(result.getCode());
    }

    private <T> T successfulData(Result<T> result) {
        return isSuccess(result) ? result.getData() : null;
    }

    /**
     * 查询某用户的订单列表。
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
     * 查询订单详情（只能查自己的订单）；不存在或不属于该用户时返回 null（控制器转 404）。
     */
    @Override
    public OrderDetailVO getOrderDetail(Long id, Long userId) {
        Order order = orderMapper.selectById(id);
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
        List<StockOperationItem> stockItems = items.stream()
                .map(item -> new StockOperationItem(item.getBookId(), item.getQuantity()))
                .toList();

        if (!stockItems.isEmpty()) {
            publishStockReleaseOrThrow(id, userId, stockItems);
        }
        return true;
    }

    /**
     * 标记订单已支付：仅由 PaySuccessConsumer 调用，是订单进入已支付状态的唯一入口，无 HTTP 接口。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markPaid(Long id, Long userId) {
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, id)
                .eq(Order::getUserId, userId)
                .eq(Order::getStatus, 0)
                .set(Order::getStatus, 1)
                .set(Order::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            Order existing = orderMapper.selectById(id);
            // 已支付视为幂等成功，避免支付消息重投被误判失败；库存确认事件只在 0→1 迁移时发一次，
            // 否则重复消息会对同一订单二次确认库存、扣减其他订单的锁定库存
            if (existing != null && existing.getUserId().equals(userId)
                    && existing.getStatus() != null && existing.getStatus() == 1) {
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

    /**
     * 事务内发布库存确认事件，发送失败即抛异常回滚：宁可支付消息重投，也不让订单已支付而库存悬空。
     */
    private void publishOrderPaidOrThrow(Long orderId, Long userId, List<StockOperationItem> items) {
        try {
            orderEventPublisher.publishOrderPaid(orderId, userId, items);
        } catch (Exception ex) {
            throw new BusinessException(500, "订单支付事件发送失败，请稍后重试");
        }
    }

    /**
     * 事务内发布库存释放事件，发送失败即抛异常回滚，状态迁移与下游释放同生同灭；超时关单场景 userId 传 null。
     */
    private void publishStockReleaseOrThrow(Long orderId, Long userId, List<StockOperationItem> items) {
        try {
            orderEventPublisher.publishStockRelease(orderId, userId, items);
        } catch (Exception ex) {
            throw new BusinessException(500, "库存释放事件发送失败，请稍后重试");
        }
    }
}
