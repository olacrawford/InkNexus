-- 007: 项目由 BookMall 更名为 InkNexus，将数据库 bookmall 重命名为 inknexus
-- 适用：已经按旧命名（数据库 bookmall）初始化过的存量环境。
-- 全新环境不需要执行本脚本，直接运行 sql/sql.txt 初始化即可。
--
-- MySQL 不支持直接 RENAME DATABASE，采用「新建库 + 逐表 RENAME TABLE」迁移，
-- 表结构与数据原样保留，跨库 RENAME TABLE 是原子操作，不复制数据。

CREATE DATABASE IF NOT EXISTS inknexus DEFAULT CHARACTER SET utf8mb4;

RENAME TABLE bookmall.t_user        TO inknexus.t_user;
RENAME TABLE bookmall.t_category    TO inknexus.t_category;
RENAME TABLE bookmall.t_book        TO inknexus.t_book;
RENAME TABLE bookmall.t_user_address TO inknexus.t_user_address;
RENAME TABLE bookmall.t_cart_item   TO inknexus.t_cart_item;
RENAME TABLE bookmall.t_book_stock  TO inknexus.t_book_stock;
RENAME TABLE bookmall.t_order       TO inknexus.t_order;
RENAME TABLE bookmall.t_order_item  TO inknexus.t_order_item;
RENAME TABLE bookmall.t_payment     TO inknexus.t_payment;

-- 确认 inknexus 库业务正常后，手动删除旧库：
-- DROP DATABASE bookmall;
