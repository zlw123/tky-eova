# Session Current

## 1. 作用

`eova` 工作区当前热快照。

---

## 2. 当前任务快照

- **Orchestrator 本轮**（2026-08-29T15:35Z，cron `*/7`，`bc-270de1d5`）：已有 In Progress，**未新认领**。`EovaExpParam` 清单已存在且源文件仍在，原样保留；Worker 已提交 draft PR `#5`，Verifier 未通过 `EovaExpParam`，不派再下一单元。
- **In Progress**: 1
  - `LC-011`：eova-core 内核（后端）— `EovaExp`、`SqlParse` 已验证；本轮单元仍为 `EovaExpParam`（Worker 已 port，待 Verifier）
- **Ready**: 2
  - `FE-001`：eova-ui 工程初始化（前端，白名单；LC-011 进行中，不新认领）
  - `AUTO-003`：Agents Window 创建三条 Automation（**不在试点白名单，禁止认领**）
- **Done（近期）**: AUTO-002、AUTO-001、DES-002-R1、DES-002-R1-F、DES-002-01~03
- **Blocked**: 无（15:30Z Worker 空跑不算任务级 Blocked）

---

## 3. Worker 清单（本 run 唯一单元）

```json
{
  "taskId": "LC-011",
  "unitType": "java",
  "sourcePath": "meta-eova/eova/core/src/main/java/cn/eova/engine/EovaExpParam.java",
  "targetPath": "remis-eova/backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java/cn/eova/engine/EovaExpParam.java",
  "traceability": "cn.eova.engine.EovaExpParam",
  "acceptance": [
    "mvn -pl yudao-module-eova/eova-core -am compile -DskipTests",
    "无 JFinal Db 直调（网关占位可 TODO）",
    "替换 PR #3 中 EovaExpParam compile-stub，禁止把 stub 当已迁移"
  ]
}
```

Worker 注意：

- 本 checkout / `dev` 上 `remis-eova/` 仍仅 `.gitkeep`；脚手架、`EovaExp`、`SqlParse` 在 PR `#3`；`EovaExpParam` 实 port 在 draft PR `#5`（`cursor/eova-porting-e293`）。
- `EovaExpParam` 为无 JFinal 依赖的 enum（A 直迁）。PR `#5` 已带 `ported from` 头注释并替换 stub；**整任务未完成，待 Verifier**。
- `EovaExp` 与 `SqlParse` 已验证，**禁止重 port**。`TableSource` 在 PR `#5` 仍为 compile-stub，**禁止**把 `TableSource` 当本单元已 port。15:30Z Worker `bc-cb9f1833` 已核对 PR `#5` 后跳过，**禁止再重 port `EovaExpParam`**。
- Orchestrator **不写业务代码、不开 PR、不 merge**。

---

## 4. Verifier 已确认（PR #3 / SqlParse）

| 检查项 | 结果 |
|--------|------|
| Java compile | **BUILD SUCCESS**（2026-08-29T15:05:12Z） |
| `ported from` | 通过 |
| 结构对应 | 通过（232 vs 源 229 行；方法 1:1） |
| JFinal `Db`/`Record` | 无 |
| stub 已替换 | 通过 |
| `TableSource` | 仍为 compile-stub，**未**当已 port |
| golden API | **golden: skipped** |
| LC-011 整任务 | **未完成**（当前单元仍为 `EovaExpParam`，待 Verifier） |

- Worker PR：https://github.com/zlw123/tky-eova/pull/5 （DRAFT，`port(LC-011): EovaExpParam`，`cursor/eova-porting-e293`）
- 上一 Worker PR：https://github.com/zlw123/tky-eova/pull/3 （DRAFT，`port(LC-011): SqlParse`）
- Verifier docs PR：https://github.com/zlw123/tky-eova/pull/4 （DRAFT；`SqlParse` 已通过，清单已清空）
- 更早 Worker PR：https://github.com/zlw123/tky-eova/pull/1 （DRAFT，`EovaExp`）
- 更早 Verifier docs PR：https://github.com/zlw123/tky-eova/pull/2 （DRAFT，仅 `EovaExp`）
- 15:21Z / 15:28Z 复核：PR `#5` 上 `EovaExpParam.java` **已非 stub**（`// ported from` + enum 同源）。
- 15:30Z Worker `bc-cb9f1833` 跳过（分支 `cursor/eova-porting-282b`，仅治理文档、**无新 PR**）。
- 15:35Z：本 tick 并行 Verifier `bc-2082b094`（`cursor/eova-migration-verification-6a58`）RUNNING，结果未回；仍无 `EovaExpParam` 验证 PR。

---

## 5. 当前已知结论

1. **Automation 模式**：Orchestrator → Worker → Verifier（见 `docs/automation/README.md`）。
2. **Git 远程**：
   - **GitHub（Cursor Cloud / Automation）**：`https://github.com/zlw123/tky-eova.git`（分支 **dev**）
   - **GitLab（内网备份）**：`http://10.20.110.206:45001/remis/modules/remis-eova.git`
   - submodule：`meta-eova/eova`
3. **Kingbase SQL 备份**：`docs/sql/kingbase/`。
4. **Automation**：本 run `bc-270de1d5-318c-43cc-8faa-73c7c982f3a2`；上一 Orchestrator `bc-0f24eb2a` IDLE（15:28Z 保留 `EovaExpParam`）；15:30Z Worker `bc-cb9f1833` IDLE 跳过无 PR；上一 Worker `bc-ff58e26f` IDLE 且 draft PR `#5`；上一 Verifier `bc-4866cd2a` IDLE 且 PR `#4` 通过 `SqlParse`。本 tick 并行 Verifier `bc-2082b094` RUNNING，**尚未**确认 `EovaExpParam`。
5. 试点顺序：**LC-011**（`EovaExp` + `SqlParse` 已验证 → 当前 `EovaExpParam` 已 port 待验证）→ 再内核单元或 **FE-001/FE-002**。
6. 源文件已核对存在：`meta-eova/eova/core/src/main/java/cn/eova/engine/EovaExpParam.java`。PR `#5` 中 `EovaExpParam.java` 已为实 port。

---

## 6. 后续锚点

下一 Verifier 按本 JSON 核验 `EovaExpParam`（PR `#5`）。Orchestrator **不**再认领 FE-001，**不**开 PR。Verifier 通过前不派再下一单元。

---

## 7. 启动协议

1. `docs/automation/README.md`
2. `docs/DES-002-R1-code-level-migration.md`
3. `docs/ai-task-board.md`
