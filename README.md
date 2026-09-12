# 墨枢 InkNexus

墨枢 InkNexus 是一个面向学习与展示的微服务图书商城项目，采用前后端分离架构，后端基于 Spring Cloud Alibaba，前端基于 Vue 3。当前保留六个核心业务微服务（用户、图书、购物车、库存、支付、订单）加网关、公共模块与 AI 问答助手，核心链路（注册登录、图书浏览、购物车结算下单、库存预占与确认、模拟支付、订单管理与超时关单）已跑通。

## 项目亮点

- 微服务拆分清晰：认证、图书、购物车、库存、支付、订单六个业务服务 + 网关、公共模块独立演进
- 前后端分离：Vue 3 前端通过 Gateway 与各业务服务联通
- 企业常见基础能力已接入：Nacos、Nacos Config、Gateway、OpenFeign、Redis、Sentinel、RabbitMQ
- 缓存三级防护：空值缓存防穿透、SETNX 分布式锁互斥重建 + Lua 原子释放防击穿、TTL 随机抖动 + 分级过期防雪崩
- 订单-库存一致性：SQL 条件更新 + 三态库存防超卖，下单唯一索引、支付状态 CAS、消息 eventId 去重三层幂等，RabbitMQ 延迟消息超时关单、消费重试 + 死信队列 + 发布确认
- 网关统一鉴权：JWT 校验 + 用户身份/角色透传（X-User-Id / X-User-Role），图书管理接口仅管理员可用
- 全链路 TraceId：网关生成透传，跨服务日志可串联，异常响应只带 traceId 不泄漏内部信息
- 接口文档：Knife4j 自动生成在线文档
- 核心链路可运行：注册、登录、图书浏览、分类查看、购物车结算下单、支付确认库存、订单超时自动取消

## 技术栈

| 分类 | 技术 |
|---|---|
| 后端 | Java 17, Spring Boot 3.2.5 |
| 微服务 | Spring Cloud 2023.0.2, Spring Cloud Alibaba 2023.0.1.0 |
| 注册发现 | Nacos |
| 配置中心 | Nacos Config |
| 接口文档 | Knife4j |
| 网关 | Spring Cloud Gateway |
| 服务调用 | OpenFeign |
| 消息队列 | RabbitMQ |
| 数据访问 | MyBatis-Plus |
| 数据库 | MySQL 8.x |
| 前端 | Vue 3, Vite, Vue Router, Axios |
| 认证 | JWT, BCrypt |

## 仓库结构

```text
InkNexus/
├─ InkNexus/        后端微服务工程
├─ front/           前端工程
├─ sql/             数据库脚本（sql.txt + updates/ 增量脚本）
├─ nacos-config/    Nacos 配置中心脚本
└─ 说明文档/         模块说明文档
```

## 模块与端口

| 模块 | 端口 | 职责 |
|---|---:|---|
| `inknexus-common` | - | 公共返回体、错误码、异常处理、分页对象 |
| `inknexus-gateway` | 8080 | 统一入口、路由转发、JWT 鉴权、跨域 |
| `inknexus-auth` | 8060 | 注册、登录、收货地址管理 |
| `inknexus-book` | 8070 | 图书增删改查、分页、分类 |
| `inknexus-cart` | 8083 | 购物车增加、查询、修改、删除、清空、结算 |
| `inknexus-stock` | 8090 | 库存查询、下单预占、支付确认、取消释放 |
| `inknexus-order` | 8050 | 订单（直接下单、购物车下单、列表、详情、取消、超时自动关单） |
| `inknexus-payment` | 8051 | 支付单、内部模拟支付、发布支付成功事件触发订单异步更新与库存确认 |
| `inknexus-ai` | 8071 | AI 问答助手：LangChain4j + DashScope 通义千问，只读调用图书/订单，会话记忆存 Redis |
| `front` | 5173 | 前端，Vite 托管 |

## 系统架构

```text
Browser
  -> Vue Frontend (Vite)
  -> /api/**
  -> Gateway (8080)
  -> auth / book / cart / stock / payment / order
  -> MySQL / Nacos / RabbitMQ
```

鉴权链路：

```text
前端请求（带 Bearer token）
  -> 网关 AuthGlobalFilter：校验 JWT → 放行，并把 userId/role 放入 X-User-Id / X-User-Role 头
  -> 下游服务：从请求头拿身份与角色（客户端自带的伪造头会被覆盖）
```

## 已完成功能

### 用户侧

- 用户注册
- 用户登录
- 收货地址管理（新增、编辑、删除、设置默认地址）

### 商城侧

- 图书增删改查 + 分页查询（写接口仅管理员角色可用）
- 分类列表（平铺大类，不细分）
- 购物车页面（加入、数量修改、勾选、删除、清空、结算下单）
- 图书库存查询、下单预占、支付确认、取消订单释放
- 支付单生成、内部模拟支付、订单状态变为已支付并确认库存
- 支付成功事件通过 RabbitMQ 异步补偿，订单更新失败时仍可最终一致
- 直接下单（选书 + 收货地址或手填收货信息）
- 订单列表、详情、支付、确认收货、取消、超时自动取消（含越权校验）

### 基础设施

- Nacos 服务注册与发现
- Gateway 统一路由转发 + JWT 鉴权过滤器
- OpenFeign 服务间调用
- RabbitMQ 支付成功事件发布与消费
- Redis 缓存图书列表/分页/详情/分类，Sentinel 接口限流
- 购物车并发加购原子更新、OpenFeign 超时配置、订单查询复合索引
- 单元测试覆盖全部 9 个后端模块共 76 个用例（服务层、网关过滤器、TraceId、AI 支撑类），不依赖中间件可独立运行
- 统一返回体与全局异常处理

## 本地运行说明

### 1. 基础环境

- macOS（Apple Silicon）+ IDEA 启动 Java 服务
- Docker Desktop + `docker-compose.infra.yml` 启动 MySQL、Nacos、Redis、RabbitMQ

### 2. 启动基础设施

一键启动全部中间件：

```bash
docker compose -f docker-compose.infra.yml up -d
```

也可以运行 macOS 引导脚本，它会检查 arm64、拉起 Docker、等待健康检查并发布 Nacos 配置：

```bash
bash scripts/dev-macos.sh
```

- MySQL: `localhost:3306`
- Nacos: `localhost:8848`
- Redis: `localhost:6379`
- RabbitMQ: `localhost:5672`（`admin` / `123456`）

### 3. 导入数据库

新环境初始化直接执行 [sql/sql.txt](sql/sql.txt) 即可，脚本已包含用户、图书、购物车、库存、订单、支付、MQ 消费去重等全部 10 张表和默认库存。

已有环境按顺序执行 `sql/updates/001_cart_address_stock.sql`、`002_stock_order.sql`、`003_payment.sql`、`004_order_expire_stock_confirm.sql`、`005_optimization.sql`、`006_order_request_id.sql`、`007_rename_database_to_inknexus.sql`、`008_user_role.sql`、`009_mq_consumed_log.sql` 完成增量升级。注意：`008` 会把 id=1 的用户提升为管理员，可按需修改；升级到含死信参数的队列版本后需删除旧 RabbitMQ 队列让服务重新声明。

### 4. 数据库与配置

数据库连接、JWT 密钥、Redis 地址等环境依赖写在 `nacos-config/*.yaml` 里（默认 `localhost:3306`、账号 `root`、密码 `123456`）。各服务 `application.yml` 只维护端口、Nacos 地址和配置导入，本地直连无需额外修改。首次运行或 Nacos 数据丢失后，执行 `cd nacos-config && bash publish.sh` 重新发布配置。

### 5. 启动后端服务

建议顺序：

1. `inknexus-auth`（8060）
2. `inknexus-book`（8070）
3. `inknexus-cart`（8083）
4. `inknexus-stock`（8090）
5. `inknexus-order`（8050）
6. `inknexus-payment`（8051）
7. `inknexus-gateway`（8080）
8. `inknexus-ai`（8071，可选，AI 问答助手）

启动 `inknexus-book` 时如需指定 Sentinel 日志目录：

```bash
mkdir -p logs/sentinel
mvn -f InkNexus/pom.xml -pl inknexus-book spring-boot:run "-Dspring-boot.run.jvmArguments=-Dcsp.sentinel.log.dir=${PWD}/logs/sentinel"
```

启动 `inknexus-ai` 前需设置通义千问 Key：

```bash
export DASHSCOPE_API_KEY='sk-你的通义千问Key'
```

命令行启动方式：先安装公共模块，再按需替换模块名启动（不要对 `spring-boot:run` 使用 `-am`，否则会尝试在父工程上找启动类）：

```bash
mvn -f InkNexus/pom.xml -DskipTests install
mvn -f InkNexus/pom.xml -pl inknexus-auth spring-boot:run
```

### 6. 启动前端

```bash
cd front
npm install
npm run dev
```

### 7. 访问入口

- 前端首页: `http://localhost:5173`
- AI 助手页: `http://localhost:5173/ai`（需登录后从侧边栏「AI 助手」进入）
- 网关入口: `http://localhost:8080`
- 图书接口示例: `http://localhost:5173/api/books`
- Knife4j 接口文档: 各业务服务 `http://localhost:<端口>/doc.html`（auth 8060 / book 8070 / cart 8083 / stock 8090 / order 8050 / payment 8051）

## 验证缓存与限流

Redis 缓存（图书列表、分页、详情、分类，统一 30 分钟过期，图书增删改自动失效）：

```bash
curl http://localhost:8080/api/books
docker exec -it redis redis-cli --scan --pattern 'book*'
```

Sentinel 限流（超过阈值返回 429）：`listBooks` 50 QPS、`pageBooks` 80 QPS、`getBookById` 120 QPS、`listCategories` 80 QPS：

```bash
for i in $(seq 1 100); do curl -s -o /dev/null "http://localhost:8080/api/books/page?pageNum=1&pageSize=10"; done
```

## 文档目录

- [说明文档/InkNexus-基础设施搭建说明.md](说明文档/InkNexus-基础设施搭建说明.md)
- [说明文档/InkNexus-auth说明文档.md](说明文档/InkNexus-auth说明文档.md)
- [说明文档/InkNexus-book说明文档.md](说明文档/InkNexus-book说明文档.md)
- [说明文档/InkNexus-cart说明文档.md](说明文档/InkNexus-cart说明文档.md)
- [说明文档/InkNexus-stock说明文档.md](说明文档/InkNexus-stock说明文档.md)
- [说明文档/InkNexus-payment说明文档.md](说明文档/InkNexus-payment说明文档.md)
- [说明文档/InkNexus-order说明文档.md](说明文档/InkNexus-order说明文档.md)
- [说明文档/InkNexus-gateway说明文档.md](说明文档/InkNexus-gateway说明文档.md)
- [说明文档/InkNexus-ai-assistant说明文档.md](说明文档/InkNexus-ai-assistant说明文档.md)
- [说明文档/InkNexus-数据库设计说明.md](说明文档/InkNexus-数据库设计说明.md)
- [说明文档/InkNexus-增强项实施说明.md](说明文档/InkNexus-增强项实施说明.md)
- [说明文档/InkNexus-Nginx部署说明.md](说明文档/InkNexus-Nginx部署说明.md)
- [说明文档/InkNexus-改进方案.md](说明文档/InkNexus-改进方案.md)
- [说明文档/README.md](说明文档/README.md)
