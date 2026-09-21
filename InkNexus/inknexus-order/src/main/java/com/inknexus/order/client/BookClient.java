package com.inknexus.order.client;

import com.inknexus.common.result.Result;
import com.inknexus.order.client.dto.BookSnapshot;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 图书服务 Feign 客户端：下单时获取书目与价格，是订单价格快照的数据来源。
 */
@FeignClient(name = "book")
public interface BookClient {

    @GetMapping("/books/{id}")
    Result<BookSnapshot> getBookById(@PathVariable("id") Long bookId);

    @GetMapping("/books/by-ids")
    Result<List<BookSnapshot>> listBooksByIds(@RequestParam("ids") List<Long> bookIds);
}
