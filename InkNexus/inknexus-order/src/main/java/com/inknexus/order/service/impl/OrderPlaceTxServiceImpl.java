package com.inknexus.order.service.impl;

import com.inknexus.common.exception.BusinessException;
import com.inknexus.common.result.Result;
import com.inknexus.order.client.StockClient;
import com.inknexus.order.client.dto.StockOperationItem;
import com.inknexus.order.client.dto.StockOperationRequest;
import com.inknexus.order.entity.Order;
import com.inknexus.order.mq.OrderEventPublisher;
import com.inknexus.order.service.OrderPlaceTxService;
import lombok.extern.slf4j.Slf4j;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Supplier;

/**
 * 下单全局事务实现：远程预占库存（stock 分支事务）与本地订单落库收敛在同一个
 * {@code @GlobalTransactional} 边界内。
 *
 * <p>订单落库失败时异常抛出边界，TC 通知 stock 基于 undo_log 自动还原预占库存，
 * 全程不经过 MQ、同步完成——原有"失败后发 MQ 释放消息"的手工补偿随之废弃，
 * 也消除了补偿消息发送失败导致锁定库存无人回收的隐患。
 * {@code @Transactional} 把订单头/明细插入聚为一个本地分支，减少 undo_log 记录。</p>
 */
@Slf4j
@Service
public class OrderPlaceTxServiceImpl implements OrderPlaceTxService {

    @Autowired
    private StockClient stockClient;
    @Autowired
    private OrderEventPublisher orderEventPublisher;

    @Override
    @GlobalTransactional(rollbackFor = Exception.class)
    @Transactional(rollbackFor = Exception.class)
    public Order place(List<StockOperationItem> stockItems, Supplier<Order> orderWriter) {
        reserveStock(stockItems);
        Order order = orderWriter.get();
        publishCloseDelay(order.getId());
        return order;
    }

    /**
     * 远程预占库存；库存不足或远程返回非 200 时抛 BusinessException。
     * 此时 stock 侧本地事务已自行回滚、全局事务尚无成功分支，异常抛出后整体回滚无副作用。
     */
    private void reserveStock(List<StockOperationItem> items) {
        StockOperationRequest request = new StockOperationRequest();
        request.setItems(items);
        Result<Void> result = stockClient.deduct(request);
        if (result == null || !Integer.valueOf(200).equals(result.getCode())) {
            String message = result != null && result.getMessage() != null
                    ? result.getMessage() : "库存不足，请稍后重试";
            throw new BusinessException(400, message);
        }
    }

    private void publishCloseDelay(Long orderId) {
        try {
            orderEventPublisher.publishOrderCloseDelay(orderId);
        } catch (Exception ex) {
            // 延迟关单消息发送失败不阻断下单，兜底扫描任务会关闭漏网订单
            log.warn("发布订单关单延迟消息失败：orderId={}", orderId, ex);
        }
    }
}
