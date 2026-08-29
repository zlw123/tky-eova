# Session Handoff

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

1. 平台 view **113** + demo **25** 文件；自研业务 JS ~55 个、~3500 行。
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
