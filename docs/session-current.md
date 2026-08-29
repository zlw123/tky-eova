# Session Current

## 1. 作用

`eova` 工作区当前热快照。

---

## 2. 当前任务快照

- **Orchestrator 本轮**（2026-08-29T15:42Z，cron `*/7`，`bc-511667c6`）：已有 In Progress，**未新认领**。Verifier PR `#6` 确认 `EovaExpParam` 通过且清单已清空，本轮补写下一单元 `SqlCondition`。
- **In Progress**: 1
  - `LC-011`：eova-core 内核（后端）— `EovaExp`、`SqlParse`、`EovaExpParam` 已验证；本轮单元 `SqlCondition`
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
  "sourcePath": "meta-eova/eova/core/src/main/java/cn/eova/engine/SqlCondition.java",
  "targetPath": "remis-eova/backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java/cn/eova/engine/SqlCondition.java",
  "traceability": "cn.eova.engine.SqlCondition",
  "acceptance": [
    "mvn -pl yudao-module-eova/eova-core -am compile -DskipTests",
    "无 JFinal Db 直调（网关占位可 TODO）",
    "PR #5 尚无 SqlCondition，本单元新建实 port；禁止把 TableSource stub 或其它文件当本单元已迁移"
  ]
}
```

Worker 注意：

- 本 checkout / `dev` 上 `remis-eova/` 仍仅 `.gitkeep`；脚手架、`EovaExp`、`SqlParse`、`EovaExpParam` 在 draft PR `#5`（`cursor/eova-porting-e293`）。
- `SqlCondition` 为无 JFinal 依赖的 POJO（A 直迁）。PR `#5` 的 `engine/` 仅有 `EovaExp`/`SqlParse`/`EovaExpParam`，需新建本文件。
- `EovaExp`、`SqlParse`、`EovaExpParam` 已验证，**禁止重 port**。`TableSource` 在 PR `#5` 仍为 compile-stub，**禁止**当本单元。
- 未派 `EovaExpBuilder`：其依赖 `ExpUtil.parseSql` 与 JFinal `Db`/`Record`，单单元会被迫堆 stub；Verifier 建议的备选中优先 A 直迁。
- Orchestrator **不写业务代码、不开 PR、不 merge**。

---

## 4. Verifier 已确认（PR #5 / EovaExpParam）

| 检查项 | 结果 |
|--------|------|
| Java compile | **BUILD SUCCESS**（2026-08-29T15:37:20Z） |
| `ported from` | 通过（`EovaExpParam` + 既有 `EovaExp` / `SqlParse`） |
| 结构对应 | 通过（49 vs 源 46 行；enum 常量 5 个 + getter/setter 1:1） |
| JFinal `Db`/`Record` | 无 |
| stub 已替换 | 通过（PR `#3` compile-stub 已换成实 port） |
| `TableSource` | 仍为 compile-stub，**未**当已 port |
| 前端 `pnpm build` | **skipped**（无 `remis-eova/fornt/eova-ui`） |
| golden API | **golden: skipped** |
| LC-011 整任务 | **未完成**（当前单元 `SqlCondition`） |

- Worker PR：https://github.com/zlw123/tky-eova/pull/5 （DRAFT，`port(LC-011): EovaExpParam`，`cursor/eova-porting-e293`）
- Verifier docs PR：https://github.com/zlw123/tky-eova/pull/6 （DRAFT；`EovaExpParam` 已通过，清单已清空）
- 上一 Worker PR：https://github.com/zlw123/tky-eova/pull/3 （DRAFT，`port(LC-011): SqlParse`）
- 上一 Verifier docs PR：https://github.com/zlw123/tky-eova/pull/4 （DRAFT；`SqlParse` 已通过）
- 更早 Worker PR：https://github.com/zlw123/tky-eova/pull/1 （DRAFT，`EovaExp`）
- 更早 Verifier docs PR：https://github.com/zlw123/tky-eova/pull/2 （DRAFT，仅 `EovaExp`）
- 15:30Z Worker `bc-cb9f1833` 跳过（分支 `cursor/eova-porting-282b`，仅治理文档、**无新 PR**）。
- 15:37Z Verifier `bc-2082b094` IDLE，PR `#6` 通过 `EovaExpParam`。

---

## 5. 当前已知结论

1. **Automation 模式**：Orchestrator → Worker → Verifier（见 `docs/automation/README.md`）。
2. **Git 远程**：
   - **GitHub（Cursor Cloud / Automation）**：`https://github.com/zlw123/tky-eova.git`（分支 **dev**）
   - **GitLab（内网备份）**：`http://10.20.110.206:45001/remis/modules/remis-eova.git`
   - submodule：`meta-eova/eova`
3. **Kingbase SQL 备份**：`docs/sql/kingbase/`。
4. **Automation**：本 run `bc-511667c6-e705-4ad8-93c6-3a1a980f6ccd`；Verifier `bc-2082b094` IDLE 且 draft PR `#6` 通过 `EovaExpParam`；Worker `bc-cb9f1833` IDLE 跳过无 PR；上一 Worker `bc-ff58e26f` IDLE 且 draft PR `#5`；上一 Orchestrator `bc-270de1d5` IDLE（15:35Z 仍待验 `EovaExpParam`）。
5. 试点顺序：**LC-011**（`EovaExp` + `SqlParse` + `EovaExpParam` 已验证 → 当前 `SqlCondition`）→ 再内核单元或 **FE-001/FE-002**。
6. 源文件已核对存在：`meta-eova/eova/core/src/main/java/cn/eova/engine/SqlCondition.java`。

---

## 6. 后续锚点

下一 Worker 按本 JSON 单文件 port `SqlCondition`（基线 PR `#5`）。Orchestrator **不**再认领 FE-001，**不**开 PR。Verifier 通过前不派再下一单元。

---

## 7. 启动协议

1. `docs/automation/README.md`
2. `docs/DES-002-R1-code-level-migration.md`
3. `docs/ai-task-board.md`
