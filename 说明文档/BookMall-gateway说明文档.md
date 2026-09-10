# 墨枢 InkNexus · Gateway 模块说明文档

## 1. 当前职责

`bookmall-gateway` 是统一入口模块，负责请求路由、跨域和 JWT 鉴权。

当前已实现：

- 路由转发到 `auth`、`book`、`cart`、`stock`、`order`、`payment`、`ai` 服务
- 通过 Nacos 服务名完成服务发现与负载均衡
- 校验 JWT，并把 `userId` 写入 `X-User-Id` 请求头透传给下游
- 登录、注册、健康检查和图书浏览接口白名单放行
- 全局 CORS 配置

## 2. 当前项目结构

- [GatewayApplication.java](D:/workspace_idea/BookMall/BookMall/bookmall-gateway/src/main/java/com/bookmall/gateway/GatewayApplication.java)：启动类
- [AuthGlobalFilter.java](D:/workspace_idea/BookMall/BookMall/bookmall-gateway/src/main/java/com/bookmall/gateway/filter/AuthGlobalFilter.java)：JWT 鉴权与用户身份透传
- [application.yml](D:/workspace_idea/BookMall/BookMall/bookmall-gateway/src/main/resources/application.yml)：端口、Nacos、路由、CORS

## 3. 配置说明

- 端口：`8080`
- 服务名：`gateway`
- Nacos 注册中心：`localhost:8848`
- Nacos Config：`gateway.yaml`

路由使用服务发现地址：

```yaml
- id: auth
  uri: lb://auth
- id: cart
  uri: lb://cart
- id: stock
  uri: lb://stock
- id: book
  uri: lb://book
- id: order
  uri: lb://order
- id: payment
  uri: lb://payment
- id: ai
  uri: lb://ai-assistant
```

## 4. 当前路由规则

| 网关路径 | 下游服务 | 实际路径 |
|---|---|---|
| `/api/auth/**` | `auth` | `/auth/**` |
| `/api/cart/**` | `cart` | `/cart/**` |
| `/api/stock/**` | `stock` | `/stock/**` |
| `/api/books/**` | `book` | `/books/**` |
| `/api/orders/**` | `order` | `/orders/**` |
| `/api/payment/**` | `payment` | `/payment/**` |
| `/api/ai/**` | `ai-assistant` | `/ai/**` |

网关通过 `StripPrefix=1` 去掉路径中的 `api`。

## 5. 鉴权规则

`AuthGlobalFilter` 默认校验请求头：

```text
Authorization: Bearer <token>
```

校验通过后会把 JWT 中的 `subject`（用户 ID）写入：

```text
X-User-Id: <userId>
```

下游服务只读取 `X-User-Id`，不接收前端传入的用户 ID。

白名单：

- `POST /api/auth/login`
- `POST /api/auth/register`
- 任意以 `/hello` 结尾的接口
- `GET /api/books/**`
- `GET /api/stock/**`

> 说明：`/api/ai/**` 中 `GET /api/ai/hello` 会被上面的 `/hello` 规则放行；`POST /api/ai/chat` 不在白名单内，必须携带 token 才能访问。

未携带 token、token 无效或 token 过期时返回 `401`。

## 6. 跨域配置

网关已配置全局 CORS：

- 允许所有来源
- 允许 GET、POST、PUT、DELETE、OPTIONS
- 允许所有请求头
- 允许携带凭证

## 7. 当前已实现依赖

- `spring-cloud-starter-gateway`
- `spring-cloud-starter-alibaba-nacos-discovery`
- `spring-cloud-starter-alibaba-nacos-config`
- `spring-cloud-starter-loadbalancer`
- `jjwt-api / jjwt-impl / jjwt-jackson`

在 Apple Silicon（arm64）上，`bookmall-gateway/pom.xml` 通过 `macos-arm64` Maven profile 引入 Netty 原生 DNS 解析库（`netty-resolver-dns-native-macos`），避免网关启动时因缺少原生库报错。

## 8. 验证方式

健康检查：

```text
GET http://localhost:8080/api/auth/hello
GET http://localhost:8080/api/books/hello
GET http://localhost:8080/api/stock/hello
GET http://localhost:8080/api/orders/hello
GET http://localhost:8080/api/payment/hello
GET http://localhost:8080/api/ai/hello
```

需要登录的接口：

```text
GET http://localhost:8080/api/orders
Authorization: Bearer xxxxx.yyyyy.zzzzz
```

未登录访问会返回 401；携带正确 token 后，网关会把 `X-User-Id` 透传给下游。
