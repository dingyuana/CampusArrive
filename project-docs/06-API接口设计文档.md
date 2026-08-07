# 微迎新（CampusArrive） v1.1 API 接口设计文档

| 项目名称 | 微迎新（CampusArrive） |
| --- | --- |
| 文档名称 | API 接口设计文档（API Design Specification） |
| 文档编号 | API-CA-2026-06 |
| 文档版本 | v1.1.0 |
| 状态 | 正式发布 |
| 编制日期 | 2026-08-07 |
| 适用版本 | v1.1（基于 v1.0 迭代） |
| 密级 | L2 内部敏感 |
| 关联文档 | SRS-CA-2026-03《需求规格说明书》、PC-CA-2026-01《项目章程》 |

---

## 版本历史

| 版本 | 日期 | 修订人 | 修订说明 |
| --- | --- | --- | --- |
| v1.0.0 | 2025-08 | 研发组 | v1.0 首版，覆盖报到流程、导航、进度、流程配置、三端协作接口 |
| v1.1.0-draft | 2026-07 | 研发组 | 新增 AI 助手、家长端、系统集成接口草案，引入 SSE/WebSocket 流式协议 |
| v1.1.0 | 2026-08-07 | 研发组 | 正式发布，补全认证授权、安全规范、限流策略、事件载荷与错误码对照表 |

---

## 1 文档说明

### 1.1 编写目的

本文档对微迎新（CampusArrive） v1.1 版本对外与对内暴露的全部 API 接口进行完整、可实施的定义，作为前后端联调、第三方系统对接、测试验证与安全审计的统一基准。文档面向后端工程师、前端工程师、测试工程师、DevOps 工程师、校方信息中心对接人员及外部系统（教务、财务、宿舍、一卡通、人事）集成方。

v1.1 在 v1.0 既有接口基础上新增 6 个核心 API 端点，覆盖 AI 迎新智能助手、家长查看端、系统集成中间件三大新增模块。所有接口经 Spring Cloud Gateway API 网关统一鉴权、限流与协议适配，学生前端为微信小程序，家长端为 H5 页面。

### 1.2 文档范围

本文档覆盖以下内容：

- 接口规范总则（URL 规范、请求/响应格式、错误码体系、版本管理）；
- 认证与授权方案（JWT 令牌结构、OAuth 2.0 scope、令牌刷新机制）；
- 接口安全规范（安全维度表、数据脱敏、审计日志、幂等控制）；
- v1.1 新增 6 个核心 API 端点的详细定义（请求/响应/错误码/限流/安全）；
- SSE 流式响应协议与 WebSocket 流式对话协议；
- 限流策略与核心事件类型定义；
- 完整错误码对照表。

不在本文档范围内的事项：数据库表结构设计、微服务内部 RPC 接口、UI 交互细节、部署拓扑，上述内容由配套的架构设计文档与部署文档承担。

### 1.3 术语与缩略语

| 术语 / 缩略语 | 含义 |
| --- | --- |
| API 网关 | 基于 Spring Cloud Gateway 的统一入口，承担路由、鉴权、限流、协议适配 |
| JWT | JSON Web Token，无状态身份令牌，用于新生与家长鉴权 |
| OAuth 2.0 | 开放授权协议，用于外部系统接入本系统接口 |
| SSE | Server-Sent Events，服务端推送流式响应协议 |
| WebSocket | 全双工通信协议，用于 AI 流式对话实时推送 |
| RAG | 检索增强生成（Retrieval-Augmented Generation） |
| MCP | 模型上下文协议（Model Context Protocol），大模型调用外部工具 |
| scope | OAuth 2.0 授权范围，控制外部系统数据可见范围 |
| event_id | 事件唯一标识，用于事件推送幂等去重 |
| request_id | 请求唯一标识，用于链路追踪与幂等控制 |
| 令牌桶 | 限流算法，按固定速率生成令牌，请求消耗令牌 |
| RabbitMQ | 消息队列中间件，用于事件异步处理 |
| CDC | 变更数据捕获（Change Data Capture） |
| PII | 个人身份信息（Personally Identifiable Information） |
| 幂等性 | 同一请求多次执行产生相同结果的特性 |
| TTL | 生存时间（Time To Live），令牌或缓存有效期 |

### 1.4 参考文档

| 编号 | 名称 | 用途 |
| --- | --- | --- |
| REF-01 | RFC 7519 JSON Web Token (JWT) | JWT 令牌结构依据 |
| REF-02 | RFC 6749 The OAuth 2.0 Authorization Framework | OAuth 2.0 授权框架依据 |
| REF-03 | RFC 6749 OAuth 2.0 Bearer Token Usage | Bearer 令牌使用规范 |
| REF-04 | HTML Living Standard - Server-Sent Events | SSE 协议依据 |
| REF-05 | RFC 6455 The WebSocket Protocol | WebSocket 协议依据 |
| REF-06 | SRS-CA-2026-03 需求规格说明书 | 功能需求与验收标准来源 |
| REF-07 | PC-CA-2026-01 项目章程 | 架构与中间件选型来源 |
| REF-08 | 《生成式人工智能服务管理暂行办法》 | AI 内容标识合规依据 |
| REF-09 | OWASP API Security Top 10 (2023) | API 安全防护基线 |

---

## 2 接口规范总则

### 2.1 URL 规范

#### 2.1.1 URL 结构

所有接口 URL 遵循以下结构：

```
https://{gateway-host}/{api-prefix}/{resource}/{action-or-id}
```

| 组成 | 说明 | 示例 |
| --- | --- | --- |
| scheme | 固定 `https`，禁用 HTTP 明文 | `https` |
| gateway-host | API 网关域名，按部署环境区分 | `api.freshman.edu.cn` |
| api-prefix | 版本前缀，格式 `/api/v{major}.{minor}` | `/api/v1`、`/api/v1.2` |
| resource | 资源名称，复数名词，小写中划线分隔 | `ai`、`parent`、`integration` |
| action-or-id | 操作动词或资源标识 | `chat`、`bind`、`progress`、`{student_id}` |

#### 2.1.2 命名约定

- 资源路径使用小写字母与中划线（kebab-case），如 `/parent/verify-code`；
- 路径参数使用小写下划线（snake_case），如 `{student_id}`；
- 查询参数使用小写下划线，如 `?page=1&page_size=20`；
- 动作型端点使用动词，如 `/ai/chat`、`/parent/bind`；
- 资源型端点使用名词，如 `/integration/student/{student_id}`。

#### 2.1.3 版本管理策略

| 版本前缀 | 适用范围 | 说明 |
| --- | --- | --- |
| `/api/v1/` | v1.0 既有接口 + v1.1 新增接口 | v1.1 新增接口仍归属 v1 主版本 |
| `/api/v1.2/` | v1.2 及以后新增接口 | 仅用于未来迭代新增能力 |

版本演进原则：

1. **向后兼容**：新增字段不视为破坏性变更，旧客户端忽略未知字段；
2. **字段废弃**：废弃字段保留 2 个小版本周期，响应中标记 `"deprecated": true`；
3. **破坏性变更**：涉及字段删除、语义变更、类型变更时，新增 `/api/v1.2/` 端点，旧端点保留过渡；
4. **版本下线**：旧版本下线前至少提前 1 个版本周期公告，提供迁移文档。

### 2.2 请求规范

#### 2.2.1 通用请求头

| 请求头 | 是否必填 | 说明 | 示例 |
| --- | --- | --- | --- |
| `Authorization` | 视接口而定 | 鉴权令牌，格式 `Bearer {token}` 或 `OAuth {access_token}` | `Bearer eyJhbGciOi...` |
| `Content-Type` | POST/PUT/PATCH 必填 | 请求体类型，默认 `application/json; charset=utf-8` | `application/json; charset=utf-8` |
| `Accept` | 可选 | 响应类型，AI 对话可指定 `text/event-stream` 启用 SSE | `text/event-stream` |
| `X-Request-Id` | 可选 | 请求唯一标识，未提供时网关自动生成 | `550e8400-e29b-41d4-a716-446655440000` |
| `X-Client-Type` | 可选 | 客户端类型：`miniapp`/`h5`/`web`/`integration` | `miniapp` |
| `X-Client-Version` | 可选 | 客户端版本号 | `1.1.0` |
| `User-Agent` | 自动 | 客户端环境信息 | `Mozilla/5.0 ...` |

#### 2.2.2 请求体规范

- 请求体统一使用 JSON 格式，UTF-8 编码；
- 字段命名使用小写下划线（snake_case），如 `student_id`、`verify_code`；
- 时间字段统一使用 ISO 8601 格式与 UTC 时区，如 `2026-08-25T08:30:00Z`；
- 布尔字段使用 `true`/`false`，不使用 0/1；
- 空值字段使用 `null`，不省略必填字段。

### 2.3 统一响应格式

#### 2.3.1 标准响应结构

所有接口（SSE 与 WebSocket 除外）返回统一 JSON 结构：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "field_a": "value_a",
    "field_b": "value_b"
  },
  "request_id": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-08-25T08:30:00Z"
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | integer | 业务状态码，`0` 表示成功，非 `0` 表示业务错误 |
| `message` | string | 状态描述，成功为 `"success"`，失败为可读错误信息 |
| `data` | object \| array \| null | 业务数据载荷，无数据时为 `null` |
| `request_id` | string | 请求唯一标识，用于链路追踪与问题排查 |
| `timestamp` | string | 响应生成时间，ISO 8601 UTC 格式 |

#### 2.3.2 分页响应结构

列表类接口返回分页结构：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [
      { "id": "STU20260001", "name": "张同学" }
    ],
    "total": 128,
    "page": 1,
    "page_size": 20
  },
  "request_id": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-08-25T08:30:00Z"
}
```

#### 2.3.3 错误响应结构

```json
{
  "code": 40101,
  "message": "令牌已过期，请重新登录",
  "data": null,
  "errors": [
    {
      "field": "Authorization",
      "message": "JWT expired at 2026-08-25T09:00:00Z"
    }
  ],
  "request_id": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-08-25T08:30:00Z"
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | integer | 错误码，详见第 10 章错误码对照表 |
| `message` | string | 面向用户的可读错误信息（中文） |
| `errors` | array | 字段级错误明细，仅参数校验类错误返回 |
| `errors[].field` | string | 出错字段名 |
| `errors[].message` | string | 字段级错误描述 |

### 2.4 HTTP 状态码使用规范

本项目业务状态码与 HTTP 状态码协同使用。HTTP 状态码反映传输层语义，业务 `code` 字段反映业务语义。

| HTTP 状态码 | 含义 | 使用场景 |
| --- | --- | --- |
| 200 OK | 请求成功 | 所有成功响应（含业务错误，业务错误 code 非 0） |
| 400 Bad Request | 请求格式错误 | JSON 解析失败、必填字段缺失 |
| 401 Unauthorized | 未认证 | 令牌缺失、过期、无效 |
| 403 Forbidden | 无权限 | 已认证但无权访问该资源 |
| 404 Not Found | 资源不存在 | 路径错误或资源 ID 不存在 |
| 429 Too Many Requests | 限流 | 超出限流阈值 |
| 500 Internal Server Error | 服务端错误 | 未捕获异常 |
| 502 Bad Gateway | 网关错误 | 下游服务不可达 |
| 503 Service Unavailable | 服务不可用 | 维护中或降级 |

> 说明：网关层鉴权失败（401/403/429）由网关直接返回，不进入业务服务。业务服务的错误统一以 HTTP 200 + 业务 code 非 0 返回，便于客户端统一处理。

### 2.5 错误码体系概述

错误码采用 5 位整数编码，按区间划分：

| 错误码区间 | 类别 | 说明 |
| --- | --- | --- |
| `0` | 成功 | 请求处理成功 |
| `10000-19999` | 通用错误 | 参数校验、格式、通用业务错误 |
| `20000-29999` | 认证授权错误 | JWT、OAuth、权限相关 |
| `30000-39999` | AI 助手错误 | AI 对话、知识检索、降级相关 |
| `40000-49999` | 家长端错误 | 绑定、令牌、进度查询相关 |
| `50000-59999` | 系统集成错误 | 事件推送、幂等、数据同步相关 |
| `90000-99999` | 系统级错误 | 服务不可用、降级、限流兜底 |

完整错误码列表见第 10 章。

---

## 3 认证与授权方案

### 3.1 认证体系总览

系统采用双轨认证体系，按调用方身份区分：

| 调用方 | 认证方式 | 令牌类型 | 有效期 | 适用接口 |
| --- | --- | --- | --- | --- |
| 新生 | 微信登录 + 学号绑定 → JWT | 学生 JWT | 7 天 | 学生端全部接口 |
| 家长 | 手机号 + 验证码绑定 → JWT | 家长 JWT | 30 天 | 家长端只读接口 |
| 辅导员/管理员 | 统一身份认证 → JWT | 管理端 JWT | 8 小时 | 管理台全部接口 |
| 外部系统 | OAuth 2.0 客户端凭证 → Access Token | OAuth Access Token | 1 小时（可刷新） | 系统集成接口 |

所有令牌均通过 `Authorization` 请求头携带，格式为 `Bearer {token}`（JWT）或 `Bearer {access_token}`（OAuth）。

### 3.2 JWT 令牌结构

#### 3.2.1 令牌组成

JWT 令牌由 Header、Payload、Signature 三部分组成，使用 HS256 算法签名。

**Header:**

```json
{
  "alg": "HS256",
  "typ": "JWT",
  "kid": "fcs-jwt-key-2026"
}
```

**Payload（家长 JWT 示例）:**

```json
{
  "iss": "freshman-checkin-system",
  "sub": "parent:P20260001",
  "aud": "parent-h5",
  "iat": 1724572200,
  "nbf": 1724572200,
  "exp": 1727164200,
  "jti": "8f14e45f-ce2a-463f-b977-43b8a7b8d4e9",
  "role": "parent",
  "student_id": "STU20260001",
  "scope": "parent:read",
  "bind_time": "2026-08-25T08:30:00Z"
}
```

#### 3.2.2 Payload 字段说明

| 字段 | 类型 | 是否必填 | 说明 |
| --- | --- | --- | --- |
| `iss` | string | 是 | 签发方，固定 `freshman-checkin-system` |
| `sub` | string | 是 | 令牌主体，格式 `{role}:{subject_id}`，如 `parent:P20260001`、`student:STU20260001` |
| `aud` | string | 是 | 接收方，标识令牌用途端，如 `parent-h5`、`student-miniapp`、`admin-web` |
| `iat` | integer | 是 | 签发时间（Unix 时间戳，秒） |
| `nbf` | integer | 是 | 生效时间（Unix 时间戳，秒） |
| `exp` | integer | 是 | 过期时间（Unix 时间戳，秒） |
| `jti` | string | 是 | 令牌唯一标识，用于防重放与吊销 |
| `role` | string | 是 | 角色标识：`student`/`parent`/`counselor`/`admin` |
| `student_id` | string | 视角色 | 关联学生 ID，家长 JWT 必填，仅关联一个学生 ID |
| `scope` | string | 是 | 权限范围，如 `parent:read`、`student:full` |
| `bind_time` | string | 家长必填 | 绑定时间，家长 JWT 专有字段 |

#### 3.2.3 安全要求

- 签名算法固定 HS256，密钥独立管理并支持轮换（通过 `kid` 标识密钥版本）；
- 令牌 Payload **不含敏感字段**（身份证号、手机号、密码等），仅含 `student_id` 关联标识；
- `jti` 存入 Redis 吊销列表，令牌主动注销或被管理员吊销后立即失效；
- 网关层校验 `exp`、`nbf`、`iss`、`aud`，任一不匹配则拒绝；
- 令牌传输全程 HTTPS/TLS 1.3，不在 URL 查询参数中传递。

### 3.3 OAuth 2.0 授权（外部系统）

#### 3.3.1 授权流程

外部系统（教务、财务、宿舍、一卡通、人事）采用 OAuth 2.0 客户端凭证模式（Client Credentials Grant）接入：

```
外部系统                      授权服务                    资源服务(本系统)
   |                             |                             |
   |-- 1. POST /oauth/token ---->|                             |
   |   client_id, client_secret  |                             |
   |   grant_type=client_credentials                           |
   |   scope=integration:student:read                          |
   |                             |                             |
   |<-- 2. access_token (1h) ----|                             |
   |   refresh_token (30d)       |                             |
   |                             |                             |
   |-- 3. GET /api/v1/integration/student/{id} -------------->|
   |   Authorization: Bearer {access_token}                    |
   |                             |                             |
   |<-- 4. 学生信息（按scope过滤） ----------------------------|
   |                             |                             |
   |-- 5. access_token 过期后使用 refresh_token 刷新 ---------->|
   |<-- 新的 access_token -------|                             |
```

#### 3.3.2 Scope 权限矩阵

OAuth 2.0 通过 scope 控制外部系统的数据可见范围，敏感字段需额外 scope 授权：

| Scope | 权限说明 | 可见数据字段 | 敏感字段 |
| --- | --- | --- | --- |
| `integration:student:read` | 基础学生信息读取 | student_id, name, college, major, grade, student_type | 无 |
| `integration:student:phone` | 手机号读取 | 在基础字段上增加 phone（脱敏后 4 位） | phone |
| `integration:student:idcard` | 身份证号读取 | 在基础字段上增加 id_card（脱敏后 4 位） | id_card |
| `integration:student:full` | 完整学生信息 | 全部非密级字段 | phone, id_card |
| `integration:event:push` | 事件推送 | 写入事件至 RabbitMQ | 无 |
| `integration:event:read` | 事件订阅 | 订阅核心事件 | 无 |

> 说明：敏感字段（手机号、身份证号）默认不在响应中返回，需外部系统在申请 access_token 时显式声明对应 scope 并经校方信息中心审批授权。敏感字段在响应中始终以脱敏形式返回（手机号 `138****5678`，身份证号 `110***********1234`），明文仅在校内可信系统间传输。

#### 3.3.3 令牌签发与刷新

| 项目 | Access Token | Refresh Token |
| --- | --- | --- |
| 有效期 | 1 小时 | 30 天 |
| 用途 | 调用业务接口 | 刷新 Access Token |
| 刷新端点 | — | `POST /oauth/token`（grant_type=refresh_token） |
| 存储 | 调用方内存，不落盘 | 调用方安全存储，加密落盘 |
| 吊销 | 短期自然过期 | 管理员可吊销，吊销后立即失效 |

刷新请求示例：

```
POST /oauth/token
Content-Type: application/x-www-form-urlencoded

grant_type=refresh_token&refresh_token={refresh_token}&client_id={client_id}
```

### 3.4 令牌刷新机制（JWT）

新生与管理端 JWT 采用短期令牌 + 刷新令牌机制：

| 角色 | Access Token 有效期 | Refresh Token 有效期 | 刷新端点 |
| --- | --- | --- | --- |
| 新生 | 7 天 | 30 天 | `POST /api/v1/auth/refresh` |
| 辅导员/管理员 | 8 小时 | 7 天 | `POST /api/v1/auth/refresh` |
| 家长 | 30 天（无刷新，过期重新绑定） | — | — |

> 家长 JWT 有效期 30 天且不提供刷新机制，过期后需重新走手机号验证码绑定流程（FR-03-02）。这是出于安全考虑：家长端为只读低频场景，重新绑定成本可接受，且绑定流程可重新校验预登记手机号有效性。

刷新请求：

```
POST /api/v1/auth/refresh
Authorization: Bearer {即将过期的access_token}
Content-Type: application/json

{
  "refresh_token": "rt_8f14e45fce2a463fb97743b8a7b8d4e9"
}
```

刷新响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiIs...",
    "refresh_token": "rt_9e25f657df3b574ac088549b8c9e5f0a",
    "token_type": "Bearer",
    "expires_in": 604800
  }
}
```

### 3.5 令牌吊销机制

| 吺销触发场景 | 实现方式 |
| --- | --- |
| 用户主动退出登录 | `jti` 写入 Redis 吊销列表，TTL = 令牌剩余有效期 |
| 管理员强制吊销 | 管理台操作，`jti` 写入 Redis 吊销列表，TTL = 令牌剩余有效期 |
| 家长解除绑定 | 家长 JWT `jti` 吊销，重新绑定签发新令牌 |
| 令牌泄露应急 | 批量吊销指定 `sub` 下所有令牌，强制全部重新认证 |
| OAuth 客户端凭证吊销 | 管理台撤销 client_id，已签发 access_token 自然过期 |

网关鉴权过滤器在每次请求时检查 `jti` 是否在 Redis 吊销列表中，命中则返回 `401 Unauthorized`。

---

## 4 接口安全规范

### 4.1 安全维度总表

| 安全维度 | 实现方案 | 适用范围 | 关联需求 |
| --- | --- | --- | --- |
| 身份认证 | JWT 令牌（新生/家长）+ OAuth 2.0（外部系统） | 所有接口 | FR-03-02、FR-04-02 |
| 数据加密 | HTTPS/TLS 1.3 全链路加密，禁用 TLS 1.0/1.1 | 所有接口 | FR-05-02 |
| 限流防刷 | 网关层令牌桶限流，按接口与租户维度配置 | 高频接口 | FR-04-03 |
| 数据脱敏 | 响应中间件自动脱敏身份证号、手机号等 PII | 含敏感数据接口 | FR-05-04 |
| 审计日志 | 跨系统调用记录请求/响应摘要，保留 180 天 | 集成接口 | NFR-SEC-04 |
| 幂等控制 | 基于 event_id 或 request_id 去重，Redis 缓存去重窗口 | 事件推送接口 | FR-04-05 |
| 输入校验 | 网关层与业务层双重参数校验，防注入 | 所有 POST/PUT 接口 | FR-05-15 |
| 输出控制 | 响应最小化，按角色与 scope 过滤字段 | 所有接口 | FR-05-12、FR-03-07 |
| CORS 策略 | 白名单域名，仅允许已知前端域名跨域 | 浏览器端接口 | FR-05-15 |
| 防重放 | JWT 携带 jti + 时间窗口校验，OAuth nonce 校验 | 认证接口 | NFR-SEC-03 |

### 4.2 数据脱敏规则

响应中间件按字段类型自动脱敏，规则如下：

| 字段类型 | 脱敏规则 | 示例 | 明文可见条件 |
| --- | --- | --- | --- |
| 身份证号 | 保留前 3 位与后 4 位，中间以 `*` 填充 | `110***********1234` | 仅校内可信系统 + `integration:student:idcard` scope |
| 手机号 | 保留前 3 位与后 4 位，中间以 `*` 填充 | `138****5678` | 仅校内可信系统 + `integration:student:phone` scope |
| 姓名 | 保留姓与名末字，中间以 `*` 填充（留学生不脱敏） | `张*学` | 家长端仅显示脱敏后姓名 |
| 银行卡号 | 保留后 4 位 | `************1234` | 不可见，仅对账使用 |
| 一卡通号 | 保留后 4 位 | `********5678` | 学生本人可见完整 |

脱敏在响应序列化前由统一中间件执行，业务代码无需感知。家长端接口额外执行数据最小化过滤，仅返回进度状态与环节名称，不返回任何敏感字段（FR-03-07）。

### 4.3 审计日志规范

#### 4.3.1 审计范围

| 审计级别 | 适用接口 | 记录内容 |
| --- | --- | --- |
| L1 全量审计 | 系统集成接口（事件推送、学生信息查询） | 请求方、时间、请求摘要、响应摘要、耗时、状态码 |
| L2 操作审计 | 家长绑定、令牌签发/刷新/吊销 | 调用方、操作类型、目标资源、时间、结果 |
| L3 访问审计 | 敏感数据查询（含手机号/身份证号的请求） | 调用方、scope、查询字段、脱敏后返回值摘要 |

#### 4.3.2 审计日志结构

```json
{
  "audit_id": "aud-20260825-000001",
  "timestamp": "2026-08-25T08:30:00.123Z",
  "request_id": "550e8400-e29b-41d4-a716-446655440000",
  "caller": {
    "type": "integration",
    "client_id": "edu-system-001",
    "ip": "10.20.30.40"
  },
  "request": {
    "method": "GET",
    "path": "/api/v1/integration/student/STU20260001",
    "headers_summary": { "scope": "integration:student:read" },
    "body_summary": null
  },
  "response": {
    "status": 200,
    "code": 0,
    "latency_ms": 45,
    "data_summary": "returned 6 fields, 0 sensitive"
  }
}
```

#### 4.3.3 存储与保留

- 审计日志写入独立日志库（Elasticsearch），与业务日志隔离；
- 保留期 180 天，超期自动归档至冷存储；
- 审计日志不可篡改，写入即追加，不支持修改与删除（仅合规审计可按流程脱敏归档）；
- 审计日志中不含明文 PII，敏感字段以脱敏后值或字段标识记录。

### 4.4 幂等控制设计

#### 4.4.1 幂等适用范围

| 接口 | 幂等键 | 去重窗口 | 实现 |
| --- | --- | --- | --- |
| `POST /api/v1/integration/event` | `event_id`（请求体内） | 24 小时 | Redis SETNX + 持久化去重表 |
| `POST /api/v1/parent/bind` | `phone` + `verify_code` 组合 | 5 分钟 | Redis 计数器 |
| `POST /api/v1/ai/chat` | `request_id`（请求头） | 60 秒 | Redis 缓存 |

#### 4.4.2 幂等处理流程（事件推送）

```
外部系统 --POST /integration/event--> 网关 --鉴权--> 事件服务
                                                       |
                                          1. Redis SETNX(event_id, "processing", TTL=24h)
                                             |-- 成功(首次) --> 写入 RabbitMQ --> 返回 200, code=0
                                             |-- 失败(重复) --> 查询去重表
                                                  |-- 已成功处理 --> 返回 200, code=0, idempotent_replay=true
                                                  |-- 处理中    --> 返回 409, code=50003
                                                  |-- 处理失败  --> 返回 200, code=0, 触发重试
```

---

## 5 AI 助手接口详细定义

### 5.1 POST /api/v1/ai/chat — AI 对话接口

#### 5.1.1 接口概述

| 项目 | 说明 |
| --- | --- |
| URL | `POST /api/v1/ai/chat` |
| 功能 | 新生向 AI 助手发起对话，获取基于知识库检索增强生成的回答 |
| 认证 | 学生 JWT（`Authorization: Bearer {token}`） |
| 限流 | 10 次/分钟/学生（令牌桶） |
| 安全 | 身份认证、传输加密、AI 内容标识、拒答护栏、提示词注入防护 |
| 关联需求 | FR-01-01 至 FR-01-18、FR-05-07 至 FR-05-10 |
| 响应模式 | 支持 SSE 流式响应（`Accept: text/event-stream`）与普通 JSON 响应 |

#### 5.1.2 请求头

| 请求头 | 必填 | 说明 |
| --- | --- | --- |
| `Authorization` | 是 | `Bearer {student_jwt}` |
| `Content-Type` | 是 | `application/json; charset=utf-8` |
| `Accept` | 否 | `application/json`（默认，普通响应）或 `text/event-stream`（流式响应） |
| `X-Request-Id` | 否 | 请求唯一标识，用于幂等与链路追踪 |

#### 5.1.3 请求体

```json
{
  "student_id": "STU20260001",
  "session_id": "sess-20260825-0001",
  "message": "报到需要准备哪些材料？",
  "context": {
    "current_step": "material_upload",
    "student_type": "undergraduate",
    "step_index": 2
  },
  "stream": false
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `student_id` | string | 是 | 学生 ID，需与 JWT 中 `student_id` 一致 |
| `session_id` | string | 否 | 会话 ID，用于多轮上下文（FR-01-06），未提供时新建会话 |
| `message` | string | 是 | 用户提问内容，长度 1-500 字符 |
| `context.current_step` | string | 否 | 当前报到环节标识，如 `checkin`、`payment`、`verification`、`dorm_assign`、`material_upload` |
| `context.student_type` | string | 否 | 学生类型：`undergraduate`/`postgraduate`/`international` |
| `context.step_index` | integer | 否 | 当前环节序号 |
| `stream` | boolean | 否 | 是否流式响应，默认 `false`。设为 `true` 或请求头 `Accept: text/event-stream` 时启用流式 |

#### 5.1.4 响应体（普通模式）

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "session_id": "sess-20260825-0001",
    "message_id": "msg-20260825-00001",
    "reply": "本科新生报到需准备以下材料：\n1. 录取通知书原件\n2. 身份证原件及复印件 2 份\n3. 近期一寸免冠照片 8 张\n4. 户口迁移证（自愿迁移者）\n5. 团组织关系转接证明\n\n建议您将材料按顺序整理，到校后按报到流程逐环节提交。",
    "sources": [
      {
        "doc_id": "kb-manual-2026-v3",
        "title": "2026 级本科新生报到手册",
        "section": "第三章 材料清单",
        "snippet": "本科新生报到需准备录取通知书、身份证、照片等材料...",
        "score": 0.92
      },
      {
        "doc_id": "kb-faq-001",
        "title": "报到常见问题",
        "section": "材料类",
        "snippet": "照片建议使用近期一寸免冠彩色照片...",
        "score": 0.85
      }
    ],
    "content_label": {
      "is_ai_generated": true,
      "label_type": "ai_content",
      "label_text": "本内容由 AI 生成，仅供参考",
      "degraded": false,
      "degrade_mode": null
    },
    "tools_invoked": [],
    "transfer_to_human": false,
    "tokens": {
      "prompt": 512,
      "completion": 128,
      "total": 640
    }
  },
  "request_id": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-08-25T08:30:00Z"
}
```

#### 5.1.5 响应字段说明

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.session_id` | string | 会话 ID，后续多轮对话需携带 |
| `data.message_id` | string | 本次回答消息 ID |
| `data.reply` | string | AI 回复正文 |
| `data.sources` | array | 知识库来源列表，支持溯源（FR-01-12、FR-01-14） |
| `data.sources[].doc_id` | string | 知识文档 ID |
| `data.sources[].title` | string | 文档标题 |
| `data.sources[].section` | string | 命中章节 |
| `data.sources[].snippet` | string | 命中片段（已脱敏，不含明文 PII，FR-05-09） |
| `data.sources[].score` | float | 检索相关性分数（0-1） |
| `data.content_label` | object | AI 内容标识（FR-01-14、FR-05-07） |
| `data.content_label.is_ai_generated` | boolean | 是否为 AI 生成内容 |
| `data.content_label.label_type` | string | 标识类型：`ai_content`（正常）/ `faq_mode`（降级模式） |
| `data.content_label.label_text` | string | 面向用户的标识文案 |
| `data.content_label.degraded` | boolean | 是否处于降级模式（FR-01-17） |
| `data.content_label.degrade_mode` | string \| null | 降级模式：`faq_keyword`（FAQ 关键词匹配）/ `null`（正常） |
| `data.tools_invoked` | array | MCP 工具调用记录（FR-01-15、FR-01-16） |
| `data.transfer_to_human` | boolean | 是否触发转人工（FR-01-05） |
| `data.tokens` | object | token 用量统计 |

#### 5.1.6 MCP 工具调用响应示例

当 AI 判断需调用系统能力时，`tools_invoked` 返回工具调用记录：

```json
{
  "tools_invoked": [
    {
      "tool_name": "navigate_to",
      "tool_id": "mcp-nav-001",
      "params": { "destination": "图书馆", "poi_id": "poi-lib-001" },
      "result": "导航已发起，目的地：图书馆（北门进入后左转 200 米）",
      "success": true
    }
  ]
}
```

#### 5.1.7 限流规则

| 维度 | 阈值 | 算法 | 超限响应 |
| --- | --- | --- | --- |
| 单学生 | 10 次/分钟 | 令牌桶（bucket=10, rate=10/min） | HTTP 429, code=90001 |
| 全局 | 500 次/秒 | 令牌桶（兜底保护） | HTTP 429, code=90002 |

#### 5.1.8 错误码

| HTTP | code | message | 触发场景 |
| --- | --- | --- | --- |
| 401 | 20001 | 令牌无效或已过期 | JWT 校验失败 |
| 403 | 20004 | 无权访问该学生数据 | student_id 与 JWT 不匹配 |
| 429 | 90001 | AI 对话频率超限（10次/分钟） | 触发限流 |
| 400 | 10001 | 参数校验失败：message 不能为空 | message 为空 |
| 400 | 10002 | 参数校验失败：message 长度超出限制 | message > 500 字符 |
| 200 | 30001 | AI 服务暂时不可用，已降级为 FAQ 模式 | DeepSeek 不可用，降级成功（FR-01-17） |
| 200 | 30003 | 未检索到相关知识，建议联系辅导员 | 知识库未覆盖 |
| 200 | 30004 | 该问题超出 AI 服务范围，已为您转接辅导员 | 命中拒答护栏（FR-05-08） |
| 200 | 30005 | 检测到疑似提示词注入，请求已被拦截 | 提示词注入防护命中（FR-05-10） |
| 503 | 90003 | AI 服务不可用，请稍后重试 | 降级也失败 |

#### 5.1.9 安全要求

- 提示词注入防护：用户输入经护栏前置校验，命中注入特征即拦截（FR-05-10）；
- 拒答护栏：超出迎新范围、违法、敏感问题拒答并引导转人工（FR-05-08）；
- 知识库脱敏：检索片段不含明文 PII（FR-05-09）；
- 内容标识：每条 AI 回复携带不可移除的内容标识（FR-01-14、FR-05-07）；
- 首词响应：流式模式下首 token 到达客户端 P95 ≤ 2 秒（NFR-PER-03）。

---

### 5.2 SSE 流式响应协议

#### 5.2.1 启用方式

客户端在请求头设置 `Accept: text/event-stream` 或请求体 `stream: true` 时，服务端以 SSE 流式返回。

#### 5.2.2 SSE 响应格式

响应 `Content-Type: text/event-stream`，以 UTF-8 编码的 `event:` 与 `data:` 行组成的事件流推送：

```
event: meta
data: {"session_id":"sess-20260825-0001","message_id":"msg-20260825-00001"}

event: token
data: {"delta":"本科新生报到","index":0}

event: token
data: {"delta":"需准备以下","index":1}

event: token
data: {"delta":"材料：","index":2}

event: sources
data: {"sources":[{"doc_id":"kb-manual-2026-v3","title":"2026级本科新生报到手册","section":"第三章 材料清单","score":0.92}]}

event: tool
data: {"tool_name":"navigate_to","tool_id":"mcp-nav-001","params":{"destination":"图书馆"},"success":true}

event: label
data: {"is_ai_generated":true,"label_type":"ai_content","label_text":"本内容由AI生成，仅供参考","degraded":false}

event: done
data: {"message_id":"msg-20260825-00001","finish_reason":"stop","tokens":{"prompt":512,"completion":128,"total":640}}
```

#### 5.2.3 SSE 事件类型

| event | 说明 | data 内容 |
| --- | --- | --- |
| `meta` | 流开始元信息 | session_id, message_id |
| `token` | 回复文本增量片段 | delta（增量文本）, index（片段序号） |
| `sources` | 知识库来源 | sources 数组（同普通响应 sources 字段） |
| `tool` | MCP 工具调用 | tool_name, tool_id, params, result, success |
| `label` | AI 内容标识 | content_label 对象（同普通响应 content_label 字段） |
| `transfer` | 转人工通知 | reason（情绪/未解决）, counselor_id, context_summary |
| `error` | 流式错误 | code, message（非致命错误，流可继续或终止） |
| `done` | 流结束 | message_id, finish_reason, tokens |

#### 5.2.4 finish_reason 取值

| 值 | 说明 |
| --- | --- |
| `stop` | 正常生成完成 |
| `length` | 达到最大长度限制 |
| `degraded` | 降级为 FAQ 模式完成 |
| `blocked` | 命中拒答护栏，停止生成 |
| `transferred` | 转人工，停止 AI 生成 |

#### 5.2.5 客户端断连处理

- 客户端断连后服务端检测到写失败即停止生成，释放资源；
- 已生成内容不缓存，客户端需在 60 秒内通过 `X-Request-Id` 重试获取完整响应（幂等）；
- 服务端通过心跳（每 15 秒发送 `event: ping`）维持连接。

---

### 5.3 WS /api/v1/ai/chat/stream — 流式对话 WebSocket

#### 5.3.1 接口概述

| 项目 | 说明 |
| --- | --- |
| URL | `WS /api/v1/ai/chat/stream`（升级自 `GET /api/v1/ai/chat/stream`） |
| 功能 | 建立 WebSocket 全双工连接，实时推送 AI 回复片段 |
| 认证 | 连接时通过查询参数传递 JWT：`?token={student_jwt}` |
| 限流 | 单学生同时仅允许 1 个活跃连接；10 次/分钟消息发送 |
| 安全 | JWT 鉴权、消息大小限制（单帧 4KB）、连接超时（无活动 5 分钟断开） |

#### 5.3.2 连接建立

```
GET /api/v1/ai/chat/stream?token=eyJhbGciOi...&student_id=STU20260001
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
Sec-WebSocket-Version: 13
```

握手成功后返回 HTTP 101 Switching Protocols，建立 WebSocket 连接。JWT 在握手阶段校验，失败返回 HTTP 401。

#### 5.3.3 客户端发送消息格式

```json
{
  "type": "chat",
  "message_id": "cli-msg-001",
  "session_id": "sess-20260825-0001",
  "student_id": "STU20260001",
  "message": "图书馆怎么走？",
  "context": {
    "current_step": "checkin",
    "student_type": "undergraduate"
  }
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `type` | string | 是 | 消息类型：`chat`（对话）/ `ping`（心跳）/ `cancel`（取消生成） |
| `message_id` | string | 是 | 客户端消息 ID，用于关联服务端回复 |
| `session_id` | string | 否 | 会话 ID |
| `student_id` | string | 是 | 学生 ID |
| `message` | string | type=chat 时必填 | 提问内容 |
| `context` | object | 否 | 报到上下文，同 5.1.3 |

#### 5.3.4 服务端推送消息格式

服务端通过 WebSocket 帧推送消息，每条消息携带 token 增量和完成标志：

```json
{
  "type": "token",
  "ref_message_id": "cli-msg-001",
  "server_message_id": "msg-20260825-00002",
  "delta": "图书馆位于",
  "index": 0,
  "done": false
}
```

```json
{
  "type": "token",
  "ref_message_id": "cli-msg-001",
  "server_message_id": "msg-20260825-00002",
  "delta": "校园东区，",
  "index": 1,
  "done": false
}
```

```json
{
  "type": "complete",
  "ref_message_id": "cli-msg-001",
  "server_message_id": "msg-20260825-00002",
  "full_reply": "图书馆位于校园东区，从北门进入后沿主干道直行约 300 米即可到达。",
  "sources": [
    {
      "doc_id": "kb-poi-001",
      "title": "校园 POI 信息",
      "section": "图书馆",
      "score": 0.95
    }
  ],
  "content_label": {
    "is_ai_generated": true,
    "label_type": "ai_content",
    "label_text": "本内容由 AI 生成，仅供参考",
    "degraded": false,
    "degrade_mode": null
  },
  "tools_invoked": [],
  "finish_reason": "stop",
  "done": true
}
```

#### 5.3.5 服务端消息类型

| type | 说明 | 关键字段 |
| --- | --- | --- |
| `start` | 开始生成 | ref_message_id, server_message_id |
| `token` | 回复片段增量 | delta, index, done（固定 false） |
| `sources` | 知识库来源 | sources 数组 |
| `tool` | MCP 工具调用 | tool_name, params, result, success |
| `label` | AI 内容标识 | content_label 对象 |
| `transfer` | 转人工通知 | reason, counselor_id |
| `complete` | 生成完成 | full_reply, finish_reason, done（固定 true） |
| `error` | 错误 | code, message, done |
| `pong` | 心跳响应 | — |

#### 5.3.6 错误码

| code | message | 触发场景 |
| --- | --- | --- |
| 20001 | 令牌无效或已过期 | 握手阶段 JWT 校验失败 |
| 30002 | 同时仅允许一个活跃连接 | 重复连接 |
| 30006 | 消息发送频率超限 | 超出 10 次/分钟 |
| 30007 | 单帧消息大小超限 | 单帧超过 4KB |
| 30008 | 会话已超时，请重新连接 | 无活动超过 5 分钟 |
| 30003 | 未检索到相关知识，建议联系辅导员 | 知识库未覆盖 |
| 30004 | 该问题超出 AI 服务范围，已为您转接辅导员 | 拒答护栏 |
| 30005 | 检测到疑似提示词注入，请求已被拦截 | 注入防护 |

---

## 6 家长端接口详细定义

### 6.1 POST /api/v1/parent/bind — 家长绑定

#### 6.1.1 接口概述

| 项目 | 说明 |
| --- | --- |
| URL | `POST /api/v1/parent/bind` |
| 功能 | 家长通过手机号 + 验证码绑定预登记关系，成功后下发 JWT 令牌 |
| 认证 | 无需认证（公开接口），但需先调用 `POST /api/v1/parent/verify-code` 获取验证码 |
| 限流 | 5 次/分钟/手机号（防刷验证码） |
| 安全 | 传输加密、验证码限频、预登记校验、令牌不含敏感字段 |
| 关联需求 | FR-03-01、FR-03-02、FR-05-14 |

#### 6.1.2 绑定流程

```
家长 H5                     网关                    家长服务
  |                           |                        |
  |-- 1. POST /parent/verify-code --->|                 |
  |   { phone }               |                        |
  |                           |-- 校验预登记手机号 ---->|
  |                           |<-- 匹配则下发验证码 ----|
  |<-- 2. 验证码已发送 -------|                        |
  |                           |                        |
  |-- 3. POST /parent/bind -->|                        |
  |   { phone, verify_code }  |                        |
  |                           |-- 校验验证码 ---------->|
  |                           |<-- 签发 JWT(30天) ------|
  |<-- 4. JWT 令牌 + 关联学生ID -|                        |
```

#### 6.1.3 请求头

| 请求头 | 必填 | 说明 |
| --- | --- | --- |
| `Content-Type` | 是 | `application/json; charset=utf-8` |
| `X-Request-Id` | 否 | 请求唯一标识 |

#### 6.1.4 请求体

```json
{
  "phone": "13812345678",
  "verify_code": "836201"
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `phone` | string | 是 | 家长手机号，需与预登记手机号匹配（FR-03-01） |
| `verify_code` | string | 是 | 6 位数字验证码，5 分钟有效 |

#### 6.1.5 响应体

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJmcmVzaG1hbi1jaGVja2luLXN5c3RlbSIsInN1YiI6InBhcmVudDpQMjAyNjAwMDEiLCJhdWQiOiJwYXJlbnQtaDUiLCJpYXQiOjE3MjQ1NzIyMDAsImV4cCI6MTcyNzE2NDIwMCwianRpIjoiOGYxNGU0NWYtY2UyYS00NjNmLWI5NzctNDNiOGE3YjhkNGU5Iiwicm9sZSI6InBhcmVudCIsInN0dWRlbnRfaWQiOiJTVFUyMDI2MDAwMSIsInNjb3BlIjoicGFyZW50OnJlYWQiLCJiaW5kX3RpbWUiOiIyMDI2LTA4LTI1VDA4OjMwOjAwWiJ9.signature",
    "token_type": "Bearer",
    "expires_in": 2592000,
    "student_id": "STU20260001",
    "student_name_masked": "张*学",
    "bind_id": "P20260001"
  },
  "request_id": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-08-25T08:30:00Z"
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.token` | string | 家长 JWT 令牌，有效期 30 天（FR-03-02） |
| `data.token_type` | string | 固定 `Bearer` |
| `data.expires_in` | integer | 有效期（秒），30 天 = 2592000 秒 |
| `data.student_id` | string | 关联学生 ID（令牌仅关联此 ID） |
| `data.student_name_masked` | string | 学生脱敏姓名 |
| `data.bind_id` | string | 绑定记录 ID |

#### 6.1.6 错误码

| HTTP | code | message | 触发场景 |
| --- | --- | --- | --- |
| 400 | 40001 | 手机号格式不正确 | phone 非法 |
| 400 | 40002 | 验证码不能为空 | verify_code 为空 |
| 200 | 40003 | 该手机号未在预登记名单中，无法绑定 | phone 不匹配预登记（FR-03-01） |
| 200 | 40004 | 验证码错误或已过期 | verify_code 校验失败 |
| 200 | 40005 | 验证码错误次数过多，请 30 分钟后重试 | 连续错误超限锁定 |
| 429 | 90004 | 绑定请求过于频繁，请稍后再试 | 触发限流 |
| 200 | 40006 | 该手机号已绑定，无需重复绑定 | 重复绑定（返回已有令牌或提示） |

#### 6.1.7 限流规则

| 维度 | 阈值 | 算法 | 说明 |
| --- | --- | --- | --- |
| 单手机号 | 5 次/分钟 | 令牌桶 | 防止验证码爆破 |
| 单 IP | 20 次/分钟 | 令牌桶 | 防止分布式刷接口 |
| 验证码错误 | 5 次锁定 30 分钟 | 计数器 | 防止暴力枚举验证码 |

#### 6.1.8 安全要求

- 预登记校验：仅预登记手机号可发起绑定（FR-03-01）；
- 验证码有效期 5 分钟，限频防刷；
- JWT 令牌仅关联学生 ID，不含敏感字段（FR-03-02）；
- 令牌可被管理员吊销（见 3.5 节）；
- 家长授权依据预登记关系，仅可查看本人关联学生（FR-05-14）。

---

### 6.2 GET /api/v1/parent/progress — 查询报到进度

#### 6.2.1 接口概述

| 项目 | 说明 |
| --- | --- |
| URL | `GET /api/v1/parent/progress` |
| 功能 | 家长查询关联学生的报到环节进度、到校状态与待办材料清单（仅名称） |
| 认证 | 家长 JWT（`Authorization: Bearer {token}`） |
| 限流 | 30 次/分钟/家长 |
| 安全 | 数据最小化原则，不返回身份证号、宿舍门牌号、缴费金额等敏感信息（FR-03-07） |
| 关联需求 | FR-03-03、FR-03-04、FR-03-05、FR-03-07 |

#### 6.2.2 请求头

| 请求头 | 必填 | 说明 |
| --- | --- | --- |
| `Authorization` | 是 | `Bearer {parent_jwt}` |
| `X-Request-Id` | 否 | 请求唯一标识 |

#### 6.2.3 请求参数

无请求体，学生 ID 从 JWT `student_id` 字段提取，家长不可指定查询其他学生。

#### 6.2.4 响应体

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "student_id": "STU20260001",
    "student_name_masked": "张*学",
    "overall_status": "in_progress",
    "progress_percent": 60,
    "checkin_status": "arrived",
    "checkin_date": "2026-08-25",
    "steps": [
      {
        "step_id": "step-01",
        "step_name": "现场签到",
        "step_index": 1,
        "status": "completed",
        "completed_at": "2026-08-25T08:35:00Z"
      },
      {
        "step_id": "step-02",
        "step_name": "材料提交",
        "step_index": 2,
        "status": "completed",
        "completed_at": "2026-08-25T09:10:00Z"
      },
      {
        "step_id": "step-03",
        "step_name": "缴费",
        "step_index": 3,
        "status": "completed",
        "completed_at": "2026-08-25T09:45:00Z"
      },
      {
        "step_id": "step-04",
        "step_name": "身份核验",
        "step_index": 4,
        "status": "in_progress",
        "completed_at": null
      },
      {
        "step_id": "step-05",
        "step_name": "宿舍分配",
        "step_index": 5,
        "status": "pending",
        "completed_at": null
      }
    ],
    "pending_materials": [
      { "name": "录取通知书原件", "submitted": true },
      { "name": "身份证复印件", "submitted": true },
      { "name": "近期一寸照片", "submitted": false }
    ]
  },
  "request_id": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-08-25T10:00:00Z"
}
```

#### 6.2.5 响应字段说明

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.student_name_masked` | string | 学生脱敏姓名（不返回完整姓名） |
| `data.overall_status` | string | 整体状态：`not_started`/`in_progress`/`completed` |
| `data.progress_percent` | integer | 进度百分比（0-100） |
| `data.checkin_status` | string | 到校状态：`not_arrived`/`arrived`（FR-03-05） |
| `data.checkin_date` | string \| null | 到校日期（精确到日，FR-03-05），未到校为 null |
| `data.steps` | array | 环节列表，与新生端一致但仅含名称与状态（FR-03-03、FR-03-04） |
| `data.steps[].status` | string | 环节状态：`pending`/`in_progress`/`completed` |
| `data.steps[].completed_at` | string \| null | 完成时间戳 |
| `data.pending_materials` | array | 待办材料清单，仅含名称与是否已提交（FR-03-07） |

> 数据最小化说明：响应中不返回身份证号、宿舍门牌号、缴费金额、一卡通余额等敏感字段（FR-03-07）。`pending_materials` 仅返回材料名称与提交状态，不返回材料内容与审核详情。

#### 6.2.6 错误码

| HTTP | code | message | 触发场景 |
| --- | --- | --- | --- |
| 401 | 20001 | 令牌无效或已过期 | JWT 校验失败 |
| 401 | 20002 | 令牌已过期，请重新绑定 | JWT exp 过期 |
| 403 | 40007 | 无权查询该学生信息 | 令牌被吊销或 student_id 不匹配 |
| 404 | 40008 | 学生信息不存在 | 关联学生 ID 无效 |
| 429 | 90005 | 查询频率超限（30次/分钟） | 触发限流 |

#### 6.2.7 限流规则

| 维度 | 阈值 | 算法 |
| --- | --- | --- |
| 单家长 | 30 次/分钟 | 令牌桶（bucket=30, rate=30/min） |

#### 6.2.8 安全要求

- 数据最小化：仅返回进度状态与环节名称，不返回敏感字段（FR-03-07）；
- 权限隔离：家长仅可查询令牌关联的学生，越权查询被拒（FR-03-07）；
- 到校状态精确到日，不暴露精确时间（FR-03-05）。

---

### 6.3 消息推送说明

#### 6.3.1 推送场景

学生签到后，系统在 10 秒内向绑定家长推送微信通知（FR-03-06、NFR-PER-05）。

#### 6.3.2 推送流程

```
学生签到 --> 触发 student.checkin.success 事件 --> RabbitMQ
  --> 家长通知消费者 --> 查询绑定关系 --> 微信模板消息推送 --> 家长微信
```

#### 6.3.3 推送内容

推送内容遵循数据最小化原则，仅包含到校提示，不含敏感信息：

```
标题：到校通知
内容：您关注的新生已安全到校，报到进度可点击查看详情。
跳转：家长端 H5 进度页
```

> 推送内容不含学生姓名、学号、宿舍等敏感信息，家长点击通知后进入 H5 页面，经 JWT 鉴权后查看进度详情。

---

## 7 系统集成接口详细定义

### 7.1 POST /api/v1/integration/event — 系统事件推送

#### 7.1.1 接口概述

| 项目 | 说明 |
| --- | --- |
| URL | `POST /api/v1/integration/event` |
| 功能 | 外部系统向本系统推送事件，网关 OAuth 鉴权后写入 RabbitMQ 异步处理 |
| 认证 | OAuth 2.0 Access Token（scope: `integration:event:push`） |
| 限流 | 100 次/秒/客户端（令牌桶） |
| 安全 | OAuth 鉴权、幂等控制（event_id 去重）、审计日志 |
| 关联需求 | FR-04-01、FR-04-02、FR-04-03、FR-04-05 |

#### 7.1.2 请求头

| 请求头 | 必填 | 说明 |
| --- | --- | --- |
| `Authorization` | 是 | `Bearer {oauth_access_token}` |
| `Content-Type` | 是 | `application/json; charset=utf-8` |
| `X-Client-Id` | 是 | OAuth 客户端 ID |
| `X-Request-Id` | 否 | 请求唯一标识 |

#### 7.1.3 请求体

```json
{
  "event_id": "evt-20260825-000001",
  "event_type": "student.checkin.success",
  "source": "edu-system",
  "source_event_id": "edu-evt-78901",
  "occurred_at": "2026-08-25T08:35:00Z",
  "student_id": "STU20260001",
  "payload": {
    "checkin_location": "北门报到处",
    "checkin_method": "qr_scan",
    "operator_id": "counselor-001"
  }
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `event_id` | string | 是 | 事件唯一标识，用于幂等去重（24 小时窗口） |
| `event_type` | string | 是 | 事件类型，见第 8 章核心事件类型 |
| `source` | string | 是 | 事件来源系统标识 |
| `source_event_id` | string | 否 | 来源系统原始事件 ID，用于双向追溯 |
| `occurred_at` | string | 是 | 事件发生时间，ISO 8601 UTC |
| `student_id` | string | 是 | 关联学生 ID |
| `payload` | object | 是 | 事件载荷，结构因 event_type 而异 |

#### 7.1.4 响应体

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "event_id": "evt-20260825-000001",
    "status": "accepted",
    "idempotent_replay": false,
    "queue": "rabbitmq://checkin.exchange/checkin.queue"
  },
  "request_id": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-08-25T08:35:01Z"
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.event_id` | string | 回显事件 ID |
| `data.status` | string | 接收状态：`accepted`（已接收）/ `duplicate`（重复事件） |
| `data.idempotent_replay` | boolean | 是否为幂等重放（true 表示该 event_id 已处理过） |
| `data.queue` | string | 投递的目标队列信息 |

#### 7.1.5 幂等设计

幂等控制基于 `event_id` 去重，处理流程见 4.4.2 节。

| 项目 | 说明 |
| --- | --- |
| 幂等键 | `event_id`（请求体内，由调用方生成） |
| 去重窗口 | 24 小时 |
| 存储介质 | Redis（热查询）+ MySQL 持久化去重表（冷兜底） |
| 并发控制 | Redis SETNX 原子操作，确保并发请求仅一个进入处理 |
| 重复请求响应 | 返回 HTTP 200，`code=0`，`data.status="duplicate"`，`data.idempotent_replay=true` |
| 处理中重复 | 返回 HTTP 409，`code=50003`，提示事件处理中 |

幂等去重表结构（MySQL）：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `event_id` | varchar(64) PK | 事件唯一标识 |
| `event_type` | varchar(64) | 事件类型 |
| `source` | varchar(64) | 来源系统 |
| `status` | varchar(16) | 处理状态：`processing`/`succeeded`/`failed` |
| `received_at` | datetime | 接收时间 |
| `processed_at` | datetime | 处理完成时间 |
| `result_summary` | varchar(512) | 处理结果摘要 |

#### 7.1.6 错误码

| HTTP | code | message | 触发场景 |
| --- | --- | --- | --- |
| 401 | 20003 | OAuth 令牌无效或已过期 | access_token 校验失败 |
| 403 | 20005 | 无事件推送权限，缺少 scope: integration:event:push | scope 不匹配 |
| 400 | 10003 | 参数校验失败：event_id 不能为空 | event_id 缺失 |
| 400 | 10004 | 参数校验失败：event_type 不合法 | event_type 非已知类型 |
| 400 | 10005 | 参数校验失败：payload 结构不符合 event_type 规范 | payload 校验失败 |
| 200 | 50001 | 事件已接收，正在异步处理 | 正常成功 |
| 200 | 50002 | 重复事件，已忽略（幂等重放） | event_id 重复 |
| 409 | 50003 | 事件处理中，请勿重复推送 | event_id 处理中重复推送 |
| 429 | 90006 | 事件推送频率超限（100次/秒） | 触发限流 |
| 500 | 50004 | 事件处理失败，已入死信队列 | RabbitMQ 投递失败 |

#### 7.1.7 限流规则

| 维度 | 阈值 | 算法 | 说明 |
| --- | --- | --- | --- |
| 单客户端 | 100 次/秒 | 令牌桶（bucket=100, rate=100/s） | 保护 RabbitMQ 与消费者 |
| 全局 | 2000 次/秒 | 令牌桶（兜底保护） | 防止多客户端同时洪峰 |

#### 7.1.8 安全要求

- OAuth 2.0 客户端凭证鉴权，scope 校验（FR-04-02）；
- 幂等控制基于 event_id 去重，防止重复事件导致状态错乱（FR-04-05）；
- 审计日志记录请求/响应摘要，保留 180 天（NFR-SEC-04）；
- 事件载荷不含明文 PII，敏感字段在入队前脱敏（FR-05-05）；
- 消息可靠性保障：RabbitMQ 持久化队列 + 消费确认，消息不丢失（NFR-MAINT-04）；
- 死信队列处理失败事件，支持重放（FR-04-05 异常处理）。

---

### 7.2 GET /api/v1/integration/student/{student_id} — 学生信息查询

#### 7.2.1 接口概述

| 项目 | 说明 |
| --- | --- |
| URL | `GET /api/v1/integration/student/{student_id}` |
| 功能 | 供外部系统查询学生信息，OAuth 2.0 scope 控制数据可见范围，敏感字段需额外 scope 授权 |
| 认证 | OAuth 2.0 Access Token |
| 限流 | 50 次/秒/客户端 |
| 安全 | OAuth scope 控制、数据脱敏、审计日志 |
| 关联需求 | FR-04-01、FR-04-02、FR-04-14、FR-05-04 |

#### 7.2.2 请求头

| 请求头 | 必填 | 说明 |
| --- | --- | --- |
| `Authorization` | 是 | `Bearer {oauth_access_token}` |
| `X-Client-Id` | 是 | OAuth 客户端 ID |
| `X-Request-Id` | 否 | 请求唯一标识 |

#### 7.2.3 路径参数

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `student_id` | string | 是 | 学生 ID |

#### 7.2.4 查询参数

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `fields` | string | 否 | 指定返回字段，逗号分隔，如 `name,college,major`；不指定时按 scope 返回全部可见字段 |

#### 7.2.5 响应体（基础 scope: integration:student:read）

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "student_id": "STU20260001",
    "name": "张同学",
    "college": "计算机科学与技术学院",
    "major": "软件工程",
    "grade": "2026",
    "student_type": "undergraduate",
    "checkin_status": "arrived",
    "report_status": "in_progress",
    "updated_at": "2026-08-25T09:45:00Z"
  },
  "request_id": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-08-25T10:00:00Z"
}
```

#### 7.2.6 响应体（含敏感字段 scope: integration:student:full）

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "student_id": "STU20260001",
    "name": "张同学",
    "college": "计算机科学与技术学院",
    "major": "软件工程",
    "grade": "2026",
    "student_type": "undergraduate",
    "checkin_status": "arrived",
    "report_status": "in_progress",
    "phone": "138****5678",
    "id_card": "110***********1234",
    "dorm_building": "3号楼",
    "dorm_room": "302",
    "card_number": "********5678",
    "payment_status": "paid",
    "updated_at": "2026-08-25T09:45:00Z"
  },
  "request_id": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-08-25T10:00:00Z"
}
```

#### 7.2.7 响应字段与 scope 映射

| 字段 | 基础 scope 可见 | 敏感字段 scope | 说明 |
| --- | --- | --- | --- |
| `student_id` | 是 | — | 学生 ID |
| `name` | 是 | — | 姓名 |
| `college` | 是 | — | 学院 |
| `major` | 是 | — | 专业 |
| `grade` | 是 | — | 年级 |
| `student_type` | 是 | — | 学生类型 |
| `checkin_status` | 是 | — | 到校状态 |
| `report_status` | 是 | — | 报到状态 |
| `phone` | 否 | `integration:student:phone` | 手机号（脱敏） |
| `id_card` | 否 | `integration:student:idcard` | 身份证号（脱敏） |
| `dorm_building` | 否 | `integration:student:full` | 宿舍楼栋 |
| `dorm_room` | 否 | `integration:student:full` | 宿舍房间号 |
| `card_number` | 否 | `integration:student:full` | 一卡通号（脱敏） |
| `payment_status` | 否 | `integration:student:full` | 缴费状态 |

> Scope 控制说明：基础 scope（`integration:student:read`）仅返回非敏感字段。敏感字段需外部系统在申请 access_token 时显式声明对应 scope 并经校方信息中心审批授权。所有敏感字段在响应中始终以脱敏形式返回，明文仅在校内可信系统间传输（FR-05-04）。

#### 7.2.8 错误码

| HTTP | code | message | 触发场景 |
| --- | --- | --- | --- |
| 401 | 20003 | OAuth 令牌无效或已过期 | access_token 校验失败 |
| 403 | 20006 | 无权访问敏感字段，缺少对应 scope | 请求敏感字段但 scope 不足 |
| 404 | 50005 | 学生信息不存在 | student_id 无效 |
| 429 | 90007 | 查询频率超限（50次/秒） | 触发限流 |

#### 7.2.9 限流规则

| 维度 | 阈值 | 算法 |
| --- | --- | --- |
| 单客户端 | 50 次/秒 | 令牌桶（bucket=50, rate=50/s） |
| 全局 | 1000 次/秒 | 令牌桶（兜底保护） |

#### 7.2.10 安全要求

- OAuth 2.0 客户端凭证鉴权，scope 逐字段控制数据可见范围；
- 敏感字段（手机号、身份证号、宿舍门牌号、一卡通号）默认不返回，需额外 scope 授权；
- 响应中敏感字段自动脱敏（FR-05-04）；
- 审计日志记录调用方、scope、查询字段，保留 180 天（NFR-SEC-04）；
- 主数据映射：通过学号-身份证号-一卡通号映射表关联各系统数据（FR-04-14）。

---

## 8 限流策略

### 8.1 限流架构

限流在 API 网关层（Spring Cloud Gateway）统一实现，采用令牌桶算法。网关为每个限流维度维护独立令牌桶，请求到达时消耗令牌，令牌不足时返回 HTTP 429。

```
客户端请求 --> API 网关
                |-- 路由匹配
                |-- 鉴权过滤器
                |-- 限流过滤器（令牌桶）
                |     |-- 单用户维度（Redis 计数）
                |     |-- 单 IP 维度（Redis 计数）
                |     |-- 全局维度（本地计数）
                |-- 协议适配
                --> 后端服务
```

### 8.2 限流规则总表

| 接口 | 限流维度 | 阈值 | 算法 | 超限响应 |
| --- | --- | --- | --- | --- |
| `POST /api/v1/ai/chat` | 单学生 | 10 次/分钟 | 令牌桶 | 429, code=90001 |
| `POST /api/v1/ai/chat` | 全局 | 500 次/秒 | 令牌桶 | 429, code=90002 |
| `WS /api/v1/ai/chat/stream` | 单学生连接数 | 1 个活跃连接 | 计数器 | 拒绝连接, code=30002 |
| `WS /api/v1/ai/chat/stream` | 单学生消息 | 10 次/分钟 | 令牌桶 | code=30006 |
| `POST /api/v1/parent/bind` | 单手机号 | 5 次/分钟 | 令牌桶 | 429, code=90004 |
| `POST /api/v1/parent/bind` | 单 IP | 20 次/分钟 | 令牌桶 | 429, code=90004 |
| `POST /api/v1/parent/bind` | 验证码错误 | 5 次锁定 30 分钟 | 计数器 | 200, code=40005 |
| `GET /api/v1/parent/progress` | 单家长 | 30 次/分钟 | 令牌桶 | 429, code=90005 |
| `POST /api/v1/integration/event` | 单客户端 | 100 次/秒 | 令牌桶 | 429, code=90006 |
| `POST /api/v1/integration/event` | 全局 | 2000 次/秒 | 令牌桶 | 429, code=90006 |
| `GET /api/v1/integration/student/{id}` | 单客户端 | 50 次/秒 | 令牌桶 | 429, code=90007 |
| `GET /api/v1/integration/student/{id}` | 全局 | 1000 次/秒 | 令牌桶 | 429, code=90007 |
| 学生端核心接口 | 单学生 | 60 次/分钟 | 令牌桶 | 429, code=90008 |
| 管理端接口 | 单用户 | 120 次/分钟 | 令牌桶 | 429, code=90009 |

### 8.3 限流响应格式

```json
{
  "code": 90001,
  "message": "AI 对话频率超限（10次/分钟），请稍后再试",
  "data": {
    "retry_after": 12,
    "limit": 10,
    "window": 60
  },
  "request_id": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-08-25T08:30:00Z"
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.retry_after` | integer | 建议重试等待秒数 |
| `data.limit` | integer | 当前限流阈值 |
| `data.window` | integer | 限流窗口（秒） |

### 8.4 限流响应头

限流响应携带标准响应头，便于客户端感知限流状态：

| 响应头 | 说明 | 示例 |
| --- | --- | --- |
| `X-RateLimit-Limit` | 限流阈值 | `10` |
| `X-RateLimit-Remaining` | 当前窗口剩余配额 | `3` |
| `X-RateLimit-Reset` | 限流窗口重置时间（Unix 时间戳） | `1724572260` |
| `Retry-After` | 建议重试等待秒数 | `12` |

---

## 9 核心事件类型定义

### 9.1 事件载荷通用结构

所有核心事件遵循统一的信封结构，业务载荷在 `payload` 字段内按事件类型定义：

```json
{
  "event_id": "evt-20260825-000001",
  "event_type": "student.checkin.success",
  "source": "checkin-service",
  "source_event_id": null,
  "occurred_at": "2026-08-25T08:35:00Z",
  "student_id": "STU20260001",
  "version": "1.0",
  "payload": { }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `event_id` | string | 事件唯一标识，用于幂等去重 |
| `event_type` | string | 事件类型，见 9.2 节 |
| `source` | string | 事件来源服务/系统 |
| `source_event_id` | string \| null | 来源系统原始事件 ID |
| `occurred_at` | string | 事件发生时间，ISO 8601 UTC |
| `student_id` | string | 关联学生 ID |
| `version` | string | 事件 schema 版本 |
| `payload` | object | 事件业务载荷，结构因 event_type 而异 |

### 9.2 核心事件类型清单

#### 9.2.1 student.checkin.success — 新生到校签到

| 项目 | 说明 |
| --- | --- |
| 事件类型 | `student.checkin.success` |
| 触发条件 | 新生现场签到成功 |
| 生产者 | 签到服务 |
| RabbitMQ Exchange | `checkin.exchange` |
| 路由键 | `student.checkin.success` |

```json
{
  "event_type": "student.checkin.success",
  "payload": {
    "checkin_location": "北门报到处",
    "checkin_method": "qr_scan",
    "checkin_time": "2026-08-25T08:35:00Z",
    "operator_id": "counselor-001",
    "device_id": "scanner-device-001"
  }
}
```

| payload 字段 | 类型 | 说明 |
| --- | --- | --- |
| `checkin_location` | string | 签到地点 |
| `checkin_method` | string | 签到方式：`qr_scan`/`manual`/`face` |
| `checkin_time` | string | 签到时间 |
| `operator_id` | string | 操作人 ID（辅导员） |
| `device_id` | string | 签到设备 ID |

#### 9.2.2 student.payment.completed — 缴费完成

| 项目 | 说明 |
| --- | --- |
| 事件类型 | `student.payment.completed` |
| 触发条件 | 学生缴费完成（财务系统回调或线下缴费确认） |
| 生产者 | 缴费服务 / 财务系统集成 |
| RabbitMQ Exchange | `payment.exchange` |
| 路由键 | `student.payment.completed` |

```json
{
  "event_type": "student.payment.completed",
  "payload": {
    "payment_id": "PAY20260825001",
    "payment_type": "tuition",
    "amount_cents": 580000,
    "currency": "CNY",
    "payment_method": "online",
    "payment_time": "2026-08-25T09:45:00Z",
    "receipt_no": "RCP20260825001"
  }
}
```

| payload 字段 | 类型 | 说明 |
| --- | --- | --- |
| `payment_id` | string | 缴费记录 ID |
| `payment_type` | string | 缴费类型：`tuition`/`dorm`/`insurance`/`card` |
| `amount_cents` | integer | 金额（分），避免浮点精度问题 |
| `currency` | string | 币种，固定 `CNY` |
| `payment_method` | string | 缴费方式：`online`/`offline`/`wechat`/`alipay` |
| `payment_time` | string | 缴费时间 |
| `receipt_no` | string | 票据号 |

> 说明：事件载荷中金额为整数分，响应给家长端时不展示金额（FR-03-07），仅财务系统与对账场景使用。

#### 9.2.3 student.verified.success — 身份核验通过

| 项目 | 说明 |
| --- | --- |
| 事件类型 | `student.verified.success` |
| 触发条件 | 学生身份核验通过 |
| 生产者 | 核验服务 |
| RabbitMQ Exchange | `verification.exchange` |
| 路由键 | `student.verified.success` |

```json
{
  "event_type": "student.verified.success",
  "payload": {
    "verification_method": "id_card_face",
    "verification_time": "2026-08-25T10:15:00Z",
    "verifier_id": "counselor-001",
    "match_score": 0.98
  }
}
```

| payload 字段 | 类型 | 说明 |
| --- | --- | --- |
| `verification_method` | string | 核验方式：`id_card_face`/`manual`/`biometric` |
| `verification_time` | string | 核验时间 |
| `verifier_id` | string | 核验人 ID |
| `match_score` | float | 人脸匹配分数（0-1），仅 biometric 方式 |

#### 9.2.4 student.checkin.completed — 全部环节完成

| 项目 | 说明 |
| --- | --- |
| 事件类型 | `student.checkin.completed` |
| 触发条件 | 学生全部报到环节完成，报到归档 |
| 生产者 | 报到流程服务 |
| RabbitMQ Exchange | `checkin.exchange` |
| 路由键 | `student.checkin.completed` |

```json
{
  "event_type": "student.checkin.completed",
  "payload": {
    "completed_steps": ["step-01", "step-02", "step-03", "step-04", "step-05"],
    "total_steps": 5,
    "start_time": "2026-08-25T08:35:00Z",
    "complete_time": "2026-08-25T11:20:00Z",
    "duration_minutes": 165,
    "archive_id": "ARCH2026-00001"
  }
}
```

| payload 字段 | 类型 | 说明 |
| --- | --- | --- |
| `completed_steps` | array | 已完成环节 ID 列表 |
| `total_steps` | integer | 环节总数 |
| `start_time` | string | 报到开始时间 |
| `complete_time` | string | 报到完成时间 |
| `duration_minutes` | integer | 报到总耗时（分钟） |
| `archive_id` | string | 归档记录 ID |

### 9.3 消费者路由

核心事件经 RabbitMQ 投递后，由多个消费者按路由键订阅，实现事件驱动解耦（FR-04-05、FR-04-06）：

| 事件类型 | Exchange | 路由键 | 消费者队列 | 消费者服务 | 处理动作 |
| --- | --- | --- | --- | --- | --- |
| `student.checkin.success` | `checkin.exchange` | `student.checkin.success` | `parent.notify.queue` | 家长通知服务 | 推送家长到校通知（FR-03-06） |
| `student.checkin.success` | `checkin.exchange` | `student.checkin.success` | `edu.sync.queue` | 教务集成服务 | 同步学籍状态至教务系统 |
| `student.checkin.success` | `checkin.exchange` | `student.checkin.success` | `card.activate.queue` | 一卡通服务 | 开通一卡通 |
| `student.payment.completed` | `payment.exchange` | `student.payment.completed` | `edu.sync.queue` | 教务集成服务 | 同步缴费状态 |
| `student.payment.completed` | `payment.exchange` | `student.payment.completed` | `dorm.assign.queue` | 宿舍管理服务 | 触发宿舍分配 |
| `student.verified.success` | `verification.exchange` | `student.verified.success` | `edu.sync.queue` | 教务集成服务 | 同步核验状态 |
| `student.verified.success` | `verification.exchange` | `student.verified.success` | `dorm.assign.queue` | 宿舍管理服务 | 确认分配资格 |
| `student.checkin.completed` | `checkin.exchange` | `student.checkin.completed` | `archive.queue` | 归档服务 | 报到记录归档 |
| `student.checkin.completed` | `checkin.exchange` | `student.checkin.completed` | `edu.sync.queue` | 教务集成服务 | 同步最终报到状态 |
| `student.checkin.completed` | `checkin.exchange` | `student.checkin.completed` | `parent.notify.queue` | 家长通知服务 | 推送报到完成通知 |

### 9.4 事件链流转顺序

核心事件依业务顺序流转（FR-04-06），前序事件未触发时后序事件不可触发：

```
student.checkin.success（签到）
        --> student.payment.completed（缴费）
                --> student.verified.success（核验）
                        --> [宿舍分配完成]
                                --> student.checkin.completed（全部完成）
```

集成层监控事件链完整性，缺环时告警并支持补正（FR-04-06 异常处理）。事件链状态存储于 Redis，每个学生维护当前已触发事件集合，消费者处理事件时校验前序事件是否已完成。

### 9.5 事件可靠性保障

| 保障维度 | 实现方案 |
| --- | --- |
| 消息持久化 | RabbitMQ 队列与消息均持久化（durable=true, persistent=true） |
| 消费确认 | 消费者处理成功后手动 ACK，失败不 ACK 触发重试 |
| 死信队列 | 重试 3 次仍失败的消息进入死信队列（DLX），人工介入 |
| 幂等消费 | 消费者基于 event_id 去重，防止重复消费导致状态错乱 |
| 顺序保障 | 同一 student_id 的事件通过一致路由键路由至同一队列，保证顺序消费 |
| 断点续传 | CDC 数据同步支持断点续传，无漏单（FR-04-07） |

---

## 10 错误码对照表

### 10.1 错误码编码规则

错误码采用 5 位整数编码，按区间划分（见 2.5 节）。HTTP 状态码与业务 code 协同使用：网关层错误（鉴权、限流）由网关直接返回 HTTP 4xx/5xx，业务层错误以 HTTP 200 + 业务 code 非 0 返回。

### 10.2 通用错误码（10000-19999）

| code | HTTP | message | 触发场景 |
| --- | --- | --- | --- |
| 0 | 200 | success | 请求处理成功 |
| 10001 | 400 | 参数校验失败：{field} 不能为空 | 必填字段缺失 |
| 10002 | 400 | 参数校验失败：{field} 长度超出限制 | 字段超长 |
| 10003 | 400 | 参数校验失败：event_id 不能为空 | 事件 ID 缺失 |
| 10004 | 400 | 参数校验失败：event_type 不合法 | 事件类型未知 |
| 10005 | 400 | 参数校验失败：payload 结构不符合规范 | 事件载荷校验失败 |
| 10006 | 400 | 请求体 JSON 格式错误 | JSON 解析失败 |
| 10007 | 400 | 不支持的 Content-Type | Content-Type 非法 |
| 10008 | 404 | 请求的资源不存在 | 路径或资源 ID 无效 |
| 10009 | 405 | 请求方法不被允许 | HTTP 方法错误 |
| 10010 | 400 | 请求参数类型错误 | 参数类型不匹配 |

### 10.3 认证授权错误码（20000-29999）

| code | HTTP | message | 触发场景 |
| --- | --- | --- | --- |
| 20001 | 401 | 令牌无效或已过期 | JWT 校验失败（签名错误、格式错误） |
| 20002 | 401 | 令牌已过期，请重新登录/绑定 | JWT exp 过期 |
| 20003 | 401 | OAuth 令牌无效或已过期 | OAuth access_token 校验失败 |
| 20004 | 403 | 无权访问该资源 | 权限不足（学生 ID 不匹配） |
| 20005 | 403 | 无权限：缺少 scope {scope} | OAuth scope 不匹配 |
| 20006 | 403 | 无权访问敏感字段，缺少对应 scope | 请求敏感字段但 scope 不足 |
| 20007 | 401 | 令牌已被吊销 | JWT jti 在吊销列表中 |
| 20008 | 401 | 未提供认证令牌 | Authorization 头缺失 |
| 20009 | 401 | 令牌签发方不匹配 | JWT iss 校验失败 |
| 20010 | 401 | 令牌受众不匹配 | JWT aud 校验失败 |

### 10.4 AI 助手错误码（30000-39999）

| code | HTTP | message | 触发场景 |
| --- | --- | --- | --- |
| 30001 | 200 | AI 服务暂时不可用，已降级为 FAQ 模式 | DeepSeek 不可用，降级成功（FR-01-17） |
| 30002 | 200 | 同时仅允许一个活跃连接 | WebSocket 重复连接 |
| 30003 | 200 | 未检索到相关知识，建议联系辅导员 | 知识库未覆盖 |
| 30004 | 200 | 该问题超出 AI 服务范围，已为您转接辅导员 | 拒答护栏命中（FR-05-08） |
| 30005 | 200 | 检测到疑似提示词注入，请求已被拦截 | 提示词注入防护（FR-05-10） |
| 30006 | 200 | 消息发送频率超限 | WebSocket 消息超 10 次/分钟 |
| 30007 | 200 | 单帧消息大小超限 | WebSocket 单帧超过 4KB |
| 30008 | 200 | 会话已超时，请重新连接 | WebSocket 无活动超 5 分钟 |
| 30009 | 200 | 生成已被用户取消 | 客户端发送 cancel |
| 30010 | 200 | MCP 工具调用失败，已回退为文字引导 | 工具调用异常 |

### 10.5 家长端错误码（40000-49999）

| code | HTTP | message | 触发场景 |
| --- | --- | --- | --- |
| 40001 | 400 | 手机号格式不正确 | phone 非法 |
| 40002 | 400 | 验证码不能为空 | verify_code 为空 |
| 40003 | 200 | 该手机号未在预登记名单中，无法绑定 | phone 不匹配预登记（FR-03-01） |
| 40004 | 200 | 验证码错误或已过期 | verify_code 校验失败 |
| 40005 | 200 | 验证码错误次数过多，请 30 分钟后重试 | 连续错误超限锁定 |
| 40006 | 200 | 该手机号已绑定，无需重复绑定 | 重复绑定 |
| 40007 | 403 | 无权查询该学生信息 | 令牌被吊销或 student_id 不匹配 |
| 40008 | 404 | 学生信息不存在 | 关联学生 ID 无效 |
| 40009 | 200 | 验证码已发送，请 60 秒后重试 | 验证码发送限频 |

### 10.6 系统集成错误码（50000-59999）

| code | HTTP | message | 触发场景 |
| --- | --- | --- | --- |
| 50001 | 200 | 事件已接收，正在异步处理 | 事件推送正常成功 |
| 50002 | 200 | 重复事件，已忽略（幂等重放） | event_id 重复 |
| 50003 | 409 | 事件处理中，请勿重复推送 | event_id 处理中重复推送 |
| 50004 | 500 | 事件处理失败，已入死信队列 | RabbitMQ 投递失败 |
| 50005 | 404 | 学生信息不存在 | 查询的 student_id 无效 |
| 50006 | 200 | 数据同步中，请稍后查询 | CDC 同步进行中 |
| 50007 | 500 | 主数据映射失败 | 学号-身份证号-一卡通号映射缺失（FR-04-14） |
| 50008 | 502 | 下游系统不可达 | 外部系统对接失败 |
| 50009 | 200 | 字段冲突已告警 | 数据同步字段冲突（FR-04-09） |

### 10.7 系统级错误码（90000-99999）

| code | HTTP | message | 触发场景 |
| --- | --- | --- | --- |
| 90001 | 429 | AI 对话频率超限（10次/分钟） | AI 对话限流 |
| 90002 | 429 | 系统繁忙，请稍后再试 | AI 全局限流 |
| 90003 | 503 | AI 服务不可用，请稍后重试 | AI 降级也失败 |
| 90004 | 429 | 绑定请求过于频繁，请稍后再试 | 家长绑定限流 |
| 90005 | 429 | 查询频率超限（30次/分钟） | 家长进度查询限流 |
| 90006 | 429 | 事件推送频率超限（100次/秒） | 事件推送限流 |
| 90007 | 429 | 查询频率超限（50次/秒） | 学生信息查询限流 |
| 90008 | 429 | 请求频率超限，请稍后再试 | 学生端核心接口限流 |
| 90009 | 429 | 请求频率超限，请稍后再试 | 管理端接口限流 |
| 90010 | 503 | 系统维护中，请稍后再试 | 维护模式 |
| 90011 | 502 | 网关错误 | 下游服务不可达 |
| 90012 | 500 | 系统内部错误 | 未捕获异常 |
| 90013 | 503 | 服务降级中，部分功能不可用 | 熔断降级 |

---

## 附录 A：接口端点速查表

| 序号 | 方法 | URL | 认证 | 限流 | 所属模块 |
| --- | --- | --- | --- | --- | --- |
| 1 | POST | `/api/v1/ai/chat` | 学生 JWT | 10 次/分钟 | AI 助手 |
| 2 | WS | `/api/v1/ai/chat/stream` | 学生 JWT | 1 连接 + 10 次/分钟 | AI 助手 |
| 3 | POST | `/api/v1/parent/bind` | 无（验证码） | 5 次/分钟 | 家长端 |
| 4 | GET | `/api/v1/parent/progress` | 家长 JWT | 30 次/分钟 | 家长端 |
| 5 | POST | `/api/v1/integration/event` | OAuth 2.0 | 100 次/秒 | 系统集成 |
| 6 | GET | `/api/v1/integration/student/{student_id}` | OAuth 2.0 | 50 次/秒 | 系统集成 |

## 附录 B：核心事件类型速查表

| 事件类型 | 说明 | 生产者 | 消费者 |
| --- | --- | --- | --- |
| `student.checkin.success` | 新生到校签到 | 签到服务 | 家长通知、教务同步、一卡通开通 |
| `student.payment.completed` | 缴费完成 | 缴费服务 | 教务同步、宿舍分配 |
| `student.verified.success` | 身份核验通过 | 核验服务 | 教务同步、宿舍分配确认 |
| `student.checkin.completed` | 全部环节完成 | 报到流程服务 | 归档、教务同步、家长通知 |

---

> 本文档为微迎新（CampusArrive） v1.1 的 API 接口设计基线，经研发组评审通过后作为前后端联调、第三方对接与安全审计的统一依据。文档范围内的接口字段变更须经技术负责人评审并更新版本号。