package com.inknexus.ai.feign;

import com.inknexus.ai.feign.dto.BookSnapshot;
import com.inknexus.ai.feign.dto.CategorySnapshot;
import com.inknexus.common.result.PageResult;
import com.inknexus.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** Book 服务 OpenFeign 客户端：只读调用图书服务的查询接口。name = "book" 对应 Nacos 注册的服务名。 */
@FeignClient(name = "book")
public interface BookFeignClient {

    /** 图书分页搜索：支持按关键词 keyword、分类 categoryId 过滤。供 AI 的 searchBooks 工具使用。 */
    @GetMapping("/books/page")
    Result<PageResult<BookSnapshot>> pageBooks(
            @RequestParam("pageNum") Integer pageNum,
            @RequestParam("pageSize") Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "categoryId", required = false) Long categoryId);

    /** 查询所有图书分类。供 AI 的 listCategories 工具使用。 */
    @GetMapping("/books/categories")
    Result<List<CategorySnapshot>> listCategories();
}