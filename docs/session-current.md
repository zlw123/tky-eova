# Session Current

## 1. 作用

`eova` 工作区当前热快照。

---

## 2. 当前任务快照

- **Worker 本轮**（2026-08-29T15:45Z，cron `*/15`，`bc-cc198c65`）：已按清单单文件 port `SqlCondition`（A 直迁 POJO），待 Verifier。
- **In Progress**: 1
  - `LC-011`：eova-core 内核（后端）— `EovaExp`、`SqlParse`、`EovaExpParam` 已验证；本轮单元 `SqlCondition` **已 port、待 Verifier**
- **Ready**: 2
  - `FE-001`：eova-ui 工程初始化（前端，白名单；LC-011 进行中，不新认领）
  - `AUTO-003`：Agents Window 创建三条 Automation（**不在试点白名单，禁止认领**）
- **Done（近期）**: AUTO-002、AUTO-001、DES-002-R1、DES-002-R1-F、DES-002-01~03
- **Blocked**: 无

---

## 3. Worker 完成状态（本 run 唯一单元）

```json
{
  "taskId": "LC-011",
  "unitType": "java",
  "unitName": "SqlCondition",
  "sourcePath": "meta-eova/eova/core/src/main/java/cn/eova/engine/SqlCondition.java",
  "targetPath": "remis-eova/backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java/cn/eova/engine/SqlCondition.java",
  "traceability": "cn.eova.engine.SqlCondition",
  "workerStatus": "ported_awaiting_verifier",
  "acceptance": [
    "mvn -pl yudao-module-eova/eova-core -am compile -DskipTests → BUILD SUCCESS",
    "mvn -pl yudao-module-eova/eova-core -am test → 16 tests, Failures: 0（含 SqlConditionGoldenTest 4）",
    "无 JFinal Db/Record 直调（A 直迁 POJO）",
    "未重 port EovaExp/SqlParse/EovaExpParam；TableSource 仍为 compile-stub"
  ]
}
```

Worker 完成说明：

- 基线从 draft PR `#5`（`cursor/eova-porting-e293`）检出 `remis-eova/backend`；本单元**新建** `SqlCondition.java`（非替换 stub）。
- 头注释 `// ported from` + `old FQCN`；三构造器 + getter/setter 与源 1:1；无逻辑改写。
- golden 钩子：`SqlConditionGoldenTest`（4 tests）；完整 API golden 等 LC-013。
- **禁止自派下一单元**。`EovaExp` / `SqlParse` / `EovaExpParam` 禁止重 port。`TableSource` 仍为 compile-stub，**禁止**当已迁移。

---

## 4. Verifier 已确认（PR #5 / EovaExpParam）+ 本轮待验

| 检查项 | 结果 |
|--------|------|
| Java compile（本轮 Worker） | **BUILD SUCCESS**（cwd `remis-eova/backend/yudao-cloud`） |
| golden 单测（本轮） | **16 tests, Failures: 0**（`SqlConditionGoldenTest` 4） |
| `ported from` | Worker 自检通过（`SqlCondition` + 既有 `EovaExp` / `SqlParse` / `EovaExpParam`） |
| 结构对应 | Worker 自检：构造器 3 + getter/setter 1:1，无 JFinal |
| `TableSource` | 仍为 compile-stub，**未**当本单元 |
| 前端 `pnpm build` | **skipped**（无 `remis-eova/fornt/eova-ui`） |
| golden API | **golden: skipped**（钩子已留，完整 baseline 等 LC-013） |
| LC-011 整任务 | **未完成**（当前单元 `SqlCondition` 待 Verifier） |

- 本轮 Worker PR：https://github.com/zlw123/tky-eova/pull/7 （DRAFT/OPEN，`port(LC-011): SqlCondition`，`cursor/eova-porting-dc30` → `dev`）
- 上一 Worker PR：https://github.com/zlw123/tky-eova/pull/5 （DRAFT，`port(LC-011): EovaExpParam`，`cursor/eova-porting-e293`）
- Verifier docs PR：https://github.com/zlw123/tky-eova/pull/6 （DRAFT；`EovaExpParam` 已通过）
- 上一 Worker PR：https://github.com/zlw123/tky-eova/pull/3 （DRAFT，`port(LC-011): SqlParse`）
- 上一 Verifier docs PR：https://github.com/zlw123/tky-eova/pull/4 （DRAFT；`SqlParse` 已通过）
- 更早 Worker PR：https://github.com/zlw123/tky-eova/pull/1 （DRAFT，`EovaExp`）
- 更早 Verifier docs PR：https://github.com/zlw123/tky-eova/pull/2 （DRAFT，仅 `EovaExp`）
- 15:30Z Worker `bc-cb9f1833` 跳过（分支 `cursor/eova-porting-282b`，仅治理文档、**无新 PR**）。
- 15:37Z Verifier `bc-2082b094` IDLE，PR `#6` 通过 `EovaExpParam`。
- 15:42Z Orchestrator `bc-511667c6` IDLE，补派 `SqlCondition`。

---

## 5. 当前已知结论

1. **Automation 模式**：Orchestrator → Worker → Verifier（见 `docs/automation/README.md`）。
2. **Git 远程**：
   - **GitHub（Cursor Cloud / Automation）**：`https://github.com/zlw123/tky-eova.git`（分支 **dev**）
   - **GitLab（内网备份）**：`http://10.20.110.206:45001/remis/modules/remis-eova.git`
   - submodule：`meta-eova/eova`
3. **Kingbase SQL 备份**：`docs/sql/kingbase/`。
4. **Automation**：本 run `bc-cc198c65-c467-4813-8f7e-0456c69d2873`；Orchestrator `bc-511667c6` IDLE 已派 `SqlCondition`；Verifier `bc-2082b094` IDLE 且 draft PR `#6` 通过 `EovaExpParam`；上一 Worker `bc-ff58e26f` IDLE 且 draft PR `#5`。
5. 试点顺序：**LC-011**（`EovaExp` + `SqlParse` + `EovaExpParam` 已验证 → 当前 `SqlCondition` 已 port 待验）→ 再内核单元或 **FE-001/FE-002**。
6. 源文件只读参考，**未改** `meta-eova`。

---

## 6. 后续锚点

待 Verifier 核验 `SqlCondition`（compile + `ported from` + 结构 1:1）。Verifier 通过前 Orchestrator **不**派再下一单元。Worker **禁止自派**。LC-011 整任务未 Done。

---

## 7. 启动协议

1. `docs/automation/README.md`
2. `docs/DES-002-R1-code-level-migration.md`
3. `docs/ai-task-board.md`
