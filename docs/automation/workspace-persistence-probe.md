# workspace persistence probe

> 状态：未执行
> 用途：在启用三条 Automation 前，证明 Orchestrator、Worker、Verifier 看到的是同一个持久化工作区和同一份治理状态。

## 通过条件

必须使用三次独立角色 Manual Run，分别由 Orchestrator、Worker、Verifier 执行；不能由同一个 run 自己模拟三种角色。完成前三次后，再启动一个新的 Orchestrator Manual Run 做最终读回确认，因此完整闭环实际点击 4 次 Manual Run。四次 run 的 Repository、Branch、工作区挂载路径和权限必须相同：

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
5. 探测结果只存本地 `docs/.local/persistence-probe-<probeId>.json`，不得提交到 GitHub；`session-current.md` 只记录摘要、probeId 和结果。

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

当前本地只能证明 `docs/.local` 可由本地进程读写，不能替代三条 Cursor Automation 的跨 run 证据。因此在实际 Manual Run 完成前，R3 门禁保持 `not executed`，`AUTO-003` 不得转 Ready。
