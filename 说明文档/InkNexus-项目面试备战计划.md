# InkNexus 项目面试备战计划（5~6 天版）

> 配套：《InkNexus-面试问答准备.md》（所有追问的答案，按题号引用）、《InkNexus-简历简化版.md》。
> 适用起点：会 CRUD、基本微服务、Nacos 放配置、Feign 远程调用；不会缓存三防、MQ 可靠性、幂等、延迟关单。
>
> **核心原则：不要按模块顺序读。从 `OrderServiceImpl#createOrder` 开始，沿"一次下单"的调用链往下走。** 所有模块都为这条链服务，主线懂了其他都是枝叶。
> **每天产出一张图**（时序图 / 消息拓扑图），面试白板画图就靠它。

---

## Day 1：把项目跑起来 + 读懂下单链路

**上午：完整跑一遍（从没完整跑过自己项目的先做这个）**

1. `docker-compose.infra.yml` 起 MySQL / Redis / RabbitMQ / Nacos；
2. 按 auth → book → cart → stock → order → payment → gateway 的顺序起服务；
3. 前端（或 Knife4j）真实走一遍：注册登录 → 浏览图书 → 加购 → 下单 → 支付 → 查订单；
4. 开着控制台日志，用响应里的 traceId 把这次请求跨服务的日志串起来看一遍。

**下午：读下单链路（第一遍精读），每个文件只回答括号里的问题**

| 顺序 | 文件 | 问题 |
|---|---|---|
| 1 | `order/service/impl/OrderServiceImpl.java` → `createOrder`（约 89 行起） | 下单共几步？哪步防超卖、哪步防重复？ |
| 2 | `stock/mapper/StockMapper.java` + `stock/service/impl/StockServiceImpl.java` | 条件更新 SQL 长什么样？`stock` 和 `locked_stock` 各是什么？为什么 `WHERE stock >= n` 就不会超卖？ |
| 3 | `sql/sql.txt` 里 `t_order` 的建表语句 | 唯一索引 `uk_user_request` 建在哪两个字段上？防什么场景？ |

**晚上：产出第一张图**——下单时序图（Controller → Service → Feign → 库存 SQL → 唯一索引冲突分支）。

## Day 2：支付-库存-关单后半段 + MQ 拓扑

**上午：支付与事件驱动**

| 顺序 | 文件 | 问题 |
|---|---|---|
| 1 | `payment/service/impl/PaymentServiceImpl.java` | 支付怎么防重复（`uk_order_id` + 已支付直接返回不发消息）？ |
| 2 | `payment/mq/PaySuccessPublisher.java` | 发了什么消息，eventId 从哪来？ |
| 3 | `order/mq/PaySuccessConsumer.java` | 订单怎么 0→1？为什么必须条件更新？重复消息到达会怎样？ |
| 4 | `stock/mq/OrderStockConsumer.java` + `stock/mapper/MqConsumedLogMapper.java` | `INSERT IGNORE` 去重怎么做的？为什么去重 insert 和库存更新要同一个事务？ |
| 5 | `order/mq/RabbitReliabilityConfig.java` + `payment/mq/RabbitReliabilityConfig.java` | Confirm / returns 回调各做了什么？nack 之后消息怎么办？（⚠️ 简历上明写了 Publisher Confirm，Q3.3 / Q3.4 是必答题，不是兜底话术） |

**下午：延迟关单**

| 顺序 | 文件 | 问题 |
|---|---|---|
| 1 | `order/mq/RabbitMqConfig.java` | 延迟队列声明了哪些参数？为什么用队列级 TTL 不用消息级（队头阻塞）？ |
| 2 | `order/mq/OrderCloseDelayConsumer.java` + `order/task/OrderTimeoutTask.java` | 关单 SQL 是什么？兜底任务和延迟消息同时到同一笔订单，为什么不会关两次？ |

**晚上：产出第二张图**——MQ 消息拓扑图（5 个交换机、7 类队列，谁发谁收；背 Q3.7 的拓扑速记）。

## Day 3：合上代码，重写主线（练习工程）

新建干净的 Spring Boot 练习工程（`order` / `stock` 两个包），两张表（`t_book_stock`、`t_order` 带 `uk_user_request`），**只写 4 件事**：

1. 条件更新预占：`UPDATE ... SET stock=stock-n, locked_stock=locked_stock+n WHERE book_id=? AND stock>=n`，0 行抛"库存不足"；
2. 下单唯一索引幂等：`DuplicateKeyException` → 查已有订单原样返回；
3. 支付成功消息 → 消费者条件更新 0→1；
4. 延迟队列关单（TTL 改 1 分钟）+ 定时任务兜底，条件更新 0→2 后发库存释放。

规则：卡住翻原代码 ≤ 2 分钟，翻完合上重写。
**检收**：① 下单→支付→已支付；② 下单→不付款→1 分钟自动关单、库存加回。

## Day 4：缓存三防 + 网关鉴权

**上午：缓存（全项目性价比最高的 206 行）**

1. 精读 `book/support/BookDetailCache.java`，合上重写 `load()` 六步：查缓存 → 命中 `__NULL__` 返回 null → SETNX 抢锁（值 UUID）→ 双重检查 → 回源写缓存（TTL ±10% 抖动）→ finally 里 Lua 校验解锁；未抢到锁 50ms 自旋 × 20 次后直接回源；
2. `JitterRedisCacheWriter` 会一句话：Spring Cache 写入路径的装饰器，对 `@Cacheable` TTL 做 ±10% 抖动，业务零侵入；
3. 对照 Q4.1~Q4.6 自测。

**下午：网关与越权（代码量很小，gateway 全模块才 3 个类）**

| 文件 | 问题 |
|---|---|
| `gateway/filter/AuthGlobalFilter.java` | 伪造 `X-User-Id` 怎么被防住的（mutate 覆盖语义）？无 role 的旧 token 为什么按 USER 透传（fail-closed）？ |
| `order` 里 `getOrderDetail` 的归属校验 | 为什么"非本人返回 404"而不是 403？ |

对照 Q1.4~Q1.6 自测。

## Day 5：AI 模块 + 通用必问题

**上午：AI 客服（难点在框架，你的代码很薄）**

| 文件 | 问题 |
|---|---|
| `ai/ai/BookAssistantAiService.java` | `@AiService` 接口是怎么变成实现的（启动生成代理）？System Prompt 约束了什么？ |
| `ai/tool/QueryBookTool.java`、`QueryOrderTool.java` | Function Calling 原理一轮讲清（Q5.1）；userId 为什么不进 prompt（Q5.2 三层约束） |
| `ai/controller/AiAssistantController.java` | SSE 三个事件（meta/delta/done）、断连处理（Q5.4） |

**下午：通用必问（这些几乎每场都会被问）**

- 最难的问题：背熟 Q6.1 的"关单一秒之差"STAR 故事（这是最出彩的素材）；
- 流量涨 10 倍先坏哪（Q6.3）；为什么不用 Seata（Q2.10）；为什么选 RabbitMQ（Q6.5）；重做会改什么（Q6.4）；
- 简历明牌必答题：Publisher Confirm 的取舍与 outbox 演进方向（Q3.3）、四层消息可靠性（Q3.4）——简历上写了 Confirm，这两题必须脱稿。

**晚上：自问自答 10 题**（防超卖原理 / 双击下单 / 消息为何重投 / 延迟关单 / 队头阻塞 / 三防 / Lua 解锁 / AI 越权 / 单测测了什么 / 最难的问题），录音回放听表达。

## Day 6：模拟面试 + 收尾

1. **上午**：过《面试问答准备》第七节防穿帮清单（压测数字绝不提）；简历逐词"两分钟测试"；
2. **下午**：模拟面试两轮——让 AI 助手扮面试官，按"是什么 → 为什么不用别的 → 失败了怎么办"三板斧追问，答不住的记下来；
3. **晚上**：错题回炉——每个答不住的点回到面试文档对应题号，重讲一遍。

**每天晚上另外花 30~60 分钟背技能行八股**（清单见技能行讨论：HashMap、线程池、volatile、ThreadLocal、@Transactional 失效、B+ 树、隔离级别），ThreadLocal / 唯一索引 / 三防直接用项目里的例子答。

---

## 检收标准（第 6 天结束时）

1. 白板能画出两张图：下单时序图、MQ 消息拓扑图；
2. 三板斧追问答得住：防超卖（为什么不用分布式锁）、多层幂等（三种重复来源）、延迟关单（队头阻塞、双路仲裁）；
3. **简历明牌五道硬题全部脱稿**：Confirm 的取舍（Q3.3）、四层消息可靠性（Q3.4）、eventId 去重同事务（Q2.6）、Lua 解锁竞态（Q4.3）、Function Calling 原理（Q5.1）；
4. 边界诚实答得出：锁无续期、Confirm 只留证据不重发、无退款——承认 + 讲取舍 + 给方向；
5. 简历上每个词不看稿讲 2 分钟。

## 怎么用 AI 助手提效

- 读不懂的文件直接发它，要"人话版逐步讲解"；
- Day 3 练习工程写完丢给它 review；
- Day 6 让它扮面试官追问（指定"按三板斧深挖"）；
- 让它抽背：随机说一个简历术语，你口述回答，它对照面试文档挑错。
