# workspace persistence probe 手动提示词（可选诊断）

> 用途：在怀疑云端 workspace 挂载、路径或权限异常时，验证 Orchestrator、Worker、Verifier 是否共享同一份工作区。该流程不再是正式迁移门禁。
>
> 这不是业务迁移 run。三条 Automation 需要临时以 `PERSISTENCE_PROBE_MODE` 执行，不能直接使用正常迁移提示词，否则 R3 门禁会按预期阻止运行。

## 执行前（仅在需要诊断时）

1. 三个 Automation 都选择 **Manual Run**；临时关闭 Schedule 和 GitHub event，避免混入其他 run。
2. 三个 run 必须使用相同 Repository：`https://github.com/zlw123/tky-eova.git`、相同 Branch：`dev`、相同 workspace 挂载和权限。
3. 生成一个只用于本次探测的 `probeId`，例如 `probe-20260830T120000Z-a7f3`；三次角色 run 和最终读回 run 必须使用同一个 `probeId`。
4. 严格顺序：Orchestrator → Worker → Verifier → 新的 Orchestrator 读回确认。前三次是角色 run，最后一次是确认 run，所以实际点击 **4 次 Manual Run**。

## Run 1：Orchestrator 写入

```text
这是 workspace persistence probe，不是业务迁移。进入 PERSISTENCE_PROBE_MODE，忽略正常迁移派单逻辑，但仍禁止写业务代码、commit、push、创建分支或 PR。

Repository 必须是 https://github.com/zlw123/tky-eova.git，Branch 必须是 dev。先执行 pwd、git remote -v、git branch --show-current，记录 Automation 实际看到的绝对 workspacePath；不要假设它等于本机路径。

使用本次固定 probeId=<填入同一个 probeId>，只在 docs/.local/ 写入 docs/.local/persistence-probe-<probeId>.json。内容至少包含：probeId、repository、branch、workspacePath、orchestrator.seen=true、orchestrator.at=<UTC>、worker.seen=false、verifier.seen=false、nextOrchestrator.seen=false、result=pending、failureReason=null。

写入后立即读回并确认 JSON 完整；如果 docs/.local 不存在、不可写、仓库/分支不一致或无法记录绝对 workspacePath，写 result=blocked、failureReason=control-plane-not-persistent 并停止。不得修改 session-current 的 workerStatus，不得触碰 remis-eova 和 meta-eova。
```

验收：文件存在；`probeId` 正确；`orchestrator.seen=true`；记录了实际 `workspacePath`；没有业务代码、commit 或 push。

## Run 2：Worker 读取并追加

```text
这是 workspace persistence probe，不是业务迁移。进入 PERSISTENCE_PROBE_MODE，禁止 port、commit、push、创建分支或 PR，也不要修改 workerStatus。

Repository 必须是 https://github.com/zlw123/tky-eova.git，Branch 必须是 dev。先执行 pwd、git remote -v、git branch --show-current。读取 docs/.local/persistence-probe-<同一个 probeId>.json。

确认文件存在、probeId 与本 run 一致、repository/branch 一致，并且本次 pwd 得到的绝对 workspacePath 与文件中的 workspacePath 完全相同。通过后只把 worker.seen=true、worker.at=<UTC> 写回同一个 JSON，保留 Orchestrator 记录不变。

如果读不到文件、probeId 不同、workspacePath 不同、仓库/分支不一致或目录不可写，写 result=blocked、failureReason=control-plane-not-persistent 并停止。不得创建替代文件，不得猜测路径，不得修改业务代码。
```

验收：同一个文件被读取并追加；`orchestrator.seen` 保持 true；`worker.seen=true`；workspacePath 完全一致。

## Run 3：Verifier 读取并追加

```text
这是 workspace persistence probe，不是业务迁移。进入 PERSISTENCE_PROBE_MODE，禁止 port、commit、push、创建分支或 PR，也不要修改 workerStatus。

Repository 必须是 https://github.com/zlw123/tky-eova.git，Branch 必须是 dev。先执行 pwd、git remote -v、git branch --show-current。读取 docs/.local/persistence-probe-<同一个 probeId>.json。

确认文件存在、probeId 与本 run 一致、repository/branch 一致、workspacePath 与本次 pwd 完全相同，并且 orchestrator.seen=true、worker.seen=true。通过后只把 verifier.seen=true、verifier.at=<UTC> 写回同一个 JSON，result 仍保持 pending。

如果任一前置记录读不到或路径不一致，写 result=blocked、failureReason=control-plane-not-persistent 并停止。不要修改业务代码，不要执行正常 Verifier 的 verified/blocked 状态流转。
```

验收：同一个文件被读取并追加；前三阶段记录均存在；`verifier.seen=true`；`result` 仍为 `pending`。

## Run 4：新的 Orchestrator 读回确认

```text
这是 workspace persistence probe 的最终读回确认，不是业务迁移。进入 PERSISTENCE_PROBE_MODE，禁止派单、写业务代码、commit、push、创建分支或 PR。

Repository 必须是 https://github.com/zlw123/tky-eova.git，Branch 必须是 dev。先执行 pwd、git remote -v、git branch --show-current。读取 docs/.local/persistence-probe-<同一个 probeId>.json。

确认 probeId、repository、branch、workspacePath 与前三个 run 完全一致，并且 orchestrator.seen=true、worker.seen=true、verifier.seen=true。通过后只更新 nextOrchestrator.seen=true、nextOrchestrator.at=<UTC>、result=passed，并读回确认。将 session-current.md 只追加一行摘要：probeId、workspacePath、result=passed；不得改变 workerStatus 或任务板状态。

如果任一记录缺失、路径不一致、文件不可写或仓库/分支不一致，更新 result=blocked、failureReason=control-plane-not-persistent，并在 session-current 记录 blocker；不得启用迁移队列。
```

## 通过标准

只有同一个文件、同一个 `probeId`、同一个绝对 `workspacePath` 被四次 run 连续读写并最终得到 `result=passed`，才能说明诊断通过。诊断失败只记录 `control-plane-not-persistent` 供排障，不改变正式 `automation/` 状态，也不要求通过诊断后才能派单；正式派单仍由 Git 控制面、manifest、baseline、Ready 白名单和 lease 门禁决定。
