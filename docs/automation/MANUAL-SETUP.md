# Automation 手建指南（无导入功能）

> Cursor Automations **没有**「导入 JSON」按钮。  
> `prefill-workflows.json` 仅供 **Agents Window 里 Agent 调用编辑器预填**；拿哥在 UI 里需 **手动新建** 三条 Automation。

---

## 通用设置（三条都要配）

| 项 | 值 |
|----|-----|
| 仓库 | `remis/modules/remis-eova`（内网 GitLab） |
| 分支 | **dev** |
| 算力 | 建议 **Cloud Agent**（Worker 写代码用） |
| Memory | 开启 |

入口：**Cursor → Automations → New automation**

---

## 1. Orchestrator（编排器）

| 字段 | 填写 |
|------|------|
| **Name** | `eova-migration-orchestrator` |
| **Description** | 读任务板，认领 1 个 Ready 为 In Progress，产出 Worker 单单元清单，更新 rolling docs。不写业务代码。 |
| **Trigger** | Schedule → Weekdays 09:00（或先选手动/Webhook 试跑） |
| **Tools** | 不勾选（只改 docs） |

**Instructions（整段复制）：**

```
你是 EOVA 迁移 Orchestrator。严格按仓库内 docs/automation/orchestrator-instructions.md 执行。

本 run 只更新 docs/ai-task-board.md、docs/session-current.md、docs/session-handoff.md。
不写 remis-eova 业务代码。最多认领 1 个 Ready 任务，产出 1 个 Worker 单元 JSON 清单。
若已有 In Progress，只补清单不新认领。试点白名单见 orchestrator-instructions.md。
完成后 commit 到 dev，message 形如 chore(governance): claim LC-011 unit EovaExp。
```

---

## 2. Worker（执行器）

| 字段 | 填写 |
|------|------|
| **Name** | `eova-migration-worker` |
| **Description** | 按 session-current 的 Worker 清单，代码级 port 1 个单元到 remis-eova，开 PR。 |
| **Trigger** | Schedule → Weekdays 09:30（或在 Orchestrator 跑完后再手动 Run） |
| **Tools** | 若支持 GitLab MR：勾选创建 MR；否则 Agent 用 git 命令 push 分支后在 GitLab 手开 MR |

**Instructions（整段复制）：**

```
你是 EOVA 迁移 Worker。严格按 docs/automation/worker-instructions.md 与 DES-002-R1 / R1-F 执行。

从 docs/session-current.md 读取 Worker 清单 JSON。只 port 1 个单元；禁止重写逻辑；禁止修改 meta-eova 源仓库内容（只读参考，submodule 内勿提交）。

完成后：更新 rolling docs、commit、向 dev 创建 Merge Request（标题 port(taskId): 单元名）。
PR/MR 的 target 分支为 dev。
```

---

## 3. Verifier（验证器）

| 字段 | 填写 |
|------|-----|
| **Name** | `eova-migration-verifier` |
| **Description** | 对 Worker 的 MR 做编译验证，回写 Done 或 Blocked。 |
| **Trigger** | 若 GitLab MR 事件不可用 → 先用 **Manual Run** 或 Schedule；有 MR push 触发再改 |
| **Tools** | MR/PR 评论（有则勾选） |

**Instructions（整段复制）：**

```
你是 EOVA 迁移 Verifier。严格按 docs/automation/verifier-instructions.md 执行。

验证当前 MR diff：Java 跑 mvn compile；前端跑 pnpm build（若存在）；检查 ported from 追溯注释。
golden baseline 不存在则跳过 API diff，在 handoff 记 golden: skipped。

结果写入 rolling docs；失败标 Blocked 并在 MR 评论摘要；不写新 port 代码。target 分支 dev。
```

---

## 试跑顺序（首次建议全用手动 Run）

1. **Orchestrator** Manual Run → 看 `session-current.md` 是否出现 Worker JSON  
2. **Worker** Manual Run → 看 `remis-eova/` 是否出现首个 port + MR  
3. **Verifier** Manual Run → 看编译结果与任务板状态  

三条都 OK 再开 Schedule。

---

## 内网 GitLab / Cloud Agent 挂库

Cloud run 若报 `repos: null`、`/agent 为空`，见 **`docs/automation/CLOUD-AGENT-SETUP.md`**。  
内网一时不通可先把 Automation **Compute 改 Local**，用本机 `/Users/zhouliwei/eova` dev 分支试跑。

---

在 **Agents Window** 对 Agent 说：

> 用 open_automation 预填 eova-migration-orchestrator，仓库 remis-eova、分支 dev

Agent 会打开 **已填好字段的编辑器**，拿哥核对后点 Save。  
**当前普通 Chat 窗口** 可能没有该能力，需切 Agents Window。

---

## 内网 GitLab 注意

- 远程：`http://10.20.110.206:45001/remis/modules/remis-eova.git`
- Automations 的 Git 集成需已连接该 GitLab（或 Cursor 能 checkout dev）
- MR 触发若 UI 只有 GitHub，Verifier 先用 **Manual Run** 代替，别卡在这一步
