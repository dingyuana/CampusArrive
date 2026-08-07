# Git 分支策略

| 项目名称 | 微迎新（CampusArrive） |
| --- | --- |
| 文档编号 | GIT-CA-2026-12 |
| 版本 | v1.0.0 |
| 编制日期 | 2026-08-07 |
| 适用范围 | 全部代码仓库（backend / frontend / deploy） |

---

## 1 分支模型

采用 `master + develop + feature + hotfix` 四分支模型，基于 GitHub Flow 简化。

```mermaid
gitGraph
    commit id: "init"
    commit id: "infra-1.1"
    branch develop
    checkout develop
    commit id: "dev-latest"
    branch feature/MW-2.1-gateway
    checkout feature/MW-2.1-gateway
    commit id: "gateway-impl"
    commit id: "gateway-tests"
    checkout develop
    merge feature/MW-2.1-gateway
    checkout master
    merge develop
    branch hotfix/security-patch
    commit id: "fix"
    checkout master
    merge hotfix/security-patch
```

## 2 分支定义

| 分支 | 命名规则 | 来源 | 合并目标 | 权限 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `master` | 固定 | develop / hotfix | — | 仅管理员合并 | 生产分支，每次合并打 Tag，可部署 |
| `develop` | 固定 | feature / hotfix | master | 开发负责人合并 | 集成分支，最新开发成果 |
| `feature/*` | `feature/{任务编号}-{简述}` | develop | develop | 开发者自由创建 | 功能开发分支，如 `feature/MW-2.1-gateway` |
| `hotfix/*` | `hotfix/{简述}` | master | master + develop | 开发负责人创建 | 生产紧急修复 |

## 3 分支命名约定

```
feature/INFRA-1.1-project-skeleton
feature/MW-2.1-api-gateway
feature/AI-3.2-dialog-workflow
feature/PARENT-4.1-binding-auth
feature/A11Y-5.1-wcag-baseline
feature/SEC-6.1-pii-middleware
hotfix/jwt-token-validation
hotfix/cdc-sync-memory-leak
```

## 4 合并规则

| 规则 | 说明 |
| --- | --- |
| PR 必须 | feature → develop、develop → master 均通过 Pull Request 合并，禁止直接 push |
| Review 必须 | 至少 1 名同行 Code Review；关键模块（AI/安全/中间件）须技术负责人 Review |
| CI 必须通过 | 编译 → 单元测试 → 覆盖率门禁 → 静态分析全部通过方可合并 |
| 测试先行 | PR 必须包含对应的测试代码，覆盖率达标 |
| Squash Merge | feature → develop 使用 Squash Merge，保持历史整洁 |
| Merge Commit | develop → master 使用 Merge Commit，保留集成记录 |
| Hotfix 合并 | hotfix → master 后须同步合并至 develop，避免修复丢失 |

## 5 版本标签

| 标签格式 | 示例 | 说明 |
| --- | --- | --- |
| `v{major}.{minor}.{patch}` | `v1.1.0` | 正式发布版本 |
| `v{major}.{minor}.{patch}-rc.{n}` | `v1.1.0-rc.1` | 候选发布版本 |
| `v{major}.{minor}.{patch}-hotfix.{n}` | `v1.1.0-hotfix.1` | 紧急修复版本 |

## 6 CI 集成

| 事件 | 触发分支 | CI 行为 |
| --- | --- | --- |
| push | master / develop | 全量 CI：编译 → 测试 → 覆盖率 → 静态分析 → 镜像构建 |
| PR 创建/更新 | feature → develop | CI：编译 → 测试 → 覆盖率 → 静态分析（不构建镜像） |
| PR 合并 | develop → master | 全量 CI + 镜像构建 + 可选灰度部署 |

## 7 保护分支配置

| 分支 | 保护规则 |
| --- | --- |
| `master` | 禁止 force push、禁止删除、必须 PR、必须 CI 通过、必须 2 人 Review |
| `develop` | 禁止 force push、禁止删除、必须 PR、必须 CI 通过、必须 1 人 Review |
