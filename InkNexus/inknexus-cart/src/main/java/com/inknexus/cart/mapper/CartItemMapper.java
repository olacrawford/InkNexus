package com.inknexus.cart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.inknexus.cart.entity.CartItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {

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
