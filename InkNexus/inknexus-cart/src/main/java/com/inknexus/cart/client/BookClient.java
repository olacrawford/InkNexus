package com.inknexus.cart.client;

import com.inknexus.cart.client.dto.BookSnapshot;
import com.inknexus.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "book")
public interface BookClient {

    // 通过 Nacos 服务名调用图书服务，不写死 IP 和端口
    @GetMapping("/books/{id}")
    Result<BookSnapshot> getBookById(@PathVariable("id") Long bookId);
}
