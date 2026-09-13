package com.inknexus.order.client;

import com.inknexus.common.result.Result;
import com.inknexus.order.client.dto.BookSnapshot;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "book")
public interface BookClient {

    @GetMapping("/books/{id}")
    Result<BookSnapshot> getBookById(@PathVariable("id") Long bookId);

    // 批量查书：购物车下单一次拉齐所有图书，替代循环单查
    @GetMapping("/books/by-ids")
    Result<List<BookSnapshot>> listBooksByIds(@RequestParam("ids") List<Long> bookIds);
}
