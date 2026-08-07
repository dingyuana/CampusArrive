#!/usr/bin/env bash
# =============================================================================
# 微迎新 CampusArrive — 网络出站审计脚本 (INFRA-1.3 / UT-INFRA-009)
#
# 目标：验证 MaxKB 容器的出站流量仅发往 api.deepseek.com:443，
#       确认无其他外网请求（数据不出校原则）。
#
# 对应测试用例：
#   UT-INFRA-009  数据不出校：抓包确认无外网请求（除 DeepSeek API 域名）
#
# 原理：
#   1. 解析 api.deepseek.com 的 IP 地址，构建白名单
#   2. 获取 MaxKB 容器在 campus-ai 网络中的 IP
#   3. 使用 tcpdump 在容器网络命名空间抓取出站流量（指定时长）
#   4. 分析抓包结果：剔除白名单 IP 与内网网段，剩余即为违规出站
#   5. 输出审计报告（含违规目标 IP / 端口 / 频次）
#
# 安全说明：
#   - 本脚本需要 root 权限运行（tcpdump 抓包 + 网络命名空间访问）
#   - 抓包期间需有人为触发 MaxKB 对话请求以产生流量样本
#   - 抓包文件默认保存到 /tmp/maxkb-egress-<timestamp>.pcap
#
# 用法：
#   sudo ./audit-network-egress.sh                    # 默认抓包 60s
#   sudo ./audit-network-egress.sh --duration 120      # 抓包 120s
#   sudo ./audit-network-egress.sh --pcap /tmp/x.pcap  # 指定输出路径
#   sudo ./audit-network-egress.sh --analyze-only /tmp/x.pcap  # 仅分析已有抓包
# =============================================================================
set -euo pipefail

# ---- 配置 ----
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${DEPLOY_DIR}/.env"

MAXKB_CONTAINER="${MAXKB_CONTAINER:-campus-maxkb}"
DEEPSEEK_DOMAIN="${DEEPSEEK_BASE_URL:-https://api.deepseek.com}"
# 从 URL 提取域名
DEEPSEEK_DOMAIN="$(echo "$DEEPSEEK_DOMAIN" | sed -E 's|^https?://||; s|/.*$||; s|:.*$||')"
DEEPSEEK_DOMAIN="${DEEPSEEK_DOMAIN:-api.deepseek.com}"

DURATION=60
PCAP_FILE=""
ANALYZE_ONLY=""

# 从 .env 读取 DeepSeek 域名
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

# 解析命令行参数
while [[ $# -gt 0 ]]; do
    case "$1" in
        --duration) DURATION="$2"; shift 2 ;;
        --pcap) PCAP_FILE="$2"; shift 2 ;;
        --analyze-only) ANALYZE_ONLY="$2"; shift 2 ;;
        --container) MAXKB_CONTAINER="$2"; shift 2 ;;
        -h|--help)
            sed -n '2,/^# =\+/p' "$0" | sed 's/^# \?//' | head -n 35
            exit 0 ;;
        *) echo "未知参数: $1（使用 -h 查看帮助）" >&2; exit 1 ;;
    esac
done

# ---- 颜色 ----
if [[ -t 1 ]]; then
    C_GREEN=$'\033[1;32m'; C_RED=$'\033[1;31m'; C_YELLOW=$'\033[1;33m'
    C_CYAN=$'\033[1;36m'; C_GRAY=$'\033[90m'; C_BOLD=$'\033[1m'; C_NC=$'\033[0m'
else
    C_GREEN=''; C_RED=''; C_YELLOW=''; C_CYAN=''; C_GRAY=''; C_BOLD=''; C_NC=''
fi

log()  { echo "${C_BOLD}[audit]${C_NC} $*"; }
ok()   { echo "${C_GREEN}[ok]${C_NC} $*"; }
warn() { echo "${C_YELLOW}[warn]${C_NC} $*"; }
err()  { echo "${C_RED}[error]${C_NC} $*" >&2; }

# ---- 前置检查：root 权限 ----
require_root() {
    if [[ "$(id -u)" -ne 0 ]]; then
        err "本脚本需要 root 权限运行（tcpdump 抓包 + 网络命名空间访问）。"
        err "请使用：sudo $0 $*"
        exit 1
    fi
}

# ---- 前置检查：依赖工具 ----
check_deps() {
    local missing=()
    command -v docker >/dev/null 2>&1 || missing+=("docker")
    command -v tcpdump >/dev/null 2>&1 || missing+=("tcpdump")
    if [[ ${#missing[@]} -gt 0 ]]; then
        err "缺少依赖工具：${missing[*]}"
        err "安装：apt-get install -y tcpdump  （或 yum install -y tcpdump）"
        exit 1
    fi
}

# ---- 解析 DeepSeek API 域名为 IP 列表 ----
resolve_whitelist() {
    log "解析白名单域名：${DEEPSEEK_DOMAIN}"
    local ips
    # 使用 getent / dig / host 任一可用工具
    if command -v dig >/dev/null 2>&1; then
        ips="$(dig +short "$DEEPSEEK_DOMAIN" A 2>/dev/null | grep -E '^[0-9]+\.' || true)"
    elif command -v host >/dev/null 2>&1; then
        ips="$(host "$DEEPSEEK_DOMAIN" 2>/dev/null | grep -oE 'has address [0-9.]+' | awk '{print $NF}' || true)"
    elif command -v getent >/dev/null 2>&1; then
        ips="$(getent ahostsv4 "$DEEPSEEK_DOMAIN" 2>/dev/null | grep -oE '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+' | sort -u || true)"
    else
        # 最后兜底：nslookup
        ips="$(nslookup "$DEEPSEEK_DOMAIN" 2>/dev/null | grep -A1 'Name:' | grep -oE 'Address: [0-9.]+' | awk '{print $2}' || true)"
    fi
    if [[ -z "$ips" ]]; then
        warn "无法解析 ${DEEPSEEK_DOMAIN} 的 IP，将使用域名过滤模式"
        DEEPSEEK_IPS=""
        return 1
    fi
    DEEPSEEK_IPS="$ips"
    echo "$ips" | while read -r ip; do
        echo "  ${C_GREEN}白名单 IP${C_NC}: $ip"
    done
    return 0
}

# ---- 获取 MaxKB 容器网络信息 ----
get_container_net() {
    if ! docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "$MAXKB_CONTAINER"; then
        err "MaxKB 容器 ${MAXKB_CONTAINER} 未运行"
        err "请先启动：./scripts/deploy.sh sit maxkb"
        exit 1
    fi
    # 获取容器在 campus-ai 网络的 IP
    CONTAINER_IP="$(docker inspect -f \
        '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' \
        "$MAXKB_CONTAINER" 2>/dev/null | head -n1 || true)"
    if [[ -z "$CONTAINER_IP" ]]; then
        err "无法获取 ${MAXKB_CONTAINER} 的 IP 地址"
        exit 1
    fi
    # 获取容器的网络命名空间路径（用于 nsenter 抓包）
    local pid
    pid="$(docker inspect -f '{{.State.Pid}}' "$MAXKB_CONTAINER" 2>/dev/null || true)"
    if [[ -z "$pid" || "$pid" == "0" ]]; then
        err "无法获取容器 PID，容器可能未运行"
        exit 1
    fi
    NETNS="/proc/${pid}/ns/net"
    log "MaxKB 容器 IP：${CONTAINER_IP}，PID：${pid}"
    log "网络命名空间：${NETNS}"
}

# ---- 抓包 ----
capture_traffic() {
    local pcap="${PCAP_FILE:-/tmp/maxkb-egress-$(date +%Y%m%d%H%M%S).pcap}"
    PCAP_FILE="$pcap"

    log "开始抓包 ${DURATION}s，输出到 ${pcap}"
    log "${C_YELLOW}请在此时通过 MaxKB 发送若干测试对话请求，以产生流量样本${C_NC}"
    echo

    # 在容器网络命名空间内抓取出站流量
    # nsenter 进入容器 netns，tcpdump -i any 监听所有接口
    local container_pid
    container_pid="$(docker inspect -f '{{.State.Pid}}' "$MAXKB_CONTAINER" 2>/dev/null || true)"
    nsenter -t "$container_pid" -n tcpdump -i any -nn -w "$pcap" \
        -U "not (src net 127.0.0.0/8 and dst net 127.0.0.0/8)" \
        2>/dev/null &
    local tcpdump_pid=$!

    # 等待抓包时长
    local waited=0
    while [[ $waited -lt $DURATION ]]; do
        sleep 5
        waited=$((waited + 5))
        echo -n "."
        # 检查 tcpdump 是否已退出
        if ! kill -0 "$tcpdump_pid" 2>/dev/null; then
            break
        fi
    done
    echo

    # 用 SIGINT 终止 tcpdump，确保缓冲写入并正确关闭 pcap 文件
    kill -INT "$tcpdump_pid" 2>/dev/null || true
    wait "$tcpdump_pid" 2>/dev/null || true

    if [[ -f "$pcap" ]]; then
        local size
        size="$(stat -c%s "$pcap" 2>/dev/null || stat -f%z "$pcap" 2>/dev/null || echo 0)"
        ok "抓包完成：${pcap}（$(( size / 1024 )) KB）"
    else
        err "抓包文件未生成，请检查 tcpdump 是否可用"
        exit 1
    fi
}

# ---- 分析抓包结果 ----
analyze_pcap() {
    local pcap="${ANALYZE_ONLY:-$PCAP_FILE}"
    if [[ -z "$pcap" || ! -f "$pcap" ]]; then
        err "抓包文件不存在：${pcap}"
        exit 1
    fi

    log "分析抓包文件：${pcap}"

    # 提取所有出站 TCP SYN 包的目标 IP:端口（即容器主动发起的连接）
    # SYN 且无 ACK 表示主动发起连接；tcpdump 直接读取 pcap 文件（无需 nsenter）
    local conns_file total_conn violations=0
    conns_file="$(mktemp)"
    tcpdump -r "$pcap" -nn 2>/dev/null \
        'tcp[tcpflags] & tcp-syn != 0 and tcp[tcpflags] & tcp-ack == 0' \
        | grep -oE '>[[:space:]]+[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+:' \
        | sed -E 's/>[[:space:]]+//; s/:$//' \
        | sort | uniq -c | sort -rn > "$conns_file" 2>/dev/null || true

    total_conn="$(wc -l < "$conns_file" 2>/dev/null || echo "0")"
    log "共发现 ${total_conn} 个不同目标的出站连接"

    # 内网网段（RFC1918）+ 回环，视为合法内网流量
    # 白名单：DeepSeek API IP + 内网 + DNS 服务器
    local report_file
    report_file="$(mktemp)"
    echo 0 > /tmp/.violation_count

    while IFS= read -r line; do
        [[ -z "$line" ]] && continue
        # 格式: "  3 1.2.3.4.443"
        local count dest_ip dest_port
        count="$(echo "$line" | awk '{print $1}')"
        dest="$(echo "$line" | awk '{print $2}')"
        # 拆分 IP 与端口（最后一段为端口）
        dest_ip="$(echo "$dest" | rev | cut -d. -f2- | rev)"
        dest_port="$(echo "$dest" | rev | cut -d. -f1 | rev)"

        # 判断是否为内网（RFC1918 / 回环 / 链路本地）
        local is_internal=false is_whitelisted=false
        case "$dest_ip" in
            10.*|172.1[6-9].*|172.2[0-9].*|172.3[01].*|192.168.*|127.*|169.254.*)
                is_internal=true ;;
        esac

        # 判断是否在 DeepSeek 白名单
        if [[ -n "${DEEPSEEK_IPS:-}" ]]; then
            if echo "$DEEPSEEK_IPS" | grep -qx "$dest_ip"; then
                is_whitelisted=true
            fi
        fi

        if [[ "$is_internal" == true ]]; then
            echo "PASS|内网|$dest_ip|$dest_port|$count" >> "$report_file"
        elif [[ "$is_whitelisted" == true ]]; then
            echo "PASS|白名单(DeepSeek)|$dest_ip|$dest_port|$count" >> "$report_file"
        else
            echo "FAIL|违规外网|$dest_ip|$dest_port|$count" >> "$report_file"
            echo $(( $(cat /tmp/.violation_count) + 1 )) > /tmp/.violation_count
        fi
    done < "$conns_file"

    violations="$(cat /tmp/.violation_count)"
    rm -f /tmp/.violation_count

    # ---- 输出审计报告 ----
    echo
    echo "${C_BOLD}================================================================${C_NC}"
    echo "${C_BOLD}  微迎新 CampusArrive — 网络出站审计报告 (UT-INFRA-009)${C_NC}"
    echo "${C_BOLD}================================================================${C_NC}"
    echo "  审计时间：$(date '+%Y-%m-%d %H:%M:%S')"
    echo "  MaxKB 容器：${MAXKB_CONTAINER} (${CONTAINER_IP:-N/A})"
    echo "  白名单域名：${DEEPSEEK_DOMAIN}"
    if [[ -n "${DEEPSEEK_IPS:-}" ]]; then
        echo "  白名单 IP：$(echo "$DEEPSEEK_IPS" | tr '\n' ' ')"
    fi
    echo "  抓包文件：${pcap}"
    echo "  抓包时长：${DURATION}s"
    echo "${C_GRAY}----------------------------------------------------------------${C_NC}"
    printf "${C_BOLD}%-8s %-16s %-18s %-8s %s${C_NC}\n" "状态" "分类" "目标IP" "端口" "连接数"
    echo "${C_GRAY}----------------------------------------------------------------${C_NC}"

    if [[ ! -s "$report_file" ]]; then
        echo "  ${C_YELLOW}未捕获到任何出站连接${C_NC}（可能未产生对话流量）"
    else
        sort -t'|' -k1,1 -r "$report_file" | while IFS='|' read -r st cat ip port cnt; do
            local color
            case "$st" in
                PASS) color="$C_GREEN" ;;
                FAIL) color="$C_RED" ;;
                *)    color="$C_YELLOW" ;;
            esac
            printf "%-8s %-16s %-18s %-8s %s\n" "${color}${st}${C_NC}" "$cat" "$ip" "$port" "$cnt"
        done
    fi

    echo "${C_GRAY}----------------------------------------------------------------${C_NC}"
    echo
    if [[ "$violations" -eq 0 ]]; then
        ok "审计通过 (UT-INFRA-009)：未发现违规外网请求，数据不出校原则满足。"
        ok "所有出站流量均发往内网或 DeepSeek API 白名单。"
    else
        err "审计失败 (UT-INFRA-009)：发现 ${violations} 个违规外网目标！"
        err "请检查 MaxKB 配置或使用防火墙脚本限制出站："
        err "  ./maxkb/deepseek-egress-firewall.sh add"
        err "违规抓包详情可进一步分析：tcpdump -r ${pcap} -nn host <违规IP>"
    fi
    echo
    echo "${C_GRAY}提示：抓包文件保留供事后取证，可手动删除：rm -f ${pcap}${C_NC}"
    echo

    rm -f "$conns_file" "$report_file"

    if [[ "$violations" -gt 0 ]]; then
        exit 1
    fi
    exit 0
}

# =============================================================================
# 主流程
# =============================================================================
main() {
    log "微迎新 CampusArrive — 网络出站审计 (UT-INFRA-009)"
    log "白名单：仅允许 ${DEEPSEEK_DOMAIN}:443 出站"
    echo "${C_GRAY}$(printf '%.0s-' {1..70})${C_NC}"

    check_deps
    resolve_whitelist || true

    if [[ -n "$ANALYZE_ONLY" ]]; then
        # 仅分析模式：需获取容器信息用于 netns
        get_container_net 2>/dev/null || true
        analyze_pcap
        return
    fi

    require_root
    get_container_net
    capture_traffic
    analyze_pcap
}

main "$@"
