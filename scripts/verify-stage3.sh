#!/bin/bash
# 功能验证第三阶段：网关安全（401/403/防伪造）+ 管理员图书 CRUD + 缓存防护 + TraceId
GW=http://localhost:8080/api
TOKEN=$(cat /tmp/inknexus_verify_token)
UH="Authorization: Bearer $TOKEN"
PASS=0; FAIL=0; FAILED_CASES=""
check() { if [ "$2" = "$3" ]; then PASS=$((PASS+1)); echo "PASS  $1"; else FAIL=$((FAIL+1)); FAILED_CASES="$FAILED_CASES\n  - $1 (期望=$2 实际=$3)"; echo "FAIL  $1 (期望=$2 实际=$3)"; fi }
q() { mysql -h127.0.0.1 -uroot -p123456 inknexus -N -e "$1" 2>/dev/null; }

echo "===== 网关安全 ====="
C=$(curl -s -o /dev/null -w '%{http_code}' -X POST $GW/cart -H 'Content-Type: application/json' -d '{"bookId":1,"quantity":1}')
check "无 token 加购 → 401" 401 "$C"
C=$(curl -s -o /dev/null -w '%{http_code}' $GW/orders)
check "无 token 查订单 → 401" 401 "$C"
C=$(curl -s -o /dev/null -w '%{http_code}' $GW/orders -H "Authorization: Bearer invalid.jwt.token")
check "伪造 JWT → 401" 401 "$C"
MYID=$(q "SELECT id FROM t_user WHERE username LIKE 'verify_%' ORDER BY id DESC LIMIT 1")
R=$(curl -s -X POST $GW/orders -H "$UH" -H "Content-Type: application/json" -H "X-User-Id: 1" -H "X-User-Role: ADMIN" \
  -d "{\"bookId\":2,\"quantity\":1,\"receiverName\":\"攻\",\"receiverPhone\":\"1\",\"receiverAddress\":\"x\",\"clientRequestId\":\"forge-$RANDOM\"}")
OID=$(echo "$R" | jq -r '.data.id // empty')
DBUID=$(q "SELECT user_id FROM t_order WHERE id=$OID")
check "伪造 X-User-Id:1/X-User-Role:ADMIN 被网关覆盖(订单归属真实用户$MYID)" "$MYID" "$DBUID"
R=$(curl -s -X POST $GW/books -H "$UH" -H 'Content-Type: application/json' -d '{"title":"越权测试","author":"x","price":1,"categoryId":1}')
check "USER 角色建书 → 403" 403 "$(echo "$R" | jq -r '.code')"

echo "===== /api/stock/deduct 内部端点封禁 ====="
R=$(curl -s -X POST $GW/stock/deduct -H "$UH" -H 'Content-Type: application/json' -d '{"items":[{"bookId":2,"quantity":1}]}')
check "USER token 调内部扣库存 → 403" 403 "$(echo "$R" | jq -r '.code')"
R=$(curl -s -o /dev/null -w '%{http_code}' -X POST $GW/stock/deduct -H 'Content-Type: application/json' -d '{"items":[{"bookId":2,"quantity":1}]}')
check "无 token 调内部扣库存 → 403" 403 "$R"

echo "===== 管理员图书 CRUD ====="
q "UPDATE t_user SET role='ADMIN' WHERE id=$MYID"
R=$(curl -s -X POST $GW/auth/login -H 'Content-Type: application/json' -d '{"username":"'"$(q "SELECT username FROM t_user WHERE id=$MYID")"'","password":"Test123456"}')
ATOKEN=$(echo "$R" | jq -r '.data.token // .data')
AH="Authorization: Bearer $ATOKEN"
R=$(curl -s -X POST $GW/books -H "$AH" -H 'Content-Type: application/json' -d '{"title":"验证之书","author":"验证员","price":42.00,"categoryId":2,"description":"自动化验证创建"}')
check "ADMIN 建书" 200 "$(echo "$R" | jq -r '.code')"
BID=$(echo "$R" | jq -r '.data.id // .data')
R=$(curl -s $GW/books/$BID)
check "新书详情可查" 200 "$(echo "$R" | jq -r '.code')"
check "新书标题正确" "验证之书" "$(echo "$R" | jq -r '.data.title')"
R=$(curl -s -X PUT $GW/books/$BID -H "$AH" -H 'Content-Type: application/json' -d '{"title":"验证之书V2","author":"验证员","price":45.00,"categoryId":2,"description":"改","status":1}')
check "ADMIN 改书" 200 "$(echo "$R" | jq -r '.code')"
R=$(curl -s $GW/books/$BID)
check "改后价格=45.00" "45.00" "$(echo "$R" | jq -r '.data.price')"
R=$(curl -s -X DELETE $GW/books/$BID -H "$AH")
check "ADMIN 删书" 200 "$(echo "$R" | jq -r '.code')"
R=$(curl -s $GW/books/$BID)
[ "$(echo "$R" | jq -r '.code')" != "200" ] && { PASS=$((PASS+1)); echo "PASS  删后再查返回业务错误 ($(echo "$R"|jq -r '.message'))"; } || { FAIL=$((FAIL+1)); echo "FAIL  删除后书仍可查"; }

echo "===== 缓存防护 ====="
# 先删键再查详情，确保测到的是本次互斥重建的 TTL（键已存在时命中缓存不会续期）
docker exec redis redis-cli DEL cache:book:detail:2 > /dev/null
curl -s $GW/books/2 > /dev/null
V=$(docker exec redis redis-cli GET cache:book:detail:2)
[ ${#V} -gt 10 ] && { PASS=$((PASS+1)); echo "PASS  详情缓存已写入 (${#V} bytes)"; } || { FAIL=$((FAIL+1)); echo "FAIL  详情缓存缺失"; }
TTL=$(docker exec redis redis-cli TTL cache:book:detail:2)
echo "      缓存 TTL=${TTL}s（基准 30min，抖动 ±10% 即 [1620,1980]）"
[ "$TTL" -ge 1620 ] && [ "$TTL" -le 1980 ] && { PASS=$((PASS+1)); echo "PASS  TTL 抖动在 ±10% 区间"; } || { FAIL=$((FAIL+1)); echo "FAIL  TTL=${TTL}s 超出区间"; }
R=$(curl -s $GW/books/99998)
[ "$(echo "$R" | jq -r '.code')" != "200" ] && { PASS=$((PASS+1)); echo "PASS  查不存在书籍返回错误"; } || { FAIL=$((FAIL+1)); echo "FAIL  不存在的书返回成功"; }
NM=$(docker exec redis redis-cli GET cache:book:detail:99998)
[ "$NM" = "__NULL__" ] && { PASS=$((PASS+1)); echo "PASS  空值标记已写入(防穿透)"; } || { FAIL=$((FAIL+1)); echo "FAIL  空值标记缺失，实际=[$NM]"; }

echo "===== TraceId 链路 ====="
# 删键制造缓存未命中：互斥重建会打日志，日志行携带请求线程的 MDC traceId，断言才可确定
docker exec redis redis-cli DEL cache:book:detail:2 > /dev/null
TID="verify-trace-$RANDOM$RANDOM"
curl -s -H "X-Trace-Id: $TID" $GW/books/2 > /dev/null
sleep 1
HIT=$(grep -c "$TID" /Users/ibupro/workspace/workspace_idea/InkNexus/logs/book.log 2>/dev/null)
if [ "$HIT" -ge 1 ]; then PASS=$((PASS+1)); echo "PASS  外部 TraceId 透传到 book 日志 ($TID)"; else FAIL=$((FAIL+1)); echo "FAIL  TraceId 未透传"; fi

echo
echo "===== 阶段3结果: PASS=$PASS FAIL=$FAIL ====="
[ $FAIL -gt 0 ] && echo -e "失败项:$FAILED_CASES"
echo "$ATOKEN" > /tmp/inknexus_verify_admin_token
