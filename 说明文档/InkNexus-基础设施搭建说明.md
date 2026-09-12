# 墨枢 InkNexus · 基础设施搭建说明

## 1. 当前开发环境

当前项目运行在以下环境中：

- macOS（Apple Silicon）启动 Java 微服务
- Docker Desktop 使用 arm64 镜像启动基础设施
- MySQL、Nacos、Redis、RabbitMQ 运行在 Docker 容器中
- Java 服务通过 `localhost` 访问 Docker 映射出来的端口
- 一键启动：`docker compose -f docker-compose.infra.yml up -d`
- 引导脚本：`bash scripts/dev-macos.sh` 会检查 arm64、启动并等待中间件健康、发布 Nacos 配置

当前基础设施端口：

| 组件 | 地址 |
|---|---|
| MySQL | `localhost:3306` |
| Nacos | `localhost:8848` |
| Redis | `localhost:6379` |
| RabbitMQ | `localhost:5672`（账号 `admin` / `123456`） |

## 2. 当前后端模块

当前实际存在的后端模块：

| 模块 | 端口 | 当前职责 |
|---|---:|---|
| `inknexus-common` | - | 公共返回体、错误码、异常、分页 |
| `inknexus-gateway` | 8080 | 路由、跨域、JWT 鉴权 |
| `inknexus-auth` | 8060 | 注册、登录、地址管理 |
| `inknexus-book` | 8070 | 图书、分类、Redis 缓存、Sentinel |
| `inknexus-cart` | 8083 | 购物车条目、OpenFeign 图书校验 |
| `inknexus-stock` | 8090 | 库存查询、下单预占、取消释放 |
| `inknexus-order` | 8050 | 直接/购物车下单、订单管理、OpenFeign |
| `inknexus-payment` | 8051 | 支付单、内部模拟支付、发布支付成功事件 |
| `inknexus-ai` | 8071 | AI 问答助手（LangChain4j + 通义千问） |

## 3. 当前基础设施能力

### 3.1 Nacos

当前已接入：

- 服务注册与发现
- Nacos Config 配置中心
- Gateway 通过 `lb://auth`、`lb://book`、`lb://cart`、`lb://stock`、`lb://order`、`lb://payment`、`lb://ai-assistant` 路由

配置脚本位于 `nacos-config/`：

- `auth.yaml`
- `book.yaml`
- `cart.yaml`
- `stock.yaml`
- `order.yaml`
- `payment.yaml`
- `gateway.yaml`
- `ai-assistant.yaml`

更新配置后执行：

```bash
cd nacos-config
bash publish.sh
```

### 3.2 MySQL

数据库脚本：

- `sql/sql.txt`：初始化数据库和基础表
- `sql/updates/`：增量脚本

当前已创建的表：

- `t_user`
- `t_category`
- `t_book`
- `t_order`
- `t_order_item`
- `t_user_address`
- `t_cart_item`
- `t_book_stock`
- `t_payment`

### 3.3 Redis

当前 Redis 用于 `inknexus-book`：

- Spring Cache 缓存图书列表、分页、详情和分类
- 缓存统一 30 分钟过期，查询结果为 `null` 时不写入缓存
- 新增、修改、删除图书时清理 `book` 和 `books` 缓存

当前 Redis 也用于 `inknexus-ai`：

- 保存 AI 对话会话记忆（`ChatMemoryStore`），按「用户 ID + 会话 ID」隔离
- 会话记忆默认 TTL 2 小时，超时自动过期
- 由 `RedisChatMemoryStore` 使用 `StringRedisTemplate` 读写

### 3.4 Sentinel

当前 Sentinel 接在 `inknexus-book`：

- `listBooks`：50 QPS
- `pageBooks`：80 QPS
- `getBookById`：120 QPS
- `listCategories`：80 QPS
- 超限返回 429

启动 `inknexus-book` 时，如果 Sentinel 无法写入默认日志目录，指定项目内日志目录：

```bash
mkdir -p logs/sentinel
mvn -f InkNexus/pom.xml -pl inknexus-book spring-boot:run "-Dspring-boot.run.jvmArguments=-Dcsp.sentinel.log.dir=${PWD}/logs/sentinel"
```

### 3.5 RabbitMQ

当前 RabbitMQ 用于支付和订单状态事件：

- 交换机：`inknexus.pay.success.exchange`（Topic）
- 队列：`inknexus.order.pay.success.queue`
- 路由键：`pay.success`
- 发布端：`inknexus-payment`
- 消费端：`inknexus-order`

订单侧还会发布库存确认/释放事件：

- 交换机：`inknexus.order.stock.exchange`（Topic）
- 队列：`inknexus.stock.order.paid.queue`
- 队列：`inknexus.stock.order.release.queue`
- 生产端：`inknexus-order`
- 消费端：`inknexus-stock`

RabbitMQ 连接配置位于 `nacos-config/payment.yaml`、`nacos-config/order.yaml` 和 `nacos-config/stock.yaml` 的 `spring.rabbitmq`。

启动 RabbitMQ：

```bash
docker compose -f docker-compose.infra.yml up -d rabbitmq
```

支付服务和订单服务都会声明同名交换机、队列和绑定，RabbitMQ 声明是幂等的，不依赖两个服务的严格启动顺序。

### 3.6 OpenFeign

当前 `inknexus-order`、`inknexus-cart`、`inknexus-ai` 使用 OpenFeign：

- 服务名：`book`
- 调用：`GET /books/{id}`
- 分别返回订单模块、购物车模块自己的 `BookSnapshot`
- 订单服务还通过服务名 `cart` 调用 `GET /cart/selected`，读取购物车已选条目
- 订单服务通过服务名 `stock` 调用 `POST /stock/deduct`，完成下单前的库存预占
- 支付服务通过服务名 `order` 调用 `GET /orders/{id}`，完成支付前订单校验
- `inknexus-ai` 通过服务名 `book` 调用 `GET /books/page`、`GET /books/{id}`、`GET /books/categories`，通过服务名 `order` 调用 `GET /orders`、`GET /orders/{id}`，以上均为只读查询，Feign 从 `UserContextHolder` 注入 `X-User-Id` 透传给订单服务
- `cart.yaml`、`order.yaml`、`payment.yaml`、`ai-assistant.yaml` 配置默认连接超时，其中 `ai-assistant.yaml` 连接超时 3 秒、读取超时 8 秒

## 4. 启动顺序

后端服务的启动顺序、常用命令与常见问题（Sentinel 日志目录、`DASHSCOPE_API_KEY` 等）统一维护在根 [README.md](../README.md#5-启动后端服务)「本地运行说明」，此处不再重复。要点：auth → book → cart → stock → order → payment → gateway，`inknexus-ai` 与主链路解耦可最后启动。

## 5. 当前状态

本节只记录当前已实现的基础设施能力。
