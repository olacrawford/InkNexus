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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BookServiceImpl implements BookService {

    private final BookMapper bookMapper;
    private final BookDetailCache detailCache;

    //构造注入mapper与详情缓存组件
    public BookServiceImpl(BookMapper bookMapper, BookDetailCache detailCache) {
        this.bookMapper = bookMapper;
        this.detailCache = detailCache;
    }

    /**
     * 查询所有上架图书列表
     * @return 图书VO集合
     */
    @Override
    @Cacheable(cacheNames = "books")
    @SentinelResource(value = "listBooks", blockHandler = "listBooksBlocked")
    public List<BookVO> listBooks() {
        //只查询状态=1（上架）的图书，entity转为VO返回
        return bookMapper.selectList(
                new LambdaQueryWrapper<Book>().eq(Book::getStatus, 1)
        ).stream().map(this::toBookVO).collect(Collectors.toList());
    }

    // 限流触发时返回友好提示
    public List<BookVO> listBooksBlocked(BlockException e) {
        throw new BusinessException(429, "图书列表请求过于频繁，请稍后再试");
    }

    /**
     * 按 id 批量查询上架图书：一条 IN 查询替代逐本 select，
     * 只返回存在且上架的图书，缺失的由调用方（订单服务）判定报错
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

    // 限流触发时返回友好提示
    public BookDetailVO getBookByIdBlocked(BlockException e) {
        throw new BusinessException(429, "图书详情请求过于频繁，请稍后再试");
    }

    // 限流触发时返回友好提示
    public PageResult<BookVO> pageBooksBlocked(BlockException e) {
        throw new BusinessException(429, "图书分页请求过于频繁，请稍后再试");
    }

    /**
     * 根据id查询图书详情
     * <p>详情走手写 Cache-Aside：互斥重建防击穿、空值缓存防穿透（见 {@link BookDetailCache}）
     * @param id 图书id
     * @return 图书详情VO，不存在返回null
     */
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
     * 图书分页查询，支持书名关键词和分类精确筛选
     * @param pageNum 页码
     * @param pageSize 每页条数
     * @param keyword 书名搜索关键词
     * @param categoryId 分类id（精确匹配，不包含子分类）
     * @return 分页结果对象
     */
    @Override
    @Cacheable(cacheNames = "books")
    @SentinelResource(value = "pageBooks", blockHandler = "pageBooksBlocked")
    public PageResult<BookVO> pageBooks(Integer pageNum, Integer pageSize, String keyword, Long categoryId) {
        //页码、页大小容错处理，为空或小于1给默认值
        long currentPage = pageNum == null || pageNum < 1 ? 1 : pageNum;
        long size = pageSize == null || pageSize < 1 ? 10 : pageSize;

        LambdaQueryWrapper<Book> wrapper = new LambdaQueryWrapper<Book>()
                .eq(Book::getStatus, 1)
                //关键词不为空才执行like模糊查询
                .like(keyword != null && !keyword.isBlank(), Book::getTitle, keyword)
                //分类不为空才按分类精确筛选
                .eq(categoryId != null, Book::getCategoryId, categoryId)
                .orderByDesc(Book::getId);

        //mybatis‑plus分页对象
        Page<Book> page = new Page<>(currentPage, size);
        bookMapper.selectPage(page, wrapper);

        //entity转VO
        List<BookVO> records = page.getRecords().stream()
                .map(this::toBookVO)
                .collect(Collectors.toList());

        //封装自定义分页返回对象
        return new PageResult<>(records, page.getTotal(), page.getPages(), page.getCurrent(), page.getSize());
    }

    /**
     * 新增图书
     * @param request 新增图书请求DTO
     * @return 新增完成的图书详情VO
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

    /**
     * 修改图书信息
     * @param id 图书id
     * @param request 修改请求DTO
     * @return 修改后详情VO，图书不存在返回null
     */
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
        book.setStatus(request.getStatus());
        book.setUpdateTime(LocalDateTime.now());

        bookMapper.updateById(book);
        // 先更库后精确驱逐该本图书的详情缓存，避免全清带来的缓存命中率抖动
        detailCache.evict(id);
        return toDetailVO(book);
    }

    /**
     * 删除图书
     * @param id 图书id
     * @return true删除成功，false图书不存在
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

    /**
     * 转换：Book实体 → BookVO列表简单对象
     */
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

    /**
     * 转换：Book实体 → BookDetailVO详情对象
     */
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
