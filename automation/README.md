# EOVA Automation 控制面

本目录是三个云端 Automation 在同一 `dev` 分支上的机器状态事实源。业务代码仍位于 `remis-eova/`，旧源码 `meta-eova/eova/` 只读。

## 目录约定

- `queue/units.json`：迁移单元索引和状态。
- `queue/blocked.json`：被阻塞的单元摘要。
- `state/current.json`：唯一活动 `runId`、状态、lease、活动切片引用和全局控制面状态。
- `state/sequence.json`：由 Orchestrator 分配递增 `runId`。
- `runs/index.json`：所有 run 的摘要数组，便于快速扫描。
- `runs/<runId>/`：任务快照、派单、Worker 结果、Verifier 结果和事件明细。
- `slices/index.json`：按用户旅程登记切片状态、局部 manifest 和局部 baseline。
- `slices/<sliceId>/manifest.jsonl`：当前切片逐单元源/目标/依赖/契约清单。
- `plan/migration-plan.json`：主协作者预先冻结的迁移路线；Orchestrator 只读并按 `dispatchOrder` 调度。

## 当前状态

全局 Git 控制面当前 `controlPlaneStatus=ready`，表示本目录可读写、可审计；当前没有可派切片，所以 `queueStatus=blocked`。迁移路线以 `plan/migration-plan.json` 为准，Orchestrator 不负责长程规划，只按已冻结的 `dispatchOrder` 和 `unitOrder` 调度。`S01-login-shell` 的局部 `manifestStatus=provisional`、`baselineStatus=not_ready`，准备完成后才会进入 Ready。`docs/.local/persistence-probe-*.json` 仅是可选诊断，不是正式派单门禁；正式状态由本目录的提交历史、`stateRevision`、lease 和 hash 保护。

## 写入边界

- Orchestrator 只能写 `automation/`，不能改业务代码。
- Worker 只能写当前单元业务代码和对应 run 的状态文件。
- Verifier 只能写验证结果和事件，不能改业务代码。
- 所有角色必须先 fetch/rebase，禁止 force push；提交前检查 staged path 白名单。
