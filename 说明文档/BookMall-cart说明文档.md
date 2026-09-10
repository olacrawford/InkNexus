# 墨枢 InkNexus · Cart 模块说明文档

## 1. 当前职责

`bookmall-cart` 是购物车服务模块，负责当前用户的购物车条目管理。

当前已实现：

- 查询当前用户购物车
- 加入图书，同一本书重复加入时累加数量
- 修改购物车条目数量、勾选状态
- 删除单个购物车条目
- 清空当前用户购物车
- 向订单服务提供购物车已选条目
- 通过 OpenFeign 调用图书服务校验图书是否存在和上架状态

## 2. 当前项目结构

启动类：

- [CartApplication.java](D:/workspace_idea/BookMall/BookMall/bookmall-cart/src/main/java/com/bookmall/cart/CartApplication.java)

业务代码：

- `controller`：`CartController`
- `service`：`CartService`
- `service.impl`：`CartServiceImpl`
- `mapper`：`CartItemMapper`
- `entity`：`CartItem`
- `dto`：`CartItemCreateRequest`、`CartItemUpdateRequest`
- `vo`：`CartItemVO`
- `client`：`BookClient`
- `client.dto`：`BookSnapshot`

## 3. 配置说明

服务配置：

- 端口：`8083`
- 服务名：`cart`
- Nacos 注册中心：`localhost:8848`
- Nacos Config：`cart.yaml`
- MySQL：`localhost:3306/bookmall`

数据库连接配置在 [nacos-config/cart.yaml](D:/workspace_idea/BookMall/nacos-config/cart.yaml) 中维护。

## 4. 当前接口

接口前缀：`/cart`。通过网关访问时前缀为 `/api/cart`。

### 4.1 GET /cart/hello

健康检查，返回 `bookmall-cart is running`。

### 4.2 GET /cart

查询当前用户购物车。

### 4.3 GET /cart/selected

查询当前用户购物车中已勾选的条目，订单服务通过 Feign 调用此接口。

### 4.4 POST /cart

加入购物车。

请求体：

```json
{
  "bookId": 1,
  "quantity": 2,
  "selected": true
}
```

同一用户同一本书重复加入时，数量累加。

### 4.5 PUT /cart/{id}

修改购物车条目。

请求体：

```json
{
  "quantity": 3,
  "selected": false
}
```

### 4.6 DELETE /cart/{id}

删除当前用户自己的购物车条目。

### 4.7 DELETE /cart

清空当前用户购物车。

## 5. 数据模型

`CartItem` 映射 `t_cart_item`：

- `userId`：所属用户
- `bookId`：图书 ID
- `quantity`：购买数量
- `selected`：是否勾选，0 否，1 是
- `createTime`、`updateTime`

数据库通过唯一键 `(user_id, book_id)` 保证同一用户的一本书只有一条购物车记录。

## 6. 业务逻辑

- 所有接口从 `X-User-Id` 获取当前用户，不接收前端传入的 `userId`
- 新增购物车条目前调用 `BookClient` 校验图书存在且上架
- 修改、删除购物车条目时校验条目属于当前用户
- 加入同一本书时由 `CartItemMapper.insertOrUpdate` 使用数据库唯一键原子累加数量，避免并发重复插入
- 默认 OpenFeign 连接超时 3 秒、读取超时 5 秒，配置在 `cart.yaml`
- 订单服务通过 `GET /cart/selected` 读取已选条目并创建订单

## 7. 前端接入

当前前端已接入购物车：

- [CartView.vue](D:/workspace_idea/BookMall/front/src/views/CartView.vue)：购物车页面，支持勾选、数量增减、删除、清空、合计和购物车结算
- [bookmall.js](D:/workspace_idea/BookMall/front/src/api/bookmall.js)：`cartApi` 请求封装
- [BooksView.vue](D:/workspace_idea/BookMall/front/src/views/BooksView.vue)：图书列表提供“加入购物车”入口
- [App.vue](D:/workspace_idea/BookMall/front/src/App.vue)、[router/index.js](D:/workspace_idea/BookMall/front/src/router/index.js)：购物车导航和 `/cart` 路由

## 8. 验证方式

通过网关验证：

```text
GET    http://localhost:8080/api/cart
GET    http://localhost:8080/api/cart/hello
GET    http://localhost:8080/api/cart/selected
POST   http://localhost:8080/api/cart
PUT    http://localhost:8080/api/cart/1
DELETE http://localhost:8080/api/cart/1
DELETE http://localhost:8080/api/cart
```

除购物车服务自身直接访问外，正式请求都需要携带：

```text
Authorization: Bearer xxxxx.yyyyy.zzzzz
```

网关校验 JWT 后会写入 `X-User-Id`。
