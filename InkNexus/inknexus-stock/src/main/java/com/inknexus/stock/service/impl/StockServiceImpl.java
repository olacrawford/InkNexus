package com.inknexus.stock.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.inknexus.common.exception.BusinessException;
import com.inknexus.stock.dto.StockOperationItem;
import com.inknexus.stock.entity.BookStock;
import com.inknexus.stock.mapper.MqConsumedLogMapper;
import com.inknexus.stock.mapper.StockMapper;
import com.inknexus.stock.service.StockService;
import com.inknexus.stock.vo.StockVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 库存业务：查询、预占、释放和确认。
 * 释放/确认由 MQ 消息驱动，按消息 eventId 做消费幂等：
 * 去重记录与库存更新写在同一事务，重复投递的消息不会再次执行库存操作。
 */
@Slf4j
@Service
public class StockServiceImpl implements StockService {

    @Autowired
    private StockMapper stockMapper;
    @Autowired
    private MqConsumedLogMapper mqConsumedLogMapper;

    /**
     * 查询库存账目。
     *
     * @throws com.inknexus.common.exception.BusinessException 404 库存记录不存在
     */
    @Override
    public StockVO getByBookId(Long bookId) {
        BookStock row = stockMapper.selectOne(new LambdaQueryWrapper<BookStock>()
                .eq(BookStock::getBookId, bookId));
        if (row == null) {
            throw new BusinessException(404, "库存不存在");
        }
        return toVO(row);
    }

    /**
     * 批量预占库存（下单路径）：逐本条件更新，任一本不足即整体回滚，与订单创建构成跨服务同步流程。
     * @throws com.inknexus.common.exception.BusinessException 400 任一图书库存不足
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deduct(List<StockOperationItem> items) {
        for (StockOperationItem item : items) {
            // 原子更新失败说明库存不足，事务会回滚本次所有预占
            int affected = stockMapper.deductStock(item.getBookId(), item.getQuantity());
            if (affected == 0) {
                throw new BusinessException(400, "库存不足：图书ID " + item.getBookId());
            }
        }
    }

    /**
     * 释放预占库存（消费订单取消/超时事件），幂等以 eventId 去重。
     * 策略宽松：单本 affected==0 只记日志不抛错——释放属于补偿动作，
     * 库存可能早已释放（历史订单、重复补偿），报错只会让补偿流程卡死。
     * @return true=已执行；false=重复消息被跳过
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean release(String messageId, List<StockOperationItem> items) {
        if (!markConsumed(messageId, "stock-release")) {
            log.info("库存释放消息重复投递，跳过：messageId={}", messageId);
            return false;
        }
        for (StockOperationItem item : items) {
            // 原子更新返回0说明没有锁定库存，按已释放处理，避免历史订单或重试补偿卡住
            int affected = stockMapper.releaseStock(item.getBookId(), item.getQuantity());
            if (affected == 0) {
                log.info("库存无需释放或已释放：图书ID {}", item.getBookId());
            }
        }
        return true;
    }

    /**
     * 确认库存（消费订单支付成功事件），幂等以 eventId 去重。
     * 策略严格：锁定库存不足且未确认过时抛出 500，让监听器重试/最终进死信，
     * 因为确认失败意味着账目不平，静默吞掉会造成超卖。
     * @return true=已执行；false=重复消息被跳过
     * @throws com.inknexus.common.exception.BusinessException 500 确认失败且非重复确认
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean confirm(String messageId, List<StockOperationItem> items) {
        if (!markConsumed(messageId, "stock-confirm")) {
            log.info("库存确认消息重复投递，跳过：messageId={}", messageId);
            return false;
        }
        for (StockOperationItem item : items) {
            // 支付完成才允许确认；锁定库存不足时抛出异常，让订单事务回滚
            int affected = stockMapper.confirmStock(item.getBookId(), item.getQuantity());
            if (affected == 0 && !alreadyConfirmed(item.getBookId())) {
                throw new BusinessException(500, "库存确认失败：图书ID " + item.getBookId());
            }
        }
        return true;
    }

    /**
     * 消费去重：insertOnce 命中唯一键返回 0 表示消息已消费过。
     * 去重记录与后续库存更新在同一事务——库存操作异常回滚时记录一并回滚，消息重投后仍可重试。
     */
    private boolean markConsumed(String messageId, String consumer) {
        if (messageId == null || messageId.isBlank()) {
            // 事件ID缺失的消息无法去重，按首次消费处理，幂等仍由状态条件更新与 LEAST 钳制兜底
            return true;
        }
        return mqConsumedLogMapper.insertOnce(messageId, consumer) > 0;
    }

    private boolean alreadyConfirmed(Long bookId) {
        // 消息重复消费时锁定库存已经为 0，按已确认成功处理，避免重复执行报错
        BookStock row = stockMapper.selectOne(new LambdaQueryWrapper<BookStock>()
                .eq(BookStock::getBookId, bookId));
        return row != null && row.getLockedStock() != null && row.getLockedStock() <= 0;
    }

    private StockVO toVO(BookStock row) {
        StockVO vo = new StockVO();
        vo.setBookId(row.getBookId());
        vo.setStock(row.getStock());
        vo.setLockedStock(row.getLockedStock());
        // 预占时 stock 已同步减少，因此可售库存直接使用 stock
        vo.setAvailableStock(row.getStock());
        return vo;
    }
}
