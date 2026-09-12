package com.inknexus.book;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@MapperScan("com.inknexus.book.mapper")
@EnableCaching
@SpringBootApplication(scanBasePackages = {"com.inknexus.book", "com.inknexus.common"})
public class BookApplication {
    public static void main(String[] args) {
        SpringApplication.run(BookApplication.class, args);
    }
}
