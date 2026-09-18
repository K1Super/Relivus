#!/usr/bin/env bash
# ============================================================
# Relivus 部署验证脚本（部署后一键验证主链路）
# 依赖：bash + curl + grep/sed（无需 jq）、部署机已按 README-RUN 启动服务。
# 用法：
#   ./scripts/verify-deploy.sh                      # 使用 /etc/relivus/relivus.env 的 TOKEN 与元库信息
#   BASE_URL=http://host:8080 ./scripts/verify-deploy.sh
# 目标库连接参数可用环境变量覆盖：
#   VERIFY_DB_HOST / VERIFY_DB_PORT / VERIFY_DB_NAME / VERIFY_DB_USER / VERIFY_DB_PASS / VERIFY_DB_TYPE
# 说明：脚本自行创建一次性连接 relivus-verify（已存在则先删除），验证完成保留供排查。
# ============================================================
set -uo pipefail

BASE_URL="${RELIVUS_BASE_URL:-http://127.0.0.1:8080}"

# ---------- 配置来源 ----------
if [ -n "${RELIVUS_TOKEN:-}" ]; then
  TOKEN="$RELIVUS_TOKEN"
elif [ -f /etc/relivus/relivus.env ]; then
  set -a; # shellcheck disable=SC1091
  source /etc/relivus/relivus.env
  set +a
  TOKEN="$RELIVUS_TOKEN"
else
  echo "[FAIL] 未找到 RELIVUS_TOKEN，请 export 或在 /etc/relivus/relivus.env 提供" >&2
  exit 1
fi

VERIFY_DB_HOST="${VERIFY_DB_HOST:-127.0.0.1}"
VERIFY_DB_PORT="${VERIFY_DB_PORT:-5432}"
VERIFY_DB_NAME="${VERIFY_DB_NAME:-relivus_demo}"
VERIFY_DB_USER="${VERIFY_DB_USER:-postgres}"
VERIFY_DB_PASS="${VERIFY_DB_PASS:-postgres}"
VERIFY_DB_TYPE="${VERIFY_DB_TYPE:-postgresql}"
CONN_NAME="relivus-verify"

AUTH="Authorization: Bearer $TOKEN"
CT="Content-Type: application/json"
PASS=0; FAIL=0

ok()   { PASS=$((PASS+1)); echo "[PASS] $1"; }
bad()  { FAIL=$((FAIL+1)); echo "[FAIL] $1"; }

# 提取 JSON 字段：json_get '{"code":0,"data":{"id":3}}' code -> 0 ；解析 data 子字段用 json_get "$DATA" id
json_get() {
  echo "$1" | grep -oE "\"$2\"[[:space:]]*:[[:space:]]*\"?[^,}]*" | head -1 | sed -E "s/.*:[[:space:]]*//" | tr -d '" '
}

req() { # $1=method $2=path $3=body(可选)
  local out
  if [ -n "${3:-}" ]; then
    out=$(curl -s -X "$1" "$BASE_URL$2" -H "$AUTH" -H "$CT" -d "$3")
  else
    out=$(curl -s -X "$1" "$BASE_URL$2" -H "$AUTH")
  fi
  echo "$out"
}

code_of() { json_get "$1" code; }

wait_task() { # $1=taskId
  local id=$1 status prog try
  for try in $(seq 1 120); do
    local body
    body=$(req GET "/api/tasks/$id")
    status=$(json_get "$body" status)
    prog=$(json_get "$body" progress)
    if [ "$status" = "SUCCESS" ]; then ok "任务 $id 完成 SUCCESS(progress=$prog)"; return 0; fi
    if [ "$status" = "FAILED" ]; then bad "任务 $id FAILED: $(json_get "$body" errorMessage)"; return 1; fi
    sleep 2
  done
  bad "任务 $id 超时（最后一次 status=$status progress=$prog）"
  return 1
}

# ---------- 1. 健康检查 ----------
HEALTH=$(curl -s "$BASE_URL/actuator/health")
case "$HEALTH" in *'"UP"'*) ok "健康检查 /actuator/health UP" ;; *) bad "健康检查失败: $HEALTH" ;; esac

# ---------- 2. 连接管理 ----------
LIST=$(req GET /api/connections)
if [ "$(code_of "$LIST")" = "0" ]; then ok "连接列表可访问" ; else bad "连接列表失败: $LIST"; fi
OLD_ID=$(echo "$LIST" | grep -oE "\"name\":\"$CONN_NAME\"[^}]*\"id\":[0-9]+" | grep -oE '"id": *[0-9]+' | head -1 | sed -E 's/.*: *//' | tr -d ' ')
if [ -n "$OLD_ID" ]; then
  req DELETE "/api/connections/$OLD_ID" > /dev/null
  ok "清理旧连接 $CONN_NAME(id=$OLD_ID)"
fi

CONN_BODY=$(printf '{"name":"%s","dbType":"%s","host":"%s","port":%s,"database":"%s","username":"%s","password":"%s"}' \
  "$CONN_NAME" "$VERIFY_DB_TYPE" "$VERIFY_DB_HOST" "$VERIFY_DB_PORT" "$VERIFY_DB_NAME" "$VERIFY_DB_USER" "$VERIFY_DB_PASS")
CONN_RES=$(req POST /api/connections "$CONN_BODY")
CONN_ID=$(json_get "$CONN_RES" id)
if [ -n "$CONN_ID" ] && [ "$(code_of "$CONN_RES")" = "0" ]; then ok "创建连接 relivus-verify id=$CONN_ID"; else bad "创建连接失败: $CONN_RES"; exit 1; fi

TEST_RES=$(req POST "/api/connections/$CONN_ID/test" "{}")
if [ "$(code_of "$TEST_RES")" = "0" ]; then ok "测试连接成功"; else bad "测试连接失败: $TEST_RES"; exit 1; fi

# ---------- 3. Schema 扫描 ----------
TABLES_RES=$(req GET "/api/schema/$CONN_ID/tables")
case "$TABLES_RES" in *"users"*) ok "表列表包含 users" ;; *) bad "表列表异常: $(echo "$TABLES_RES" | head -c 200)" ;; esac
DETAIL_RES=$(req GET "/api/schema/$CONN_ID/tables/users")
case "$DETAIL_RES" in *"email"*) ok "users 表详情含 email 列" ;; *) bad "表详情异常: $(echo "$DETAIL_RES" | head -c 200)" ;; esac

# ---------- 4. 数据生成（users 1000 + orders 2000，truncate前清空保证幂等） ----------
GEN_BODY='{"connectionId":'$CONN_ID',"truncateBefore":true,"batchSize":1000,"tables":[
  {"table":"users","rowCount":1000,"columns":{"email":{"generator":"faker","params":{"provider":"email"}},"name":{"generator":"faker","params":{"provider":"name"}}}},
  {"table":"orders","rowCount":2000,"columns":{}}]}'
GEN_RES=$(req POST /api/generation/execute "$GEN_BODY")
GEN_TASK=$(json_get "$GEN_RES" id)
if [ -n "$GEN_TASK" ] && [ "$(code_of "$GEN_RES")" = "0" ]; then ok "生成任务已提交 taskId=$GEN_TASK"; else bad "生成任务提交失败: $GEN_RES"; exit 1; fi
wait_task "$GEN_TASK" || exit 1

# ---------- 5. 脱敏（users.email/name 用 hmac 算法） ----------
MASK_BODY='{"connectionId":'$CONN_ID',"batchSize":1000,"tables":[
  {"table":"users","columns":{"email":{"algorithm":"hmac","params":{},"keyVersion":1},"name":{"algorithm":"hmac","params":{},"keyVersion":1}}}],
 "verifyTables":[{"table":"users","pkColumn":"id","joinKeyColumn":"id","whereClause":""}]}'
MASK_RES=$(req POST /api/masking/execute "$MASK_BODY")
MASK_TASK=$(json_get "$MASK_RES" id)
if [ -n "$MASK_TASK" ] && [ "$(code_of "$MASK_RES")" = "0" ]; then ok "脱敏任务已提交 taskId=$MASK_TASK"; else bad "脱敏任务提交失败: $MASK_RES"; exit 1; fi
wait_task "$MASK_TASK" || exit 1

# ---------- 6. JOIN 一致性验证（脱敏不改 JOIN 行集合，空 JOIN 行视为一致） ----------
VERIFY_BODY='{"connectionId":'$CONN_ID',"joinSql":"SELECT o.user_id FROM orders o WHERE o.user_id = ?","targetTable":"users","pkColumn":"id","joinKeyColumn":"user_id","whereClause":""}'
VRES=$(req POST /api/masking/verify "$VERIFY_BODY")
V_OK=$(json_get "$VRES" consistent)
if [ "$("$V_OK" | tr '[:upper:]' '[:lower:]')" = "true" ]; then ok "JOIN 一致性验证通过"; else bad "JOIN 一致性验证失败: $VRES"; fi

echo "=================================="
echo "结果: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ] || exit 1
echo "部署链路验证通过。"