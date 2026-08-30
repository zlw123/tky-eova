# Session Current

## 1. 作用

`eova` 工作区当前热快照。

---

## Orchestrator 停止（2026-08-30T11:02Z）

- **是否派单**: 否
- **唯一 blocker**: workspace persistence probe 不是 `passed`（当前 `not executed`；`docs/.local/` 无 `persistence-probe-*.json`）
- **unitId / runId / sourceRevision / sourceSha256 / targetBeforeSha256 / leaseUntil / acceptanceProfile**: 未分配
- 已切到 `dev` 并 pull 到 `287c2e2`；`DES-002-R3.reviewStatus=approved`；未改 `automation/`（控制面已是 `blocked`）；未 commit；未开 PR

---

## 2. 当前任务快照

- **当前工作模式**：本轮只做迁移规划与设计；具体代码实现由 Cursor Automations 的 Worker 自动执行。本地协作者不得直接 port 业务代码。
- **设计产出范围**：源码盘点、267 Java/132 前端资产对照表、依赖 DAG、底座适配契约、API/golden 基线、环境 readiness、Automation 提示词和验收规则。

- **In Progress**: 1
  - `DES-002-R3`：迁移总体重设计 — **评审已通过**，等待控制面持久化探测、manifest freeze 和 baseline

- **Ready**: 0（R3 评审已通过，但剩余执行门禁期间暂停派单）
  - `LC-011`、`FE-001`、`AUTO-003` 均暂不开放

- **Done（近期）**: AUTO-002、AUTO-001、DES-002-R1、DES-002-R1-F

- **R3 reviewStatus**: `approved`（拿哥已确认评审通过；不等于执行门禁全部完成）

- **Blocked**: 无

---

## 3. LC-011 已合入 dev 的 port 成果

| 单元 | 目标路径 | 状态 |
|------|----------|------|
| EovaExp | `remis-eova/backend/.../engine/EovaExp.java` | 已合入 dev |
| SqlParse | 同上目录 `SqlParse.java` | 已合入 dev |
| EovaExpParam | 同上目录 `EovaExpParam.java` | 已合入 dev |
| SqlCondition | 同上目录 `SqlCondition.java` | 已合入 dev |
| 脚手架 | `remis-eova/backend/**/pom.xml` | 已合入 dev |
| TableSource | `.../sql/dql/TableSource.java` | **compile-stub**，非实 port |

- golden 单测：4 类 × 各 4 tests（Worker 自检通过，待本地复验）；依赖的 MetaField / MetaObject / EovaOption / EovaConfig / SqlUtil / x 当前仍为 compile-stub，不计入代码级迁移完成
- **Automation 已全部停用**；`cursor/*` 分支已清理；**R3 最终提示词**见 `docs/automation/PROMPTS.md`，预填配置见 `docs/automation/prefill-workflows.json`；R3 要求先通过 workspace persistence probe
- **当前主设计为 R3 评审通过版**：`docs/DES-002-R3-overall-migration-redesign.md`；R2 执行、API、适配、DB、环境和边界文档保留为局部约束
- **清单口径已纠偏**：实时核对为 Java 267、view 105、demo 27，前端资产合计 132（84 JS、46 HTML、2 Vue）；旧“113/25/55 JS”只保留为历史估算，不得用于派单。
- **新增清单冻结设计**：`docs/DES-002-R2-inventory-design.md`；manifest 未冻结前，DES-002-R2/R2-F 继续保持 Idea，Automation 只运行显式试点队列。
- **Slice 0 本地 manifest 已生成但未冻结**：`docs/.local/java-manifest.jsonl`（267 行）、`docs/.local/frontend-manifest.jsonl`（132 行）、`docs/.local/manifest-summary.json`；状态 `provisional`，阻塞项为目标映射、直接依赖、契约引用和 persistence probe。
- **workspace persistence probe**：说明见 `docs/automation/workspace-persistence-probe.md`，手动提示词见 `docs/automation/workspace-persistence-probe-prompts.md`；当前 `not executed`。严格为 3 次角色 run + 1 次 Orchestrator 最终读回，三条 Automation 仍不得启用。
- **触发设计**：R3 放行后 Orchestrator `Weekdays 09:00`、Worker `Weekdays 10:00`；Verifier 使用 GitHub `New push to branch=dev` 即时触发，并以 `Weekdays 14:00`（`Asia/Shanghai`）Schedule 兜底。当前仍只允许 Manual Run 做控制面探测，禁止业务派单。
- **旧 demo baseline 输入已整理**：`docs/DES-002-R3-slice0-baseline-input.md`；静态确认 9090/Java 8/双库配置和 5 个 Router case，但旧 demo readiness、Kingbase 快照和响应录制仍为 `not executed/baseline_pending`。
- **审计发现并已修正**：仓库新增项目级 `AGENTS.md`；LC-011 队列拆成 S 类适配链，但 persistence probe、manifest freeze 和 baseline 完成前仍暂停，不得派 `EovaExpConfig`。
- **Automation 控制面已初始化**：同一 `dev` 分支新增顶层 `automation/` 目录；`automation/state/current.json` 当前为 `idle`、`controlPlaneStatus=blocked`，队列为空，未创建任何 run。
- **三份 Automation 提示词已更新**：`docs/automation/PROMPTS.md`、`orchestrator-instructions.md`、`worker-instructions.md`、`verifier-instructions.md` 和 `prefill-workflows.json` 已统一为单分支控制面模型；Orchestrator/Verifier 只提交 `automation/`，Worker 分两次提交代码和状态。
- **最新发布状态**：控制面初始化提交 `43bb5f0`、状态记录 `287c2e2` 已推送到 `github/dev`；最新 Orchestrator 反馈提交为 `d848c75`，确认 R3 已可读取，但因 persistence probe 仍为 `not executed` 停止，未创建 run。

---

## 4. 当前已知结论

1. **GitHub 主 remote**：`https://github.com/zlw123/tky-eova.git`（**dev**）；GitLab 内网备份仍保留。
2. **Automation 机器状态事实源**：同一 `dev` 分支的 `automation/`；本地 `docs/session-current.md`、`docs/session-handoff.md`、`docs/ai-task-board.md` 和 `docs/.local/` 仍是本地治理视图，不由云端 Automation 提交。
3. **乱因**：Orchestrator cron 过密 + Worker Draft PR 未 merge + 并行 run + 控制面持久化未证明 → 分支和状态分叉；**已人工收敛**。
4. 下一步不是派业务单元，而是完成三次独立 Manual Run 的 workspace persistence probe、Slice 0 manifest 人工复核和旧 demo baseline；**禁止**再开 `*/7` cron。

---

## 5. 后续锚点

1. 按 `docs/automation/workspace-persistence-probe.md` 完成三次独立 Manual Run 探测（当前未执行）
2. 人工复核 `docs/.local/` manifest，并冻结 manifest revision
3. 按 `DES-002-R3-slice0-baseline-input.md` 录制旧 demo baseline

---

## 6. 启动协议

1. `docs/DES-002-R1-code-level-migration.md`
2. `docs/ai-task-board.md`
3. `remis-eova/backend/yudao-cloud/yudao-module-eova/eova-core/`
