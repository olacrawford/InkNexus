# 墨枢 InkNexus 说明文档

> 本文档目录按模块与横向能力组织，随项目进度同步更新。所有文档均为「当前已实现」口径。

## 一、模块说明

| 文档 | 说明 |
| --- | --- |
| [InkNexus-auth说明文档.md](InkNexus-auth说明文档.md) | 注册、登录、地址管理模块 |
| [InkNexus-book说明文档.md](InkNexus-book说明文档.md) | 图书、分类、Redis 缓存、Sentinel 模块 |
| [InkNexus-cart说明文档.md](InkNexus-cart说明文档.md) | 购物车模块 |
| [InkNexus-stock说明文档.md](InkNexus-stock说明文档.md) | 库存预占、取消释放模块 |
| [InkNexus-order说明文档.md](InkNexus-order说明文档.md) | 下单、订单管理、MQ 事件模块 |
| [InkNexus-payment说明文档.md](InkNexus-payment说明文档.md) | 支付单、模拟支付、支付结果事件 |
| [InkNexus-gateway说明文档.md](InkNexus-gateway说明文档.md) | 网关路由、JWT 鉴权、跨域、`X-User-Id` 透传 |
| [InkNexus-ai-assistant说明文档.md](InkNexus-ai-assistant说明文档.md) | AI 只读问答助手（LangChain4j + DashScope） |

## 二、能力与部署说明

| 文档 | 说明 |
| --- | --- |
| [InkNexus-数据库设计说明.md](InkNexus-数据库设计说明.md) | 表结构、索引与初始化脚本说明 |
| [InkNexus-基础设施搭建说明.md](InkNexus-基础设施搭建说明.md) | 中间件（MySQL/Nacos/Redis/RabbitMQ）、模块端口、启动顺序 |
| [InkNexus-增强项实施说明.md](InkNexus-增强项实施说明.md) | Redis 缓存、Sentinel、MQ 最终一致性、单元测试、AI 助手横向能力 |
| [InkNexus-Nginx部署说明.md](InkNexus-Nginx部署说明.md) | Nginx 反向代理与前端部署 |

## 三、演进规划

| 文档 | 说明 |
| --- | --- |
| [InkNexus-改进方案.md](InkNexus-改进方案.md) | P0~P3 演进路线：安全、可靠性、工程化、可观测性与功能增强（规划口径） |

## 四、相关设计文档

- 后端根说明：[../README.md](../README.md)

## 五、归档

历史文档见 [archive/README.md](archive/README.md)（AI 模块施工文档、旧版后端工程 README），仅作留存，不随代码更新。

## 五、更新约定

- 文档随代码、配置同步更新，单个改动尽量包含后端、前端、SQL、Nacos 配置与本文档。
- 新增服务请在「模块说明」表补一行，并在「基础设施」补充端口、Nacos 配置与启动顺序。
- 涉及真实密钥（如 `DASHSCOPE_API_KEY`）只写在环境变量里，文档不落地明文。
