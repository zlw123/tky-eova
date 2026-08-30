# Session Handoff

## 2026-08-30T10:44Z - Orchestrator 未派单（控制面门禁）

- **唯一 blocker**: DES-002-R3 的 `reviewStatus` 不是 `approved`（`docs/DES-002-R3-overall-migration-redesign.md` 在 `origin/dev` 不存在）
- 未派单；未写 `automation/`；未 commit；未开 PR
- 后续：先落地并批准 DES-002-R3，再执行 workspace persistence probe 与 Slice 0 frozen manifest

---

## 2026-08-30 - 初始化单分支 Automation 控制面

### 本轮确认

1. 在 `dev` 分支新增顶层 `automation/` 目录，初始化 `schema-version.json`、`queue/`、`state/` 和 `runs/` 状态骨架；当前 `controlPlaneStatus=blocked`、`stateRevision=0`、`runs=[]`。
2. 三条 Automation 统一使用同一仓库同一 `dev` 分支；云端机器状态以 `automation/` 为事实源，本地 rolling docs 不作为跨 run 共享存储。
3. 更新三份角色提示词及预填配置：Orchestrator/Verifier 只允许提交 `automation/`，Worker 先提交业务代码再提交对应 run 状态；全部禁止 force push、分支和 PR。

### 当前剩余问题

workspace persistence probe、Slice 0 manifest freeze 和旧 demo baseline 仍未完成；三条 Automation 继续停用，只允许 Manual Run 做控制面探测。

### 下一步

在 Cursor UI 按 `docs/automation/workspace-persistence-probe-prompts.md` 完成 3 次角色 run + 1 次 Orchestrator 最终读回，再决定是否把控制面从 `blocked` 更新为 `ready`。

## 2026-08-30 - 发布控制面并复核调度反馈

### 本轮确认

1. 远端 `github/dev` 曾因 Cursor Orchestrator 提交 `fd7ffc2` 暂时领先；已无损 rebase，保留其 `DES-002-R3` 缺失阻塞记录。
2. 本地提交 `43bb5f0` 已成功 push 到 `github/dev`；远端现在包含 `automation/`、R3 设计文档和最新三份 Automation 提示词。
3. 远端控制面读回为 `activeRunId=null`、`stateRevision=0`、`controlPlaneStatus=blocked`、`runs=[]`；没有业务派单或迁移 Run。

### 当前剩余问题

本次调度反馈属于控制面发布前的旧版本 Run；三次 persistence probe、manifest freeze 和旧 demo baseline 仍未完成，不能启用业务 Schedule。

## 2026-08-30 - 明确规划设计与 Automation 实现边界

### 本轮确认

1. 当前任务只做 EOVA 代码级迁移的规划和设计，不在本地直接实现迁移代码。
2. 具体 Java/JS port、构建、自检和 push 由 Cursor Automations 的 Worker/Verifier 按已冻结的单元协议执行。
3. 后续本地设计工作必须输出可直接派发的 source/target、依赖、允许适配、acceptanceProfile、证据和 blocked 条件；缺少这些字段的单元不得进入 Ready。

### 下一步

继续完成 `DES-002-R2`、`DES-002-R2-F`、API/golden、适配层和环境设计；设计完成后只更新 Automation 队列和提示词，不直接修改 `remis-eova` 业务代码。

## 2026-08-30 - 代码级迁移执行设计冻结

### 本轮目标

梳理三条 Cursor Automation 能够连续推进 EOVA 代码级迁移所需的设计边界，消除 Worker/Verifier 的输入、适配、验收和失败处理歧义。

### 本轮确认的事实

1. 代码级迁移必须以旧源码和 source revision 为唯一基线，按单元逐文件 port；只允许清单内的底座适配。
2. 已新增 `docs/DES-002-R2-migration-execution-design.md`，冻结单元协议、A-E/F 分级、EovaDbGateway 边界、API/前端契约、golden 证据、状态机、重试和 Done 定义。
3. 已新增 `DES-API-R2.md`、`DES-DB-ADAPTER.md`、`DES-ENV-R2.md`、`DES-BOUNDARY-R2.md` 四份设计产物，并识别出 B 类仍需 `DES-ADAPTER-R2`；上述均为 design-only，不代表实际 baseline、环境或适配实现已完成。
4. 已将 `docs/automation/PROMPTS.md` 增加代码级门禁；治理文档和 golden 证据按 AGENTS.md 只在本地维护，不提交、不 push。

### 当前剩余问题

1. 267 Java/132 前端资产分类对照表和实际 API golden 录制尚未完成；“55 JS”仅为历史估算，不作派单依据。
2. EovaDbGateway 尚未实现，D 类 Config/Render/Interceptor 尚未逐项完成等价边界说明。
3. 三条 Automation 仍停用，尚未进行首个单元的 Manual Run。

### 审计补充

1. 仓库原先没有 `AGENTS.md`，已新增项目级规则文件，确保 Cursor Automation 能读取代码级迁移和 Git/治理边界。
2. `EovaExpConfig`、`ExpUtil` 实际使用 `Kv`/模板引擎等旧底座；已将最小 `EovaKvAdapter` 作为 `LC-011-000` S 类 support 单元放入同一队列，并将 LC-011 后续单元依赖补全。
3. 当前 `meta-eova/eova` submodule 只有无关的 `demo/sql/kingbase/` 未跟踪改动；未触碰、未纳入本轮。
4. LC-011 已合入的四个 engine 类是真实 port，但其依赖支撑类仍是 compile-stub；后续任务完成定义必须区分“目标单元已 port”和“依赖链已代码级迁移”。

### 下一步

先完成 `DES-002-R2` / `DES-002-R2-F` 对照表，再做 `DES-API-R2` 的旧 demo baseline 和 `DES-DB-ADAPTER` 接口测试设计；确认同一持久化 Cursor 工作区后，再手建三条 Automation，并先试跑 LC-011 的 `LC-011-000` S 类 support 单元。

## 2026-08-30 - 迁移清单与 Automation 缺口复核

### 本轮确认

1. 实时目录复核：Java 267；view 105；demo 27；前端资产 132（84 JS、46 HTML、2 Vue）。旧记录的 113/25/55 JS 已降级为历史估算。
2. 新增 `docs/DES-002-R2-inventory-design.md`，冻结 manifest 字段、Java/前端分类、1:N 拆分、vendor/error/shell 排除、hash 和派单门禁。
3. LC-011 support 细化为 `EovaKvAdapter`、`EovaTemplateAdapter`、`EovaLegacyUtilityAdapter` 三个 S 类单元；`ExpUtil` 必须等待三类适配及配置 port，`EovaExpBuilder` 还必须等待 DB 适配。
4. Worker/Verifier 改为按 `acceptanceProfile` 选择 Maven、pnpm 或脚手架检查；前端不能再被强制执行后端 Maven 命令。

### 尚未完成

1. 267 Java 和 132 前端资产的逐行 manifest 尚未生成，DES-002-R2/R2-F 不得改 Done。
2. 旧 demo API/HAR、DB adapter 实测和环境 readiness 尚未执行；缺失时只能 `baseline_pending`、`golden: skipped` 或 `not executed`。

## 2026-08-30 - R3 总体迁移方案重设计

### 本轮确认

1. 已核对 GitHub 主仓库为 `https://github.com/zlw123/tky-eova.git`，主分支 `dev`，本地 remote `github`；`origin` 仅为内网备份。
2. 最近 dev 业务成果只有 4 个 engine 类和脚手架；其传递依赖仍有 compile-stub，不能继续按文件合入数计算迁移进度。
3. 新增 `docs/DES-002-R3-overall-migration-redesign.md`：改为“manifest → 适配 → 内核 → DB → 一个元数据对象 → 表单/权限 → 横向扩展”的垂直切片路线。
4. R3 新增 workspace persistence probe：治理文档不提交时，必须先证明三条 Automation 共享同一控制面；否则状态为 `blocked: control-plane-not-persistent`。
5. 当前唯一 `In Progress` 调整为 `DES-002-R3`；LC-011、FE-001、AUTO-003 在 R3 评审期间暂停，不派 Worker。

### 尚未完成

1. R3 尚未获得方案评审确认。
2. Slice 0 的 267 Java/132 前端 manifest、旧 demo baseline 和 workspace persistence probe 尚未执行。

## 2026-08-30 - 按 R3 顺序推进 Slice 0

### 本轮确认

1. 生成本地 provisional manifest：`docs/.local/java-manifest.jsonl` 267 行、`docs/.local/frontend-manifest.jsonl` 132 行、`manifest-summary.json`；源 revision 为 `1b1d39e7350f7e031b216aad0399fc8cc55dce08`。
2. 初步 Java 分类为 A=95、B=40、C=94、D=38；前端分类为 demo=27、frontend-template=84、frontend-core=6、vendor=11、error=4。该分类只作审计起点，未冻结、未派单。
3. 新增 `docs/automation/workspace-persistence-probe.md`，定义三次独立 Manual Run 的跨 Automation 读写证据；当前状态 `not executed`。

### 当前阻塞

1. Automation 的共享工作区、控制面和真实挂载路径无法由本地单进程证明，必须在 Cursor UI 中完成 probe。
2. manifest 的目标路径、直接依赖和 API contractRefs 仍需人工复核；R3、AUTO-003 和所有 Worker 单元保持未放行。

## 2026-08-30 - R3 Automation 提示词最终整理

### 本轮确认

1. 已将 `docs/automation/PROMPTS.md` 重写为三段可直接粘贴到 Cursor 的最终提示词：Orchestrator 只派单、Worker 只迁移一个单元、Verifier 只做证据验收。
2. 三段提示词统一采用 GitHub `dev`、URL 识别主 remote、Manual Run、严格串行、`ready -> ported_awaiting_verifier -> verified/blocked` 和 source/hash/runId/lease 门禁。
3. 明确 R3 评审、workspace persistence probe、Slice 0 manifest freeze 和旧 demo baseline 未完成前，三条 Automation 必须停在门禁处；同步更新 `docs/automation/prefill-workflows.json`。
4. 本轮只修改规划/治理文档，未执行 Worker、未派发业务单元、未修改 `remis-eova` 业务代码。

### 验证与下一步

- `jq empty docs/automation/prefill-workflows.json` 已通过，JSON 格式有效。
- 尚未执行三次独立 Manual Run；仍需先完成 R3 评审、persistence probe、manifest 冻结和旧 demo baseline，再恢复业务派单。

## 2026-08-30 - 定时调度触发方案

### 本轮确认

1. 因 Orchestrator/Worker 使用定时调度，采用工作日错峰 Schedule，而不是 `*/7` 高频 cron 或同刻触发；Verifier 另加受控的 GitHub `New push to branch=dev` 即时事件。
2. R3 放行后的推荐时间（`Asia/Shanghai`）为：Orchestrator `09:00`、Worker `10:00`、Verifier `14:00`，每条每天最多一次。
3. Schedule 只负责唤醒，三条提示词仍必须依据 `workerStatus`、runId、hash、lease 和依赖门禁决定执行或 no-op；迁移单元需控制在 30 分钟 lease 内，过大单元先拆分。

### 当前状态

- R3 评审已通过但仍为 Design-only 执行阶段，persistence probe 为 `not executed`，所以不能启用业务 Schedule；只可用 Manual Run 完成控制面探测。

## 2026-08-30 - GitHub 事件触发组合评估

### 本轮确认

0. 拿哥确认 `DES-002-R3` 评审通过；已将 R3 `reviewStatus` 记录为 `approved`，但未将执行任务提前标记 Done。
1. 截图中的 GitHub 事件中，当前最适合本项目的是 `New push to branch=dev`，只用于 Worker push 后即时唤醒 Verifier。
2. Orchestrator 仍使用 Weekdays 09:00，Worker 仍使用 Weekdays 10:00；Verifier 同时保留 Weekdays 14:00 Schedule 作为 push 事件丢失或延迟的兜底。
3. 不采用 Worker 的 push 自触发、Draft/PR/review 事件、`Checks completed` 或 `Workflow run completed`：当前仓库没有 `.github/workflows`，且这些事件要么与禁止 PR 冲突，要么没有稳定事件源。
4. Verifier 必须把 GitHub 事件里的 branch/commit 当作候选输入，仍以 session-current 的 runId、Worker commit、source/hash 和 workerStatus 为准；不满足门禁时 no-op/block。

### 当前状态

- 本轮仍为设计更新，未添加 GitHub trigger、未启用 Schedule、未运行 Automation；R3 和 workspace persistence probe 完成后才按上述组合配置。

## 2026-08-30 - Slice 0 baseline 输入整理

### 本轮确认

1. 新增 `docs/DES-002-R3-slice0-baseline-input.md`，固定旧 demo 启动类、9090 端口、Java 8 脚本、双库配置键和首批 5 个 Router case。
2. `.http` 样例只提供请求输入，未执行旧 demo，未把任何响应写成 golden；`/grid/*`、`/api/meta/*`、菜单、表单、上传和导出仍需从真实前端调用补录。
3. Slice 0 当前状态：manifest `provisional`；old-demo readiness `not executed`；API golden `baseline_pending`；workspace persistence probe `not executed`。

### 下一步

1. 在 Cursor UI 按 `workspace-persistence-probe-prompts.md` 完成 3 次角色 Manual Run，再做第 4 次 Orchestrator 最终读回确认。
2. 人工复核 `docs/.local/` manifest 的目标映射、直接依赖和 contractRefs。
3. 具备旧 demo 和测试库运行条件后，再录制脱敏 API/HAR baseline。

## 2026-08-29 - Automation 收敛：PR #7 合入 dev + 清理 cursor 分支

### 背景

Automation cron 过密（`*/7`）导致 dev 仅堆治理文档；Worker 代码在 14 条 `cursor/*` 分支 + 8 个 Draft PR，未 merge。

### 本轮动作

1. 拿哥 **停用全部 Automation**
2. 将 `cursor/eova-porting-dc30`（PR #7）**merge 进 dev** — 含 EovaExp / SqlParse / EovaExpParam / SqlCondition + eova-core 脚手架
3. 清理 rolling docs（删除 Orchestrator 重复 handoff）
4. 关闭 Draft PR #1–#8，删除远程 `cursor/*` 分支

### 教训（下轮 Automation 前必改）

- Orchestrator **禁止** cron 直 push dev；Worker **必须 merge 到 dev** 后再跑 Verifier
- 禁止并行 Worker + Verifier；同一时刻 1 个 In Progress + 1 个单元
- 首次试跑用 **Manual Run**，不要 `*/7`

### 下一步

1. 本地 `mvn test` 复验 eova-core
2. 修订 `docs/automation/*` 规则后再启 Automation

---

## 2026-08-29 - 推送到 GitHub tky-eova

### 本轮目标

将工作区推送到 `https://github.com/zlw123/tky-eova`，便于 Cursor Cloud Agent 绑库。

### 远程策略

- **github** → `zlw123/tky-eova`（Automation / Cloud Agent 主 remote）
- **origin** → 内网 GitLab remis-eova（备份）

---

## 2026-08-29 - AUTO-001 Cursor Automations 三层流水线

### 本轮目标

按推荐模式落地 Orchestrator + Worker + Verifier，供 Agents Window 创建 Automation。

### 本轮产出

1. `docs/automation/README.md` — 架构与前置条件
2. `orchestrator-instructions.md` / `worker-instructions.md` / `verifier-instructions.md` — 完整 prompt
3. `prefill-workflows.json` — Agent 预填草稿（无 UI 导入）
4. 任务板新增 AUTO-001~004

---

## 2026-08-29 - DES-002-R1-F 前端代码级迁移方案

### 本轮目标

与后端 R1 同口径，输出前端逐文件 port 方案。

### 本轮确认的事实

1. 当前可复核目录为 view **105** + demo **27**，共 **132** 个 JS/Vue/HTML 资产（84 JS、46 HTML、2 Vue）；业务文件子集尚待 manifest 分类冻结。
2. 已是 Vue3 setup，但契约依赖 `window.urls`、`{state,msg,data}`、`uzoo.page`。
3. 已写 `docs/DES-002-R1-frontend-code-level-migration.md`；FE-001 Ready。

### 下一步建议

1. DES-002-R2 / R2-F 完整对照表。
2. FE-001 与 LC-011 并行。

---

## 2026-08-29 - DES-002-R1 代码级迁移路线修订

### 本轮目标

回应拿哥「原方案做不到代码级迁移」的质疑，修订方法论。

### 本轮确认的事实

1. 原 DES-002 任务清单偏 **按功能重写**，缺文件级追溯与 golden 对照。
2. meta-eova/core：**267** Java，**149** 个直接 import JFinal；WidgetManager 等必须用 **Db 适配层 + 逻辑移植**。
3. 已写 `docs/DES-002-R1-code-level-migration.md`；LC-001 后移，LC-011/012 前置。

### 下一步建议

1. 拿哥确认 R1 修订路线。
2. DES-002-R2：267 文件对照表 + golden API 清单。

---

## 2026-08-29 - DES-002 三项决策落定

### 本轮目标

确认落仓命名、身份策略、前端形态，更新迁移方案。

### 本轮确认的事实

1. **remis-eova 仓库** = 原方案所称新代码落点（不再称「eova 仓」）。
2. **身份**：先迁移 `eova_user/eova_role`；并入 platform System → **DES-003 后续**。
3. **前端**：先独立 `remis-eova/fornt/eova-ui`；嵌入 yudao-ui → **DES-004 后续**。
4. **LC-001** 已进入 Ready，待放行执行脚手架。

### 下一步建议

1. 拿哥确认「开干脚手架」。
2. LC-001 初始化 `remis-eova/backend/yudao-cloud/yudao-module-eova/`。

---

## 2026-08-29 - DES-002 落仓目录调整为 remis-eova

### 本轮目标

按拿哥要求，将迁移方案推荐目录改为前后端均放在 `remis-eova/` 下。

### 本轮确认的事实

1. 新工程根：`/Users/zhouliwei/eova/remis-eova/`。
2. 后端：`remis-eova/backend/yudao-cloud/yudao-module-eova/`
3. 前端：`remis-eova/fornt/eova-ui/`

---

## 2026-08-29 - DES-002 meta-eova 技术栈迁移方案

### 本轮目标

输出与 platform 完全一致技术栈的代码级迁移方案、任务规划、详细清单与进度表。

---

## 2026-08-25 - DES-001 执行完成（建库+导入+VAL）

### 本轮目标

按 DES-001 在 54321 创建并导入 `eova_meta` / `demo`。

---

## 2026-08-25 - Kingbase 54321 连通性验证

### 本轮目标

确认能否链接 Kingbase 端口 `54321`。
