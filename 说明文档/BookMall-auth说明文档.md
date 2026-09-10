# 墨枢 InkNexus · Auth 模块说明文档

## 1. 项目概述

BookMall 是一个用于练习 Spring Boot + Spring Cloud 微服务开发的图书商城项目。
当前已经完成 `auth` 模块的基础闭环，包含：

- 用户注册
- 用户登录
- BCrypt 密码加密
- JWT 令牌生成
- 收货地址管理

本文档记录当前 `auth` 模块的实现状态、配置、目录结构、接口设计和验证方式。

## 2. 当前项目结构

当前根工程为 Maven 聚合工程，模块如下：

- `bookmall-common`
- `bookmall-auth`
- `bookmall-book`
- `bookmall-cart`
- `bookmall-order`
- `bookmall-gateway`

当前项目目录中的关键部分：

- [pom.xml](D:/workspace_idea/BookMall/BookMall/pom.xml)
- [bookmall-common](D:/workspace_idea/BookMall/BookMall/bookmall-common)
- [bookmall-auth](D:/workspace_idea/BookMall/BookMall/bookmall-auth)

## 3. 父工程配置说明

父工程用于统一管理子模块和依赖版本，不直接编写业务代码。

当前父工程配置要点：

- `packaging` 为 `pom`
- Java 版本为 `17`
- 统一管理 Spring Boot、Spring Cloud、Spring Cloud Alibaba 版本
- 当前模块包括：
  - `bookmall-common`
  - `bookmall-auth`
  - `bookmall-book`
  - `bookmall-cart`
  - `bookmall-order`
  - `bookmall-gateway`

当前使用的版本：

- Spring Boot `3.2.5`
- Spring Cloud `2023.0.2`
- Spring Cloud Alibaba `2023.0.1.0`

## 4. bookmall-common 模块说明

`bookmall-common` 是公共基础模块，用于存放所有服务都能复用的通用类。

当前包含的类：

- `Result<T>`：统一接口返回体
- `ErrorCode`：错误码枚举
- `BusinessException`：业务异常类

当前包结构：

- `com.bookmall.common.result`
- `com.bookmall.common.constant`
- `com.bookmall.common.exception`

### 4.1 Result<T>

文件路径：

- [Result.java](D:/workspace_idea/BookMall/BookMall/bookmall-common/src/main/java/com/bookmall/common/result/Result.java)

用途：

- 统一接口返回格式
- 所有服务尽量都使用这个结构返回数据

常用方法：

- `Result.success(data)`
- `Result.success()`
- `Result.fail(code, message)`
- `Result.fail(message)`

### 4.2 ErrorCode

文件路径：

- [ErrorCode.java](D:/workspace_idea/BookMall/BookMall/bookmall-common/src/main/java/com/bookmall/common/constant/ErrorCode.java)

当前错误码：

- `SUCCESS(200, "success")`
- `PARAM_ERROR(400, "参数错误")`
- `UNAUTHORIZED(401, "未授权")`
- `FORBIDDEN(403, "无权限")`
- `NOT_FOUND(404, "资源不存在")`
- `SYSTEM_ERROR(500, "系统异常")`

### 4.3 BusinessException

文件路径：

- [BusinessException.java](D:/workspace_idea/BookMall/BookMall/bookmall-common/src/main/java/com/bookmall/common/exception/BusinessException.java)

用途：

- 业务层主动抛出异常
- 例如：用户名已存在、密码错误、token 为空等

## 5. bookmall-auth 模块说明

`bookmall-auth` 是当前已经完成的认证模块，主要负责用户身份相关能力。

当前职责包括：

- 用户注册
- 用户登录
- 密码加密存储
- JWT 生成
- 收货地址管理

### 5.1 启动类

文件路径：

- [AuthApplication.java](D:/workspace_idea/BookMall/BookMall/bookmall-auth/src/main/java/com/bookmall/auth/AuthApplication.java)

当前注解：

- `@SpringBootApplication`
- `@MapperScan("com.bookmall.auth.mapper")`

作用：

- 启动 Spring Boot 服务
- 扫描 MyBatis-Plus Mapper 接口

### 5.2 配置文件

文件路径：

- [application.yml](D:/workspace_idea/BookMall/BookMall/bookmall-auth/src/main/resources/application.yml)

当前配置包含：

- 服务端口：`8060`
- 服务名：`auth`
- Nacos 注册中心与 Nacos Config：`localhost:8848`
- Nacos Config：`auth.yaml`
- MyBatis-Plus 配置

数据源和 JWT 配置位于 [nacos-config/auth.yaml](D:/workspace_idea/BookMall/nacos-config/auth.yaml)。

当前 MySQL 连接信息：

- Host: `localhost`
- Port: `3306`
- Database: `bookmall`
- Username: `root`
- Password: `123456`

当前 JWT 配置：

- `secret`: `bookmall-jwt-secret-key-2026-safe`
- `expire-seconds`: `86400`

### 5.3 依赖说明

文件路径：

- [bookmall-auth/pom.xml](D:/workspace_idea/BookMall/BookMall/bookmall-auth/pom.xml)

当前依赖包括：

- `bookmall-common`
- `spring-boot-starter-web`
- `mybatis-plus-spring-boot3-starter`
- `mysql-connector-java`
- `spring-boot-starter-validation`
- `lombok`
- `spring-security-crypto`
- `jjwt-api`
- `jjwt-impl`
- `jjwt-jackson`

## 6. 当前接口说明

当前 `auth` 模块的接口前缀是：

- `/auth`

### 6.1 GET /auth/hello

用途：

- 服务健康检查
- 验证服务是否启动成功

当前返回：

- `bookmall-auth is running`

### 6.2 POST /auth/register

用途：

- 注册用户

请求对象：

- `RegisterRequest`

请求字段：

- `username`
- `password`
- `nickname`
- `phone`
- `email`

功能说明：

- 校验用户名是否重复
- 使用 BCrypt 加密密码
- 插入用户到 `t_user` 表
- 返回创建后的用户信息

### 6.3 POST /auth/login

用途：

- 用户登录
- 登录成功后返回 token

请求对象：

- `LoginRequest`

请求字段：

- `username`
- `password`

功能说明：

- 根据用户名查询用户
- 使用 BCrypt 校验密码
- 生成 JWT token
- 返回 `LoginResponse`

### 6.4 地址管理接口

地址接口前缀为 `/auth/addresses`，通过网关访问时为 `/api/auth/addresses`。

当前接口：

- `GET /auth/addresses`：查询当前用户地址列表
- `POST /auth/addresses`：新增地址
- `PUT /auth/addresses/{id}`：修改地址
- `PUT /auth/addresses/{id}/default`：设置默认地址
- `DELETE /auth/addresses/{id}`：删除地址

请求头：

- `X-User-Id`：由网关解析 JWT 后透传，地址接口不接收前端传入的用户 ID

地址请求字段：

- `receiverName`：收货人
- `receiverPhone`：联系电话
- `province` / `city` / `district`：省市区分段
- `detailAddress`：详细地址
- `isDefault`：是否设为默认地址

## 7. 业务类结构说明

### 7.1 Entity

文件路径：

- [User.java](D:/workspace_idea/BookMall/BookMall/bookmall-auth/src/main/java/com/bookmall/auth/entity/User.java)

收货地址实体：

- [UserAddress.java](D:/workspace_idea/BookMall/BookMall/bookmall-auth/src/main/java/com/bookmall/auth/entity/UserAddress.java)

映射表：

- `t_user_address`

映射表：

- `t_user`

字段：

- `id`
- `username`
- `password`
- `nickname`
- `phone`
- `email`
- `status`
- `createTime`
- `updateTime`

### 7.2 Mapper

文件路径：

- [UserMapper.java](D:/workspace_idea/BookMall/BookMall/bookmall-auth/src/main/java/com/bookmall/auth/mapper/UserMapper.java)
- [UserAddressMapper.java](D:/workspace_idea/BookMall/BookMall/bookmall-auth/src/main/java/com/bookmall/auth/mapper/UserAddressMapper.java)

说明：

- 继承 `BaseMapper<User>`
- 使用 MyBatis-Plus 提供的基础 CRUD 能力

### 7.3 DTO

注册请求：

- [RegisterRequest.java](D:/workspace_idea/BookMall/BookMall/bookmall-auth/src/main/java/com/bookmall/auth/dto/RegisterRequest.java)

登录请求：

- [LoginRequest.java](D:/workspace_idea/BookMall/BookMall/bookmall-auth/src/main/java/com/bookmall/auth/dto/LoginRequest.java)

地址请求：

- [AddressRequest.java](D:/workspace_idea/BookMall/BookMall/bookmall-auth/src/main/java/com/bookmall/auth/dto/AddressRequest.java)

### 7.4 VO

登录返回：

- [LoginResponse.java](D:/workspace_idea/BookMall/BookMall/bookmall-auth/src/main/java/com/bookmall/auth/vo/LoginResponse.java)

注册返回与地址返回：

- [UserVO.java](D:/workspace_idea/BookMall/BookMall/bookmall-auth/src/main/java/com/bookmall/auth/vo/UserVO.java)
- [AddressVO.java](D:/workspace_idea/BookMall/BookMall/bookmall-auth/src/main/java/com/bookmall/auth/vo/AddressVO.java)

### 7.5 Service

接口：

- [UserService.java](D:/workspace_idea/BookMall/BookMall/bookmall-auth/src/main/java/com/bookmall/auth/service/UserService.java)

实现：

- [UserServiceImpl.java](D:/workspace_idea/BookMall/BookMall/bookmall-auth/src/main/java/com/bookmall/auth/service/impl/UserServiceImpl.java)

当前方法：

- `register(RegisterRequest request)`
- `login(LoginRequest request)`

地址服务：

- [AddressService.java](D:/workspace_idea/BookMall/BookMall/bookmall-auth/src/main/java/com/bookmall/auth/service/AddressService.java)
- [AddressServiceImpl.java](D:/workspace_idea/BookMall/BookMall/bookmall-auth/src/main/java/com/bookmall/auth/service/impl/AddressServiceImpl.java)

当前方法：

- `list(Long userId)`
- `create(Long userId, AddressRequest request)`
- `update(Long userId, Long addressId, AddressRequest request)`
- `setDefault(Long userId, Long addressId)`
- `delete(Long userId, Long addressId)`

### 7.6 JWT 工具类

文件路径：

- [JwtUtil.java](D:/workspace_idea/BookMall/BookMall/bookmall-auth/src/main/java/com/bookmall/auth/util/JwtUtil.java)

用途：

- 生成 JWT
- 解析 JWT
- 提取用户 ID、用户名、昵称等信息

## 8. 数据库说明

当前数据库：

- `bookmall`

当前已经创建的表：

- `t_user`
- `t_category`
- `t_book`
- `t_book_stock`
- `t_cart_item`
- `t_order`
- `t_order_item`
- `t_user_address`

其中 `auth` 模块当前主要使用：

- `t_user`
- `t_user_address`

## 9. 已验证功能

当前已经验证通过的功能有：

- `GET /auth/hello` 可访问
- `POST /auth/register` 可注册新用户
- `POST /auth/login` 可登录并返回 token
- `GET /auth/addresses` 可查询当前用户地址
- `POST /auth/addresses` 可新增地址
- `PUT /auth/addresses/{id}` 可修改地址
- `PUT /auth/addresses/{id}/default` 可设置默认地址
- `DELETE /auth/addresses/{id}` 可删除地址
- 重复用户名会返回业务错误
- 密码已使用 BCrypt 加密存储
- 地址接口只信任网关透传的 `X-User-Id`

## 10. 当前测试方式

当前功能主要通过以下方式验证：

- 浏览器访问 `GET /auth/hello`
- Postman / Apifox 测试 `POST` 接口
- IDEA HTTP Client 测试接口
- MySQL 中查看 `t_user` 表数据

### 10.1 注册接口测试

请求示例：

```json
{
  "username": "tom3",
  "password": "123456",
  "nickname": "Tom3",
  "phone": "13800000000",
  "email": "tom3@example.com"
}
```

### 10.2 登录接口测试

请求示例：

```json
{
  "username": "tom3",
  "password": "123456"
}
```

### 10.3 地址接口测试

通过网关访问，需要先登录获取 token：

```text
GET http://localhost:8080/api/auth/addresses
Authorization: Bearer xxxxx.yyyyy.zzzzz
```

网关会校验 token 并写入 `X-User-Id`，地址接口再按当前用户查询、修改或删除地址。

## 11. 当前模块状态总结

`auth` 模块已经完成了认证和收货地址管理的最小可用闭环。

现在它已经具备：

- 可运行
- 可注册
- 可登录
- 可发 token
- 可管理当前用户收货地址
- 前端 [AddressView.vue](D:/workspace_idea/BookMall/front/src/views/AddressView.vue) 已接入地址管理页，并支持在图书下单和购物车结算时带入地址

## 12. 维护规则

以后每完成一个模块，都要同步输出一份对应的说明文档，建议命名格式如下：

- `bookmall-auth-说明文档.md`
- `bookmall-book-说明文档.md`
- `bookmall-gateway-说明文档.md`

文档建议包含：

- 模块职责
- 配置说明
- 文件结构
- 接口列表
- 数据模型
- 测试方式
- 当前状态
