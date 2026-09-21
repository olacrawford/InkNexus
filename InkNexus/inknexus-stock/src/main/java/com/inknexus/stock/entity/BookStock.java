package com.inknexus.stock.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 图书库存账目，对应表 t_book_stock（book_id 唯一键，一本图书一条记录）。
 * 两段式模型：预占时 stock 减、locked_stock 加；支付确认只减 locked_stock；
 * 取消/超时释放把 locked_stock 还回 stock。stock 因此始终代表真实可售数量。
 */
@Data
@TableName("t_book_stock")
public class BookStock {

    private Long id;
    private Long bookId;
    /**
     * 可售库存：下单预占即扣减，释放时由 locked_stock 归还
     */
    private Integer stock;
    /**
     * 预占库存：订单未终态期间冻结的数量，确认时清减、释放时归还
     */
    private Integer lockedStock;
    /**
     * 乐观锁版本号：每次条件更新自增，用于观察账目变更
     */
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
