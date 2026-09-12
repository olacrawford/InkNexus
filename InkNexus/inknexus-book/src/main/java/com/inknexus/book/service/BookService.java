package com.inknexus.book.service;

import com.inknexus.book.dto.BookCreateRequest;
import com.inknexus.book.dto.BookUpdateRequest;
import com.inknexus.book.vo.BookDetailVO;
import com.inknexus.book.vo.BookVO;
import com.inknexus.common.result.PageResult;

import java.util.List;

/**
 * 图书业务接口
 */
public interface BookService {

    /**
     * 查询所有上架图书
     * @return 图书列表VO集合
     */
    List<BookVO> listBooks();

    /**
     * 根据id查询图书详情
     * @param id 图书id
     * @return 图书详情VO，不存在返回null
     */
    BookDetailVO getBookById(Long id);

    /**
     * 图书分页查询，支持书名关键词、分类精确筛选
     * @param pageNum 页码
     * @param pageSize 每页条数
     * @param keyword 书名搜索关键字
     * @param categoryId 分类id
     * @return 分页结果对象
     */
    PageResult<BookVO> pageBooks(Integer pageNum, Integer pageSize, String keyword, Long categoryId);

    /**
     * 新增图书
     * @param request 新增图书入参DTO
     * @return 新增后的图书详情VO
     */
    BookDetailVO createBook(BookCreateRequest request);

    /**
     * 修改图书信息
     * @param id 待修改图书id
     * @param request 修改图书入参DTO
     * @return 修改后图书详情VO，图书不存在返回null
     */
    BookDetailVO updateBook(Long id, BookUpdateRequest request);

    /**
     * 删除图书
     * @param id 图书id
     * @return true删除成功；false图书不存在删除失败
     */
    boolean deleteBook(Long id);
}
