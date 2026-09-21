package com.inknexus.stock.service;

import com.inknexus.stock.dto.StockOperationItem;
import com.inknexus.stock.vo.StockVO;

import java.util.List;

/**
 * 库存业务接口：同步查询/预占 + MQ 驱动的确认/释放。
 * deduct 由订单服务 HTTP 调用；release/confirm 由本服务消费订单事件触发，
 * 以事件 eventId 做消费幂等。
 */
public interface StockService {

    /**
     * 查询图书库存账目。
     *
     * @throws com.inknexus.common.exception.BusinessException 404 库存记录不存在
     */
    StockVO getByBookId(Long bookId);

    /**
     * 批量预占库存（下单路径）。任一图书可售库存不足即抛出异常，
     * 由事务保证整批预占的原子性——不会出现部分预占成功。
     *
     * @throws com.inknexus.common.exception.BusinessException 400 库存不足
     */
    void deduct(List<StockOperationItem> items);

    /**
     * 释放预占库存（消费 MQ 消息）。
     * @return true=已执行；false=重复消息被跳过
     */
    boolean release(String messageId, List<StockOperationItem> items);

    /**
     * 确认库存（消费 MQ 消息）。
     * @return true=已执行；false=重复消息被跳过
     */
    boolean confirm(String messageId, List<StockOperationItem> items);
}
