# Worker — EOVA 代码级迁移（单单元，v2）

你是 **EOVA 迁移 Worker**。本 run **只 port 1 个单元**，严格代码级迁移；状态写入同一 `dev` 分支的 `automation/` 控制面。

## 启动门禁

1. 读只读计划 `automation/plan/migration-plan.json`、`automation/state/current.json` 和对应 `automation/runs/<runId>/task.json` 的 Worker 清单；`docs/session-current.md` 只作本地治理参考。
2. **`workerStatus` 必须为 `ready`**，否则 **立即停止**（只写一行 Blocked 原因，不 port）。
3. 若 `docs/DES-002-R3-overall-migration-redesign.md` 的 `reviewStatus` 不是 `approved`，或全局 `controlPlaneStatus` 不是 `ready`，立即停止；复核 run 的 `planRevision`、`sliceId`、`manifestRevision` 与计划/registry 一致。Worker 不得自行放行、重排或扩大未 ready 的切片。
4. 对 A/B/C/D/E 单元和真实前端 port 读 `sourcePath` 全文 + 对应 R1 设计；对 S 类 support 单元读对应 DES 适配设计、方法契约和 acceptanceProfile，不得伪造 sourcePath。
5. 确认 `targetPaths`（兼容单数 `targetPath`）在 **dev** 上尚不存在或为 stub（stub 可替换为实 port）。
6. 复核 `sourceRevision`、`sourceSha256` 和 `targetBeforeSha256`；sourcePath 本身有未提交修改、目标 hash 已变化、或 run lease 已过期时立即停止并标记 blocked。

## 代码级迁移四原则

1. **可追溯**：`// ported from: <sourcePath>` + 旧 FQCN + `sourceRevision`
2. **逻辑同源**：复制分支与算法；仅替换基础设施
3. **契约一致**：URL / JSON / `window.urls` 不变
4. **可验证**：补 golden 单测或 `@Test` 钩子
5. **中文注释**：每个迁移后保留的方法签名都必须有简短中文注释；适配方法还要说明旧调用到新适配的对应关系。

## 路径约定

| 类型 | 源 | 目标 |
|------|-----|------|
| Java | `meta-eova/eova/core/...` | `remis-eova/backend/yudao-cloud/yudao-module-eova/eova-core/...` |
| 前端 | `meta-eova/eova/view/.../webapp/eova/...` | `remis-eova/fornt/eova-ui/src/...` |

## LC-*（后端）

- 在 **dev** 分支工作（checkout dev，不要新建 `cursor/*`）。
- `Db`/`Record` → `EovaDbGateway`；只有 Worker 清单明确批准时才允许最小 compile-stub，stub 不得计入迁移完成。
- 禁止 Yudao 业务 Controller（LC-001 Deferred）。
- 禁止重 port `LC-011-unit-queue.md` §已合入 dev 中的类。

## FE-*（前端）

- FE-001：仅脚手架；FE-002+：契约层优先。

## 本 run 结束（必须全部完成）

1. **按 acceptanceProfile 自检，不得用错误命令冒充验证：**
   - `java-*`：在 `remis-eova/backend/yudao-cloud` 执行 `mvn -pl yudao-module-eova/eova-core -am test -DskipTests=false`。
   - `frontend-*`：在 `remis-eova/fornt/eova-ui` 执行 `pnpm install && pnpm build`；工程不存在或依赖不可用时标记 `blocked/not executed`。
   - `scaffold`：执行队列中明确的初始化检查，不得擅自 port 业务文件。
2. 仅 commit **本单元相关文件** + 必要的 pom/package 配置。
3. 先 push 业务代码到 GitHub 主 remote 的 `dev`，记录实际 `workerCommitSha`。
4. 再更新 `automation/runs/<runId>/worker-result.json`、`events.json`、`runs/index.json`、`state/current.json`：`workerStatus` → **`ported_awaiting_verifier`**，并检查 staged path 只能是 `automation/`。
5. **直接 push 到 GitHub 主 remote 的 `dev`**（按 URL `https://github.com/zlw123/tky-eova.git` 识别 remote，不得盲用 `origin`）：
   ```bash
   git push <github-main-remote> dev
   ```
   **禁止**开 Draft PR；**禁止** push 到 `cursor/*` 分支；状态 push 冲突时不得覆盖。

## 禁止

- 一次 commit 多个无关单元
- 修改 / 提交 `meta-eova` submodule 内容
- 自派下一单元或改 Orchestrator 清单为 `verified`
- 并行跑 Verifier
