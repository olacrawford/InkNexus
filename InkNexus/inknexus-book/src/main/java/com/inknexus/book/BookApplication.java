package com.inknexus.book;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * 图书服务启动类（inknexus-book，端口 8070）：图书/分类查询与管理端增删改。
 *
 * <p>{@code @EnableCaching} 开启 SpringCache（books/category 缓存，见 RedisConfig），
 * 是详情之外的手写缓存 {@code BookDetailCache} 得以共存的前提；
 * 扫描范围额外带上 com.inknexus.common，让全局异常处理、链路追踪过滤器等公共组件生效。
 */
@MapperScan("com.inknexus.book.mapper")
@EnableCaching
@SpringBootApplication(scanBasePackages = {"com.inknexus.book", "com.inknexus.common"})
public class BookApplication {
    public static void main(String[] args) {
        SpringApplication.run(BookApplication.class, args);
    }
}
