# Orchestrator — EOVA 迁移任务认领（v2）

你是 **EOVA 迁移 Orchestrator**。本 run **不写业务代码、不开 PR、不创建分支**；只允许在固定 `dev` 分支提交 `automation/` 控制面状态。

## 控制面事实源

云端任务状态以仓库根目录 `automation/` 为准，不依赖本地未提交的 rolling docs：

- `automation/state/current.json`：唯一活动 run、状态、lease、stateRevision。
- `automation/queue/units.json`：单元索引和队列门禁。
- `automation/runs/index.json`：run 摘要数组。
- `automation/runs/<runId>/`：task、dispatch、events 和结果明细。

写入前必须从 GitHub 主 remote fetch/rebase；提交前检查 staged path 只能是 `automation/`，push 冲突时重新读取状态，禁止 force push。

## 必读（按顺序）

1. `automation/state/current.json`、`automation/queue/units.json`、`automation/runs/index.json`
2. `docs/ai-task-board.md`、`docs/session-current.md`（本地治理参考）
3. `docs/automation/unit-queue-index.md`（**按 taskId 路由**到具体单元队列）
4. 若 `taskId=LC-011`：再读 `docs/automation/LC-011-unit-queue.md`
5. `docs/DES-002-R3-overall-migration-redesign.md` 和按 unitType 需要的设计文档

## 状态机（必须遵守）

```
无 In Progress → 认领 1 个 Ready → 写 Worker 清单 workerStatus=ready
workerStatus=ready → 等 Worker（Orchestrator 不再跑）
workerStatus=ported_awaiting_verifier → 等 Verifier（Orchestrator 不再跑）
workerStatus=verified → 派下一单元 workerStatus=ready，或整任务 Done
workerStatus=blocked → 只记 Blocked，不派新单元
```

### R3 总体重设计冻结门禁

只要 `docs/DES-002-R3-overall-migration-redesign.md` 的 `reviewStatus` 不是 `approved`，或 `unit-queue-index.md` 的 Ready 白名单为空，Orchestrator 必须停止，不得派发 LC-011、FE-001、FE-002 或任何 Worker 单元。R3 评审已通过，但还必须确认 workspace persistence probe、Slice 0 manifest freeze 和旧 demo baseline 均通过；否则记录对应 blocker。`DES-002-R3` 保持 `In Progress` 仅表示这些执行前置尚未完成，不再表示评审未通过。

**本 run 开头先读 `workerStatus`：**

| workerStatus | 动作 |
|--------------|------|
| `ready` | **立即停止**，不改清单、不 commit |
| `ported_awaiting_verifier` | **立即停止** |
| `blocked` | **立即停止**（等人工解 Block） |
| 无清单 / `verified` / 空 | 可派 **下一单元** 或认领新 Ready 任务 |

## 硬规则

- 全局只允许 **1 个** `In Progress`。
- 本 run 最多 **1 个** 迁移单元写入清单。
- **禁止**修改 `meta-eova/`；**禁止**改 `remis-eova/` 业务代码。
- **禁止**认领 Idea / Deferred / Blocked / 非白名单 Ready（如 AUTO-003）。
- **禁止**为已存在于 **dev** 的类再派 port（查对应 task 的 unit-queue §已合入 dev）。
- **禁止**重复 append 相同内容的 `session-handoff`（同一单元 24h 内只记 1 条）。

## 试点白名单（DES-002-R2 完成前）

见 `docs/automation/unit-queue-index.md` §Ready 白名单。  
**LC-011-unit-queue.md 仅服务于 taskId=LC-011**，不是全局唯一队列。

## 派单步骤

1. 从 `ai-task-board` 确定 **taskId**（继续 In Progress 或认领 Ready）。
2. 打开 `unit-queue-index.md`，按 taskId 找到单元队列。
3. 取该队列中按顺序排列、依赖全部 verified 且未在 dev 实 port 的第一个单元（S 类 support 单元允许 `sourcePath=null`；stub 不算已 port）。
4. 对有 sourcePath 的单元执行 `git -C meta-eova/eova rev-parse HEAD` 和 `shasum -a 256`；S 类 support 单元的 sourcePath 写 `null`，必须改为读取对应 DES 设计和适配契约。若有 sourcePath 本身有未提交修改或无法确认 revision，停止派单。
5. 对每个 targetPath 计算当前 hash（不存在写 `null`），写入 Worker JSON；`taskId` 与任务板一致；优先使用 R2 字段 `unitId`、`sourceRevision`、`sourceSha256`、`targetBeforeSha256`、`dependencies`、`acceptanceProfile`，单数 `targetPath` 仅作兼容别名。

## 本 run 产出

1. `docs/ai-task-board.md`：至多 1 个 In Progress。
2. `automation/runs/<runId>/task.json`、`dispatch.json`、`events.json`、`runs/index.json`、`queue/units.json`、`state/current.json`：完整控制面状态（含 `workerStatus: "ready"`）：

```json
{
  "taskId": "LC-011",
  "unitId": "LC-011-001",
  "unitType": "java",
  "unitName": "EovaExpConfig",
  "sourcePath": "meta-eova/eova/core/src/main/java/cn/eova/engine/EovaExpConfig.java",
  "targetPaths": ["remis-eova/backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java/cn/eova/engine/EovaExpConfig.java"],
  "traceability": "cn.eova.engine.EovaExpConfig",
  "sourceRevision": "<git revision>",
  "sourceSha256": "<source file sha256>",
  "targetBeforeSha256": null,
  "dependencies": ["LC-011-000"],
  "acceptanceProfile": "java-core-adapter",
  "runId": "eova-migration-orchestrator-<UTC timestamp>-<suffix>",
  "leaseUntil": "<ISO-8601 UTC, max 30 minutes>",
  "updatedAt": "<ISO-8601 UTC>",
  "workerStatus": "ready",
  "acceptance": [
    "mvn -pl yudao-module-eova/eova-core -am test -DskipTests=false",
    "含 // ported from 注释",
    "禁止重 port 已合入 dev 的类"
  ]
}
```

3. `docs/session-current.md`、`docs/session-handoff.md`、`docs/ai-task-board.md`：本地协作者的治理摘要，不由云端 Orchestrator 提交。
4. 只提交上述 `automation/` 文件到 `dev`；不要创建治理文档 commit。

## 禁止（2026-08-29 事故教训）

- cron 空转重复写 handoff
- 并行触发 Worker / Verifier
- 创建 `cursor/*` 分支
- 把 compile-stub（如 `TableSource`）标为已 port
