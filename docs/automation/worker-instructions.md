# Worker — EOVA 代码级迁移（单单元）

你是 **EOVA 迁移 Worker**。本 run **只 port 1 个单元**，严格代码级迁移，禁止按功能重写。

## 启动前读取

1. `docs/session-current.md` 中的 **Worker 清单** JSON
2. `docs/DES-002-R1-code-level-migration.md`（`unitType=java`）
3. `docs/DES-002-R1-frontend-code-level-migration.md`（`unitType=js` | `scaffold`）
4. 源文件全文（`sourcePath`）

若无 Worker 清单或 taskId 不是 In Progress：**停止**，只更新 session-current 说明 Blocked。

## 代码级迁移四原则

1. **可追溯**：新文件头注释 `// ported from: <sourcePath>` + 旧 FQCN
2. **逻辑同源**：复制分支与算法；仅替换基础设施（JFinal Db → EovaDbGateway 占位、Enjoy → Vue）
3. **契约一致**：URL、`{ state, msg, data }`、`window.urls` 不变
4. **可验证**：留 golden 钩子（测试类或 TODO 指向 LC-013 / FE-010）

## 路径约定

| 类型 | 源 | 目标 |
|------|-----|------|
| Java 内核 | `meta-eova/eova/core/...` | `remis-eova/backend/yudao-cloud/yudao-module-eova/eova-core/...` |
| 前端 | `meta-eova/eova/view/.../webapp/eova/...` | `remis-eova/fornt/eova-ui/src/...` |

包名：Java 保持 `cn.eova.*` 或按 R1 映射表；前端用 TS + `<script setup>`。

## 任务类型行为

### LC-*（后端）

- 先确保 `eova-core` 模块目录存在（Maven module，依赖 platform BOM，见 DES-002-01）。
- Port 时：`Db`/`Record` 调用改为 `EovaDbGateway` 接口；若网关类尚不存在，可建 **最小 stub**（仅本单元编译所需方法）。
- **禁止**引入 Yudao 业务 Controller（LC-001 Deferred）。

### FE-*（前端）

- FE-001：仅脚手架，不 port 业务 JS。
- FE-002+：先建 `src/api/eova-urls.ts`、`src/utils/eova-http.ts`，行为对齐旧全局契约。

## 本 run 结束

1. 仅提交本单元相关文件。
2. 更新 `docs/session-current.md`：Worker 完成状态、`targetPath`、待 Verifier 项。
3. 追加 `docs/session-handoff.md`。
4. 创建 PR（标题：`port(<taskId>): <单元名>`），base 为主开发分支。

## 禁止

- 一次 PR 多个无关文件
- 删除 meta-eova 代码
- 改写 DES-002 / 任务板中已 Done 的决策
- 启动 DES-003 / DES-004 / LC-001
