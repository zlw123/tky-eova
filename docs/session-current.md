# Session Current

## 1. 作用

`eova` 工作区当前热快照。

---

## 2. 当前任务快照

- **Verifier 本轮**（2026-08-29T15:05Z，cron `*/30`）：Worker PR `#3` `SqlParse` **单元通过**；`LC-011` 保持 In Progress；Worker 清单已清空，待 Orchestrator 派下一单元。
- **In Progress**: 1
  - `LC-011`：eova-core 内核（后端）— `EovaExp`、`SqlParse` 已验证；整任务未完成
- **Ready**: 2
  - `FE-001`：eova-ui 工程初始化（前端，白名单；LC-011 进行中，不新认领）
  - `AUTO-003`：Agents Window 创建三条 Automation（**不在试点白名单，禁止认领**）
- **Done（近期）**: AUTO-002、AUTO-001、DES-002-R1、DES-002-R1-F、DES-002-01~03
- **Blocked**: 无

---

## 3. Worker 清单

（已清空。Orchestrator 派下一单元后再写入唯一 JSON。）

---

## 4. Verifier 已确认（PR #3 / SqlParse）

| 检查项 | 结果 |
|--------|------|
| Java compile | **BUILD SUCCESS**（2026-08-29T15:05:12Z） |
| `ported from` | 通过（`SqlParse` + 既有 `EovaExp`） |
| 结构对应 | 通过（232 vs 源 229 行；方法 1:1，无整文件重写） |
| JFinal `Db`/`Record` | 无 |
| stub 已替换 | 通过（不再是 39 行 compile-stub） |
| `TableSource` | 仍为 compile-stub，**未**当已 port |
| 前端 `pnpm build` | **skipped**（无 `remis-eova/fornt/eova-ui`） |
| golden API | **golden: skipped**（无 `docs/golden/`、无 DES-002-R2 baseline） |
| LC-011 整任务 | **未完成**（engine 仍有 `EovaExpParam` stub / `EovaExpBuilder` / `EovaExpConfig` / `ExpUtil` / `SqlCondition`） |

- Worker PR：https://github.com/zlw123/tky-eova/pull/3 （DRAFT，`port(LC-011): SqlParse`）
- 本轮 Verifier docs PR：https://github.com/zlw123/tky-eova/pull/4
- 上一 Worker PR：https://github.com/zlw123/tky-eova/pull/1 （DRAFT，`EovaExp`）
- 上一 Verifier docs PR：https://github.com/zlw123/tky-eova/pull/2 （DRAFT，仅 `EovaExp`）
- 编译命令：`cd remis-eova/backend/yudao-cloud && mvn -pl yudao-module-eova/eova-core -am compile -DskipTests`

---

## 5. 当前已知结论

1. **Automation 模式**：Orchestrator → Worker → Verifier（见 `docs/automation/README.md`）。
2. **Git 远程**：
   - **GitHub（Cursor Cloud / Automation）**：`https://github.com/zlw123/tky-eova.git`（分支 **dev**）
   - **GitLab（内网备份）**：`http://10.20.110.206:45001/remis/modules/remis-eova.git`
   - submodule：`meta-eova/eova`
3. **Kingbase SQL 备份**：`docs/sql/kingbase/`。
4. **Automation**：本 run `bc-4866cd2a`；Worker `bc-8bc8dca0` 已交付 PR `#3`；Orchestrator `bc-58935e16` IDLE（15:00Z 未新认领）。
5. 试点顺序：**LC-011**（`EovaExp` + `SqlParse` 已验证 → 下一内核单元）→ 再 **FE-001/FE-002**。
6. Automation Tools 仅有 `open_git_pr`，无法给 Worker PR 写评论；验证摘要写在本 Verifier docs PR。

---

## 6. 后续锚点

Orchestrator 补下一单元清单（建议替换 `EovaExpParam` stub，或 port `EovaExpBuilder`）。Verifier 通过前不派再下一单元。Orchestrator **不**认领 FE-001，**不**开 PR。

---

## 7. 启动协议

1. `docs/automation/README.md`
2. `docs/DES-002-R1-code-level-migration.md`
3. `docs/ai-task-board.md`
