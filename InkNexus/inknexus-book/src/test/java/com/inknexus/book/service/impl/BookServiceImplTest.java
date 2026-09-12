package com.inknexus.book.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.inknexus.book.entity.Book;
import com.inknexus.book.mapper.BookMapper;
import com.inknexus.book.support.BookDetailCache;
import com.inknexus.book.vo.BookDetailVO;
import com.inknexus.book.vo.BookVO;
import com.inknexus.common.result.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookServiceImplTest {

    @Mock
    private BookMapper bookMapper;
    @Mock
    private BookDetailCache detailCache;

    private BookServiceImpl bookService;

    @BeforeEach
    void setUp() {
        bookService = new BookServiceImpl(bookMapper, detailCache);
    }

    @Test
    void listBooks_returnsMappedBookList() {
        when(bookMapper.selectList(any())).thenReturn(List.of(book()));

        List<BookVO> result = bookService.listBooks();

        assertEquals(1, result.size());
        assertEquals("Java核心技术", result.get(0).getTitle());
        assertEquals(new BigDecimal("149.00"), result.get(0).getPrice());
        verify(bookMapper).selectList(any());
    }

    @Test
    void getBookById_loadsDetailThroughCache() {
        when(bookMapper.selectOne(any())).thenReturn(book());
        // 详情走 BookDetailCache：load 未命中时通过 loader 回源
        when(detailCache.load(eq(1L), any())).thenAnswer(invocation ->
                ((Supplier<BookDetailVO>) invocation.getArgument(1)).get());

        BookDetailVO result = bookService.getBookById(1L);

        assertEquals("Java核心技术", result.getTitle());
        assertEquals("凯·霍斯特曼", result.getAuthor());
        assertEquals(2L, result.getCategoryId());
        verify(detailCache).load(eq(1L), any());
    }

    @Test
    void getBookById_returnsNull_whenBookNotFound() {
        when(bookMapper.selectOne(any())).thenReturn(null);
        when(detailCache.load(eq(999L), any())).thenAnswer(invocation ->
                ((Supplier<BookDetailVO>) invocation.getArgument(1)).get());

        assertNull(bookService.getBookById(999L));
    }

    @Test
    void pageBooks_returnsPageResult() {
        Page<Book> page = new Page<>(1, 10);
        when(bookMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<Book> target = invocation.getArgument(0);
            target.setRecords(List.of(book()));
            target.setTotal(1);
            return target;
        });

        PageResult<BookVO> result = bookService.pageBooks(1, 10, "Java", 2L);

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("Java核心技术", result.getRecords().get(0).getTitle());
    }

    private Book book() {
        Book book = new Book();
        book.setId(1L);
        book.setTitle("Java核心技术");
        book.setAuthor("凯·霍斯特曼");
        book.setPrice(new BigDecimal("149.00"));
        book.setCategoryId(2L);
        book.setCoverUrl("https://example.com/book.jpg");
        book.setDescription("Java技术参考书籍");
        book.setStatus(1);
        return book;
    }
}
