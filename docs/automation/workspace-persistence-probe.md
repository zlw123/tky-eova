# workspace persistence probe（可选诊断）

> 状态：可选，当前未运行
> 用途：仅在怀疑云端 workspace 挂载、路径或权限异常时诊断；不再是正式派单门禁。

## 诊断说明

需要时可使用三次独立角色 Manual Run，分别由 Orchestrator、Worker、Verifier 执行；完成前三次后，再启动一个新的 Orchestrator Manual Run 做最终读回确认。四次 run 的 Repository、Branch、工作区挂载路径和权限应相同：

```text
Repository = https://github.com/zlw123/tky-eova.git
Branch     = dev
workspace  = /Users/zhouliwei/eova（或 Automation 实际挂载的同一绝对路径）
```

## 探测步骤

1. Orchestrator 只在 `docs/.local/` 写入唯一随机 marker、`probeId`、UTC 时间和 `writer=orchestrator`，不改业务代码、不提交、不 push。
2. Worker Manual Run 读取同一个 marker，追加 `reader=worker` 和读取时间；如果读不到或工作区不是同一路径，立即标记 `blocked: control-plane-not-persistent`。
3. Verifier Manual Run 读取 marker 和 Worker 记录，追加 `reader=verifier`；然后启动一个新的 Orchestrator Manual Run（第 4 次 run），确认它能看到完整三阶段记录并写入 `nextOrchestrator.seen=true`。
4. 只要任一阶段读不到上一阶段写入、看到不同 `probeId`、无法写入本地治理目录，探测失败，不得启用迁移队列。
5. 探测结果只存本地 `docs/.local/persistence-probe-<probeId>.json`，不得提交到 GitHub；`session-current.md` 只记录摘要、probeId 和结果。诊断结果不改变 `automation/state/current.json` 的正式状态。

## 证据格式

```json
{
  "probeId": "probe-<UTC>-<suffix>",
  "repository": "https://github.com/zlw123/tky-eova.git",
  "branch": "dev",
  "workspacePath": "<Automation 实际看到的绝对路径>",
  "orchestrator": {"seen": true, "at": "<UTC>"},
  "worker": {"seen": true, "at": "<UTC>"},
  "verifier": {"seen": true, "at": "<UTC>"},
  "nextOrchestrator": {"seen": true, "at": "<UTC>"},
  "result": "passed|blocked",
  "failureReason": null
}
```

本地诊断结果只用于定位 workspace 问题，不能替代或覆盖 `automation/` Git 控制面状态。正式派单只检查 `automation/state/current.json`、manifest、baseline、Ready 白名单和 run lease。
