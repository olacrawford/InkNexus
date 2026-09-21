package com.inknexus.cart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.inknexus.cart.entity.CartItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 购物车表 Mapper，对应 {@code t_cart_item}。
 * <p>通用 CRUD 走 BaseMapper；加购使用下方自定义 upsert，是并发安全的关键。
 */
@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {

    /**
     * 加购 upsert：同一用户同一本书首次插入新行，已存在时数量累加。
     * <p>语义依赖表上的唯一键 uk_user_book(user_id, book_id)：
     * <ul>
     *   <li>新插入行 selected 用 COALESCE(#{selected}, 1)：未指定时默认勾选；</li>
     *   <li>冲突更新时 selected 用 COALESCE(#{selected}, selected)：未指定则保留原勾选状态；</li>
     *   <li>update_time 由 CURRENT_TIMESTAMP 统一刷新。</li>
     * </ul>
     * 返回值为 MySQL upsert 的影响行数：1=新增，2=更新。
     *
     * @param userId   用户id（唯一键一半）
     * @param bookId   图书id（唯一键另一半），须已通过图书服务校验
     * @param quantity 本次加购数量（累加到已有值上）
     * @param selected 勾选状态，null 表示"默认勾选/保持原状"
     * @return 1 新增行；2 更新已有行
     */
    // 并发加购同一本书时利用唯一键原子累加，避免先查再插入触发重复键异常
    @Insert("""
            INSERT INTO t_cart_item (user_id, book_id, quantity, selected, update_time)
            VALUES (#{userId}, #{bookId}, #{quantity}, COALESCE(#{selected}, 1), CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE quantity = quantity + #{quantity},
                    selected = COALESCE(#{selected}, selected),
                    update_time = CURRENT_TIMESTAMP
            """)
    int insertOrUpdate(@Param("userId") Long userId,
                       @Param("bookId") Long bookId,
                       @Param("quantity") Integer quantity,
                       @Param("selected") Integer selected);
}
