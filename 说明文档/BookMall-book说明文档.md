# 墨枢 InkNexus · Book 模块说明文档

## 1. 当前职责

`bookmall-book` 是图书服务模块，负责图书和分类相关能力。

当前已实现：

- 图书列表查询（仅返回上架图书）
- 图书详情查询
- 图书分页查询（支持书名关键字和分类筛选）
- 图书新增、修改、删除
- 分类列表查询
- 图书列表、分页、详情、分类 Redis 缓存
- 图书查询接口 Sentinel 限流

## 2. 当前项目结构

启动类：

- [BookApplication.java](D:/workspace_idea/BookMall/BookMall/bookmall-book/src/main/java/com/bookmall/book/BookApplication.java)

配置类：

- [RedisConfig.java](D:/workspace_idea/BookMall/BookMall/bookmall-book/src/main/java/com/bookmall/book/config/RedisConfig.java)
- [SentinelConfig.java](D:/workspace_idea/BookMall/BookMall/bookmall-book/src/main/java/com/bookmall/book/config/SentinelConfig.java)
- [MybatisPlusConfig.java](D:/workspace_idea/BookMall/BookMall/bookmall-book/src/main/java/com/bookmall/book/config/MybatisPlusConfig.java)

业务代码：

- `controller`：`BookController`
- `service`：`BookService`、`CategoryService`
- `service.impl`：`BookServiceImpl`、`CategoryServiceImpl`
- `mapper`：`BookMapper`、`CategoryMapper`
- `entity`：`Book`、`Category`
- `dto`：`BookCreateRequest`、`BookUpdateRequest`
- `vo`：`BookVO`、`BookDetailVO`、`CategoryVO`

## 3. 配置说明

服务配置：

- 端口：`8070`
- 服务名：`book`
- Nacos 注册中心：`localhost:8848`
- Nacos Config：`book.yaml`
- MySQL：`localhost:3306/bookmall`
- Redis：`localhost:6379`

数据库连接、Redis 连接等信息在 [nacos-config/book.yaml](D:/workspace_idea/BookMall/nacos-config/book.yaml) 中维护。

## 4. 当前接口

接口前缀：`/books`

- `GET /books/hello`：健康检查，返回 `bookmall-book is running`
- `GET /books`：查询全部上架图书
- `GET /books/{id}`：查询图书详情
- `GET /books/page?pageNum=1&pageSize=10&keyword=Java&categoryId=2`：分页查询
- `POST /books`：新增图书
- `PUT /books/{id}`：修改图书
- `DELETE /books/{id}`：删除图书
- `GET /books/categories`：查询分类列表

其中 `GET /books/page` 的 `keyword` 和 `categoryId` 都可选；`categoryId` 为精确分类筛选，不包含子分类。

## 5. 数据模型

`Book` 映射 `t_book`：

- `title`、`author`、`price`
- `categoryId`、`coverUrl`、`description`
- `status`（1 上架，0 下架）
- `deleted`（MyBatis-Plus `@TableLogic` 软删除）
- `createTime`、`updateTime`

`Category` 映射 `t_category`：

- `name`、`sort`、`status`
- `createTime`、`updateTime`

当前 `t_category` 是平铺大类，没有父子分类字段。

## 6. 缓存实现

当前缓存使用 Spring Cache + Redis：

- `getBookById()` 使用 `@Cacheable(cacheNames = "book")`，键为 `book::<id>`
- `listBooks()` 和 `pageBooks()` 使用 `@Cacheable(cacheNames = "books")`，键为 `books::...`
- `listCategories()` 使用 `@Cacheable(cacheNames = "category")`，键为 `category::...`
- 所有缓存统一 30 分钟过期；查询结果为 `null` 时不写入缓存
- 新增、修改、删除图书时使用 `@Caching` 清理 `book` 和 `books` 两个缓存空间

## 7. Sentinel 限流

通过 [SentinelConfig.java](D:/workspace_idea/BookMall/BookMall/bookmall-book/src/main/java/com/bookmall/book/config/SentinelConfig.java) 配置四条 QPS 规则：

- `listBooks`（`GET /books`）：50 QPS
- `pageBooks`（`GET /books/page`）：80 QPS
- `getBookById`（`GET /books/{id}`）：120 QPS
- `listCategories`（`GET /books/categories`）：80 QPS

超过阈值时分别返回对应的 429 友好提示，正常业务访问不会误伤。

## 8. 验证方式

通过网关验证：

```text
GET http://localhost:8080/api/books
GET http://localhost:8080/api/books/1
GET http://localhost:8080/api/books/page?pageNum=1&pageSize=10
GET http://localhost:8080/api/books/categories
POST http://localhost:8080/api/books
PUT http://localhost:8080/api/books/1
DELETE http://localhost:8080/api/books/1
```

限流验证：

```bash
for i in $(seq 1 80); do curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8080/api/books/page?pageNum=1&pageSize=10"; done
```

分页接口并发超过 80 QPS 时会出现 `429 图书分页请求过于频繁，请稍后再试`。

## 9. 当前状态

`bookmall-book` 当前只描述已实现功能。
