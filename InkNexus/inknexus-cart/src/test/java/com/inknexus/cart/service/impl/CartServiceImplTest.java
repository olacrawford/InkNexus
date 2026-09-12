package com.inknexus.cart.service.impl;

import com.inknexus.cart.client.BookClient;
import com.inknexus.cart.client.dto.BookSnapshot;
import com.inknexus.cart.dto.CartItemCreateRequest;
import com.inknexus.cart.entity.CartItem;
import com.inknexus.cart.mapper.CartItemMapper;
import com.inknexus.cart.vo.CartItemVO;
import com.inknexus.common.exception.BusinessException;
import com.inknexus.common.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock
    private CartItemMapper cartItemMapper;
    @Mock
    private BookClient bookClient;

    private CartServiceImpl cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartServiceImpl(cartItemMapper, bookClient);
    }

    @Test
    void add_upsertsAndReturnsQuantity_whenBookExists() {
        BookSnapshot snapshot = new BookSnapshot();
        snapshot.setId(1L);
        snapshot.setTitle("Java核心技术");
        snapshot.setStatus(1);
        when(bookClient.getBookById(1L)).thenReturn(Result.success(snapshot));
        when(cartItemMapper.insertOrUpdate(eq(1L), eq(1L), eq(2), isNull())).thenReturn(1);

        CartItem item = new CartItem();
        item.setId(100L);
        item.setUserId(1L);
        item.setBookId(1L);
        item.setQuantity(2);
        item.setSelected(1);
        when(cartItemMapper.selectOne(any())).thenReturn(item);

        CartItemVO result = cartService.add(1L, request(1L, 2, null));

        assertEquals(100L, result.getId());
        assertEquals(2, result.getQuantity());
        assertEquals(1, result.getSelected());
        verify(cartItemMapper).insertOrUpdate(eq(1L), eq(1L), eq(2), isNull());
    }

    @Test
    void add_passesSelectedAsOne_whenSelectedTrue() {
        BookSnapshot snapshot = new BookSnapshot();
        snapshot.setId(1L);
        snapshot.setStatus(1);
        when(bookClient.getBookById(1L)).thenReturn(Result.success(snapshot));
        when(cartItemMapper.selectOne(any())).thenReturn(new CartItem());

        cartService.add(1L, request(1L, 1, true));

        verify(cartItemMapper).insertOrUpdate(eq(1L), eq(1L), eq(1), eq(1));
    }

    @Test
    void add_throws_whenBookIsNotAvailable() {
        when(bookClient.getBookById(2L)).thenReturn(Result.fail(404, "图书不存在"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> cartService.add(1L, request(2L, 1, true)));

        assertEquals(400, exception.getCode());
        verify(cartItemMapper, never()).insertOrUpdate(any(), any(), any(), any());
    }

    private CartItemCreateRequest request(Long bookId, Integer quantity, Boolean selected) {
        CartItemCreateRequest request = new CartItemCreateRequest();
        request.setBookId(bookId);
        request.setQuantity(quantity);
        request.setSelected(selected);
        return request;
    }
}
