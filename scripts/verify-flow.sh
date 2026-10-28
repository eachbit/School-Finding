#!/usr/bin/env bash

set -u
BASE=${BASE:-http://localhost:8080}
PASS=0
FAIL=0

api() {
  local method=$1 path=$2 token=$3 body=${4:-}
  if [ -n "$body" ]; then
    curl -s -X "$method" "$BASE$path" -H "Content-Type: application/json" \
      ${token:+-H "Authorization: $token"} -d "$body"
  else
    curl -s -X "$method" "$BASE$path" ${token:+-H "Authorization: $token"}
  fi
}

api_q() {
  local path=$1 token=$2
  shift 2
  local args=()
  for kv in "$@"; do args+=(--data-urlencode "$kv"); done
  curl -s -G "$BASE$path" -H "Authorization: $token" "${args[@]}"
}

check() {
  if echo "$2" | grep -q -- "$3"; then
    echo "  [PASS] $1"
    PASS=$((PASS + 1))
  else
    echo "  [FAIL] $1  (期望包含: $3)"
    echo "         实际: $(echo "$2" | head -c 400)"
    FAIL=$((FAIL + 1))
  fi
}

check_not() {
  if echo "$2" | grep -q -- "$3"; then
    echo "  [FAIL] $1  (不应包含: $3)"
    echo "         实际: $(echo "$2" | head -c 400)"
    FAIL=$((FAIL + 1))
  else
    echo "  [PASS] $1"
    PASS=$((PASS + 1))
  fi
}

json_id() { echo "$1" | sed -n 's/.*"id":\([0-9]*\),.*/\1/p' | head -1; }
json_token() { echo "$1" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p'; }

NOW3=$(date -d "-3 hour" "+%Y-%m-%d %H:%M:%S")
NOW5=$(date -d "-5 hour" "+%Y-%m-%d %H:%M:%S")

echo "==================== 0. 未登录访问应被拒绝 ===================="
R=$(api GET /api/items "" "")
check "未登录访问公开列表返回 401" "$R" '"code":401'

echo
echo "==================== 1. 登录 / 注册 ===================="
S1=$(api POST /api/auth/login "" '{"username":"student1","password":"123456"}')
check "student1 登录成功" "$S1" '"code":0'
T1=$(json_token "$S1")
S2=$(api POST /api/auth/login "" '{"username":"student2","password":"123456"}')
T2=$(json_token "$S2")
AD=$(api POST /api/auth/login "" '{"username":"admin","password":"123456"}')
TAD=$(json_token "$AD")
check "admin 登录成功且角色为 ADMIN" "$AD" '"role":"ADMIN"'
R=$(api POST /api/auth/login "" '{"username":"student1","password":"wrong"}')
check "错误密码被拒绝(401)" "$R" '"code":401'
R=$(api GET /api/me "$T1" "")
check "GET /api/me 返回当前用户" "$R" '"username":"student1"'
UNAME="student3_$(date +%H%M%S)"
R=$(api POST /api/auth/register "" "{\"username\":\"$UNAME\",\"password\":\"123456\",\"contact\":\"13800000003\"}")
check "注册新学生成功（注册后直接拿到 token）" "$R" '"role":"STUDENT"'
T3=$(json_token "$R")
R=$(api POST /api/auth/register "" "{\"username\":\"$UNAME\",\"password\":\"123456\"}")
check "重复用户名注册被拒绝" "$R" '"code":400'
R=$(api POST /api/auth/register "" '{"username":"ab","password":"123456"}')
check "用户名过短被参数校验拦下" "$R" '"code":400'

echo
echo "==================== 2. 发布（待审核，不进公开列表） ===================="
FOUND=$(api POST /api/items "$T2" "{\"type\":\"FOUND\",\"title\":\"黑色钱包\",\"description\":\"在第二食堂一楼餐桌上捡到一个黑色钱包，已交到食堂值班室。\",\"category\":\"钱包\",\"location\":\"第二食堂一楼\",\"happenTime\":\"$NOW3\",\"color\":\"黑色\",\"contact\":\"13800000002\",\"hiddenFeature\":\"钱包内有一张校园卡和一张照片\",\"verifyQuestion\":\"钱包里除了校园卡还有什么？\",\"verifyAnswer\":\"一张照片\"}")
check "student2 发布拾物，状态 PENDING" "$FOUND" '"status":"PENDING"'
FID=$(json_id "$FOUND")
echo "  拾物 id=$FID"
LOST=$(api POST /api/items "$T1" "{\"type\":\"LOST\",\"title\":\"丢失一个黑色钱包\",\"description\":\"在第二食堂一楼吃完饭把黑色钱包落在餐桌上了。\",\"category\":\"钱包\",\"location\":\"第二食堂一楼\",\"happenTime\":\"$NOW5\",\"color\":\"黑色\",\"contact\":\"13800000001\",\"hiddenFeature\":\"钱包内有校园卡和照片\",\"verifyQuestion\":\"钱包里有什么？\",\"verifyAnswer\":\"校园卡和照片\"}")
check "student1 发布失物，状态 PENDING" "$LOST" '"status":"PENDING"'
LID=$(json_id "$LOST")
echo "  失物 id=$LID"
R=$(api_q /api/items "$T2" "keyword=钱包")
check_not "待审核信息不出现在公开列表(拾物)" "$R" "\"id\":$FID,"
check_not "待审核信息不出现在公开列表(失物)" "$R" "\"id\":$LID,"
R=$(api POST /api/items "$T1" '{"type":"OTHER","title":"x","description":"x","category":"x","location":"x","happenTime":"2026-10-22 10:00:00","contact":"1"}')
check "非法 type 被参数校验拦下" "$R" '"code":400'

echo
echo "==================== 3. 管理员审核信息 ===================="
R=$(api GET "/api/admin/items?status=PENDING" "$TAD" "")
check "管理员在待审核列表看到拾物" "$R" "\"id\":$FID,"
check "管理员在待审核列表看到失物" "$R" "\"id\":$LID,"
R=$(api GET "/api/admin/items?status=PENDING" "$T2" "")
check "学生调管理员接口被拒绝(403)" "$R" '"code":403'
R=$(api POST "/api/admin/items/$FID/review" "$TAD" '{"pass":true,"opinion":"信息完整，通过"}')
check "拾物审核通过 -> PUBLIC" "$R" '"status":"PUBLIC"'
R=$(api POST "/api/admin/items/$LID/review" "$TAD" '{"pass":true,"opinion":"信息完整，通过"}')
check "失物审核通过 -> PUBLIC" "$R" '"status":"PUBLIC"'
R=$(api POST "/api/admin/items/$FID/review" "$T2" '{"pass":true,"opinion":"x"}')
check "学生执行审核被拒绝(403)" "$R" '"code":403'
R=$(api POST "/api/admin/items/$FID/review" "$TAD" '{"pass":true,"opinion":"再审一次"}')
check "重复审核被拒绝" "$R" '"code":400'
R=$(api POST "/api/admin/items/6/review" "$TAD" '{"pass":false,"opinion":""}')
check "驳回未填意见被拒绝" "$R" '"code":400'

echo
echo "==================== 4. 公开列表 / 详情脱敏 ===================="
R=$(api_q /api/items "$T2" "keyword=钱包")
check "审核通过后出现在公开列表" "$R" "\"id\":$FID,"
check "公开列表能按类别筛选" "$(api_q /api/items "$T2" "category=钱包")" "\"total\":2"
check "分页参数生效" "$(api_q /api/items "$T2" "page=1" "size=1")" '"size":1'
check_not "公开列表不返回联系方式" "$R" '"contact"'
check_not "公开列表不返回隐藏特征" "$R" '"hiddenFeature"'
check_not "公开列表不返回验证答案" "$R" '"verifyAnswer"'
R=$(api GET "/api/items/$FID" "$T3" "")
check "详情返回验证问题（供认领者作答）" "$R" '"verifyQuestion"'
check_not "详情不返回验证答案" "$R" '"verifyAnswer"'
check_not "详情不返回隐藏特征" "$R" '"hiddenFeature"'
check_not "详情不返回联系方式" "$R" '"contact"'
check "发布者本人能看到联系方式" "$(api GET "/api/items/$FID" "$T2" "")" '"contact"'
check "管理员能看到验证答案" "$(api GET "/api/items/$FID" "$TAD" "")" '"verifyAnswer"'

echo
echo "==================== 5. 规则匹配（失物 -> 拾物） ===================="
R=$(api GET "/api/items/$LID/matches" "$T1" "")
echo "  匹配结果: $(echo "$R" | head -c 600)"
check "匹配结果首条就是刚发布的拾物" "$R" "\"itemId\":$FID"
check "匹配分数 ≥ 60" "$R" '"score":100'
check "匹配理由包含类别相同" "$R" '类别相同'

echo
echo "==================== 6. 认领申请 ===================="
R=$(api POST "/api/items/$FID/claims" "$T2" '{"proof":"我自己发布的","answer":"一张照片"}')
check "不能认领自己发布的信息" "$R" '"code":400'
C1=$(api POST "/api/items/$FID/claims" "$T1" '{"proof":"钱包内有一张校园卡和一张照片，钱包边缘有磨损","answer":"一张照片"}')
check "student1 提交认领 -> PENDING" "$C1" '"status":"PENDING"'
CID1=$(json_id "$C1")
echo "  认领申请 id=$CID1"
R=$(api POST "/api/items/$FID/claims" "$T1" '{"proof":"再申请一次","answer":"一张照片"}')
check "同一用户重复申请被拒绝" "$R" '"code":400'
C3=$(api POST "/api/items/$FID/claims" "$T3" '{"proof":"我猜钱包里有一张照片","answer":"一张照片"}')
check "另一个学生也能申请同一物品" "$C3" '"status":"PENDING"'
CID3=$(json_id "$C3")
echo "  认领申请 id=$CID3"
check "我的申请列表包含自己的记录" "$(api GET /api/me/claims "$T1" "")" "\"id\":$CID1,"
check_not "我的申请列表不包含他人的申请" "$(api GET /api/me/claims "$T1" "")" "\"id\":$CID3,"

echo
echo "==================== 7. 管理员审核认领（同一物品只能一个通过） ===================="
R=$(api GET "/api/admin/claims/$CID1" "$TAD" "")
check "认领详情带出物品隐藏特征" "$R" '"itemHiddenFeature"'
check "认领详情带出验证参考答案" "$R" '"itemVerifyAnswer"'
R=$(api POST "/api/admin/claims/$CID1/review" "$T2" '{"pass":true,"opinion":"x"}')
check "学生调认领审核被拒绝(403)" "$R" '"code":403'
R=$(api POST "/api/admin/claims/$CID1/review" "$TAD" '{"pass":true,"opinion":"证明与隐藏特征一致，通过"}')
check "认领审核通过 -> APPROVED" "$R" '"status":"APPROVED"'
R=$(api POST "/api/admin/claims/$CID3/review" "$TAD" '{"pass":true,"opinion":"也想通过"}')
check "同一物品第二个申请不能通过" "$R" '"code":400'
R=$(api POST "/api/admin/claims/$CID1/review" "$TAD" '{"pass":true,"opinion":"重复"}')
check "重复审核同一申请被拒绝" "$R" '"code":400'
R=$(api POST "/api/admin/claims/$CID1/revoke" "$TAD" "")
check "撤销必须填写原因" "$R" '"code":400'
R=$(api POST "/api/admin/claims/$CID1/revoke?opinion=%E8%AF%81%E6%98%8E%E4%B8%8D%E8%B6%B3" "$TAD" "")
check "管理员撤销已通过申请 -> REVOKED" "$R" '"status":"REVOKED"'
R=$(api POST "/api/admin/claims/$CID3/review" "$TAD" '{"pass":true,"opinion":"前一个已撤销，本申请通过"}')
check "撤销后可以再通过另一个申请" "$R" '"status":"APPROVED"'

echo
echo "==================== 8. 交接确认 ===================="
R=$(api POST "/api/admin/claims/$CID3/handover" "$TAD" "")
check "交接成功，申请 -> HANDED" "$R" '"status":"HANDED"'
check "物品状态 -> CLAIMED" "$(api GET "/api/items/$FID" "$TAD" "")" '"status":"CLAIMED"'
LOGS_BEFORE=$(api GET "/api/admin/logs?limit=100" "$TAD" "")
R=$(api POST "/api/admin/claims/$CID3/handover" "$TAD" "")
check "重复交接被拒绝" "$R" '"code":400'
LOGS_AFTER=$(api GET "/api/admin/logs?limit=100" "$TAD" "")
B=$(echo "$LOGS_BEFORE" | grep -o '"id":' | wc -l)
A=$(echo "$LOGS_AFTER" | grep -o '"id":' | wc -l)
if [ "$B" = "$A" ]; then
  echo "  [PASS] 重复交接没有新增日志（$A 条，前后一致）"
  PASS=$((PASS + 1))
else
  echo "  [FAIL] 重复交接新增了日志（$B -> $A）"
  FAIL=$((FAIL + 1))
fi
R=$(api POST "/api/items/$FID/claims" "$T1" '{"proof":"物品已领取还想申请","answer":"一张照片"}')
check "已领取的物品不能再认领" "$R" '"code":400'
check_not "已领取的拾物不再出现在公开列表" "$(api_q /api/items "$T2" "keyword=钱包")" "\"id\":$FID,"

echo
echo "==================== 9. 关闭 / 重新发布 / 编辑重投 / 越权 ===================="
R=$(api POST "/api/items/$LID/close" "$T1" "")
check "公开信息可关闭 -> CLOSED" "$R" '"status":"CLOSED"'
check_not "已关闭的信息不在公开列表" "$(api_q /api/items "$T2" "keyword=钱包")" "\"id\":$LID,"
R=$(api POST "/api/items/$LID/reopen" "$T1" "")
check "关闭后重新发布 -> PENDING" "$R" '"status":"PENDING"'
R=$(api PUT "/api/items/6" "$T1" "{\"type\":\"LOST\",\"title\":\"钥匙串丢失三把钥匙\",\"description\":\"钥匙串上有一个红色小挂件，共三把钥匙，可能落在第二食堂二楼。\",\"category\":\"钥匙\",\"location\":\"第二食堂二楼\",\"happenTime\":\"$NOW5\",\"color\":\"银色\",\"contact\":\"13800000001\",\"hiddenFeature\":\"红色小挂件\"}")
check "已驳回的信息编辑后重新送审 -> PENDING" "$R" '"status":"PENDING"'
R=$(api PUT "/api/items/3" "$T1" "{\"type\":\"FOUND\",\"title\":\"x\",\"description\":\"x\",\"category\":\"x\",\"location\":\"x\",\"happenTime\":\"$NOW5\",\"contact\":\"1\"}")
check "非本人不能编辑他人信息(403)" "$R" '"code":403'
check "非本人不能关闭他人信息(403)" "$(api POST "/api/items/3/close" "$T1" "")" '"code":403'
R=$(api GET /api/items/4 "$T1" "")
check "未公开信息对非本人不可见(404)" "$R" '"code":404'

echo
echo "==================== 10. 统计与日志 ===================="
R=$(api GET /api/admin/statistics "$TAD" "")
echo "  统计: $R"
check "统计含公开拾物总数" "$R" '"publicFoundCount"'
check "统计含已领取拾物数且已 +1" "$R" '"claimedFoundCount":1'
check "统计含待审核申请数" "$R" '"pendingClaimCount"'
check "日志含交接记录" "$(api GET "/api/admin/logs?limit=10" "$TAD" "")" 'CLAIM_HANDOVER'
check "日志含信息审核记录" "$(api GET "/api/admin/logs?limit=10" "$TAD" "")" 'ITEM_REVIEW'
check "学生查日志被拒绝(403)" "$(api GET "/api/admin/logs?limit=10" "$T1" "")" '"code":403'
check "学生查统计被拒绝(403)" "$(api GET /api/admin/statistics "$T1" "")" '"code":403'

echo
echo "======================================================"
echo "  通过 $PASS 项，失败 $FAIL 项"
echo "======================================================"
[ "$FAIL" -eq 0 ]
