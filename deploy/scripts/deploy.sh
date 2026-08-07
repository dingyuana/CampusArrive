#!/usr/bin/env bash
# =============================================================================
# 微迎新 CampusArrive — 一键部署脚本
# 用法： ./deploy.sh <sit|uat|perf> [stack...]
#   stack 取值（可多选，默认全部）：
#     app      主编排 (mysql/redis/rabbitmq + 5 微服务)
#     maxkb    MaxKB + pgvector (服务器A)
#     debezium Debezium CDC (服务器B)
# 示例：
#   ./deploy.sh sit                  # SIT 环境拉起全部
#   ./deploy.sh uat app              # UAT 仅拉起应用栈
#   ./deploy.sh perf app maxkb       # PERF 拉起应用 + AI 栈
# =============================================================================
set -euo pipefail

# ---- 配置 ----
DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DEPLOY_DIR"

COMPOSE="docker compose"
ENV="${1:-}"
shift || true

# 默认拉起全部栈
STACKS=("${@:-app maxkb debezium}")
[[ ${#STACKS[@]} -eq 0 ]] && STACKS=(app maxkb debezium)

# 网络定义：campus-data 为 internal 网络
declare -a NETWORKS=(
    "campus-frontend"
    "campus-backend"
    "campus-data"
    "campus-ai"
)

# 颜色
if [[ -t 1 ]]; then
    C_GREEN=$'\033[1;32m'; C_RED=$'\033[1;31m'; C_YELLOW=$'\033[1;33m'
    C_CYAN=$'\033[1;36m'; C_BOLD=$'\033[1m'; C_NC=$'\033[0m'
else
    C_GREEN=''; C_RED=''; C_YELLOW=''; C_CYAN=''; C_BOLD=''; C_NC=''
fi
log()  { echo "${C_BOLD}[deploy]${C_NC} $*"; }
ok()   { echo "${C_GREEN}[ok]${C_NC} $*"; }
warn() { echo "${C_YELLOW}[warn]${C_NC} $*"; }
err()  { echo "${C_RED}[error]${C_NC} $*" >&2; }

# ---- 校验环境参数 ----
case "$ENV" in
    sit|uat|perf) ;;
    *)
        echo "用法: $0 <sit|uat|perf> [stack...]"
        echo "  stack: app | maxkb | debezium （默认全部）"
        exit 1
        ;;
esac

# ---- 校验 .env ----
ENV_FILE=".env"
[[ "$ENV" != "sit" ]] && ENV_FILE=".env.${ENV}"
if [[ ! -f "$ENV_FILE" ]]; then
    if [[ -f .env ]]; then
        warn "未找到 ${ENV_FILE}，复用当前 .env；建议执行 cp .env.example ${ENV_FILE} 并按 ${ENV} 调整。"
        ENV_FILE=".env"
    else
        err "未找到环境变量文件 ${ENV_FILE}，请先执行： cp .env.example ${ENV_FILE} 并填入真实值。"
        exit 1
    fi
fi
log "目标环境：${C_CYAN}${ENV}${C_NC}，环境文件：${ENV_FILE}"

# 项目名按环境隔离，避免多套环境容器名冲突
export COMPOSE_PROJECT_NAME="campusarrive-${ENV}"

# ---- 前置检查 ----
command -v docker >/dev/null || { err "未检测到 docker"; exit 1; }
$COMPOSE version >/dev/null 2>&1 || { err "未检测到 docker compose（需 v2）"; exit 1; }

# ---- 创建外部网络（campus-data 为 internal，禁止数据区出站）----
log "创建/确认网络分区..."
for net in "${NETWORKS[@]}"; do
    if ! docker network inspect "$net" >/dev/null 2>&1; then
        if [[ "$net" == "campus-data" ]]; then
            docker network create --internal "$net" >/dev/null
            ok "创建 internal 网络 ${net}（数据区隔离）"
        else
            docker network create "$net" >/dev/null
            ok "创建网络 ${net}"
        fi
    else
        warn "网络 ${net} 已存在，跳过"
    fi
done

# ---- 组装 compose 文件列表 ----
COMPOSE_FILES=("-f" "docker-compose.yml")
[[ "$ENV" == "sit" && -f "docker-compose.sit.yml" ]] && COMPOSE_FILES+=("-f" "docker-compose.sit.yml")

run_app() {
    log "拉起应用栈（MySQL/Redis/RabbitMQ + 5 微服务）..."
    $COMPOSE "${COMPOSE_FILES[@]}" --env-file "$ENV_FILE" up -d --build
}

run_maxkb() {
    log "拉起 AI 栈（MaxKB + PostgreSQL/pgvector）..."
    $COMPOSE -f docker-compose.maxkb.yml --env-file "$ENV_FILE" --project-name "campusarrive-${ENV}-maxkb" up -d
}

run_debezium() {
    log "拉起 CDC 栈（Zookeeper/Kafka/Debezium Connect）..."
    $COMPOSE -f docker-compose.debezium.yml --env-file "$ENV_FILE" --project-name "campusarrive-${ENV}-debezium" up -d
}

# ---- 按选择的栈拉起 ----
for stack in "${STACKS[@]}"; do
    case "$stack" in
        app)      run_app ;;
        maxkb)    run_maxkb ;;
        debezium) run_debezium ;;
        *) warn "未知 stack：${stack}，跳过" ;;
    esac
done

# ---- 等待健康检查通过 ----
log "等待服务就绪（最长 ~180s）..."
HEALTH_SCRIPT="${DEPLOY_DIR}/scripts/health-check.sh"
[[ -x "$HEALTH_SCRIPT" ]] || chmod +x "$HEALTH_SCRIPT"

DEADLINE=$(( $(date +%s) + 180 ))
READY=false
while [[ $(date +%s) -lt $DEADLINE ]]; do
    sleep 15
    # 仅校验应用栈 5 个微服务的 actuator/health
    down=0
    for pair in "gateway:8080:direct" \
                "checkin-service:8081:proxy" \
                "ai-service:8082:proxy" \
                "parent-service:8083:proxy" \
                "integration-service:8084:proxy"; do
        svc="${pair%%:*}"; rest="${pair#*:}"; port="${rest%%:*}"
        if [[ "$rest" == *direct ]]; then
            curl -fsS --max-time 5 "http://127.0.0.1:${port}/actuator/health" >/dev/null 2>&1 || down=$((down+1))
        else
            docker exec campus-gateway curl -fsS --max-time 5 "http://${svc}:${port}/actuator/health" >/dev/null 2>&1 || down=$((down+1))
        fi
    done
    if [[ "$down" -eq 0 ]]; then READY=true; break; fi
    echo -n "."
done
echo

# ---- 输出状态 ----
echo
if $READY; then
    ok "应用栈 5 个微服务全部健康"
else
    warn "部分服务未在限定时间内就绪，请运行 health-check.sh 查看详情"
fi

log "容器状态一览（${COMPOSE_PROJECT_NAME}）："
$COMPOSE "${COMPOSE_FILES[@]}" --env-file "$ENV_FILE" ps 2>/dev/null || true

echo
ok "部署流程完成。运行查看健康状态： ${C_CYAN}./scripts/health-check.sh${C_NC}"
ok "查看实时日志： ${C_CYAN}$COMPOSE -f docker-compose.yml logs -f${C_NC}"
