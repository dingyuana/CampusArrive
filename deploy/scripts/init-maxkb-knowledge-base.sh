#!/usr/bin/env bash
# =============================================================================
# 微迎新 CampusArrive — MaxKB 知识库空间初始化引导脚本 (INFRA-1.3)
#
# 功能：
#   1. 等待 MaxKB 服务就绪（轮询健康端点）
#   2. 输出初始登录凭据提醒（默认 admin/MaxKB@123..，提醒首登修改）
#   3. 交互式引导完成 MaxKB 控制台配置：
#      a. 修改默认密码
#      b. 配置 DeepSeek 模型供应商
#      c. 创建知识库空间（报到流程手册 / 校园POI / FAQ / 材料清单）
#      d. 创建 AI 应用并关联知识库
#      e. 获取应用 API Key
#   4. 提供通过 MaxKB API 验证知识库就绪的 curl 命令模板
#
# 对应需求：FR-01-07~14（AI 智能助手）
# 用法：
#   ./init-maxkb-knowledge-base.sh
#   ./init-maxkb-knowledge-base.sh --non-interactive    # 跳过交互确认，仅打印指引
#   ./init-maxkb-knowledge-base.sh --maxkb-url http://10.0.0.1:8088
# =============================================================================
set -euo pipefail

# ---- 配置 ----
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${DEPLOY_DIR}/.env"

MAXKB_PORT="${MAXKB_PORT:-8088}"
MAXKB_HOST="${MAXKB_HOST:-127.0.0.1}"
MAXKB_ADMIN_USER="${MAXKB_ADMIN_USER:-admin}"
MAXKB_ADMIN_PASSWORD="${MAXKB_ADMIN_PASSWORD:-MaxKB@123..}"
MAXKB_BASE_URL="http://${MAXKB_HOST}:${MAXKB_PORT}"
INTERACTIVE=true

# 解析命令行参数
while [[ $# -gt 0 ]]; do
    case "$1" in
        --non-interactive)
            INTERACTIVE=false; shift ;;
        --maxkb-url)
            MAXKB_BASE_URL="$2"; shift 2 ;;
        --maxkb-port)
            MAXKB_PORT="$2"; MAXKB_BASE_URL="http://${MAXKB_HOST}:${MAXKB_PORT}"; shift 2 ;;
        --maxkb-host)
            MAXKB_HOST="$2"; MAXKB_BASE_URL="http://${MAXKB_HOST}:${MAXKB_PORT}"; shift 2 ;;
        -h|--help)
            sed -n '2,/^# =\+/p' "$0" | sed 's/^# \?//' | head -n 25
            exit 0 ;;
        *)
            echo "未知参数: $1（使用 -h 查看帮助）" >&2
            exit 1 ;;
    esac
done

# 从 .env 读取覆盖值（不使用 set -a 避免污染）
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
    MAXKB_PORT="$(env_value MAXKB_PORT "$MAXKB_PORT")"
    MAXKB_ADMIN_USER="$(env_value MAXKB_ADMIN_USER "$MAXKB_ADMIN_USER")"
    MAXKB_ADMIN_PASSWORD="$(env_value MAXKB_ADMIN_PASSWORD "$MAXKB_ADMIN_PASSWORD")"
    MAXKB_BASE_URL="http://${MAXKB_HOST}:${MAXKB_PORT}"
fi

# ---- 颜色 ----
if [[ -t 1 ]]; then
    C_GREEN=$'\033[1;32m'; C_RED=$'\033[1;31m'; C_YELLOW=$'\033[1;33m'
    C_CYAN=$'\033[1;36m'; C_BLUE=$'\033[1;34m'; C_GRAY=$'\033[90m'
    C_BOLD=$'\033[1m'; C_NC=$'\033[0m'
else
    C_GREEN=''; C_RED=''; C_YELLOW=''; C_CYAN=''; C_BLUE=''; C_GRAY=''; C_BOLD=''; C_NC=''
fi

log()  { echo "${C_BOLD}[init-kb]${C_NC} $*"; }
ok()   { echo "${C_GREEN}[ok]${C_NC} $*"; }
warn() { echo "${C_YELLOW}[warn]${C_NC} $*"; }
err()  { echo "${C_RED}[error]${C_NC} $*" >&2; }
step() { echo; echo "${C_CYAN}${C_BOLD}==> 步骤 $1：$2${C_NC}"; }

# 交互式确认（y/n），非交互模式自动返回 0
confirm() {
    local prompt="$1"
    if [[ "$INTERACTIVE" == false ]]; then
        echo "${C_GRAY}[非交互模式] $prompt (跳过确认)${C_NC}"
        return 0
    fi
    while true; do
        read -r -p "$(echo "${C_BOLD}${prompt} [y/N]: ${C_NC}")" ans
        case "${ans:-N}" in
            y|Y|yes|YES) return 0 ;;
            n|N|no|NO|"") return 1 ;;
            *) echo "请输入 y 或 n" ;;
        esac
    done
}

# 按回车继续
press_enter() {
    if [[ "$INTERACTIVE" == true ]]; then
        echo "${C_GRAY}按回车继续...${C_NC}"
        read -r
    fi
}

# =============================================================================
# 1. 等待 MaxKB 服务就绪
# =============================================================================
wait_for_maxkb() {
    log "等待 MaxKB 服务就绪（${MAXKB_BASE_URL}）..."
    local deadline=$(( $(date +%s) + 180 ))
    while [[ $(date +%s) -lt $deadline ]]; do
        if curl -fsS -o /dev/null --max-time 5 "${MAXKB_BASE_URL}/" 2>/dev/null; then
            ok "MaxKB 服务已就绪"
            return 0
        fi
        echo -n "."
        sleep 5
    done
    echo
    err "MaxKB 服务在 180s 内未就绪，请检查容器状态：docker ps -a | grep maxkb"
    err "查看日志：docker logs campus-maxkb"
    return 1
}

# =============================================================================
# 通过 MaxKB API 登录并获取 JWT token（验证凭据可用性）
# =============================================================================
maxkb_login() {
    # 返回 JWT token（成功）或空（失败）
    local resp http_code body
    resp="$(curl -s -w '\n%{http_code}' --max-time 10 \
        "${MAXKB_BASE_URL}/api/user/login" \
        -H "Content-Type: application/json" \
        -d "{\"username\":\"${MAXKB_ADMIN_USER}\",\"password\":\"${MAXKB_ADMIN_PASSWORD}\"}" 2>/dev/null || echo "ERR")"
    http_code="$(echo "$resp" | tail -n1)"
    body="$(echo "$resp" | sed '$d')"
    if [[ "$http_code" == "200" ]]; then
        # 从响应中提取 token（MaxKB v1.10 返回 {"code":200,"data":"<token>"}）
        echo "$body" | grep -oE '"data"\s*:\s*"[^"]+"' | head -n1 | cut -d'"' -f4 || echo ""
    else
        echo ""
    fi
}

# =============================================================================
# 通过 API 验证知识库就绪：列出已有知识库
# =============================================================================
verify_knowledge_base_ready() {
    local token
    token="$(maxkb_login)"
    if [[ -z "$token" ]]; then
        warn "无法登录 MaxKB（凭据可能已修改或服务异常），跳过 API 自动验证"
        return 1
    fi
    ok "MaxKB 登录成功，token 已获取"
    log "查询知识库列表..."
    local resp http_code body
    resp="$(curl -s -w '\n%{http_code}' --max-time 10 \
        "${MAXKB_BASE_URL}/api/dataset/current/page/1/page_size/100" \
        -H "Authorization: Bearer ${token}" 2>/dev/null || echo "ERR")"
    http_code="$(echo "$resp" | tail -n1)"
    body="$(echo "$resp" | sed '$d')"
    if [[ "$http_code" == "200" ]]; then
        local count
        count="$(echo "$body" | grep -oE '"name"\s*:\s*"[^"]+"' | wc -l || echo "0")"
        ok "API 验证通过：HTTP 200，已创建知识库约 ${count} 个"
        echo "${C_GRAY}知识库列表：${C_NC}"
        echo "$body" | grep -oE '"name"\s*:\s*"[^"]+"' | sed 's/"name":/  -/' || true
        return 0
    else
        warn "API 验证返回 HTTP ${http_code}，可能知识库尚未创建或 API 路径变更"
        return 1
    fi
}

# =============================================================================
# 主流程
# =============================================================================
log "微迎新 CampusArrive — MaxKB 知识库空间初始化引导"
log "MaxKB 地址：${MAXKB_BASE_URL}"
echo "${C_GRAY}$(printf '%.0s-' {1..70})${C_NC}"

# 1. 等待服务就绪
wait_for_maxkb

# 2. 输出初始登录凭据提醒
echo
echo "${C_BOLD}${C_YELLOW}================================ 注意 ====================================${C_NC}"
echo "${C_BOLD}MaxKB 初始管理员凭据（默认值，请尽快修改）：${C_NC}"
echo "  用户名：${C_CYAN}${MAXKB_ADMIN_USER}${C_NC}"
echo "  密  码：${C_CYAN}${MAXKB_ADMIN_PASSWORD}${C_NC}"
echo "${C_YELLOW}安全提醒：默认密码仅用于首次登录，完成配置后务必修改为强密码！${C_NC}"
echo "${C_YELLOW}=========================================================================${C_NC}"
echo
echo "访问 MaxKB 控制台：${C_BLUE}${MAXKB_BASE_URL}${C_NC}"
echo "详细操作步骤见：${C_BLUE}deploy/maxkb/maxkb-app-config-guide.md${C_NC}"

if ! confirm "是否已在浏览器打开 MaxKB 控制台并准备开始配置？"; then
    warn "请先在浏览器打开 ${MAXKB_BASE_URL} 后重新运行本脚本"
    exit 0
fi

# ---- 步骤 1：修改默认密码 ----
step 1 "修改默认密码"
cat <<'EOF'
操作路径：右上角头像 -> 个人设置 / 修改密码
  1. 登录后点击右上角用户名
  2. 进入「个人设置」或「修改密码」
  3. 输入旧密码 MaxKB@123.. ，设置新强密码（≥12 位，含大小写/数字/符号）
  4. 保存后需重新登录

说明：修改密码后，后续脚本中的 MAXKB_ADMIN_PASSWORD 需同步更新到 .env。
EOF
if confirm "是否已完成默认密码修改？"; then
    ok "密码已修改（请将新密码更新到 deploy/.env 的 MAXKB_ADMIN_PASSWORD）"
else
    warn "建议尽快完成密码修改"
fi

# ---- 步骤 2：配置 DeepSeek 模型供应商 ----
step 2 "配置 DeepSeek 模型供应商"
cat <<'EOF'
操作路径：系统设置 -> 模型管理 -> 添加模型
  1. 进入「系统设置」->「模型管理」
  2. 点击「添加模型」
  3. 供应商选择：DeepSeek（或自定义 OpenAI 兼容）
  4. 填写配置：
     - 模型名称：deepseek-v4-flash
     - 基础模型：deepseek-v4-flash
     - API Key：从 .env 的 DEEPSEEK_API_KEY 获取
     - API 地址：https://api.deepseek.com
  5. 保存并测试连通性

说明：可同时添加 deepseek-v4-pro（高能力模型）用于复杂问答。
EOF
if confirm "是否已配置 DeepSeek 模型并测试通过？"; then
    ok "DeepSeek 模型配置完成"
else
    warn "模型未配置将无法创建可用的 AI 应用，请先完成此步骤"
fi

# ---- 步骤 3：创建知识库空间 ----
step 3 "创建知识库空间（4 个子知识库）"
cat <<'EOF'
操作路径：知识库 -> 创建知识库
需创建以下 4 个知识库，对应 FR-01-07~10：

  1. 报到流程手册（FR-01-07）
     - 名称：freshman-checkin-guide
     - 描述：新生报到流程指引，含线上预登记、现场签到、宿舍分配等
     - 文档类型：流程手册、步骤说明

  2. 校园POI信息（FR-01-08）
     - 名称：campus-poi
     - 描述：校园兴趣点（教学楼/食堂/图书馆/宿舍/快递点等）位置与导航
     - 文档类型：地点信息表、地图标注

  3. 常见问题FAQ（FR-01-09）
     - 名称：freshman-faq
     - 描述：新生高频问答，覆盖缴费/选课/军训/生活等
     - 文档类型：Q&A 对、FAQ 文档

  4. 材料清单模板（FR-01-10）
     - 名称：checkin-materials
     - 描述：报到所需材料清单与模板（身份证/录取通知书/照片/档案等）
     - 文档类型：清单表格、模板文档

每个知识库创建后上传对应文档，MaxKB 会自动进行向量化与索引。
EOF
if confirm "是否已完成 4 个知识库的创建与文档上传？"; then
    ok "知识库空间初始化完成"
else
    warn "请继续完成知识库创建"
fi

# ---- 步骤 4：创建 AI 应用并关联知识库 ----
step 4 "创建 AI 应用并关联知识库"
cat <<'EOF'
操作路径：应用 -> 创建应用
  1. 点击「创建应用」
  2. 应用类型选择：SIMPLE（简单对话应用）
  3. 关联模型：deepseek-v4-flash
  4. 关联知识库：勾选上述 4 个知识库
     - freshman-checkin-guide
     - campus-poi
     - freshman-faq
     - checkin-materials
  5. 设置开场白（建议）：
     "你好！我是微迎新智能助手，可以为你解答报到流程、校园导航、
      材料准备等问题。请问有什么可以帮助你的？"
  6. 保存应用

说明：工作流编排（检索 -> PII 脱敏 -> 生成 -> 内容标识，FR-01-11~14）
      为后续 AI-3.2 任务实现，此处仅创建 SIMPLE 应用即可。
EOF
if confirm "是否已创建 AI 应用并关联知识库？"; then
    ok "AI 应用创建完成"
else
    warn "请继续完成 AI 应用创建"
fi

# ---- 步骤 5：获取应用 API Key ----
step 5 "获取应用 API Key"
cat <<'EOF'
操作路径：应用详情 -> API 文档 / 嵌入 -> 获取 API Key
  1. 进入已创建的应用详情页
  2. 点击「API 文档」或「嵌入」按钮
  3. 复制应用 API Key（格式形如 xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx）
  4. 将 API Key 写入 deploy/.env 的 MAXKB_API_KEY

说明：ai-service 通过此 API Key 调用 MaxKB 对话接口。
EOF
if confirm "是否已获取应用 API Key？"; then
    ok "应用 API Key 已获取"
    echo
    echo "${C_BOLD}请将 API Key 写入 .env：${C_NC}"
    echo "  ${C_CYAN}vi ${DEPLOY_DIR}/.env${C_NC}"
    echo "  ${C_GRAY}# 修改 MAXKB_API_KEY=<你的应用 API Key>${C_NC}"
else
    warn "请尽快获取应用 API Key 并写入 .env"
fi

# ---- API 验证 ----
echo
echo "${C_GRAY}$(printf '%.0s-' {1..70})${C_NC}"
log "通过 MaxKB API 验证知识库就绪..."
echo "${C_GRAY}以下 curl 命令模板可用于手动验证（替换 <TOKEN> 与 <API_KEY>）：${C_NC}"
echo
echo "${C_BOLD}# 1. 登录获取 token${C_NC}"
echo "  curl -s ${MAXKB_BASE_URL}/api/user/login \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    -d '{\"username\":\"${MAXKB_ADMIN_USER}\",\"password\":\"<你的密码>\"}'"
echo
echo "${C_BOLD}# 2. 列出知识库（Bearer <TOKEN>）${C_NC}"
echo "  curl -s ${MAXKB_BASE_URL}/api/dataset/current/page/1/page_size/100 \\"
echo "    -H 'Authorization: Bearer <TOKEN>'"
echo
echo "${C_BOLD}# 3. 调用应用对话（Bearer <API_KEY>）${C_NC}"
echo "  curl -s ${MAXKB_BASE_URL}/api/application/<APP_ID>/chat/completions \\"
echo "    -H 'Authorization: Bearer <APP_API_KEY>' \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    -d '{\"message\":\"你好，请问报到需要哪些材料？\",\"stream\":false}'"
echo

# 尝试自动验证
verify_knowledge_base_ready || warn "API 自动验证未完全通过，请检查上述手动命令"

# ---- 完成 ----
echo
echo "${C_GRAY}$(printf '%.0s-' {1..70})${C_NC}"
ok "MaxKB 知识库空间初始化引导流程结束"
echo
echo "${C_BOLD}后续步骤：${C_NC}"
echo "  1. 将应用 API Key 写入 ${DEPLOY_DIR}/.env（MAXKB_API_KEY）"
echo "  2. 运行连通性验证：${C_CYAN}./scripts/verify-maxkb-deepseek.sh${C_NC}"
echo "  3. 运行网络审计：${C_CYAN}./scripts/audit-network-egress.sh${C_NC}"
echo "  4. 配置防火墙白名单：${C_CYAN}./maxkb/deepseek-egress-firewall.sh add${C_NC}"
echo "  5. 详细操作手册：${C_CYAN}deploy/maxkb/maxkb-app-config-guide.md${C_NC}"
echo "  6. 完整部署文档：${C_CYAN}deploy/docs/INFRA-1.3-部署文档.md${C_NC}"
echo
