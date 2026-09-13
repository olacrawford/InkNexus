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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/books")
public class BookController {

    private final BookService bookService;
    private final CategoryService categoryService;

    public BookController(BookService bookService, CategoryService categoryService) {
        this.bookService = bookService;
        this.categoryService = categoryService;
    }

    @GetMapping("/hello")
    public Result<String> hello() {
        return Result.success("inknexus-book is running");
    }

    // 查询所有图书
    @GetMapping
    public Result<List<BookVO>> listBooks() {
        return Result.success(bookService.listBooks());
    }

    // 按 ID 批量查询上架图书（订单服务购物车下单用），ids 支持 1,2,3 或重复参数两种传法
    @GetMapping("/by-ids")
    public Result<List<BookVO>> listBooksByIds(@RequestParam("ids") List<Long> ids) {
        return Result.success(bookService.listBooksByIds(ids));
    }

    // 根据 ID 查询图书详情
    @GetMapping("/{id}")
    public Result<BookDetailVO> getBookById(@PathVariable("id") Long id) {
        BookDetailVO book = bookService.getBookById(id);
        if (book == null) {
            return Result.fail(404, "图书不存在");
        }
        return Result.success(book);
    }

    // 分页查询（支持书名关键字 + 分类筛选）
    @GetMapping("/page")
    public Result<PageResult<BookVO>> pageBooks(
            @RequestParam(name = "pageNum", required = false) Integer pageNum,
            @RequestParam(name = "pageSize", required = false) Integer pageSize,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "categoryId", required = false) Long categoryId) {
        return Result.success(bookService.pageBooks(pageNum, pageSize, keyword, categoryId));
    }

    // 新增图书
    @PostMapping
    public Result<BookDetailVO> createBook(@Valid @RequestBody BookCreateRequest request) {
        return Result.success(bookService.createBook(request));
    }

    // 修改图书
    @PutMapping("/{id}")
    public Result<BookDetailVO> updateBook(@PathVariable("id") Long id,
                                           @Valid @RequestBody BookUpdateRequest request) {
        BookDetailVO book = bookService.updateBook(id, request);
        if (book == null) {
            return Result.fail(404, "图书不存在");
        }
        return Result.success(book);
    }

    // 删除图书
    @DeleteMapping("/{id}")
    public Result<String> deleteBook(@PathVariable("id") Long id) {
        boolean deleted = bookService.deleteBook(id);
        if (!deleted) {
            return Result.fail(404, "图书不存在");
        }
        return Result.success("删除成功");
    }

    // 获取普通分类列表
    @GetMapping("/categories")
    public Result<List<CategoryVO>> listCategories() {
        return Result.success(categoryService.listCategories());
    }
}
