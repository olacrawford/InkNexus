package com.inknexus.book.controller;

import com.inknexus.book.dto.BookCreateRequest;
import com.inknexus.book.dto.BookUpdateRequest;
import com.inknexus.book.service.BookService;
import com.inknexus.book.service.CategoryService;
import com.inknexus.book.vo.BookDetailVO;
import com.inknexus.book.vo.BookVO;
import com.inknexus.book.vo.CategoryVO;
import com.inknexus.common.result.PageResult;
import com.inknexus.common.result.Result;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 图书与分类的 REST 入口（服务端口 8070）。
 *
 * <p>读接口（GET）公开，供门户与订单/购物车服务经 Feign 调用；
 * 写接口（POST/PUT/DELETE）是管理端能力，需 ADMIN 角色（网关判权 + {@code AdminOnlyInterceptor} 兜底）。
 * Controller 只做参数校验入口与结果包装，缓存与业务规则都在 Service 层。
 */
@RestController
@RequestMapping("/books")
public class BookController {

    @Autowired
    private BookService bookService;
    @Autowired
    private CategoryService categoryService;


    @GetMapping("/hello")
    public Result<String> hello() {
        return Result.success("inknexus-book is running");
    }

    @GetMapping
    public Result<List<BookVO>> listBooks() {
        return Result.success(bookService.listBooks());
    }

    // 按 ID 批量查询上架图书（订单服务购物车下单用），ids 支持 1,2,3 或重复参数两种传法
    @GetMapping("/by-ids")
    public Result<List<BookVO>> listBooksByIds(@RequestParam("ids") List<Long> ids) {
        return Result.success(bookService.listBooksByIds(ids));
    }

    @GetMapping("/{id}")
    public Result<BookDetailVO> getBookById(@PathVariable("id") Long id) {
        // Service 返回 null 代表「不存在 / 已下架」，这里转成 404 业务码而非抛异常
        BookDetailVO book = bookService.getBookById(id);
        if (book == null) {
            return Result.fail(404, "图书不存在");
        }
        return Result.success(book);
    }

    @GetMapping("/page")
    public Result<PageResult<BookVO>> pageBooks(
            @RequestParam(name = "pageNum", required = false) Integer pageNum,
            @RequestParam(name = "pageSize", required = false) Integer pageSize,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "categoryId", required = false) Long categoryId) {
        return Result.success(bookService.pageBooks(pageNum, pageSize, keyword, categoryId));
    }

    @PostMapping
    public Result<BookDetailVO> createBook(@Valid @RequestBody BookCreateRequest request) {
        return Result.success(bookService.createBook(request));
    }

    @PutMapping("/{id}")
    public Result<BookDetailVO> updateBook(@PathVariable("id") Long id,
                                           @Valid @RequestBody BookUpdateRequest request) {
        BookDetailVO book = bookService.updateBook(id, request);
        if (book == null) {
            return Result.fail(404, "图书不存在");
        }
        return Result.success(book);
    }

    @DeleteMapping("/{id}")
    public Result<String> deleteBook(@PathVariable("id") Long id) {
        boolean deleted = bookService.deleteBook(id);
        if (!deleted) {
            return Result.fail(404, "图书不存在");
        }
        return Result.success("删除成功");
    }

    @GetMapping("/categories")
    public Result<List<CategoryVO>> listCategories() {
        return Result.success(categoryService.listCategories());
    }
}
