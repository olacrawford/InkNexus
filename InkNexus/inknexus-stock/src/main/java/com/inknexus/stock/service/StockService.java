package com.inknexus.stock.service;

import com.inknexus.stock.dto.StockOperationItem;
import com.inknexus.stock.vo.StockVO;

import java.util.List;

public interface StockService {

    StockVO getByBookId(Long bookId);

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
