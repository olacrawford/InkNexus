package com.inknexus.stock.service;

import com.inknexus.stock.dto.StockOperationItem;
import com.inknexus.stock.vo.StockVO;

import java.util.List;

public interface StockService {

    StockVO getByBookId(Long bookId);

    void deduct(List<StockOperationItem> items);

    void release(List<StockOperationItem> items);

    void confirm(List<StockOperationItem> items);
}
