package com.inknexus.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.inknexus.stock.entity.BookStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StockMapper extends BaseMapper<BookStock> {

    // 原子预占库存
    int deductStock(@Param("bookId") Long bookId, @Param("quantity") Integer quantity);

    // 取消订单/支付失败时释放预占库存
    int releaseStock(@Param("bookId") Long bookId, @Param("quantity") Integer quantity);

    // 支付成功后确认库存：保留 stock 扣减结果，只释放 locked_stock
    int confirmStock(@Param("bookId") Long bookId, @Param("quantity") Integer quantity);
}
