#!/usr/bin/env bash
# =============================================================================
# 微迎新 CampusArrive — 环境健康检查脚本
# 检查所有服务的 /actuator/health 端点 + 基础设施容器，输出带颜色的状态表格。
#
# 设计说明：
#   - 接入区 gateway (8080) 已映射宿主机端口，可直接 curl。
#   - 应用区微服务 (8081~8084) 仅在 campus-backend 内部网络，不映射宿主机端口，
#     故通过 `docker exec campus-gateway curl <服务名>:<端口>` 在内网探测。
#   - 基础设施 (mysql/redis/rabbitmq/maxkb/postgres/kafka/debezium) 读取其容器
#     healthcheck 状态（docker inspect），无需端口映射。
# 用法： ./health-check.sh [--watch]    # --watch 每 10s 刷新
# =============================================================================
set -uo pipefail

# ---- 颜色 ----
if [[ -t 1 ]]; then
    C_GREEN=$'\033[1;32m'; C_RED=$'\033[1;31m'; C_YELLOW=$'\033[1;33m'
    C_CYAN=$'\033[1;36m'; C_GRAY=$'\033[90m'; C_BOLD=$'\033[1m'; C_NC=$'\033[0m'
else
    C_GREEN=''; C_RED=''; C_YELLOW=''; C_CYAN=''; C_GRAY=''; C_BOLD=''; C_NC=''
fi

WATCH=false
[[ "${1:-}" == "--watch" ]] && WATCH=true

# 网关容器名（用于在内网代理探测内部微服务）
PROXY_CONTAINER="${PROXY_CONTAINER:-campus-gateway}"
CURL_TIMEOUT=5

# 微服务列表: "显示名|内网服务名|端口|区域"
MICROSERVICES=(
    "gateway|gateway|8080|接入区"
    "checkin-service|checkin-service|8081|应用区"
    "ai-service|ai-service|8082|应用区"
    "parent-service|parent-service|8083|应用区"
    "integration-service|integration-service|8084|应用区"
)

# 基础设施列表: "显示名|容器名"
INFRA=(
    "mysql|campus-mysql"
    "redis|campus-redis"
    "rabbitmq|campus-rabbitmq"
    "maxkb|campus-maxkb"
    "postgres-pgvector|campus-postgres-pgvector"
    "kafka|campus-kafka"
    "debezium-connect|campus-debezium-connect"
)

# 是否存在某容器（不报错）
container_exists() {
    docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "$1"
}

# 通过容器 healthcheck 状态判断
infra_status() {
    local name="$1"
    if ! container_exists "$name"; then
        echo "ABSENT"
        return
    fi
    local st
    st="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$name" 2>/dev/null)"
    case "$st" in
        healthy)   echo "UP" ;;
        starting)  echo "STARTING" ;;
        unhealthy) echo "DOWN" ;;
        none)      echo "RUNNING" ;;   # 无 healthcheck 但容器在运行
        *)         echo "UNKNOWN" ;;
    esac
}

# 探测微服务 actuator/health
micro_status() {
    local display="$1" svc="$2" port="$3" zone="$4"
    if [[ "$svc" == "gateway" ]]; then
        # 接入区：宿主机直连
        if curl -fsS --max-time "$CURL_TIMEOUT" "http://127.0.0.1:${port}/actuator/health" >/dev/null 2>&1; then
            echo "UP"
        else
            echo "DOWN"
        fi
    else
        # 应用区：经 gateway 容器在内网探测
        if ! container_exists "$PROXY_CONTAINER"; then
            echo "NO-PROXY"
            return
        fi
        if docker exec "$PROXY_CONTAINER" curl -fsS --max-time "$CURL_TIMEOUT" \
               "http://${svc}:${port}/actuator/health" >/dev/null 2>&1; then
            echo "UP"
        else
            echo "DOWN"
        fi
    fi
}

colorize() {
    case "$1" in
        UP)         echo "${C_GREEN}$1${C_NC}" ;;
        DOWN|ABSENT) echo "${C_RED}$1${C_NC}" ;;
        STARTING|NO-PROXY) echo "${C_YELLOW}$1${C_NC}" ;;
        *)          echo "${C_YELLOW}$1${C_NC}" ;;
    esac
}

print_report() {
    clear 2>/dev/null || true
    echo "${C_BOLD}微迎新 CampusArrive — 健康状态报告${C_NC}  ${C_GRAY}$(date '+%Y-%m-%d %H:%M:%S')${C_NC}"
    echo "${C_GRAY}$(printf '%.0s-' {1..70})${C_NC}"
    printf "${C_BOLD}%-22s %-8s %-10s %-12s %s${C_NC}\n" "SERVICE" "PORT" "ZONE" "STATUS" "DETAIL"
    echo "${C_GRAY}$(printf '%.0s-' {1..70})${C_NC}"

    local ok=0 fail=0
    # 微服务
    for entry in "${MICROSERVICES[@]}"; do
        IFS='|' read -r display svc port zone <<< "$entry"
        st="$(micro_status "$display" "$svc" "$port" "$zone")"
        detail=""
        [[ "$st" == "UP" ]] && ok=$((ok+1)) || fail=$((fail+1))
        printf "%-22s %-8s %-10s %s %s\n" "$display" "$port" "$zone" "$(colorize "$st")" "$detail"
    done

    echo "${C_GRAY}$(printf '%.0s-' {1..70})${C_NC}"
    # 基础设施
    for entry in "${INFRA[@]}"; do
        IFS='|' read -r display cname <<< "$entry"
        st="$(infra_status "$cname")"
        [[ "$st" == "UP" ]] && ok=$((ok+1)) || fail=$((fail+1))
        printf "%-22s %-8s %-10s %s\n" "$display" "-" "数据/AI区" "$(colorize "$st")"
    done

    echo "${C_GRAY}$(printf '%.0s-' {1..70})${C_NC}"
    if [[ "$fail" -eq 0 ]]; then
        echo "${C_GREEN}全部健康（${ok} 项）${C_NC}"
    else
        echo "${C_RED}异常 ${fail} 项${C_NC}，正常 ${C_GREEN}${ok}${C_NC} 项 — 详见上方 DOWN/STARTING 行"
    fi
}

if [[ "$WATCH" == true ]]; then
    while true; do print_report; sleep 10; done
else
    print_report
fi
