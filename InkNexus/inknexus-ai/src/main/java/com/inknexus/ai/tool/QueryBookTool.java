package com.inknexus.ai.tool;

import com.inknexus.ai.feign.BookFeignClient;
import com.inknexus.ai.feign.dto.BookSnapshot;
import com.inknexus.ai.feign.dto.CategorySnapshot;
import com.inknexus.ai.support.ResultUtils;
import com.inknexus.common.result.PageResult;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** 图书查询工具：暴露给 AI 模型使用的 @Tool，内部只读调用 book 服务，不写任何数据。 */
@Component
@RequiredArgsConstructor
public class QueryBookTool {

    private final BookFeignClient bookFeignClient;

    /** @Tool 表示这是个“可被模型调用”的工具；@P 是参数说明，模型据此决定要传什么。 */
    @Tool("按书名关键字搜索图书，返回前 N 条图书摘要（书名/作者/价格）")
    public String searchBooks(@P("keyword") String keyword, @P("topN") Integer topN) {
        if (keyword == null || keyword.isBlank()) {
            return "请提供搜索关键词";
        }
        // topN 默认 5，最多 10，避免模型一次拿太多数据撑爆上下文
        int limit = topN == null ? 5 : Math.min(topN, 10);
        // 调 book 服务分页接口（第 1 页、limit 条、按 keyword 过滤），并用 ResultUtils 解出真实数据
        PageResult<BookSnapshot> page = ResultUtils.data(
                bookFeignClient.pageBooks(1, limit, keyword, null));
        if (page == null || page.getRecords() == null || page.getRecords().isEmpty()) {
            return "没有找到相关图书，可换个关键词试试";
        }
        // 把图书列表拼成一段人话返回给模型，模型再组织成友好回答
        return page.getRecords().stream()
                .limit(limit)
                .map(book -> String.format("《%s》 %s ¥%s",
                        book.getTitle(), book.getAuthor(), book.getPrice()))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("没有找到相关图书");
    }

    /** @Tool 返回全部分类，模型可据此引导用户按分类找书。 */
    @Tool("查询所有图书分类（返回分类ID与名称）")
    public String listCategories() {
        List<CategorySnapshot> list = ResultUtils.data(bookFeignClient.listCategories());
        if (list == null || list.isEmpty()) {
            return "暂无分类";
        }
        return list.stream()
                .map(category -> category.getId() + "-" + category.getName())
                .reduce((a, b) -> a + ", " + b)
                .orElse("暂无分类");
    }
}