# 测试部署服务器快速指南

> 面向测试部署服务器，从 GitHub 拉取代码到一键拉起 SIT 环境的完整操作手册。

---

## 1. 服务器环境要求

| 组件 | 最低版本 | 安装命令（Ubuntu/Debian） |
| --- | --- | --- |
| Docker Engine | 24.0+ | `curl -fsSL https://get.docker.com \| sh` |
| Docker Compose | v2.20+（插件版） | Docker 安装时自带，验证 `docker compose version` |
| Git | 2.34+ | `apt-get install -y git` |
| curl | 任意 | `apt-get install -y curl` |
| JDK 17（可选，仅本地构建时需要） | 17+ | `apt-get install -y openjdk-17-jdk` |

**硬件最低配置**：4C8G、50GB 磁盘（单机全量部署 SIT 环境）。

---

## 2. 从 GitHub 拉取代码

```bash
# 选定部署目录
cd /opt

# 克隆仓库（HTTPS）
git clone https://github.com/dingyuana/CampusArrive.git
cd CampusArrive

# 确认代码为最新
git pull origin master
```

如需指定分支或 Tag：

```bash
# 切换到指定分支
git checkout develop

# 或切换到指定 Tag
git checkout v1.1.0-sit
```

---

## 3. 配置环境变量

```bash
cd deploy

# 从模板复制配置文件
cp .env.example .env

# 编辑 .env，填入真实密码和密钥
vi .env
```

**必须修改的配置项**：

| 配置项 | 说明 | 示例 |
| --- | --- | --- |
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码 | `RootPwd@SIT2026` |
| `MYSQL_PASSWORD` | 业务库密码 | `AppPwd@SIT2026` |
| `REDIS_PASSWORD` | Redis 密码 | `RedisPwd@SIT2026` |
| `RABBITMQ_DEFAULT_PASS` | RabbitMQ 密码 | `MqPwd@SIT2026` |
| `JWT_SECRET` | JWT 签名密钥（≥32字节） | 随机 256bit 字符串 |
| `DEEPSEEK_API_KEY` | DeepSeek API Key | `sk-xxxxxxxx` |
| `MAXKB_API_KEY` | MaxKB 应用 API Key | MaxKB 控制台获取 |

> **安全提醒**：`.env` 文件已在 `.gitignore` 中排除，不会提交到版本库。生产密码请勿与 SIT 共用。

---

## 4. 一键部署 SIT 环境

```bash
cd deploy

# 赋予脚本执行权限
chmod +x scripts/deploy.sh scripts/health-check.sh

# 拉起 SIT 环境（全部组件：应用栈 + MaxKB + Debezium）
./scripts/deploy.sh sit

# 或仅拉起应用栈（不含 AI/CDC）
./scripts/deploy.sh sit app
```

脚本会自动完成：
1. 创建 4 个 Docker 网络分区（`campus-data` 为 internal 隔离）
2. 构建微服务 Docker 镜像（多阶段构建，约 3-5 分钟）
3. 拉起全部容器并等待健康检查通过
4. 输出容器状态一览表

---

## 5. 验证部署

### 5.1 健康检查

```bash
# 一次性检查
./scripts/health-check.sh

# 持续监控（每 10s 刷新）
./scripts/health-check.sh --watch
```

### 5.2 手动验证各服务

```bash
# 网关（唯一对外端口）
curl http://localhost:8080/actuator/health

# 内部服务（经网关容器内网访问）
docker exec campus-gateway curl -s http://checkin-service:8081/actuator/health
docker exec campus-gateway curl -s http://ai-service:8082/actuator/health
docker exec campus-gateway curl -s http://parent-service:8083/actuator/health
docker exec campus-gateway curl -s http://integration-service:8084/actuator/health
```

### 5.3 查看日志

```bash
# 全部服务日志
docker compose -f docker-compose.yml logs -f

# 指定服务日志
docker compose -f docker-compose.yml logs -f gateway
docker compose -f docker-compose.yml logs -f checkin-service
```

---

## 6. 网络拓扑

```
                    ┌─────────────────────────────────────────┐
                    │              校园内网                      │
                    │                                         │
   外部请求 ──443──▶│  ┌─────────────┐                        │
                    │  │  gateway     │ campus-frontend        │
                    │  │  :8080       │                        │
                    │  └──────┬───────┘                        │
                    │         │ campus-backend                  │
                    │  ┌──────┴───────────────────────────┐    │
                    │  │ checkin :8081  ai :8082          │    │
                    │  │ parent  :8083  integration :8084  │    │
                    │  │ redis   :6379  rabbitmq :5672    │    │
                    │  └──────┬───────────────────────────┘    │
                    │         │ campus-data (internal)          │
                    │  ┌──────┴───────────┐                    │
                    │  │  mysql :3306     │                    │
                    │  └──────────────────┘                    │
                    └─────────────────────────────────────────┘
```

| 网络分区 | 安全等级 | 可达组件 |
| --- | --- | --- |
| `campus-frontend` | 边界 | 仅 gateway，对外暴露 8080 |
| `campus-backend` | 内部 | 微服务 + Redis + RabbitMQ，仅接受网关转发 |
| `campus-data` | 核心 | MySQL，internal 网络禁止出站 |
| `campus-ai` | 内部 | ai-service + MaxKB |

---

## 7. 常用运维命令

```bash
# 停止全部服务
docker compose -f docker-compose.yml --env-file .env down

# 停止并删除数据卷（⚠️ 清空数据）
docker compose -f docker-compose.yml --env-file .env down -v

# 重新构建并启动单个服务
docker compose -f docker-compose.yml --env-file .env up -d --build gateway

# 查看容器资源占用
docker stats $(docker compose -f docker-compose.yml ps -q)

# 进入容器调试
docker exec -it campus-gateway sh
docker exec -it campus-mysql mysql -u root -p
```

---

## 8. 多环境切换

| 环境 | 命令 | 说明 |
| --- | --- | --- |
| SIT | `./scripts/deploy.sh sit` | 系统集成测试，端口 18080 |
| UAT | `./scripts/deploy.sh uat` | 用户验收测试 |
| PERF | `./scripts/deploy.sh perf` | 性能压测 |

每套环境通过 `COMPOSE_PROJECT_NAME` 隔离容器命名空间，可在同一服务器上并行运行。

---

## 9. 代码更新与重新部署

```bash
cd /opt/CampusArrive

# 拉取最新代码
git pull origin master

# 重新构建并部署
cd deploy
docker compose -f docker-compose.yml --env-file .env up -d --build
```

---

## 10. 常见问题

| 问题 | 排查方法 |
| --- | --- |
| 容器启动失败 | `docker logs <容器名>` 查看错误日志 |
| 网关 502 | 检查下游微服务是否健康：`./scripts/health-check.sh` |
| MySQL 连接拒绝 | 确认 `campus-data` 网络存在：`docker network inspect campus-data` |
| 端口冲突 | `docker ps` 检查端口占用，或修改 `.env` 中的端口映射 |
| 镜像构建失败 | 确认网络可达 Maven Central，或配置内网镜像源 |
| OOM | `docker stats` 检查内存，调整 compose 中的 `mem_limit` |

---

## 11. 当前已完成任务进度

| 任务编号 | 任务名称 | 状态 | 提交 commit |
| --- | --- | --- | --- |
| INFRA-1.1 | 项目骨架与 CI 流水线 | ✅ 已完成 | `2c59ce1` |
| INFRA-1.2 | Docker 部署环境 | ✅ 已完成 | `9fce3fa` |
| MW-2.1 | API 网关骨架与路由鉴权 | ✅ 已完成 | `9fce3fa` |
| INFRA-1.3 | MaxKB + DeepSeek 部署 | ✅ 已完成 | — |
| MW-2.2 | RabbitMQ 事件链 | ⏳ 待开发 | — |
| MW-2.3 | Debezium CDC 数据同步 | ⏳ 待开发 | — |

> 完整任务清单见 `project-docs/11-SDD-TDD开发任务书.md`
