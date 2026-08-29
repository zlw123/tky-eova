# Session Current

## 1. 作用

`eova` 工作区当前热快照。

---

## 2. 当前任务快照

- **Verifier 本轮**（2026-08-29T15:37Z，cron `*/35`）：Worker PR `#5` `EovaExpParam` **单元通过**；`LC-011` 保持 In Progress；Worker 清单已清空，待 Orchestrator 派下一单元。
- **In Progress**: 1
  - `LC-011`：eova-core 内核（后端）— `EovaExp`、`SqlParse`、`EovaExpParam` 已验证；整任务未完成
- **Ready**: 2
  - `FE-001`：eova-ui 工程初始化（前端，白名单；LC-011 进行中，不新认领）
  - `AUTO-003`：Agents Window 创建三条 Automation（**不在试点白名单，禁止认领**）
- **Done（近期）**: AUTO-002、AUTO-001、DES-002-R1、DES-002-R1-F、DES-002-01~03
- **Blocked**: 无

---

## 3. Worker 清单

（已清空。Orchestrator 派下一单元后再写入唯一 JSON。）

---

## 4. Verifier 已确认（PR #5 / EovaExpParam）

| 检查项 | 结果 |
|--------|------|
| Java compile | **BUILD SUCCESS**（2026-08-29T15:37:20Z） |
| `ported from` | 通过（`EovaExpParam` + 既有 `EovaExp` / `SqlParse`） |
| 结构对应 | 通过（49 vs 源 46 行；enum 常量 5 个 + getter/setter 1:1，无整文件重写） |
| JFinal `Db`/`Record` | 无 |
| stub 已替换 | 通过（PR `#3` 的 compile-stub 已换成实 port） |
| `TableSource` | 仍为 compile-stub，**未**当已 port |
| 前端 `pnpm build` | **skipped**（无 `remis-eova/fornt/eova-ui`） |
| golden API | **golden: skipped**（无 `docs/golden/`、无 DES-002-R2 baseline） |
| LC-011 整任务 | **未完成**（engine 仍有 `EovaExpBuilder` / `EovaExpConfig` / `ExpUtil` / `SqlCondition`） |

- Worker PR：https://github.com/zlw123/tky-eova/pull/5 （DRAFT，`port(LC-011): EovaExpParam`，`cursor/eova-porting-e293`）
- 本轮 Verifier docs PR：https://github.com/zlw123/tky-eova/pull/6 （DRAFT；`EovaExpParam` 已通过）
- 上一 Worker PR：https://github.com/zlw123/tky-eova/pull/3 （DRAFT，`port(LC-011): SqlParse`）
- 上一 Verifier docs PR：https://github.com/zlw123/tky-eova/pull/4 （DRAFT；`SqlParse` 已通过）
- 更早 Worker PR：https://github.com/zlw123/tky-eova/pull/1 （DRAFT，`EovaExp`）
- 更早 Verifier docs PR：https://github.com/zlw123/tky-eova/pull/2 （DRAFT，仅 `EovaExp`）
- 编译命令：`cd remis-eova/backend/yudao-cloud && mvn -pl yudao-module-eova/eova-core -am compile -DskipTests`
- 并行 Worker `bc-cb9f1833`（`cursor/eova-porting-282b`）仅治理 skip，**无**新 port、**无** PR

### Worker PR `#5` 评论摘要（Automation 无法 @ 评论，写在本 docs PR）

```
Verifier PASS — LC-011 单元 EovaExpParam
- mvn -pl yudao-module-eova/eova-core -am compile -DskipTests → BUILD SUCCESS (2026-08-29T15:37:20Z)
- ported from / 结构对应 / 无 JFinal Db 直调 / stub 已替换：通过
- pnpm build: skipped（无 eova-ui）
- golden: skipped（无 docs/golden、无 DES-002-R2 baseline）
- LC-011 整任务未完成，保持 In Progress；勿 merge
```

---

## 5. 当前已知结论

1. **Automation 模式**：Orchestrator → Worker → Verifier（见 `docs/automation/README.md`）。
2. **Git 远程**：
   - **GitHub（Cursor Cloud / Automation）**：`https://github.com/zlw123/tky-eova.git`（分支 **dev**）
   - **GitLab（内网备份）**：`http://10.20.110.206:45001/remis/modules/remis-eova.git`
   - submodule：`meta-eova/eova`
3. **Kingbase SQL 备份**：`docs/sql/kingbase/`。
4. **Automation**：本 run `bc-2082b094`；Worker `bc-ff58e26f` IDLE 且 draft PR `#5`；上一 Verifier `bc-4866cd2a` IDLE 且 PR `#4` 通过 `SqlParse`；Orchestrator `bc-0f24eb2a` IDLE（15:28Z 保留 `EovaExpParam` 待验）。
5. 试点顺序：**LC-011**（`EovaExp` + `SqlParse` + `EovaExpParam` 已验证 → 下一内核单元）→ 再 **FE-001/FE-002**。
6. Automation Tools 仅有 `open_git_pr`，无法给 Worker PR 写评论；验证摘要写在本 Verifier docs PR。

---

## 6. 后续锚点

Orchestrator 补下一单元清单（建议 `EovaExpBuilder`，或 `EovaExpConfig` / `ExpUtil` / `SqlCondition`）。`EovaExp` / `SqlParse` / `EovaExpParam` 已验证，**禁止重 port**。Orchestrator **不**认领 FE-001，**不**开 PR。

---

## 7. 启动协议

1. `docs/automation/README.md`
2. `docs/DES-002-R1-code-level-migration.md`
3. `docs/ai-task-board.md`
