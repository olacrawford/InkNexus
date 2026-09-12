package com.inknexus.stock;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.inknexus.stock.mapper")
@SpringBootApplication(scanBasePackages = {"com.inknexus.stock", "com.inknexus.common"})
public class StockApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockApplication.class, args);
    }
}
