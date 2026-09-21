#!/bin/bash
# 功能验证第一阶段：auth / book / stock / cart（全部经网关 8080）
GW=http://localhost:8080/api
TS=$(date +%s)
USER="verify_${TS}"
PASS=0; FAIL=0; FAILED_CASES=""

check() { # name expected actual
  if [ "$2" = "$3" ]; then PASS=$((PASS+1)); echo "PASS  $1";
  else FAIL=$((FAIL+1)); FAILED_CASES="$FAILED_CASES\n  - $1 (期望=$2 实际=$3)"; echo "FAIL  $1 (期望=$2 实际=$3)"; fi
}
codeof() { echo "$1" | jq -r '.code'; }

echo "===== auth ====="
R=$(curl -s -X POST $GW/auth/register -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER\",\"password\":\"Test123456\",\"nickname\":\"验证用户\",\"phone\":\"1380000$TS\",\"email\":\"$USER@test.com\"}")
check "注册新用户" 200 "$(codeof "$R")"

R=$(curl -s -X POST $GW/auth/register -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER\",\"password\":\"Test123456\",\"nickname\":\"重复\",\"phone\":\"1380000$TS\",\"email\":\"$USER@test.com\"}")
[ "$(codeof "$R")" != "200" ] && { PASS=$((PASS+1)); echo "PASS  重复注册被拒绝 ($(echo "$R"|jq -r '.message'))"; } || { FAIL=$((FAIL+1)); echo "FAIL  重复注册未被拒绝"; }

R=$(curl -s -X POST $GW/auth/login -H 'Content-Type: application/json' -d "{\"username\":\"$USER\",\"password\":\"Test123456\"}")
check "正确密码登录" 200 "$(codeof "$R")"
TOKEN=$(echo "$R" | jq -r '.data.token // .data')
[ -n "$TOKEN" ] && [ "$TOKEN" != "null" ] && { PASS=$((PASS+1)); echo "PASS  拿到 JWT (${#TOKEN} chars)"; } || { FAIL=$((FAIL+1)); echo "FAIL  未拿到 JWT"; }

R=$(curl -s -X POST $GW/auth/login -H 'Content-Type: application/json' -d "{\"username\":\"$USER\",\"password\":\"wrong\"}")
[ "$(codeof "$R")" != "200" ] && { PASS=$((PASS+1)); echo "PASS  错误密码被拒绝 ($(echo "$R"|jq -r '.message'))"; } || { FAIL=$((FAIL+1)); echo "FAIL  错误密码竟然成功"; }

AH="Authorization: Bearer $TOKEN"
R=$(curl -s -X POST $GW/auth/addresses -H "$AH" -H 'Content-Type: application/json' \
  -d '{"receiverName":"张三","receiverPhone":"13900001111","province":"上海市","city":"上海市","district":"浦东新区","detailAddress":"世纪大道100号","isDefault":true}')
check "新增地址" 200 "$(codeof "$R")"
ADDR_ID=$(echo "$R" | jq -r '.data.id // .data')
R=$(curl -s $GW/auth/addresses -H "$AH")
check "地址列表可读" 200 "$(codeof "$R")"
CNT=$(echo "$R" | jq '.data | length')
check "地址列表条数=1" 1 "$CNT"
R=$(curl -s -X PUT $GW/auth/addresses/$ADDR_ID -H "$AH" -H 'Content-Type: application/json' \
  -d '{"receiverName":"李四","receiverPhone":"13900002222","province":"北京市","city":"北京市","district":"海淀区","detailAddress":"中关村大街1号","isDefault":false}')
check "修改地址" 200 "$(codeof "$R")"
R=$(curl -s -X PUT $GW/auth/addresses/$ADDR_ID/default -H "$AH")
check "设默认地址" 200 "$(codeof "$R")"
R=$(curl -s -X DELETE $GW/auth/addresses/$ADDR_ID -H "$AH")
check "删除地址" 200 "$(codeof "$R")"
R=$(curl -s $GW/auth/addresses -H "$AH")
CNT=$(echo "$R" | jq '.data | length')
check "删除后列表=0" 0 "$CNT"

echo "===== book ====="
R=$(curl -s "$GW/books?pageNum=1&pageSize=3")
check "图书分页" 200 "$(codeof "$R")"
TOTAL=$(echo "$R" | jq '.data.total // .data.totalCount // .data.records|length' 2>/dev/null)
echo "      分页返回 total/records: $(echo "$R" | jq -c '.data' | head -c 200)"
R=$(curl -s "$GW/books/categories")
check "分类列表" 200 "$(codeof "$R")"
CATS=$(echo "$R" | jq '.data | length')
[ "$CATS" -ge 5 ] && { PASS=$((PASS+1)); echo "PASS  分类数量=$CATS"; } || { FAIL=$((FAIL+1)); echo "FAIL  分类数量=$CATS"; }
R=$(curl -s "$GW/books/by-ids?ids=1,2,3")
check "按ID批量查书" 200 "$(codeof "$R")"
R=$(curl -s "$GW/books/1")
check "图书详情" 200 "$(codeof "$R")"
TITLE=$(echo "$R" | jq -r '.data.title')
check "详情书名=红楼梦" "红楼梦" "$TITLE"
R=$(curl -s "$GW/books/99999")
[ "$(codeof "$R")" != "200" ] && { PASS=$((PASS+1)); echo "PASS  不存在的书返回业务错误 ($(echo "$R"|jq -r '.message'))"; } || { FAIL=$((FAIL+1)); echo "FAIL  不存在的书竟然返回成功"; }

echo "===== stock ====="
R=$(curl -s "$GW/stock/1")
check "库存查询" 200 "$(codeof "$R")"
echo "      book1 库存: $(echo "$R" | jq -c '.data')"

echo "===== cart ====="
CH="Authorization: Bearer $TOKEN"
R=$(curl -s -X POST $GW/cart -H "$CH" -H 'Content-Type: application/json' -d '{"bookId":2,"quantity":2,"selected":true}')
check "加购 book2" 200 "$(codeof "$R")"
CART_ID=$(echo "$R" | jq -r '.data.id // .data')
R=$(curl -s -X POST $GW/cart -H "$CH" -H 'Content-Type: application/json' -d '{"bookId":3,"quantity":1,"selected":true}')
check "加购 book3" 200 "$(codeof "$R")"
R=$(curl -s -X POST $GW/cart -H "$CH" -H 'Content-Type: application/json' -d '{"bookId":2,"quantity":1,"selected":true}')
echo "      重复加购同书返回: $(echo "$R" | jq -c '.code // .')" | head -c 150; echo
R=$(curl -s $GW/cart -H "$CH")
CNT=$(echo "$R" | jq '.data | length')
check "购物车条数=2" 2 "$CNT"
R=$(curl -s -X PUT $GW/cart/$CART_ID -H "$CH" -H 'Content-Type: application/json' -d '{"quantity":5,"selected":true}')
check "改数量=5" 200 "$(codeof "$R")"
R=$(curl -s -X PUT $GW/cart/$CART_ID -H "$CH" -H 'Content-Type: application/json' -d '{"quantity":5,"selected":false}')
check "取消选中" 200 "$(codeof "$R")"
R=$(curl -s $GW/cart/selected -H "$CH")
SEL=$(echo "$R" | jq '.data | length')
check "选中列表只含1条" 1 "$SEL"
R=$(curl -s -X DELETE $GW/cart/$CART_ID -H "$CH")
check "删除单条购物车" 200 "$(codeof "$R")"
R=$(curl -s -X POST $GW/cart -H "$CH" -H 'Content-Type: application/json' -d '{"bookId":99999,"quantity":1,"selected":true}')
[ "$(codeof "$R")" != "200" ] && { PASS=$((PASS+1)); echo "PASS  加购不存在的书被 Feign 校验拒绝 ($(echo "$R"|jq -r '.message'))"; } || { FAIL=$((FAIL+1)); echo "FAIL  加购不存在的书未被拦截"; }
R=$(curl -s $GW/cart -H "$CH")
CART_ID2=$(echo "$R" | jq -r '.data[0].id')
R=$(curl -s -X DELETE $GW/cart -H "$CH")
check "清空购物车" 200 "$(codeof "$R")"
R=$(curl -s $GW/cart -H "$CH")
CNT=$(echo "$R" | jq '.data | length')
check "清空后条数=0" 0 "$CNT"
# 重新加购 2 件供 from-cart 测试用
curl -s -X POST $GW/cart -H "$CH" -H 'Content-Type: application/json' -d '{"bookId":4,"quantity":1,"selected":true}' >/dev/null
curl -s -X POST $GW/cart -H "$CH" -H 'Content-Type: application/json' -d '{"bookId":5,"quantity":2,"selected":true}' >/dev/null
R=$(curl -s $GW/cart -H "$CH")
CNT=$(echo "$R" | jq '.data | length')
check "供下单用的购物车=2条" 2 "$CNT"
echo "$TOKEN" > /tmp/inknexus_verify_token
echo
echo "===== 阶段1结果: PASS=$PASS FAIL=$FAIL ====="
[ $FAIL -gt 0 ] && echo -e "失败项:$FAILED_CASES"
