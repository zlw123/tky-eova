# EOVA Automation 控制面

本目录是三个云端 Automation 在同一 `dev` 分支上的机器状态事实源。业务代码仍位于 `remis-eova/`，旧源码 `meta-eova/eova/` 只读。

## 目录约定

- `queue/units.json`：迁移单元索引和状态。
- `queue/blocked.json`：被阻塞的单元摘要。
- `state/current.json`：唯一活动 `runId`、状态、lease 和控制面门禁。
- `state/sequence.json`：由 Orchestrator 分配递增 `runId`。
- `runs/index.json`：所有 run 的摘要数组，便于快速扫描。
- `runs/<runId>/`：任务快照、派单、Worker 结果、Verifier 结果和事件明细。

## 当前状态

初始化时 `controlPlaneStatus=blocked`。完成三次独立角色 Manual Run 加一次 Orchestrator 最终读回，并确认 manifest freeze 和旧 demo baseline 后，才能由 Orchestrator 更新为 `ready`。

## 写入边界

- Orchestrator 只能写 `automation/`，不能改业务代码。
- Worker 只能写当前单元业务代码和对应 run 的状态文件。
- Verifier 只能写验证结果和事件，不能改业务代码。
- 所有角色必须先 fetch/rebase，禁止 force push；提交前检查 staged path 白名单。
