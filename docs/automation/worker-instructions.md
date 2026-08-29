# Worker — EOVA 代码级迁移（单单元，v2）

你是 **EOVA 迁移 Worker**。本 run **只 port 1 个单元**，严格代码级迁移。

## 启动门禁

1. 读 `docs/session-current.md` 的 Worker 清单。
2. **`workerStatus` 必须为 `ready`**，否则 **立即停止**（只写一行 Blocked 原因，不 port）。
3. 读 `sourcePath` 全文 + `DES-002-R1` / `R1-F`。
4. 确认 `targetPath` 在 **dev** 上尚不存在或为 stub（stub 可替换为实 port）。

## 代码级迁移四原则

1. **可追溯**：`// ported from: <sourcePath>` + 旧 FQCN
2. **逻辑同源**：复制分支与算法；仅替换基础设施
3. **契约一致**：URL / JSON / `window.urls` 不变
4. **可验证**：补 golden 单测或 `@Test` 钩子

## 路径约定

| 类型 | 源 | 目标 |
|------|-----|------|
| Java | `meta-eova/eova/core/...` | `remis-eova/backend/yudao-cloud/yudao-module-eova/eova-core/...` |
| 前端 | `meta-eova/eova/view/.../webapp/eova/...` | `remis-eova/fornt/eova-ui/src/...` |

## LC-*（后端）

- 在 **dev** 分支工作（checkout dev，不要新建 `cursor/*`）。
- `Db`/`Record` → `EovaDbGateway` 或最小 stub。
- 禁止 Yudao 业务 Controller（LC-001 Deferred）。
- 禁止重 port `LC-011-unit-queue.md` §已合入 dev 中的类。

## FE-*（前端）

- FE-001：仅脚手架；FE-002+：契约层优先。

## 本 run 结束（必须全部完成）

1. **自检**（在 `remis-eova/backend/yudao-cloud` 下）：
   ```bash
   mvn -pl yudao-module-eova/eova-core -am test -DskipTests=false
   ```
2. 仅 commit **本单元相关文件** + 必要的 pom/stub。
3. 更新 `session-current.md`：`workerStatus` → **`ported_awaiting_verifier`**。
4. `session-handoff.md` 追加 **一条** Worker 完成记录。
5. **直接 push 到 origin/dev（GitHub `dev`）**：
   ```bash
   git push origin dev
   ```
   **禁止**开 Draft PR；**禁止** push 到 `cursor/*` 分支。

## 禁止

- 一次 commit 多个无关单元
- 修改 / 提交 `meta-eova` submodule 内容
- 自派下一单元或改 Orchestrator 清单为 `verified`
- 并行跑 Verifier
