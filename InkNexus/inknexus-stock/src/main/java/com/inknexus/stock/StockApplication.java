package com.inknexus.stock;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 库存服务启动类（8090）。
 * 维护两段式库存账目：t_book_stock 的 stock（可售）与 locked_stock（预占），
 * 下单预占、支付确认、取消释放构成完整生命周期。
 * scanBasePackages 额外引入 com.inknexus.common，以装配统一异常处理、
 * Result 封装和 X-Trace-Id 日志追踪等公共组件。
 */
@MapperScan("com.inknexus.stock.mapper")
@SpringBootApplication(scanBasePackages = {"com.inknexus.stock", "com.inknexus.common"})
public class StockApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockApplication.class, args);
    }
}
