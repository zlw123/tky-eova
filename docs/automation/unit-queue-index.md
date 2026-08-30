# Automation 单元队列索引

> Orchestrator **不**默认只跑 LC-011。先读 `docs/ai-task-board.md` 定 **taskId**，再查下表选单元队列。

所有 `targetPaths` 必须使用仓库相对路径；Orchestrator 派单时写入完整仓库相对路径。

---

## 1. 选哪个任务（taskId）

| 条件 | 动作 |
|------|------|
| 已有 **1 个** `In Progress` | **继续该 taskId**，派其下一单元（LC-011 未 Done 时不切换 FE-001） |
| 无 `In Progress` | 从 **Ready 白名单**认领 **1 个**（优先级见下） |

### Ready 白名单（R3 评审后的剩余门禁期间）

| 优先级 | taskId | 单元队列文档 |
|--------|--------|--------------|
| — | （暂不开放） | `S01-login-shell` 正在准备局部 manifest、旧 demo baseline 和 UI/API 证据 |

**禁止认领**：未被 `automation/slices/index.json` 标记为 `ready=true` 的切片单元、Idea、Deferred、Blocked 和不在 Ready 白名单的任务。

---

## 2. 按 taskId 派下一单元

| taskId | 队列来源 | 说明 |
|--------|----------|------|
| **LC-011** | `docs/automation/LC-011-unit-queue.md` | 后端 engine 逐类 port |
| **FE-001** | 见下 §FE-001 | 整任务通常 **1 个单元** |
| **FE-002** | 见下 §FE-002 | 契约层，FE-001 完成后 |

Worker 清单 JSON 的 `taskId` **必须**与上表一致；commit message 用 `assign <taskId> unit <unitName>`，**不要写死 LC-011**。

---

## 3. FE-001（单单元）

| 字段 | 值 |
|------|-----|
| unitId | `FE-001-000` |
| unitName | `eova-ui-scaffold` |
| unitType | `scaffold` |
| sourcePath | `null`（无单文件，按 DES-002-R1-F 初始化） |
| targetPaths | `remis-eova/fornt/eova-ui/` |
| dependencies | `[]` |
| acceptanceProfile | `frontend-build` |
| acceptance | `pnpm install && pnpm build` |

整任务完成后 Orchestrator 将 FE-001 标 **Done**。

---

## 4. FE-002（契约层，FE-001 后）

| 顺序 | unitId | unitName | dependencies | acceptanceProfile | targetPaths |
|------|--------|----------|--------------|------------------|------------|
| 1 | FE-002-001 | eova-urls | `[FE-001-000]` | `frontend-contract` | `remis-eova/fornt/eova-ui/src/api/eova-urls.ts` |
| 2 | FE-002-002 | eova-http | `[FE-002-001, DES-API-R2]` | `frontend-contract` | `remis-eova/fornt/eova-ui/src/utils/eova-http.ts` |

---

## 5. R3 评审通过后的放行条件

每个切片完成自身 manifest 和旧 demo baseline 后，才恢复该切片的 Ready 白名单；完整 267 Java / 132 前端资产分类对照表是最终审计账本，不是首单门禁。本索引再按 taskId 链到各 `*-unit-queue.md`；在当前切片 manifest 冻结前不得派单，persistence probe 仅作可选诊断。
