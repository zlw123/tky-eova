# Automation 单元队列索引

> Orchestrator **不**默认只跑 LC-011。先读 `docs/ai-task-board.md` 定 **taskId**，再查下表选单元队列。

---

## 1. 选哪个任务（taskId）

| 条件 | 动作 |
|------|------|
| 已有 **1 个** `In Progress` | **继续该 taskId**，派其下一单元（LC-011 未 Done 时不切换 FE-001） |
| 无 `In Progress` | 从 **Ready 白名单**认领 **1 个**（优先级见下） |

### Ready 白名单（DES-002-R2 完成前）

| 优先级 | taskId | 单元队列文档 |
|--------|--------|--------------|
| 1 | LC-011 | `LC-011-unit-queue.md` |
| 2 | FE-001 | 本节 §FE-001（单单元脚手架） |
| 3 | FE-002 | 本节 §FE-002（依赖 FE-001 Done） |

**禁止认领**：AUTO-003、Idea、Deferred、Blocked、不在上表的任务。

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
| unitName | `eova-ui-scaffold` |
| unitType | `scaffold` |
| sourcePath | —（无单文件，按 DES-002-R1-F 初始化） |
| targetPath | `remis-eova/fornt/eova-ui/` |
| acceptance | `pnpm install && pnpm build` |

整任务完成后 Orchestrator 将 FE-001 标 **Done**。

---

## 4. FE-002（契约层，FE-001 后）

| 顺序 | unitName | targetPath |
|------|----------|------------|
| 1 | eova-urls | `remis-eova/fornt/eova-ui/src/api/eova-urls.ts` |
| 2 | eova-http | `remis-eova/fornt/eova-ui/src/utils/eova-http.ts` |

---

## 5. DES-002-R2 之后

完整 267 Java / 55 JS 对照表就绪后，本索引扩展为按 taskId 链到各 `*-unit-queue.md`；届时取消试点白名单。
