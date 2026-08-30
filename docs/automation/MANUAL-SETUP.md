# Automation 手建指南（无导入功能）

> Cursor Automations **没有**「导入 JSON」按钮。  
> `prefill-workflows.json` 仅供 **Agents Window 里 Agent 调用编辑器预填**；拿哥在 UI 里需 **手动新建** 三条 Automation。

---

## 通用设置（三条都要配）

| 项 | 值 |
|----|-----|
| 仓库 | `zlw123/tky-eova`（GitHub Automation 主仓） |
| 分支 | **dev** |
| 算力 | 建议 **Cloud Agent**（Worker 写代码用） |
| Memory | 开启 |

入口：**Cursor → Automations → New automation**

---

## 1. Orchestrator（编排器）

| 字段 | 填写 |
|------|------|
| **Name** | `eova-migration-orchestrator` |
| **Description** | 读取切片 registry，认领首个 `ready=true` 切片中的 1 个单元，产出 Worker 单单元清单。不写业务代码。 |
| **Trigger** | 当前 Manual Run；首个切片稳定后再改为 Weekdays 09:00（Asia/Shanghai），每天最多一次 |
| **Tools** | 允许写同一 `dev` 分支的 `automation/` 控制面；不改业务代码 |

**Instructions（整段复制）：**

```
你是 EOVA 迁移 Orchestrator。严格按仓库内 docs/automation/orchestrator-instructions.md 执行。

本 run 只读取并更新 `automation/` 控制面 JSON，不写 remis-eova 业务代码。
最多认领 1 个 Ready 任务，产出 1 个 `automation/runs/<runId>/task.json` 清单；若已有 In Progress，只补清单不新认领。试点白名单见 orchestrator-instructions.md。
提交前检查 staged path 只能是 `automation/`；本地治理文档不 commit、不 push。`docs/.local/persistence-probe-*.json` 仅作可选诊断，不是派单门禁。
```

---

## 2. Worker（执行器）

| 字段 | 填写 |
|------|------|
| **Name** | `eova-migration-worker` |
| **Description** | 按 `automation/state/current.json`、`slices/index.json` 和 `runs/<runId>/task.json` 的 Worker 清单，代码级 port 当前切片的 1 个单元到 remis-eova，直接提交并 push dev，再提交对应 `automation/` 结果。 |
| **Trigger** | 当前 Manual Run；首个切片稳定后改为错峰 Weekdays 10:00（Asia/Shanghai），仅 `workerStatus=ready` 执行 |
| **Tools** | 使用 dev 分支直接提交并 push；禁止创建 cursor/* 分支、Draft PR 或 MR。 |

**Instructions（整段复制）：**

```
你是 EOVA 迁移 Worker。严格按 docs/automation/worker-instructions.md 与 DES-002-R1 / R1-F 执行。

从 `automation/state/current.json` 和对应 `automation/runs/<runId>/task.json` 读取 Worker 清单 JSON；`docs/session-current.md` 只作本地参考。只 port 1 个单元；禁止重写逻辑；禁止修改 meta-eova 源仓库内容（只读参考，submodule 内勿提交）。

完成后：提交当前单元业务代码并 push dev；rolling docs 只在本地更新；不创建 Merge Request。
```

---

## 3. Verifier（验证器）

| 字段 | 填写 |
|------|-----|
| **Name** | `eova-migration-verifier` |
| **Description** | 在 dev 验证 Worker 刚提交的单元，回写 verified 或 blocked；不验证 MR。 |
| **Trigger** | 当前 Manual Run；首个切片稳定后添加 GitHub `New push to branch=dev`，并保留 Weekdays 14:00（Asia/Shanghai）Schedule 兜底；仅 `workerStatus=ported_awaiting_verifier` 执行 |
| **Tools** | 不需要 MR/PR 评论；只读代码并写 `automation/` 验证结果 |

**Instructions（整段复制）：**

```
你是 EOVA 迁移 Verifier。严格按 docs/automation/verifier-instructions.md 执行。

验证 dev 上 Worker 刚提交的单元：Java 跑 mvn test；前端跑 pnpm build（若存在）；检查 source revision、ported from 追溯和契约证据。
golden baseline 不存在则跳过 API diff，在 handoff 记 golden: skipped。

结果只写入对应 `automation/runs/<runId>/verifier-result.json`、`events.json`、`runs/index.json` 和 `state/current.json`；失败标 Blocked；不写新 port 代码，不开 PR/MR。目标分支 dev。
```

---

## 试跑顺序（首次建议全用手动 Run）

1. **Orchestrator** Manual Run → 看 `session-current.md` 是否出现 Worker JSON  
2. **Worker** Manual Run → 看 `remis-eova/` 是否出现首个 port 且代码已 push dev
3. **Verifier** Manual Run → 看 test/build、来源追溯和任务板状态

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
- Verifier 不依赖 MR 触发；当前使用 **Manual Run** 做探测，R3 放行后以 GitHub `New push to branch=dev` 即时触发，并以 Weekdays 14:00（Asia/Shanghai）定时运行兜底；只有 Worker push 且 `workerStatus=ported_awaiting_verifier` 时才执行验证。
