# 压测工具包使用说明

JMeter 脚本通过网关（默认 `localhost:8080`）压测两条核心链路，参数全部用 `-J` 属性注入，无需改脚本。

## 前置条件

1. 基础设施与全部服务已启动（`docker-compose.infra.yml` + auth/book/cart/stock/order/payment/gateway）。
2. MySQL 种子数据已导入（`sql/sql.txt` + `sql/updates/*.sql`）。
3. 本机安装 JMeter 5.6+（`brew install jmeter`）。

## 场景一：图书分页列表（缓存收益对比）

```bash
# 正式压测命令模板
jmeter -n -t benchmark/book-list.jmx \
  -Jusers=50 -Jramp=10 -Jduration=60 \
  -l benchmark/result-book-list.jtl -e -o benchmark/report-book-list
```

缓存冷/热两种场景的操作顺序：

1. **热缓存（命中 Redis）**：先预热——用浏览器或 `curl 'http://localhost:8080/api/books/page?pageNum=1&pageSize=8'` 请求几次，再启动上面的命令。
2. **冷缓存（未命中）**：先清空 book 服务的缓存再立刻压测：
   ```bash
   docker exec -it bookmall-redis redis-cli -a 123455 -n 0 FLUSHDB
   ```
   （容器名/密码以 `docker-compose.infra.yml` 与 `nacos-config/book.yaml` 为准；TTL 为 30 分钟，清空后第一轮请求全部回源 MySQL。）
3. 两次结果的 QPS 与 P95 差值就是缓存收益，写入简历数字。

## 场景二：直接下单

下单要登录态和充足库存：

```bash
# 1. 取 JWT（账号密码换成你的测试用户）
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"test","password":"123456"}' | sed -E 's/.*"data":"([^"]+)".*/\1/')
echo $TOKEN

# 2. 确认目标图书库存足够（bookId 默认 1，可 -JbookId 换）
#    库存不足会大量返回“库存不足”业务错误，压测前把 t_book_stock.stock 调大

# 3. 压测
jmeter -n -t benchmark/order-create.jmx \
  -Jtoken=$TOKEN -JbookId=1 -Jusers=20 -Jramp=10 -Jduration=60 \
  -l benchmark/result-order.jtl -e -o benchmark/report-order
```

说明：

- 每次请求生成独立 `clientRequestId`（JMeter `${__UUID}`），走正常下单幂等链路，不会互相去重。
- 下单是写链路，串了 book（价格快照）→ stock（预占）→ order 落库 → MQ 关单消息，QPS 显著低于纯查询属正常现象，这也是面试时“读写链路差异”的好素材。
- 压完可顺手验证幂等：把脚本里 `clientRequestId` 固定为同一个值再跑两次，两次应返回同一笔订单、库存只扣一次。

## 参数一览

| 参数 | 默认 | 含义 |
|---|---|---|
| `-Jhost` / `-Jport` | `localhost` / `8080` | 网关地址 |
| `-Jusers` | 列表 50 / 下单 20 | 并发线程数 |
| `-Jramp` | 10 | 起压时间（秒） |
| `-Jduration` | 60 | 持续时间（秒） |
| `-Jtoken` | 空 | 下单场景的 JWT |
| `-JbookId` | 1 | 下单场景目标图书 |

## 结果怎么读

`-e -o` 生成的 HTML 报告里重点看：**吞吐量（QPS）、平均值、中位数、90%/95%/99% 百分位、错误率**。把数字填进 `说明文档/BookMall-压测报告.md`，再挑最有说服力的一组写进 README 项目亮点和简历。
