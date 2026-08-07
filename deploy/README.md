# 微迎新 CampusArrive — Docker 部署指南 (INFRA-1.2)

本文档描述「微迎新 CampusArrive」项目的 Docker 容器化部署方案，覆盖三套环境（SIT / UAT / PERF）一键拉起、网络分区隔离、数据卷持久化与健康检查。

---

## 1. 环境要求

| 组件 | 版本要求 |
| --- | --- |
| Docker Engine | 24.0+ |
| Docker Compose | v2.24+（覆盖文件使用 `!override` 语法） |
| 操作系统 | Linux（推荐 CentOS 7+ / Ubuntu 22.04+） |
| 单机最低配置 | 4C 8G（按拓扑分机部署；单机跑全套建议 8C 16G） |
| 磁盘 | ≥ 50GB（含数据卷、镜像、日志） |

**部署主机需具备的外部能力**：
- 可访问公网（拉取镜像、DeepSeek API）；数据区主机可不联公网。
- 外层反向代理（nginx / 云 LB）负责 443 终结与校外 IP 过滤（接入区仅放行 443/80）。

---

## 2. 部署拓扑

依据 SAD 第 7 节，采用「三服务器 + Docker 容器化精简拓扑」：

```
        ┌─────────────────────────────────────────────────────────────┐
        │                   外层 Nginx / LB (443/80)                 │
        │            仅放行 443，拒绝校外 IP（接入区策略）             │
        └───────────────────────────────┬───────────────────────────┘
                                        │ 8080
   ┌────────────────────────────────────┴───────────────────────────┐
   │ 服务器B (4C8G 应用与中间件)        campus-frontend / campus-backend │
   │   ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────────────┐    │
   │   │ gateway  │  │  redis   │  │ rabbitmq │  │ 5 微服务    │    │
   │   │ :8080对外 │  │ :6379内  │  │ :5672内   │  │ :8081~8084 │    │
   │   └──────────┘  └──────────┘  └──────────┘  └────────────┘    │
   │   ┌──────────────────┐                                          │
   │   │ Debezium/Kafka   │  CDC → MySQL                                              │
   │   │ Connect :8083   │                                                          │
   │   └──────────────────┘                                                          │
   └──────────┬───────────────────────────────┬─────────────────────────────────┘
              │ campus-data (internal)         │ campus-ai
   ┌──────────┴──────────────────┐   ┌─────────┴──────────────────────┐
   │ 服务器C (4C8G 数据与备机)     │   │ 服务器A (4C8G AI 专用)           │
   │   MySQL 主库 :3306(内)        │   │   MaxKB :8088(外)               │
   │   MySQL 备库 (镜像)           │   │   PostgreSQL/pgvector :5432(内) │
   │   RabbitMQ 节点2(镜像)        │   └────────────────────────────────┘
   └──────────────────────────────┘
```

### 网络分区（SAD 7.3 节落地）

| 分区 | 网络 | 性质 | 容器 | 对外端口 |
| --- | --- | --- | --- | --- |
| 接入区 | `campus-frontend` | bridge | gateway | 8080（生产由 LB 暴露 443/80） |
| 应用区 | `campus-backend` | bridge | 5 微服务 + Redis + RabbitMQ + Debezium Connect | 无（RabbitMQ 管理 UI 仅绑 127.0.0.1） |
| 数据区 | `campus-data` | **internal** | MySQL + Kafka + Zookeeper | 无（禁止出站，仅接受应用区容器连接） |
| AI 区 | `campus-ai` | bridge | ai-service + MaxKB + pgvector | MaxKB 8088 |

> `campus-data` 使用 `docker network create --internal` 创建，从网络层禁止数据区容器主动出站，强制仅接受应用区指定容器连接。

---

## 3. 文件结构

```
deploy/
├── docker-compose.yml              # 主编排：MySQL/Redis/RabbitMQ + 5 微服务
├── docker-compose.maxkb.yml        # MaxKB + PostgreSQL/pgvector (服务器A)
├── docker-compose.debezium.yml     # CDC：Zookeeper/Kafka/Debezium Connect
├── docker-compose.sit.yml          # SIT 环境覆盖（端口/容器名/资源限制）
├── Dockerfile                      # 通用多阶段 Java 服务镜像（SERVICE_NAME 参数化）
├── .dockerignore                   # 构建上下文排除项
├── .env.example                    # 环境变量模板（复制为 .env 后填写）
├── README.md                       # 本文档
└── scripts/
    ├── deploy.sh                   # 一键部署（sit/uat/perf + 栈选择）
    └── health-check.sh             # 健康检查，输出彩色状态表
```

---

## 4. 快速启动

### 4.1 准备环境变量

```bash
cd deploy
cp .env.example .env          # 主环境（SIT 默认）
# 编辑 .env，填入真实密码 / JWT_SECRET / DeepSeek API Key / MaxKB 配置
```

> 三套环境分别使用 `.env`（SIT）、`.env.uat`（UAT）、`.env.perf`（PERF）。部署脚本会按环境自动选择对应文件。

### 4.2 一键拉起

```bash
# SIT 环境，拉起全部栈
./scripts/deploy.sh sit

# UAT 环境，仅应用栈
./scripts/deploy.sh uat app

# PERF 环境，应用 + AI 栈
./scripts/deploy.sh perf app maxkb

# 可选栈：app | maxkb | debezium（默认全部）
```

脚本依次完成：校验 `.env` → 创建 4 个网络分区 → 拉起所选栈 → 等待微服务 actuator/health 就绪 → 输出容器状态。

### 4.3 查看健康状态

```bash
./scripts/health-check.sh           # 单次输出彩色状态表
./scripts/health-check.sh --watch   # 每 10s 刷新
```

输出示例：
```
微迎新 CampusArrive — 健康状态报告  2026-08-07 10:00:00
----------------------------------------------------------------------
SERVICE                PORT     ZONE       STATUS       DETAIL
gateway                8080     接入区      UP
checkin-service         8081     应用区      UP
...
全部健康（12 项）
```

---

## 5. 三套环境切换说明

| 环境 | 部署脚本 | 环境文件 | Compose 覆盖 | 项目名 | 说明 |
| --- | --- | --- | --- | --- | --- |
| SIT | `./scripts/deploy.sh sit` | `.env` | `docker-compose.sit.yml` | `campusarrive-sit` | 系统集成测试，端口改高位（如 gateway→18080）避免冲突 |
| UAT | `./scripts/deploy.sh uat` | `.env.uat` | — | `campusarrive-uat` | 用户验收，标准端口，建议独立主机 |
| PERF | `./scripts/deploy.sh perf` | `.env.perf` | — | `campusarrive-perf` | 性能压测，需独立主机并放宽资源限制 |

- 三套环境通过 `COMPOSE_PROJECT_NAME` 隔离容器与命名空间，互不影响。
- SIT 覆盖文件将容器名加 `-sit` 后缀、端口改高位，可与其他环境在同一主机共存（资源充足时）。
- UAT / PERF 建议部署在独立主机（标准端口 8080），避免与 SIT 端口冲突。
- 切换环境时：先 `docker compose -p campusarrive-<旧环境> down`，再执行新环境部署脚本。

---

## 6. 数据卷与持久化

数据统一挂载到 `deploy/data/` 目录：

| 服务 | 宿主机路径 | 容器路径 |
| --- | --- | --- |
| MySQL | `./data/mysql` | `/var/lib/mysql` |
| Redis | `./data/redis` | `/data` |
| RabbitMQ | `./data/rabbitmq` | `/var/lib/rabbitmq` |
| MaxKB | `./data/maxkb` | `/var/lib/maxkb` |
| PostgreSQL/pgvector | `./data/postgres-pgvector` | `/var/lib/postgresql/data` |
| Kafka | `./data/kafka` | `/var/lib/kafka/data` |
| Zookeeper | `./data/zookeeper` | `/var/lib/zookeeper/data` |

备份建议：对 `./data/mysql`、`./data/postgres-pgvector`、`./data/rabbitmq` 定期快照；MySQL 主备由服务器C 备库承载。

---

## 7. 通用服务镜像构建

`Dockerfile` 采用多阶段构建，通过 `SERVICE_NAME` 参数指定模块：

- **构建阶段**：`maven:3.9-eclipse-temurin-17`，`mvn -pl backend/<module> -am package -DskipTests`，利用 BuildKit 缓存挂载加速。
- **运行阶段**：`eclipse-temurin:17-jre-alpine`，内置 `curl`（供 healthcheck）、`Asia/Shanghai` 时区、容器感知 JVM 参数（`MaxRAMPercentage=75`）。
- **healthcheck**：默认探测 `http://localhost:${SERVER_PORT}/actuator/health`，`start_period=60s` 适配 Spring Boot 30~40s 启动。

构建命令示例：
```bash
docker build -t campusarrive/gateway:latest \
  --build-arg SERVICE_NAME=gateway -f deploy/Dockerfile .
```

---

## 8. 常见问题排查

**Q1：网关路由 `lb://checkin-service` 报「无法解析服务实例」。**
A：Spring Cloud Gateway 的 `lb://` 协议需要服务发现客户端（Eureka/Consul）或 `spring-cloud-loadbalancer` 静态配置。本拓扑未部署注册中心，需在 `gateway/application.yml` 中将路由 `uri` 改为容器 DNS 直连（如 `http://checkin-service:8081`），或引入轻量服务发现。此为应用配置层任务，不属本 INFRA-1.2 范围。

**Q2：health-check 显示内部服务 DOWN / NO-PROXY。**
A：内部微服务不映射宿主机端口，脚本经 `campus-gateway` 容器在内网探测。若 gateway 未启动则报 NO-PROXY；请先确保 `campus-gateway` 健康。

**Q3：MySQL 容器启动失败 / 权限错误。**
A：`./data/mysql` 目录属主需为 999（mysql 用户）。清理后重启：`rm -rf ./data/mysql/* && ./scripts/deploy.sh sit app`。

**Q4：`!override` 语法报错。**
A：SIT 覆盖文件使用 `!override`，需 Docker Compose v2.24+。执行 `docker compose version` 升级。

**Q5：ai-service 无法连接 MaxKB。**
A：两者需在同一 `campus-ai` 网络。先启动 maxkb 栈（`./scripts/deploy.sh sit maxkb`），确认 `campus-maxkb` 健康后再起 app 栈。`MAXKB_BASE_URL` 应为 `http://maxkb:8080`（容器 DNS 名）。

**Q6：端口 8080 被占用。**
A：SIT 环境已将 gateway 映射到 18080；UAT/PERF 用标准 8080，需保证宿主机端口空闲或部署在独立主机。

**Q7：如何查看实时日志。**
A：`docker compose -f docker-compose.yml logs -f <service>`，或 `docker logs -f campus-gateway`。

**Q8：数据区网络 `--internal` 导致 Debezium 无法连外网。**
A：设计如此。Debezium 仅需访问 Kafka（同在 `campus-data`）与 MySQL（`campus-data`），不需出站；其 8083 管理端口经 `campus-backend` 暴露。

---

## 9. 安全注意事项

- `.env` 文件含敏感凭据，**禁止提交到版本库**（已在 `.gitignore` 排除建议）。
- 生产环境：MySQL/Redis/RabbitMQ 密码使用强随机值；JWT_SECRET 至少 256 位。
- 接入区仅放行 443/80，由外层 Nginx/LB 完成 TLS 终结与校外 IP 过滤；gateway 8080 不直接对公网开放。
- RabbitMQ 管理 UI（15672）仅绑定 `127.0.0.1`；Debezium 8083 仅限运维网段访问。
- 数据区 `campus-data` 为 internal 网络，数据库容器无法主动外联。
