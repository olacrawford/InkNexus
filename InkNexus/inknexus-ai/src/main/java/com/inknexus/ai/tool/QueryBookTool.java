package com.inknexus.ai.tool;

import com.inknexus.ai.feign.BookFeignClient;
import com.inknexus.ai.feign.dto.BookSnapshot;
import com.inknexus.ai.feign.dto.CategorySnapshot;
import com.inknexus.ai.support.ResultUtils;
import com.inknexus.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 图书查询工具：暴露给 AI 模型使用的 @Tool，内部只读调用 book 服务，不写任何数据。
 * 图书数据与用户身份无关，调用不依赖 X-User-Id。工具返回结构化对象，
 * 由 Spring AI 统一序列化为 JSON 进入模型上下文；失败时抛异常，
 * 交给 ToolExecutionExceptionProcessor 转成模型可读的失败说明。
 */
@Component
@RequiredArgsConstructor
public class QueryBookTool {

    private final BookFeignClient bookFeignClient;

    @Tool(description = "按书名关键字搜索图书，返回图书摘要列表（书名/作者/价格）")
    public List<BookBrief> searchBooks(@ToolParam(description = "搜索关键词：书名或作者名") String keyword,
                                       @ToolParam(description = "返回条数，不传默认5，最大10", required = false) Integer topN) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("搜索关键词不能为空");
        }
        // topN 默认 5，最多 10，避免模型一次拿太多数据撑爆上下文
        int limit = topN == null ? 5 : Math.min(topN, 10);
        PageResult<BookSnapshot> page = ResultUtils.data(
                bookFeignClient.pageBooks(1, limit, keyword, null));
        if (page == null || page.getRecords() == null) {
            return List.of();
        }
        return page.getRecords().stream()
                .limit(limit)
                .map(book -> new BookBrief(book.getTitle(), book.getAuthor(), book.getPrice()))
                .toList();
    }

    @Tool(description = "查询所有图书分类（返回分类ID与名称）")
    public List<CategoryBrief> listCategories() {
        List<CategorySnapshot> list = ResultUtils.data(bookFeignClient.listCategories());
        if (list == null) {
            return List.of();
        }
        return list.stream()
                .map(category -> new CategoryBrief(category.getId(), category.getName()))
                .toList();
    }

    /** 图书摘要（工具返回值，Spring AI 序列化为 JSON 提供给模型）。 */
    record BookBrief(String title, String author, BigDecimal price) {
    }

    /** 分类摘要（工具返回值）。 */
    record CategoryBrief(Long id, String name) {
    }
}
