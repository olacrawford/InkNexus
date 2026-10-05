package com.inknexus.order.service;

import com.inknexus.order.client.dto.StockOperationItem;
import com.inknexus.order.entity.Order;

import java.util.List;
import java.util.function.Supplier;

/**
 * 下单全局事务骨架：{@code @GlobalTransactional} 的宿主。
 *
 * <p>独立成 bean 而不是挂在 OrderServiceImpl 内部方法上，原因有二：
 * 一是 Seata 注解与 {@code @Transactional} 一样靠 Spring AOP 代理生效，自调用会失效；
 * 二是 Seata 以"异常是否抛出注解方法"作为回滚依据，重复下单这类需要正常返回的
 * 业务分支必须在事务边界之外 catch（见 OrderServiceImpl），否则分支会被当作成功提交。</p>
 */
public interface OrderPlaceTxService {

    /**
     * 在同一全局事务内完成远程预占库存与订单落库。
     * 预占通过 Feign 调用 stock 服务，Seata 自动透传 XID 使其注册为分支事务；
     * 任一环节失败时异常向上抛出，由 TC 协调各分支基于 undo_log 自动回滚，
     * 替代原有的"失败后发 MQ 释放消息"手工补偿。
     *
     * @param stockItems  待预占的图书与数量
     * @param orderWriter 订单落库逻辑（订单头 + 明细插入），在全局事务内执行
     * @return 已落库并回填主键的订单
     * @throws com.inknexus.common.exception.BusinessException 400 库存不足或库存服务返回失败
     */
    Order place(List<StockOperationItem> stockItems, Supplier<Order> orderWriter);
}
