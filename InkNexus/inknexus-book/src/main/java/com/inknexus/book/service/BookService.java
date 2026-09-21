package com.inknexus.book.service;

import com.inknexus.book.dto.BookCreateRequest;
import com.inknexus.book.dto.BookUpdateRequest;
import com.inknexus.book.vo.BookDetailVO;
import com.inknexus.book.vo.BookVO;
import com.inknexus.common.result.PageResult;

import java.util.List;

public interface BookService {

    /** 查询所有上架（status=1）图书 */
    List<BookVO> listBooks();

    /**
     * 按 id 批量查上架图书，供订单服务购物车下单（替代逐本远程调用）；
     * 只返回存在且上架的，顺序不保证与入参一致
     */
    List<BookVO> listBooksByIds(List<Long> ids);

    /** 查询图书详情，不存在（或已下架）返回 null */
    BookDetailVO getBookById(Long id);

    /** 分页查询图书，支持书名关键词与分类精确筛选 */
    PageResult<BookVO> pageBooks(Integer pageNum, Integer pageSize, String keyword, Long categoryId);

    /** 新增图书（固定上架 status=1） */
    BookDetailVO createBook(BookCreateRequest request);

    /** 修改图书（全量替换语义），图书不存在返回 null */
    BookDetailVO updateBook(Long id, BookUpdateRequest request);

    /** 删除图书（逻辑删除），false 表示图书不存在 */
    boolean deleteBook(Long id);
}
