#!/bin/bash
# 功能验证第二阶段：order + stock + payment 全链路（下单/幂等/支付/MQ/取消/完成/超时兜底）
# 库存断言全部基于执行时快照的增量计算，可重复执行。
GW=http://localhost:8080/api
TOKEN=$(cat /tmp/inknexus_verify_token)
OH="Authorization: Bearer $TOKEN"
PASS=0; FAIL=0; FAILED_CASES=""
check() { if [ "$2" = "$3" ]; then PASS=$((PASS+1)); echo "PASS  $1"; else FAIL=$((FAIL+1)); FAILED_CASES="$FAILED_CASES\n  - $1 (期望=$2 实际=$3)"; echo "FAIL  $1 (期望=$2 实际=$3)"; fi }
q() { mysql -h127.0.0.1 -uroot -p123456 inknexus -N -e "$1" 2>/dev/null; }
stockrow() { q "SELECT CONCAT(stock,'/',locked_stock) FROM t_book_stock WHERE book_id=$1"; }
snap() { local v; v=$(stockrow "$1"); echo "$v"; }
uuid() { python3 -c "import uuid;print(uuid.uuid4())"; }

echo "===== 直购下单 ====="
BASE1=$(snap 1); BS1=${BASE1%%/*}; BL1=${BASE1##*/}
echo "      book1 快照 stock/locked = $BASE1"
CRID1=$(uuid)
R=$(curl -s -X POST $GW/orders -H "$OH" -H 'Content-Type: application/json' \
  -d "{\"bookId\":1,\"quantity\":2,\"receiverName\":\"张三\",\"receiverPhone\":\"13900001111\",\"receiverAddress\":\"上海市浦东新区世纪大道100号\",\"clientRequestId\":\"$CRID1\"}")
check "直购下单成功" 200 "$(echo "$R" | jq -r '.code')"
ORDER1=$(echo "$R" | jq -r '.data.id')
TOTAL1=$(echo "$R" | jq -r '.data.totalAmount')
check "订单金额=119.60" "119.60" "$TOTAL1"
STATUS1=$(echo "$R" | jq -r '.data.status')
check "初始状态=0待支付" "0" "$STATUS1"
check "下单预占 book1 (stock-2/lock+2)" "$((BS1-2))/$((BL1+2))" "$(stockrow 1)"
R2=$(curl -s -X POST $GW/orders -H "$OH" -H 'Content-Type: application/json' \
  -d "{\"bookId\":1,\"quantity\":2,\"receiverName\":\"张三\",\"receiverPhone\":\"13900001111\",\"receiverAddress\":\"上海市浦东新区世纪大道100号\",\"clientRequestId\":\"$CRID1\"}")
ORDER1B=$(echo "$R2" | jq -r '.data.id')
check "同 clientRequestId 幂等返回原订单" "$ORDER1" "$ORDER1B"
check "幂等重放后库存不变" "$((BS1-2))/$((BL1+2))" "$(stockrow 1)"

echo "===== 支付 + MQ 确认 ====="
R=$(curl -s -X POST $GW/payment/pay -H "$OH" -H 'Content-Type: application/json' -d "{\"orderId\":$ORDER1}")
check "模拟支付成功" 200 "$(echo "$R" | jq -r '.code')"
sleep 4
OST=$(q "SELECT status FROM t_order WHERE id=$ORDER1")
check "MQ 消费后订单状态=1已支付" "1" "$OST"
check "支付确认 book1 (stock不变/lock清零)" "$((BS1-2))/$BL1" "$(stockrow 1)"
PST=$(q "SELECT status FROM t_payment WHERE order_id=$ORDER1")
check "支付记录状态=已支付" "1" "$PST"
MQN=$(q "SELECT COUNT(*) FROM t_mq_consumed_log")
[ "$MQN" -ge 1 ] && { PASS=$((PASS+1)); echo "PASS  MQ 消费幂等日志已记录 ($MQN 条)"; } || { FAIL=$((FAIL+1)); echo "FAIL  t_mq_consumed_log 为空"; }
R=$(curl -s -X POST $GW/payment/pay -H "$OH" -H 'Content-Type: application/json' -d "{\"orderId\":$ORDER1}")
[ "$(echo "$R" | jq -r '.code')" = "200" ] && { PASS=$((PASS+1)); echo "PASS  重复支付幂等返回既有支付单(设计语义)"; } || { FAIL=$((FAIL+1)); echo "FAIL  重复支付未按幂等处理"; }
DUP=$(q "SELECT COUNT(*) FROM t_payment WHERE order_id=$ORDER1")
check "重复支付未产生新支付单" "1" "$DUP"
R=$(curl -s -X PUT $GW/orders/$ORDER1/complete -H "$OH")
check "订单完成接口" 200 "$(echo "$R" | jq -r '.code')"
CST=$(q "SELECT status FROM t_order WHERE id=$ORDER1")
check "完成后状态=3" "3" "$CST"

echo "===== 取消订单释放库存 ====="
BASE5=$(snap 5); BS5=${BASE5%%/*}; BL5=${BASE5##*/}
CRID2=$(uuid)
R=$(curl -s -X POST $GW/orders -H "$OH" -H 'Content-Type: application/json' \
  -d "{\"bookId\":5,\"quantity\":1,\"receiverName\":\"张三\",\"receiverPhone\":\"13900001111\",\"receiverAddress\":\"上海市浦东新区世纪大道100号\",\"clientRequestId\":\"$CRID2\"}")
ORDER2=$(echo "$R" | jq -r '.data.id')
check "book5 下单预占 (stock-1/lock+1)" "$((BS5-1))/$((BL5+1))" "$(stockrow 5)"
R=$(curl -s -X PUT $GW/orders/$ORDER2/cancel -H "$OH")
check "取消订单成功" 200 "$(echo "$R" | jq -r '.code')"
sleep 2
check "取消后 book5 完全还原" "$BS5/$BL5" "$(stockrow 5)"
CST=$(q "SELECT status FROM t_order WHERE id=$ORDER2")
check "取消后状态=2" "2" "$CST"

echo "===== 购物车下单 ====="
curl -s -X DELETE $GW/cart -H "$OH" > /dev/null
curl -s -X POST $GW/cart -H "$OH" -H 'Content-Type: application/json' -d '{"bookId":4,"quantity":1,"selected":true}' > /dev/null
curl -s -X POST $GW/cart -H "$OH" -H 'Content-Type: application/json' -d '{"bookId":5,"quantity":2,"selected":true}' > /dev/null
BASE4=$(snap 4); BS4=${BASE4%%/*}; BL4=${BASE4##*/}
BASE5=$(snap 5); BS5=${BASE5%%/*}; BL5=${BASE5##*/}
R=$(curl -s -X POST $GW/orders/from-cart -H "$OH" -H 'Content-Type: application/json' \
  -d "{\"receiverName\":\"张三\",\"receiverPhone\":\"13900001111\",\"receiverAddress\":\"上海市浦东新区世纪大道100号\",\"clientRequestId\":\"$(uuid)\"}")
check "购物车下单成功" 200 "$(echo "$R" | jq -r '.code')"
ORDER3=$(echo "$R" | jq -r '.data.id')
ITEMS=$(echo "$R" | jq -r '.data.items | length')
check "订单含2个商品项" "2" "$ITEMS"
TOTAL3=$(echo "$R" | jq -r '.data.totalAmount')
check "金额=224.00(88+68x2)" "224.00" "$TOTAL3"
check "book4 下单预占" "$((BS4-1))/$((BL4+1))" "$(stockrow 4)"
check "book5 下单预占 (x2)" "$((BS5-2))/$((BL5+2))" "$(stockrow 5)"
# 设计分工：购物车条目由前端下单成功后删除，后端保留
R=$(curl -s -X DELETE $GW/cart -H "$OH")
check "清理：后端清空购物车" 200 "$(echo "$R" | jq -r '.code')"
R=$(curl -s $GW/orders -H "$OH")
LISTN=$(echo "$R" | jq '.data | length' 2>/dev/null)
[ "$LISTN" -ge 3 ] && { PASS=$((PASS+1)); echo "PASS  订单列表返回 $LISTN 单"; } || { FAIL=$((FAIL+1)); echo "FAIL  订单列表数量=$LISTN"; }
R=$(curl -s $GW/orders/$ORDER3 -H "$OH")
check "订单详情可查" 200 "$(echo "$R" | jq -r '.code')"
SNAP=$(echo "$R" | jq -r '.data.items[0].bookTitle')
[ -n "$SNAP" ] && [ "$SNAP" != "null" ] && { PASS=$((PASS+1)); echo "PASS  商品快照书名=$SNAP"; } || { FAIL=$((FAIL+1)); echo "FAIL  商品快照缺失"; }
R=$(curl -s -X PUT $GW/orders/$ORDER3/cancel -H "$OH")
check "清理：取消购物车订单" 200 "$(echo "$R" | jq -r '.code')"

echo "===== 超时关单（兜底扫描，DB 把 expire_time 拨到过去）====="
BASE3=$(snap 3); BS3=${BASE3%%/*}; BL3=${BASE3##*/}
CRID4=$(uuid)
R=$(curl -s -X POST $GW/orders -H "$OH" -H 'Content-Type: application/json' \
  -d "{\"bookId\":3,\"quantity\":1,\"receiverName\":\"张三\",\"receiverPhone\":\"13900001111\",\"receiverAddress\":\"上海市浦东新区世纪大道100号\",\"clientRequestId\":\"$CRID4\"}")
ORDER4=$(echo "$R" | jq -r '.data.id')
check "超时测试单下单预占 book3" "$((BS3-1))/$((BL3+1))" "$(stockrow 3)"
q "UPDATE t_order SET expire_time = DATE_SUB(NOW(), INTERVAL 10 MINUTE) WHERE id=$ORDER4"
echo "      等待兜底扫描 cron（每2分钟）..."
CLOSED=""
for i in $(seq 1 40); do
  ST=$(q "SELECT status FROM t_order WHERE id=$ORDER4")
  if [ "$ST" = "2" ]; then CLOSED=yes; echo "      第 $((i*5)) 秒被兜底关单"; break; fi
  sleep 5
done
check "超时单被关单(状态=2)" "yes" "${CLOSED:-no}"
check "关单后 book3 完全还原" "$BS3/$BL3" "$(stockrow 3)"

echo "===== 库存查询接口反映变更 ====="
R=$(curl -s $GW/stock/1)
AV=$(echo "$R" | jq -r '.data.availableStock')
DBAV=$(q "SELECT stock FROM t_book_stock WHERE book_id=1")
check "API 库存与 DB 一致 (book1)" "$DBAV" "$AV"

echo
echo "===== 阶段2结果: PASS=$PASS FAIL=$FAIL ====="
[ $FAIL -gt 0 ] && echo -e "失败项:$FAILED_CASES"
