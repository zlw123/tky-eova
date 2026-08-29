# Session Current

## 1. 作用

`eova` 工作区当前热快照。

---

## 2. 当前任务快照

- **Orchestrator 本轮**（2026-08-29T14:40Z，cron `*/10`）：已有 In Progress，**未新认领**。Verifier PR `#2` 已确认 `EovaExp` 通过并清空清单；本轮补派 LC-011 **下一单元 `SqlParse`**。
- **In Progress**: 1
  - `LC-011`：eova-core 内核（后端）— 首单元 `EovaExp` 已验证；本轮单元为 `SqlParse`
- **Ready**: 2
  - `FE-001`：eova-ui 工程初始化（前端，白名单；LC-011 进行中，不新认领）
  - `AUTO-003`：Agents Window 创建三条 Automation（**不在试点白名单，禁止认领**）
- **Done（近期）**: AUTO-002、AUTO-001、DES-002-R1、DES-002-R1-F、DES-002-01~03
- **Blocked**: 无

---

## 3. Worker 清单（本 run 唯一单元）

```json
{
  "taskId": "LC-011",
  "unitType": "java",
  "sourcePath": "meta-eova/eova/core/src/main/java/cn/eova/engine/SqlParse.java",
  "targetPath": "remis-eova/backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java/cn/eova/engine/SqlParse.java",
  "traceability": "cn.eova.engine.SqlParse",
  "acceptance": [
    "mvn -pl yudao-module-eova/eova-core -am compile -DskipTests",
    "无 JFinal Db 直调（网关占位可 TODO）",
    "替换 PR #1 中 SqlParse compile-stub，禁止把 stub 当已迁移"
  ]
}
```

Worker 注意：

- 本 checkout / `dev` 上 `remis-eova/` 仍仅 `.gitkeep`；脚手架与 `EovaExp` 在 draft PR `#1`（`cursor/eova-porting-143b`）。本单元应基于该分支 **替换 stub**，不要另起空模块。
- `SqlParse` 依赖 `cn.eova.sql.dql.TableSource`：若编译缺类，只允许最小 stub（字段/getter），**禁止**把 `TableSource` 当本单元已 port。
- 本 tick 并行 Worker `bc-9acdc25d` 可能读到旧 `EovaExp` 清单：`EovaExp` 已验证，**禁止重 port**；以本 JSON 为准只做 `SqlParse`。
- Orchestrator **不写业务代码、不开 PR、不 merge**。

---

## 4. Verifier 已确认（PR #1 / EovaExp）

| 检查项 | 结果 |
|--------|------|
| Java compile | **BUILD SUCCESS**（2026-08-29T14:31:32Z） |
| `ported from` | 通过 |
| JFinal `Db`/`Record` | 无 |
| golden API | **golden: skipped** |
| LC-011 整任务 | **未完成**（`SqlParse` 仍为 stub） |

- Worker PR：https://github.com/zlw123/tky-eova/pull/1 （DRAFT，未 merge）
- Verifier docs PR：https://github.com/zlw123/tky-eova/pull/2 （DRAFT，清单已清空；本轮 Orchestrator 已补 `SqlParse`）

---

## 5. 当前已知结论

1. **Automation 模式**：Orchestrator → Worker → Verifier（见 `docs/automation/README.md`）。
2. **Git 远程**：
   - **GitHub（Cursor Cloud / Automation）**：`https://github.com/zlw123/tky-eova.git`（分支 **dev**）
   - **GitLab（内网备份）**：`http://10.20.110.206:45001/remis/modules/remis-eova.git`
   - submodule：`meta-eova/eova`
3. **Kingbase SQL 备份**：`docs/sql/kingbase/`。
4. **Automation**：本 run `bc-3981182d-77af-4302-a158-4da6b824d9f1`；Verifier `bc-5c23de0f` IDLE 且已通过；新 Worker `bc-9acdc25d` 本 tick 并行。
5. 试点顺序：**LC-011**（`EovaExp` 已验证 → 本轮 `SqlParse`）→ 再内核单元或 **FE-001/FE-002**。
6. 源文件已核对存在：`meta-eova/eova/core/src/main/java/cn/eova/engine/SqlParse.java`。

---

## 6. 后续锚点

Worker 按本 JSON 单文件 port `SqlParse`（替换 stub）→ Verifier 核验。Orchestrator **不**再认领 FE-001，**不**开 PR。

---

## 7. 启动协议

1. `docs/automation/README.md`
2. `docs/DES-002-R1-code-level-migration.md`
3. `docs/ai-task-board.md`
