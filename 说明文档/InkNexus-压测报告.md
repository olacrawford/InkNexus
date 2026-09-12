# 墨枢 InkNexus · 接口压测报告

> 工具：Apache JMeter 5.6+，经网关压测；脚本与运行参数见 `benchmark/README.md`。
> 环境：本机 Docker（MySQL 8 / Redis 7 / RabbitMQ 3 / Nacos 2）+ 本地启动的微服务。
> 填表说明：跑完 `benchmark/*.jmx` 后把报告里的数字填入下表，并在「结论」里挑出写进简历的数字。

## 一、测试环境

| 项 | 值 |
|---|---|
| 机器 | Apple M__ / __ GB 内存 / macOS __ |
| JDK | 17（服务端与压测端同机/分机：__） |
| 被测链路 | 网关 8080 → book / order → stock / MySQL / Redis / RabbitMQ |
| 压测参数 | users=__，ramp=__s，duration=__s |

## 二、场景一：图书分页列表（缓存收益）

接口：`GET /api/books/page?pageNum=1&pageSize=8`（`@Cacheable` TTL 30 分钟）

| 指标 | 冷缓存（FLUSHDB 后） | 热缓存（命中 Redis） |
|---|---|---|
| 吞吐量 QPS | | |
| 平均响应（ms） | | |
| P90（ms） | | |
| P95（ms） | | |
| P99（ms） | | |
| 错误率 | | |

**缓存收益**：QPS 提升 __ 倍（__ → __），P95 下降 __%（__ ms → __ ms）。

## 三、场景二：直接下单（写链路）

接口：`POST /api/orders`（价格快照 → 库存预占 → 订单落库 → 延迟关单消息）

| 指标 | 数值 |
|---|---|
| 吞吐量 QPS | |
| 平均响应（ms） | |
| P95（ms） | |
| P99（ms） | |
| 错误率 | |

**幂等验证**：固定同一 `clientRequestId` 并发提交 __ 次，产生订单 __ 笔，库存净扣减 __ —— 重复请求返回同一订单，多余预占已补偿释放。

## 四、结论（写进简历的数字）

- 引入 Redis 缓存后，图书列表接口 QPS 从 __ 提升到 __（__ 倍），P95 从 __ ms 降至 __ ms。
- 下单写链路在 __ 并发下吞吐 __ QPS，P99 __ ms，错误率 __%，链路包含跨服务预占与异步关单。
- （可选）观察到的瓶颈与后续优化方向：__。

## 五、复现方式

```bash
# 场景一（冷/热缓存各跑一次，操作顺序见 benchmark/README.md）
jmeter -n -t benchmark/book-list.jmx -Jusers=50 -Jramp=10 -Jduration=60 \
  -l benchmark/result-book-list.jtl -e -o benchmark/report-book-list

# 场景二
jmeter -n -t benchmark/order-create.jmx -Jtoken=$TOKEN -JbookId=1 \
  -Jusers=20 -Jramp=10 -Jduration=60 \
  -l benchmark/result-order.jtl -e -o benchmark/report-order
```
