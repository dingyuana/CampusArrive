#!/usr/bin/env bash
# =============================================================================
# 微迎新 CampusArrive — MaxKB + DeepSeek 连通性验证脚本 (INFRA-1.3)
# 对应测试用例：
#   UT-INFRA-007  MaxKB 健康：API 可达且返回正常状态
#   UT-INFRA-008  DeepSeek 连通：发送测试 prompt 返回响应
#
# 功能：
#   1. 检查 MaxKB 容器健康状态（docker inspect healthcheck）
#   2. 检查 MaxKB Web UI 可达性（curl http://localhost:8088/）
#   3. 检查 PostgreSQL/pgvector 健康（pg_isready / healthcheck）
#   4. 检查 DeepSeek API 连通性（curl 发送测试 prompt）
#   5. 检查首词响应时间（流式请求测量首个 token 到达时间）
#   6. 输出彩色状态表格
#
# 用法：
#   ./verify-maxkb-deepseek.sh
#   ./verify-maxkb-deepseek.sh --deepseek-key sk-xxxx
#   ./verify-maxkb-deepseek.sh --deepseek-key sk-xxxx --model deepseek-v4-flash
#   ./verify-maxkb-deepseek.sh --json          # 输出 JSON 结果（供 CI 解析）
#
# 环境变量（可由 .env 自动加载）：
#   DEEPSEEK_API_KEY     DeepSeek API Key
#   DEEPSEEK_BASE_URL    DeepSeek API 地址（默认 https://api.deepseek.com）
#   DEEPSEEK_MODEL_FLASH 默认测试模型（默认 deepseek-v4-flash）
#   MAXKB_PORT           MaxKB 宿主机映射端口（默认 8088）
# =============================================================================
set -euo pipefail

# ---- 配置 ----
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${DEPLOY_DIR}/.env"

MAXKB_CONTAINER="${MAXKB_CONTAINER:-campus-maxkb}"
PG_CONTAINER="${PG_CONTAINER:-campus-postgres-pgvector}"
MAXKB_PORT="${MAXKB_PORT:-8088}"
DEEPSEEK_BASE_URL="${DEEPSEEK_BASE_URL:-https://api.deepseek.com}"
DEEPSEEK_MODEL="${DEEPSEEK_MODEL_FLASH:-deepseek-v4-flash}"
DEEPSEEK_API_KEY="${DEEPSEEK_API_KEY:-}"

# 解析命令行参数
JSON_OUTPUT=false
while [[ $# -gt 0 ]]; do
    case "$1" in
        --deepseek-key)
            DEEPSEEK_API_KEY="$2"; shift 2 ;;
        --model)
            DEEPSEEK_MODEL="$2"; shift 2 ;;
        --maxkb-port)
            MAXKB_PORT="$2"; shift 2 ;;
        --json)
            JSON_OUTPUT=true; shift ;;
        -h|--help)
            sed -n '2,/^# =\+/p' "$0" | sed 's/^# \?//' | head -n 30
            exit 0 ;;
        *)
            echo "未知参数: $1（使用 -h 查看帮助）" >&2
            exit 1 ;;
    esac
done

# 从 .env 加载未提供的 DeepSeek 凭据
# env_value: 从 .env 文件读取指定 key 的值（去掉首尾空白与配对引号）
env_value() {
    local key="$1" default="$2" val
    val="$(grep -E "^${key}=" "$ENV_FILE" 2>/dev/null | tail -n1 | cut -d= -f2- || true)"
    # 去掉首尾空白
    val="${val#"${val%%[![:space:]]*}"}"
    val="${val%"${val##*[![:space:]]}"}"
    # 去掉首尾配对双引号
    if [[ "$val" == \"*\" ]]; then val="${val#\"}"; val="${val%\"}"; fi
    # 去掉首尾配对单引号
    if [[ "$val" == \'*\' ]]; then val="${val#\'}"; val="${val%\'}"; fi
    echo "${val:-$default}"
}

if [[ -z "$DEEPSEEK_API_KEY" && -f "$ENV_FILE" ]]; then
    DEEPSEEK_API_KEY="$(env_value DEEPSEEK_API_KEY '')"
    DEEPSEEK_BASE_URL="$(env_value DEEPSEEK_BASE_URL 'https://api.deepseek.com')"
    DEEPSEEK_MODEL_FLASH="$(env_value DEEPSEEK_MODEL_FLASH 'deepseek-v4-flash')"
    MAXKB_PORT="$(env_value MAXKB_PORT '8088')"
    # 重新应用默认值（.env 可能未定义某些项）
    MAXKB_PORT="${MAXKB_PORT:-8088}"
    DEEPSEEK_BASE_URL="${DEEPSEEK_BASE_URL:-https://api.deepseek.com}"
    DEEPSEEK_MODEL="${DEEPSEEK_MODEL:-${DEEPSEEK_MODEL_FLASH:-deepseek-v4-flash}}"
fi

# ---- 颜色 ----
if [[ -t 1 && "$JSON_OUTPUT" == false ]]; then
    C_GREEN=$'\033[1;32m'; C_RED=$'\033[1;31m'; C_YELLOW=$'\033[1;33m'
    C_CYAN=$'\033[1;36m'; C_GRAY=$'\033[90m'; C_BOLD=$'\033[1m'; C_NC=$'\033[0m'
else
    C_GREEN=''; C_RED=''; C_YELLOW=''; C_CYAN=''; C_GRAY=''; C_BOLD=''; C_NC=''
fi

log()  { echo "${C_BOLD}[verify]${C_NC} $*"; }
ok()   { echo "${C_GREEN}[ok]${C_NC} $*"; }
warn() { echo "${C_YELLOW}[warn]${C_NC} $*"; }
err()  { echo "${C_RED}[error]${C_NC} $*" >&2; }

# ---- 工具函数 ----
container_exists() {
    docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "$1"
}

# 获取容器 healthcheck 状态
container_health() {
    if ! container_exists "$1"; then
        echo "ABSENT"
        return
    fi
    local st
    st="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$1" 2>/dev/null)"
    case "$st" in
        healthy)   echo "healthy" ;;
        starting)  echo "starting" ;;
        unhealthy) echo "unhealthy" ;;
        none)      echo "running" ;;
        *)         echo "unknown" ;;
    esac
}

colorize() {
    case "$1" in
        PASS|healthy|UP)        echo "${C_GREEN}$1${C_NC}" ;;
        FAIL|unhealthy|DOWN|ABSENT) echo "${C_RED}$1${C_NC}" ;;
        SKIP|WARN|starting)    echo "${C_YELLOW}$1${C_NC}" ;;
        *)                      echo "${C_YELLOW}$1${C_NC}" ;;
    esac
}

# ---- 结果收集 ----
declare -a RESULTS=()
TOTAL_PASS=0
TOTAL_FAIL=0
TOTAL_SKIP=0

add_result() {
    # 参数: id | 名称 | 状态 | 详情
    RESULTS+=("$1|$2|$3|$4")
    case "$3" in
        PASS) TOTAL_PASS=$((TOTAL_PASS+1)) ;;
        FAIL) TOTAL_FAIL=$((TOTAL_FAIL+1)) ;;
        SKIP|WARN) TOTAL_SKIP=$((TOTAL_SKIP+1)) ;;
    esac
}

# =============================================================================
# 检查 1：MaxKB 容器健康状态 (UT-INFRA-007)
# =============================================================================
check_maxkb_container() {
    local id="UT-INFRA-007"
    local name="MaxKB 容器健康状态"
    local st
    st="$(container_health "$MAXKB_CONTAINER")"
    case "$st" in
        healthy)
            add_result "$id" "$name" "PASS" "healthcheck=healthy" ;;
        starting)
            add_result "$id" "$name" "WARN" "healthcheck=starting（等待中）" ;;
        unhealthy)
            add_result "$id" "$name" "FAIL" "healthcheck=unhealthy" ;;
        running)
            add_result "$id" "$name" "WARN" "容器运行中但无 healthcheck" ;;
        ABSENT)
            add_result "$id" "$name" "FAIL" "容器 ${MAXKB_CONTAINER} 不存在" ;;
        *)
            add_result "$id" "$name" "FAIL" "状态未知: $st" ;;
    esac
}

# =============================================================================
# 检查 2：MaxKB Web UI 可达性 (UT-INFRA-007)
# =============================================================================
check_maxkb_webui() {
    local id="UT-INFRA-007"
    local name="MaxKB Web UI 可达性"
    local http_code
    http_code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "http://127.0.0.1:${MAXKB_PORT}/" 2>/dev/null || echo "000")"
    if [[ "$http_code" =~ ^(200|302|301)$ ]]; then
        add_result "$id" "$name" "PASS" "HTTP ${http_code} @ 127.0.0.1:${MAXKB_PORT}"
    elif [[ "$http_code" == "000" ]]; then
        add_result "$id" "$name" "FAIL" "无法连接 127.0.0.1:${MAXKB_PORT}（超时/拒绝）"
    else
        add_result "$id" "$name" "FAIL" "HTTP ${http_code} @ 127.0.0.1:${MAXKB_PORT}"
    fi
}

# =============================================================================
# 检查 3：PostgreSQL/pgvector 健康
# =============================================================================
check_pgvector() {
    local id="UT-INFRA-007"
    local name="PostgreSQL/pgvector 健康"
    local st
    st="$(container_health "$PG_CONTAINER")"
    case "$st" in
        healthy)
            add_result "$id" "$name" "PASS" "healthcheck=healthy" ;;
        starting)
            add_result "$id" "$name" "WARN" "healthcheck=starting" ;;
        unhealthy)
            add_result "$id" "$name" "FAIL" "healthcheck=unhealthy" ;;
        running)
            # 容器运行但无 healthcheck，尝试 pg_isready
            if docker exec "$PG_CONTAINER" pg_isready -U maxkb -d maxkb >/dev/null 2>&1; then
                add_result "$id" "$name" "PASS" "pg_isready=ok"
            else
                add_result "$id" "$name" "FAIL" "pg_isready 失败"
            fi ;;
        ABSENT)
            add_result "$id" "$name" "FAIL" "容器 ${PG_CONTAINER} 不存在" ;;
        *)
            add_result "$id" "$name" "FAIL" "状态未知: $st" ;;
    esac
}

# =============================================================================
# 检查 4：DeepSeek API 连通性 (UT-INFRA-008)
# 发送测试 prompt，校验响应正常
# =============================================================================
check_deepseek_connectivity() {
    local id="UT-INFRA-008"
    local name="DeepSeek API 连通性"
    if [[ -z "$DEEPSEEK_API_KEY" ]]; then
        add_result "$id" "$name" "SKIP" "未提供 DEEPSEEK_API_KEY（用 --deepseek-key 指定或写入 .env）"
        return
    fi
    local resp http_code body
    # 发送非流式测试 prompt（ping），验证鉴权与连通
    resp="$(curl -s -w '\n%{http_code}' --max-time 30 \
        "${DEEPSEEK_BASE_URL}/chat/completions" \
        -H "Authorization: Bearer ${DEEPSEEK_API_KEY}" \
        -H "Content-Type: application/json" \
        -d "{\"model\":\"${DEEPSEEK_MODEL}\",\"messages\":[{\"role\":\"user\",\"content\":\"ping\"}],\"max_tokens\":5}" 2>/dev/null || echo "ERR")"
    http_code="$(echo "$resp" | tail -n1)"
    body="$(echo "$resp" | sed '$d')"
    if [[ "$http_code" == "200" ]]; then
        # 提取回复内容片段作为证据
        local snippet
        snippet="$(echo "$body" | grep -oE '"content"\s*:\s*"[^"]*"' | head -n1 | cut -d'"' -f4 || echo "(已收到响应)")"
        add_result "$id" "$name" "PASS" "HTTP 200，模型=${DEEPSEEK_MODEL}，回复: ${snippet}"
    elif [[ "$http_code" == "ERR" ]]; then
        add_result "$id" "$name" "FAIL" "请求超时/网络错误（${DEEPSEEK_BASE_URL}）"
    else
        local errmsg
        errmsg="$(echo "$body" | grep -oE '"message"\s*:\s*"[^"]*"' | head -n1 | cut -d'"' -f4 || echo "$body")"
        add_result "$id" "$name" "FAIL" "HTTP ${http_code}: ${errmsg}"
    fi
}

# =============================================================================
# 检查 5：首词响应时间 (TTFT) (UT-INFRA-008)
# 流式请求测量首个 token 到达时间
# =============================================================================
check_deepseek_ttft() {
    local id="UT-INFRA-008"
    local name="DeepSeek 首词响应时间 (TTFT)"
    if [[ -z "$DEEPSEEK_API_KEY" ]]; then
        add_result "$id" "$name" "SKIP" "未提供 DEEPSEEK_API_KEY"
        return
    fi
    local tmp errfile
    tmp="$(mktemp)"
    errfile="$(mktemp)"
    # 流式请求，记录首 chunk 到达时间
    local start_ts end_ts first_chunk
    start_ts="$(date +%s.%N)"
    # -N 关闭缓冲，逐字节读取首个 data: 行
    curl -s -N --max-time 30 \
        "${DEEPSEEK_BASE_URL}/chat/completions" \
        -H "Authorization: Bearer ${DEEPSEEK_API_KEY}" \
        -H "Content-Type: application/json" \
        -d "{\"model\":\"${DEEPSEEK_MODEL}\",\"stream\":true,\"messages\":[{\"role\":\"user\",\"content\":\"你好\"}],\"max_tokens\":20}" \
        2>"$errfile" | { head -n 20; cat >/dev/null; } > "$tmp" || true
    end_ts="$(date +%s.%N)"
    # 找到第一个非空 data: 行（包含 content 的 chunk）
    first_chunk="$(grep -m1 '^data:.*"delta"' "$tmp" 2>/dev/null || echo "")"
    if [[ -z "$first_chunk" ]]; then
        add_result "$id" "$name" "FAIL" "未收到流式响应 chunk"
        rm -f "$tmp" "$errfile"
        return
    fi
    local elapsed_ms
    elapsed_ms="$(awk -v s="$start_ts" -v e="$end_ts" 'BEGIN{printf "%.0f", (e-s)*1000}')"
    # 提取首个 token 内容
    local first_token
    first_token="$(echo "$first_chunk" | grep -oE '"content"\s*:\s*"[^"]*"' | head -n1 | cut -d'"' -f4 || echo "")"
    local verdict="PASS"
    # TTFT 阈值 3000ms（校园网至 DeepSeek API，经验值 <2s）
    if [[ "$elapsed_ms" -gt 3000 ]]; then
        verdict="WARN"
    fi
    add_result "$id" "$name" "$verdict" "TTFT=${elapsed_ms}ms，首词=\"${first_token}\"（阈值 3000ms）"
    rm -f "$tmp" "$errfile"
}

# =============================================================================
# 输出报告
# =============================================================================
print_report() {
    if [[ "$JSON_OUTPUT" == true ]]; then
        print_json
        return
    fi
    echo
    echo "${C_BOLD}微迎新 CampusArrive — MaxKB + DeepSeek 连通性验证报告${C_NC}  ${C_GRAY}$(date '+%Y-%m-%d %H:%M:%S')${C_NC}"
    echo "${C_GRAY}$(printf '%.0s-' {1..90})${C_NC}"
    printf "${C_BOLD}%-14s %-28s %-8s %s${C_NC}\n" "UT 编号" "检查项" "状态" "详情"
    echo "${C_GRAY}$(printf '%.0s-' {1..90})${C_NC}"
    for entry in "${RESULTS[@]}"; do
        IFS='|' read -r rid rname rstatus rdetail <<< "$entry"
        printf "%-14s %-28s %-8s %s\n" "$rid" "$rname" "$(colorize "$rstatus")" "$rdetail"
    done
    echo "${C_GRAY}$(printf '%.0s-' {1..90})${C_NC}"
    echo "${C_BOLD}汇总：${C_NC}${C_GREEN}通过 ${TOTAL_PASS}${C_NC}  ${C_RED}失败 ${TOTAL_FAIL}${C_NC}  ${C_YELLOW}跳过/警告 ${TOTAL_SKIP}${C_NC}"
    echo
    if [[ "$TOTAL_FAIL" -gt 0 ]]; then
        err "存在 ${TOTAL_FAIL} 项失败，请根据详情排查。"
        exit 1
    fi
    ok "全部关键检查通过（UT-INFRA-007 / UT-INFRA-008）。"
}

print_json() {
    # 输出 JSON 数组，便于 CI / 流水线解析
    echo "{"
    echo "  \"timestamp\": \"$(date -Iseconds)\","
    echo "  \"summary\": {\"pass\": ${TOTAL_PASS}, \"fail\": ${TOTAL_FAIL}, \"skip\": ${TOTAL_SKIP}},"
    echo "  \"checks\": ["
    local first=true
    for entry in "${RESULTS[@]}"; do
        IFS='|' read -r rid rname rstatus rdetail <<< "$entry"
        # 转义详情中的双引号与反斜杠
        rdetail="${rdetail//\\/\\\\}"
        rdetail="${rdetail//\"/\\\"}"
        if [[ "$first" == true ]]; then first=false; else echo ","; fi
        printf '    {"id": "%s", "name": "%s", "status": "%s", "detail": "%s"}' "$rid" "$rname" "$rstatus" "$rdetail"
    done
    echo ""
    echo "  ]"
    echo "}"
}

# =============================================================================
# 主流程
# =============================================================================
log "开始 MaxKB + DeepSeek 连通性验证..."
log "MaxKB 端口=${MAXKB_PORT}，DeepSeek 模型=${DEEPSEEK_MODEL}，API 地址=${DEEPSEEK_BASE_URL}"

check_maxkb_container
check_maxkb_webui
check_pgvector
check_deepseek_connectivity
check_deepseek_ttft

print_report

if [[ "$TOTAL_FAIL" -gt 0 ]]; then
    exit 1
fi
exit 0
