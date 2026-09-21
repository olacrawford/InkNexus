package com.inknexus.book.service.impl;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.inknexus.book.dto.BookCreateRequest;
import com.inknexus.book.dto.BookUpdateRequest;
import com.inknexus.book.entity.Book;
import com.inknexus.book.mapper.BookMapper;
import com.inknexus.book.service.BookService;
import com.inknexus.book.support.BookDetailCache;
import com.inknexus.book.vo.BookDetailVO;
import com.inknexus.book.vo.BookVO;
import com.inknexus.common.exception.BusinessException;
import com.inknexus.common.result.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 图书业务实现：读多写少。列表/分页走 SpringCache 的 books 缓存，单本详情走
 * {@link BookDetailCache} 手写 Cache-Aside（防击穿/穿透）；写路径统一「先写库、再失效缓存」，
 * 由 @CacheEvict(allEntries) 清列表/分页并精确驱逐详情缓存，残余不一致靠短 TTL 兜底。
 */
@Service
public class BookServiceImpl implements BookService {

    @Autowired
    private BookMapper bookMapper;
    @Autowired
    private BookDetailCache detailCache;

    /**
     * 查询所有上架图书；无参方法缓存 key 为 SimpleKey.EMPTY，与 pageBooks 的参数 key 互不覆盖
     */
    @Override
    @Cacheable(cacheNames = "books")
    @SentinelResource(value = "listBooks", blockHandler = "listBooksBlocked")
    public List<BookVO> listBooks() {
        return bookMapper.selectList(
                new LambdaQueryWrapper<Book>().eq(Book::getStatus, 1)
        ).stream().map(this::toBookVO).collect(Collectors.toList());
    }

    public List<BookVO> listBooksBlocked(BlockException e) {
        throw new BusinessException(429, "图书列表请求过于频繁，请稍后再试");
    }

    /**
     * 按 id 批量查询上架图书：一条 IN 查询替代逐本 select，只返回存在且上架的，
     * 缺失的由调用方（订单服务）判定报错
     */
    @Override
    public List<BookVO> listBooksByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return bookMapper.selectList(
                new LambdaQueryWrapper<Book>()
                        .in(Book::getId, ids)
                        .eq(Book::getStatus, 1)
        ).stream().map(this::toBookVO).collect(Collectors.toList());
    }

    public BookDetailVO getBookByIdBlocked(BlockException e) {
        throw new BusinessException(429, "图书详情请求过于频繁，请稍后再试");
    }

    public PageResult<BookVO> pageBooksBlocked(BlockException e) {
        throw new BusinessException(429, "图书分页请求过于频繁，请稍后再试");
    }

    /** 查询图书详情；详情走手写 Cache-Aside 防击穿/穿透（见 {@link BookDetailCache}），不存在返回 null */
    @Override
    @SentinelResource(value = "getBookById", blockHandler = "getBookByIdBlocked")
    public BookDetailVO getBookById(Long id) {
        return detailCache.load(id, () -> {
            Book book = bookMapper.selectOne(
                    new LambdaQueryWrapper<Book>()
                            .eq(Book::getId, id)
                            .eq(Book::getStatus, 1)
            );
            return book == null ? null : toDetailVO(book);
        });
    }

    /**
     * 分页查询图书，支持书名关键词与分类精确筛选；
     * 每组参数各占一个 books 缓存条目，任何写操作 allEntries 全清避免脏读旧分页
     */
    @Override
    @Cacheable(cacheNames = "books")
    @SentinelResource(value = "pageBooks", blockHandler = "pageBooksBlocked")
    public PageResult<BookVO> pageBooks(Integer pageNum, Integer pageSize, String keyword, Long categoryId) {
        long currentPage = pageNum == null || pageNum < 1 ? 1 : pageNum;
        long size = pageSize == null || pageSize < 1 ? 10 : pageSize;

        LambdaQueryWrapper<Book> wrapper = new LambdaQueryWrapper<Book>()
                .eq(Book::getStatus, 1)
                .like(keyword != null && !keyword.isBlank(), Book::getTitle, keyword)
                .eq(categoryId != null, Book::getCategoryId, categoryId)
                .orderByDesc(Book::getId);

        Page<Book> page = new Page<>(currentPage, size);
        bookMapper.selectPage(page, wrapper);

        List<BookVO> records = page.getRecords().stream()
                .map(this::toBookVO)
                .collect(Collectors.toList());

        return new PageResult<>(records, page.getTotal(), page.getPages(), page.getCurrent(), page.getSize());
    }

    /**
     * 新增图书（固定上架 status=1）；allEntries 全清是因为新书上架会改变「全部图书」的查询结果
     */
    @Override
    @CacheEvict(cacheNames = "books", allEntries = true)
    public BookDetailVO createBook(BookCreateRequest request) {
        Book book = new Book();
        book.setTitle(request.getTitle());
        book.setAuthor(request.getAuthor());
        book.setPrice(request.getPrice());
        book.setCategoryId(request.getCategoryId());
        book.setCoverUrl(request.getCoverUrl());
        book.setDescription(request.getDescription());
        book.setStatus(1);
        book.setCreateTime(LocalDateTime.now());
        book.setUpdateTime(LocalDateTime.now());

        bookMapper.insert(book);
        return toDetailVO(book);
    }

    /** 修改图书（全量替换语义），图书不存在返回 null */
    @Override
    @CacheEvict(cacheNames = "books", allEntries = true)
    public BookDetailVO updateBook(Long id, BookUpdateRequest request) {
        Book book = bookMapper.selectById(id);
        if (book == null) {
            return null;
        }

        book.setTitle(request.getTitle());
        book.setAuthor(request.getAuthor());
        book.setPrice(request.getPrice());
        book.setCategoryId(request.getCategoryId());
        book.setCoverUrl(request.getCoverUrl());
        book.setDescription(request.getDescription());
        // 状态完全由入参覆盖：0=下架、1=上架（update 语义为全量替换，未传字段校验已拦截）
        book.setStatus(request.getStatus());
        book.setUpdateTime(LocalDateTime.now());

        // 先写库，成功后才失效缓存（Cache-Aside 的标准写时序）
        bookMapper.updateById(book);
        // 精确驱逐该本图书的详情缓存而非全清，避免缓存命中率抖动
        detailCache.evict(id);
        return toDetailVO(book);
    }

    /**
     * 删除图书：@TableLogic 作用下的逻辑删除（UPDATE deleted=1），历史订单等关联数据不受影响；
     * 图书不存在返回 false
     */
    @Override
    @CacheEvict(cacheNames = "books", allEntries = true)
    public boolean deleteBook(Long id) {
        Book book = bookMapper.selectById(id);
        if (book == null) {
            return false;
        }
        bookMapper.deleteById(id);
        detailCache.evict(id);
        return true;
    }

    private BookVO toBookVO(Book book) {
        return new BookVO(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getPrice(),
                book.getCoverUrl(),
                book.getCategoryId()
        );
    }

    private BookDetailVO toDetailVO(Book book) {
        return new BookDetailVO(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getPrice(),
                book.getCategoryId(),
                book.getCoverUrl(),
                book.getDescription(),
                book.getStatus()
        );
    }

}
