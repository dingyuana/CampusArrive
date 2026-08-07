#!/usr/bin/env bash
# =============================================================================
# 微迎新 CampusArrive — DeepSeek API 防火墙白名单配置脚本 (INFRA-1.3)
#
# 目标：配置 iptables 规则，使 MaxKB 容器所在网段仅能出站到 DeepSeek API，
#       拒绝其他所有外网请求（数据不出校原则，对应 UT-INFRA-009）。
#
# 原理：
#   1. 解析 api.deepseek.com 的 IP 地址
#   2. 获取 campus-ai Docker 网络的子网（MaxKB 容器所在网段）
#   3. 在自定义链 CAMPUS_MAXKB_EGRESS 中配置规则：
#      - ACCEPT：campus-ai 子网 -> DeepSeek API IP:443
#      - ACCEPT：campus-ai 子网 -> 内网网段（RFC1918，容器间通信）
#      - DROP  ：campus-ai 子网 -> 其他所有外网
#   4. 将自定义链挂载到 FORWARD 链
#
# 安全说明：
#   - 需要 root 权限运行 iptables
#   - 规则使用自定义链，remove 时可干净卸载，不影响现有 Docker 规则
#   - 规则非持久化，重启后失效；如需持久化请配合 iptables-persistent 或
#     将 add 操作加入系统启动脚本
#
# 用法：
#   sudo ./deepseek-egress-firewall.sh add       # 添加白名单规则
#   sudo ./deepseek-egress-firewall.sh remove    # 移除白名单规则
#   sudo ./deepseek-egress-firewall.sh status     # 查看当前规则状态
#   sudo ./deepseek-egress-firewall.sh test       # 测试连通性（不修改规则）
# =============================================================================
set -euo pipefail

# ---- 配置 ----
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${DEPLOY_DIR}/.env"

CHAIN_NAME="CAMPUS_MAXKB_EGRESS"
DEEPSEEK_DOMAIN="${DEEPSEEK_BASE_URL:-https://api.deepseek.com}"
DEEPSEEK_DOMAIN="$(echo "$DEEPSEEK_DOMAIN" | sed -E 's|^https?://||; s|/.*$||; s|:.*$||')"
DEEPSEEK_DOMAIN="${DEEPSEEK_DOMAIN:-api.deepseek.com}"
CAMPUS_AI_NETWORK="${CAMPUS_AI_NETWORK:-campus-ai}"

# 从 .env 读取配置
env_value() {
    local key="$1" default="$2" val
    val="$(grep -E "^${key}=" "$ENV_FILE" 2>/dev/null | tail -n1 | cut -d= -f2- || true)"
    val="${val#"${val%%[![:space:]]*}"}"
    val="${val%"${val##*[![:space:]]}"}"
    if [[ "$val" == \"*\" ]]; then val="${val#\"}"; val="${val%\"}"; fi
    if [[ "$val" == \'*\' ]]; then val="${val#\'}"; val="${val%\'}"; fi
    echo "${val:-$default}"
}
if [[ -f "$ENV_FILE" ]]; then
    DEEPSEEK_BASE_URL="$(env_value DEEPSEEK_BASE_URL 'https://api.deepseek.com')"
    DEEPSEEK_DOMAIN="$(echo "$DEEPSEEK_BASE_URL" | sed -E 's|^https?://||; s|/.*$||; s|:.*$||')"
    DEEPSEEK_DOMAIN="${DEEPSEEK_DOMAIN:-api.deepseek.com}"
fi

ACTION="${1:-}"

# ---- 颜色 ----
if [[ -t 1 ]]; then
    C_GREEN=$'\033[1;32m'; C_RED=$'\033[1;31m'; C_YELLOW=$'\033[1;33m'
    C_CYAN=$'\033[1;36m'; C_GRAY=$'\033[90m'; C_BOLD=$'\033[1m'; C_NC=$'\033[0m'
else
    C_GREEN=''; C_RED=''; C_YELLOW=''; C_CYAN=''; C_GRAY=''; C_BOLD=''; C_NC=''
fi

log()  { echo "${C_BOLD}[firewall]${C_NC} $*"; }
ok()   { echo "${C_GREEN}[ok]${C_NC} $*"; }
warn() { echo "${C_YELLOW}[warn]${C_NC} $*"; }
err()  { echo "${C_RED}[error]${C_NC} $*" >&2; }

# ---- 用法 ----
usage() {
    echo "用法: $0 {add|remove|status|test}"
    echo
    echo "  add      添加 DeepSeek API 出站白名单规则（仅允许 campus-ai 网段访问 DeepSeek API:443）"
    echo "  remove   移除白名单规则（恢复默认放行）"
    echo "  status   查看当前规则状态"
    echo "  test     测试 DeepSeek API 连通性（不修改规则）"
    echo
    echo "环境变量："
    echo "  DEEPSEEK_BASE_URL     DeepSeek API 地址（默认 https://api.deepseek.com）"
    echo "  CAMPUS_AI_NETWORK      Docker 网络名（默认 campus-ai）"
}

# ---- 前置检查 ----
require_root() {
    if [[ "$(id -u)" -ne 0 ]]; then
        err "需要 root 权限运行 iptables。请使用：sudo $0 $ACTION"
        exit 1
    fi
}

check_deps() {
    local missing=()
    command -v iptables >/dev/null 2>&1 || missing+=("iptables")
    command -v docker >/dev/null 2>&1 || missing+=("docker")
    if [[ ${#missing[@]} -gt 0 ]]; then
        err "缺少依赖工具：${missing[*]}"
        err "安装：apt-get install -y iptables  （或 yum install -y iptables）"
        exit 1
    fi
}

# ---- 解析 DeepSeek API 域名为 IP 列表 ----
resolve_deepseek_ips() {
    log "解析域名：${DEEPSEEK_DOMAIN}"
    DEEPSEEK_IPS=""
    local ips
    if command -v dig >/dev/null 2>&1; then
        ips="$(dig +short "$DEEPSEEK_DOMAIN" A 2>/dev/null | grep -E '^[0-9]+\.' || true)"
    elif command -v host >/dev/null 2>&1; then
        ips="$(host "$DEEPSEEK_DOMAIN" 2>/dev/null | grep -oE 'has address [0-9.]+' | awk '{print $NF}' || true)"
    elif command -v getent >/dev/null 2>&1; then
        ips="$(getent ahostsv4 "$DEEPSEEK_DOMAIN" 2>/dev/null | grep -oE '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+' | sort -u || true)"
    else
        ips="$(nslookup "$DEEPSEEK_DOMAIN" 2>/dev/null | grep -A1 'Name:' | grep -oE 'Address: [0-9.]+' | awk '{print $2}' || true)"
    fi
    if [[ -z "$ips" ]]; then
        err "无法解析 ${DEEPSEEK_DOMAIN} 的 IP 地址，请检查 DNS 配置"
        exit 1
    fi
    DEEPSEEK_IPS="$ips"
    echo "$ips" | while read -r ip; do
        echo "  ${C_GREEN}DeepSeek API IP${C_NC}: $ip"
    done
}

# ---- 获取 campus-ai 网络子网 ----
get_network_subnet() {
    CAMPUS_AI_SUBNET="$(docker network inspect "$CAMPUS_AI_NETWORK" \
        -f '{{range .IPAM.Config}}{{.Subnet}}{{end}}' 2>/dev/null || true)"
    if [[ -z "$CAMPUS_AI_SUBNET" ]]; then
        err "无法获取 Docker 网络 ${CAMPUS_AI_NETWORK} 的子网"
        err "请确认网络存在：docker network ls | grep ${CAMPUS_AI_NETWORK}"
        exit 1
    fi
    log "campus-ai 网络子网：${CAMPUS_AI_SUBNET}"
}

# ---- 添加规则 ----
do_add() {
    require_root
    check_deps
    resolve_deepseek_ips
    get_network_subnet

    log "配置 iptables 白名单规则（自定义链：${CHAIN_NAME}）..."

    # 若链已存在，先清理（幂等）
    if iptables -n -L "$CHAIN_NAME" >/dev/null 2>&1; then
        warn "链 ${CHAIN_NAME} 已存在，先清理旧规则"
        do_remove_quiet
    fi

    # 1. 创建自定义链
    iptables -N "$CHAIN_NAME" 2>/dev/null || true

    # 2. 规则：允许内网网段互访（RFC1918 + 回环 + 链路本地）
    #    这些是容器间通信、DNS、网关所必需的
    for net in "10.0.0.0/8" "172.16.0.0/12" "192.168.0.0/16" "127.0.0.0/8" "169.254.0.0/16"; do
        iptables -A "$CHAIN_NAME" -s "$CAMPUS_AI_SUBNET" -d "$net" -j ACCEPT
        echo "  ${C_GRAY}ACCEPT 内网 ${net}${C_NC}"
    done

    # 3. 规则：允许 campus-ai 子网 -> DeepSeek API IP:443
    local ip
    while IFS= read -r ip; do
        [[ -z "$ip" ]] && continue
        iptables -A "$CHAIN_NAME" -s "$CAMPUS_AI_SUBNET" -d "$ip" -p tcp --dport 443 -j ACCEPT
        echo "  ${C_GREEN}ACCEPT ${ip}:443 (DeepSeek API)${C_NC}"
    done <<< "$DEEPSEEK_IPS"

    # 4. 规则：拒绝 campus-ai 子网 -> 其他所有外网（记录日志后 DROP）
    #    使用 LOG 目标便于审计，限制日志频率避免刷屏
    iptables -A "$CHAIN_NAME" -s "$CAMPUS_AI_SUBNET" -j LOG \
        --log-prefix "CAMPUS_EGRESS_DROP: " --log-level 4 -m limit --limit 5/min --limit-burst 10
    echo "  ${C_GRAY}LOG 违规出站流量（限速 5/min）${C_NC}"
    iptables -A "$CHAIN_NAME" -s "$CAMPUS_AI_SUBNET" -j DROP
    echo "  ${C_RED}DROP 其他所有外网出站${C_NC}"

    # 5. 将自定义链挂载到 FORWARD 链（仅处理 campus-ai 子网出站流量）
    iptables -C FORWARD -s "$CAMPUS_AI_SUBNET" -j "$CHAIN_NAME" 2>/dev/null || \
        iptables -I FORWARD 1 -s "$CAMPUS_AI_SUBNET" -j "$CHAIN_NAME"
    echo "  ${C_CYAN}挂载到 FORWARD 链（源 ${CAMPUS_AI_SUBNET}）${C_NC}"

    ok "白名单规则已添加"
    echo
    warn "规则为内存态，重启后失效。如需持久化："
    echo "  Debian/Ubuntu: apt-get install -y iptables-persistent && netfilter-persistent save"
    echo "  CentOS/RHEL:   iptables-save > /etc/sysconfig/iptables"
}

# ---- 静默移除（内部使用）----
do_remove_quiet() {
    # 从 FORWARD 移除跳转
    while iptables -C FORWARD -s "$CAMPUS_AI_SUBNET" -j "$CHAIN_NAME" 2>/dev/null; do
        iptables -D FORWARD -s "$CAMPUS_AI_SUBNET" -j "$CHAIN_NAME" 2>/dev/null || true
    done
    # 清空并删除自定义链
    iptables -F "$CHAIN_NAME" 2>/dev/null || true
    iptables -X "$CHAIN_NAME" 2>/dev/null || true
}

# ---- 移除规则 ----
do_remove() {
    require_root
    check_deps
    get_network_subnet 2>/dev/null || CAMPUS_AI_SUBNET="0.0.0.0/0"

    log "移除白名单规则（自定义链：${CHAIN_NAME}）..."

    if ! iptables -n -L "$CHAIN_NAME" >/dev/null 2>&1; then
        warn "链 ${CHAIN_NAME} 不存在，无需移除"
        return
    fi

    do_remove_quiet
    ok "白名单规则已移除，恢复默认放行"
}

# ---- 查看状态 ----
do_status() {
    require_root
    check_deps

    echo "${C_BOLD}================================================================${C_NC}"
    echo "${C_BOLD}  DeepSeek 出站防火墙状态 (INFRA-1.3)${C_NC}"
    echo "${C_BOLD}================================================================${C_NC}"
    echo

    # 自定义链是否存在
    if iptables -n -L "$CHAIN_NAME" >/dev/null 2>&1; then
        ok "自定义链 ${CHAIN_NAME} 已启用"
        echo
        echo "${C_BOLD}--- ${CHAIN_NAME} 链规则 ---${C_NC}"
        iptables -n -L "$CHAIN_NAME" --line-numbers 2>/dev/null || true
    else
        warn "自定义链 ${CHAIN_NAME} 未创建（规则未启用）"
    fi

    echo
    echo "${C_BOLD}--- FORWARD 链中的相关跳转 ---${C_NC}"
    iptables -n -L FORWARD 2>/dev/null | grep -E "$CHAIN_NAME|$CAMPUS_AI_NETWORK" || \
        echo "  (无跳转规则)"
    echo

    # 显示 DeepSeek 当前解析 IP
    log "解析 ${DEEPSEEK_DOMAIN} 当前 IP："
    resolve_deepseek_ips 2>/dev/null || warn "DNS 解析失败"

    echo
    echo "${C_BOLD}--- campus-ai 网络信息 ---${C_NC}"
    docker network inspect "$CAMPUS_AI_NETWORK" \
        -f '子网: {{range .IPAM.Config}}{{.Subnet}}{{end}}' 2>/dev/null || \
        warn "网络 ${CAMPUS_AI_NETWORK} 不存在"
    echo
}

# ---- 测试连通性（不修改规则）----
do_test() {
    log "测试 DeepSeek API 连通性（不修改 iptables 规则）..."
    resolve_deepseek_ips

    echo
    echo "${C_BOLD}--- TCP 443 连通性测试 ---${C_NC}"
    local ip all_ok=true
    while IFS= read -r ip; do
        [[ -z "$ip" ]] && continue
        if timeout 5 bash -c "echo >/dev/tcp/$ip/443" 2>/dev/null; then
            echo "  ${C_GREEN}PASS${C_NC}  ${ip}:443 可达"
        else
            echo "  ${C_RED}FAIL${C_NC}  ${ip}:443 不可达"
            all_ok=false
        fi
    done <<< "$DEEPSEEK_IPS"

    echo
    echo "${C_BOLD}--- HTTPS 握手测试 ---${C_NC}"
    local http_code
    http_code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 \
        "https://${DEEPSEEK_DOMAIN}/" 2>/dev/null || echo "000")"
    if [[ "$http_code" != "000" ]]; then
        echo "  ${C_GREEN}PASS${C_NC}  HTTPS 握手成功（HTTP ${http_code}）"
    else
        echo "  ${C_RED}FAIL${C_NC}  HTTPS 握手失败"
        all_ok=false
    fi

    echo
    # 测试一个非白名单域名是否被阻止（仅在规则启用时有意义）
    if iptables -n -L "$CHAIN_NAME" >/dev/null 2>&1; then
        echo "${C_BOLD}--- 非白名单域名出站测试（验证 DROP 生效）---${C_NC}"
        local blocked_code
        blocked_code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 \
            "https://www.baidu.com/" 2>/dev/null || echo "BLOCKED")"
        if [[ "$blocked_code" == "BLOCKED" || "$blocked_code" == "000" ]]; then
            echo "  ${C_GREEN}PASS${C_NC}  非白名单域名已被阻止（www.baidu.com 不可达）"
        else
            echo "  ${C_YELLOW}WARN${C_NC}  非白名单域名仍可达（HTTP ${blocked_code}），规则可能未覆盖本机出口"
        fi
    else
        warn "白名单规则未启用，跳过非白名单阻断测试"
    fi

    echo
    if [[ "$all_ok" == true ]]; then
        ok "DeepSeek API 连通性测试通过"
    else
        err "部分连通性测试失败，请检查网络与防火墙"
        exit 1
    fi
}

# =============================================================================
# 主流程
# =============================================================================
if [[ -z "$ACTION" ]]; then
    usage
    exit 1
fi

case "$ACTION" in
    add)     do_add ;;
    remove)  do_remove ;;
    status)  do_status ;;
    test)    do_test ;;
    -h|--help) usage ;;
    *)       err "未知操作: $ACTION"; usage; exit 1 ;;
esac
