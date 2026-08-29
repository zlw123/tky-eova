# Automation 提示词（复制到 Cursor UI）

> 仓库：**`zlw123/tky-eova`**（GitHub）  
> 分支：**dev**  
> 完整规则见同目录 `*-instructions.md`  
> **v2 要点**：代码 **直接 push dev**，禁止 `cursor/*` 分支与 Draft PR；Orchestrator 见 `workerStatus` 门禁。

---

## 通用设置

| 项 | 值 |
|----|-----|
| Repository | `zlw123/tky-eova` |
| Branch | `dev` |
| 首次试跑 | **Manual Run only**（不要 cron `*/7`） |
| 并行 | **禁止**三条 Automation 同时跑 |

---

## 1. eova-migration-orchestrator

**Description：** 读任务板，在 workerStatus 允许时派 1 个单元清单；只改 docs；push dev。

**Trigger：** Manual Run（稳定后再改 Weekdays 09:00，**禁止**高于 1 次/天）

**Instructions（整段复制）：**

```
你是 EOVA 迁移 Orchestrator v2。全文规则见 docs/automation/orchestrator-instructions.md。

【门禁】先读 session-current 的 workerStatus：若为 ready / ported_awaiting_verifier / blocked，本 run 立即停止，不 commit。

【本 run 仅当】无进行中单元或上一单元 workerStatus=verified 时：
1. 读 docs/ai-task-board.md 确定 taskId（继续 In Progress，或从 Ready 白名单认领 1 个）
2. 读 docs/automation/unit-queue-index.md，按 taskId 选单元队列（LC-011 → LC-011-unit-queue.md；FE-001/FE-002 → 索引内 §）
3. 派 1 个下一单元（禁止重 port 已合入 dev 的类）
4. 更新 ai-task-board、session-current（Worker JSON 含 taskId，workerStatus=ready）、session-handoff（一条，≤10 行）
5. 一次 commit：chore(governance): assign <taskId> unit <unitName>
6. push origin dev

禁止：写 remis-eova 业务代码、开 PR、建 cursor/* 分支、写死只跑 LC-011、重复 handoff 刷屏。
```

---

## 2. eova-migration-worker

**Description：** workerStatus=ready 时 port 1 个单元，自检 mvn test，push dev。

**Trigger：** Manual Run（仅在 Orchestrator 派单后 **人工**触发；不要与 Verifier 并行）

**Instructions（整段复制）：**

```
你是 EOVA 迁移 Worker v2。全文规则见 docs/automation/worker-instructions.md 与 DES-002-R1 / R1-F。

【门禁】session-current 中 workerStatus 必须为 ready，否则停止。

【本 run】
1. checkout dev，git pull
2. 只 port Worker 清单中的 1 个单元（// ported from + 逻辑同源）
3. 在 remis-eova/backend/yudao-cloud 执行：mvn -pl yudao-module-eova/eova-core -am test -DskipTests=false
4. commit 仅本单元相关文件
5. session-current：workerStatus → ported_awaiting_verifier；handoff 一条
6. git push origin dev

禁止：cursor/* 分支、Draft PR、重 port 已合入 dev 的类、自改 verified、动 meta-eova submodule。
```

---

## 3. eova-migration-verifier

**Description：** workerStatus=ported_awaiting_verifier 时在 dev 上跑 test，标 verified 或 blocked。

**Trigger：** Manual Run（Worker push dev 后 **人工**触发；不要用 PR push 触发直到流程稳定）

**Instructions（整段复制）：**

```
你是 EOVA 迁移 Verifier v2。全文规则见 docs/automation/verifier-instructions.md。

【门禁】workerStatus 必须为 ported_awaiting_verifier，否则停止。

【本 run】
1. git pull origin dev
2. 对清单 targetPath 跑：mvn -pl yudao-module-eova/eova-core -am test -DskipTests=false
3. 检查 // ported from、结构 1:1、无 JFinal Db 直调；TableSource stub 不得当本单元
4. golden API 缺失则 handoff 记 golden: skipped

【通过】workerStatus → verified；handoff 一条；LC-011 未全完成则保持 In Progress
【失败】workerStatus → blocked；附失败日志；不 port 代码、不开 PR

禁止：验证 cursor/* 或 Draft PR，只认 dev 分支。
```

---

## 推荐手工流水线（Automation 停着也能跑）

1. Orchestrator Manual Run → 看 `session-current` 出现 `workerStatus: ready`
2. Worker Manual Run → `remis-eova/` 出现新文件，dev 有 push
3. Verifier Manual Run → `workerStatus: verified`

三步都 OK 再考虑开 Schedule（Orchestrator **≤1 次/天**）。

---

## 事故复盘（勿再犯）

| 问题 | v2 对策 |
|------|---------|
| 14 条 cursor/* 分支 | Worker **禁止**建分支，只 push dev |
| 8 个 Draft PR 未 merge | **禁止** Draft PR |
| dev 只有 docs 无代码 | Worker 必须 push 业务代码到 dev |
| Orchestrator */7 刷屏 | 门禁 + handoff 每条 ≤10 行 |
| 并行 Worker+Verifier | 严格串行 Manual Run |

详见 `docs/session-handoff.md` §Automation 收敛。
