package com.inknexus.cart.client;

import com.inknexus.cart.client.dto.BookSnapshot;
import com.inknexus.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 图书服务 OpenFeign 客户端。
 * <p>加购/改数量前的"图书存在且上架"校验都走这里：不信任前端传来的 bookId，
 * 上架过滤（status=1）由图书服务查询条件保证。失败行为见 {@code CartServiceImpl#validateBook}。
 */
@FeignClient(name = "book")
public interface BookClient {

    @GetMapping("/books/{id}")
    Result<BookSnapshot> getBookById(@PathVariable("id") Long bookId);
}
