package com.inknexus.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.inknexus.stock.entity.BookStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 库存账目 SQL（XML 实现见 resources/mapper/StockMapper.xml）。
 * 三个更新语句全部使用条件更新（WHERE 带库存约束）保证并发下的原子性，
 * 返回受影响行数，由调用方以 0/1 判断操作是否生效。
 */
@Mapper
public interface StockMapper extends BaseMapper<BookStock> {

    /**
     * 原子预占库存：stock 减、locked_stock 加。
     * XML 中 WHERE stock &gt;= #{quantity} 保证只在可售库存足够时扣减，防超卖。
     *
     * @return 1=预占成功；0=库存不足
     */
    int deductStock(@Param("bookId") Long bookId, @Param("quantity") Integer quantity);

    /**
     * 取消订单/支付失败时释放预占库存：把 locked_stock 归还 stock。
     * XML 中按 LEAST(locked_stock, #{quantity}) 钳制恢复量防负数，
     * 且 WHERE locked_stock &gt; 0——锁定已清零时返回 0，视为早已释放，天然支持补偿重试。
     *
     * @return 1=释放成功；0=无锁定库存可释放（按已释放处理）
     */
    int releaseStock(@Param("bookId") Long bookId, @Param("quantity") Integer quantity);

    /**
     * 支付成功后确认库存：保留 stock 扣减结果，只清减 locked_stock。
     * XML 要求 locked_stock &gt;= #{quantity}，账目不平（如重复确认）时返回 0。
     *
     * @return 1=确认成功；0=锁定库存不足（调用方需判断是否已确认过）
     */
    int confirmStock(@Param("bookId") Long bookId, @Param("quantity") Integer quantity);
}
