# Session Current

## 1. 作用

`eova` 工作区当前热快照。

---

## 2. 当前任务快照

- **Verifier 本轮**（2026-08-29T16:02Z，cron `*/35`，`bc-519612fb`）：核验 Worker PR `#7` 的 `SqlCondition` **通过**。`LC-011` 保持 In Progress；Worker 清单已清空。
- **In Progress**: 1
  - `LC-011`：eova-core 内核（后端）— `EovaExp`、`SqlParse`、`EovaExpParam`、`SqlCondition` 已验证
- **Ready**: 2
  - `FE-001`：eova-ui 工程初始化（前端，白名单；LC-011 进行中，不新认领）
  - `AUTO-003`：Agents Window 创建三条 Automation（**不在试点白名单，禁止认领**）
- **Done（近期）**: AUTO-002、AUTO-001、DES-002-R1、DES-002-R1-F、DES-002-01~03
- **Blocked**: 无

---

## 3. Worker 清单（已清空，待 Orchestrator 派下一单元）

```json
{}
```

Orchestrator / Worker 注意：

- `EovaExp`、`SqlParse`、`EovaExpParam`、`SqlCondition` 已验证，**禁止重 port**。
- 并行 Worker `bc-5e0fea14`（`cursor/eova-porting-ed46`）若仍以 `SqlCondition` 为清单，**必须跳过、禁止再开一单**。
- `TableSource` 在 PR `#7` 仍为 compile-stub，**禁止**当已 port。
- 本 checkout / `dev` 上 `remis-eova/` 仍仅 `.gitkeep`；脚手架与已 port 类在 draft PR `#7`（`cursor/eova-porting-dc30`）。
- 未派 `EovaExpBuilder`：其依赖 `ExpUtil.parseSql` 与 JFinal `Db`/`Record`，单单元会被迫堆 stub。
- Verifier **不写业务代码、不 merge**。

---

## 4. Verifier 已确认（PR #7 / SqlCondition）

| 检查项 | 结果 |
|--------|------|
| Java compile（SqlCondition） | **BUILD SUCCESS**（2026-08-29T16:02:08Z） |
| `ported from`（SqlCondition） | 通过（`SqlCondition` + 既有 `EovaExp` / `SqlParse` / `EovaExpParam`） |
| 结构对应（SqlCondition） | 通过（54 vs 源 55 行；构造器 3 + getter/setter 1:1） |
| JFinal `Db`/`Record` | 无（A 直迁 POJO） |
| `TableSource` | 仍为 compile-stub，**未**当已 port |
| 前端 `pnpm build` | **skipped**（无 `remis-eova/fornt/eova-ui`） |
| golden API | **golden: skipped**（无 `docs/golden/`、无 DES-002-R2 baseline） |
| LC-011 整任务 | **未完成**（内核仍有后续单元；`TableSource` 仍 stub） |

- 本轮 Worker PR：https://github.com/zlw123/tky-eova/pull/7 （DRAFT，`port(LC-011): SqlCondition`，`cursor/eova-porting-dc30`）
- 本轮 Verifier docs PR：见本 run 新开 PR（`cursor/eova-72d0`）
- 上一 Worker PR：https://github.com/zlw123/tky-eova/pull/5 （DRAFT，`port(LC-011): EovaExpParam`）
- 上一 Verifier docs PR：https://github.com/zlw123/tky-eova/pull/6 （DRAFT；`EovaExpParam` 已通过）
- 上一 Worker PR：https://github.com/zlw123/tky-eova/pull/3 （DRAFT，`port(LC-011): SqlParse`）
- 上一 Verifier docs PR：https://github.com/zlw123/tky-eova/pull/4 （DRAFT；`SqlParse` 已通过）
- 更早 Worker PR：https://github.com/zlw123/tky-eova/pull/1 （DRAFT，`EovaExp`）
- 更早 Verifier docs PR：https://github.com/zlw123/tky-eova/pull/2 （DRAFT，仅 `EovaExp`）
- 15:37Z Verifier `bc-2082b094` IDLE，PR `#6` 通过 `EovaExpParam`。
- 15:45Z Worker `bc-cc198c65` IDLE，draft PR `#7` `port(LC-011): SqlCondition`。
- 16:00Z Orchestrator `bc-f5639bcd` IDLE，保留 `SqlCondition` 清单；当时并行 Worker `bc-5e0fea14` 与本 Verifier RUNNING。
- 16:02Z 本轮 Verifier `bc-519612fb` 通过 `SqlCondition`。

---

## 5. 当前已知结论

1. **Automation 模式**：Orchestrator → Worker → Verifier（见 `docs/automation/README.md`）。
2. **Git 远程**：
   - **GitHub（Cursor Cloud / Automation）**：`https://github.com/zlw123/tky-eova.git`（分支 **dev**）
   - **GitLab（内网备份）**：`http://10.20.110.206:45001/remis/modules/remis-eova.git`
   - submodule：`meta-eova/eova`
3. **Kingbase SQL 备份**：`docs/sql/kingbase/`。
4. **Automation**：本 run `bc-519612fb-05df-4d1c-a536-00f755bd8106`；Worker `bc-cc198c65` IDLE 且 draft PR `#7` 已通过；并行 Worker `bc-5e0fea14` 禁止重 port `SqlCondition`；上一 Verifier `bc-2082b094` IDLE 且 draft PR `#6` 通过 `EovaExpParam`；上一 Orchestrator `bc-f5639bcd` IDLE（16:00Z 保留清单）。
5. 试点顺序：**LC-011**（`EovaExp` + `SqlParse` + `EovaExpParam` + `SqlCondition` 已验证）→ 再内核单元或 **FE-001/FE-002**。
6. 编译命令：`cd remis-eova/backend/yudao-cloud && mvn -pl yudao-module-eova/eova-core -am compile -DskipTests` → BUILD SUCCESS（eova-core 11 个源文件）。

---

## 6. 后续锚点

Orchestrator 派下一内核单元（勿再派 `SqlCondition`）。**不**再认领 FE-001，**不**开业务 PR，**不** merge Worker PR `#7`。并行 Worker **禁止重 port** 已验证类。

---

## 7. 启动协议

1. `docs/automation/README.md`
2. `docs/DES-002-R1-code-level-migration.md`
3. `docs/ai-task-board.md`
