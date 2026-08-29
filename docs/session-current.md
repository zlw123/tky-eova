# Session Current

## 1. 作用

`eova` 工作区当前热快照。

---

## 2. 当前任务快照

- **Orchestrator 本轮**（2026-08-29T15:07Z，cron `*/7`）：已有 In Progress，**未新认领**。Verifier PR `#4` 确认 `SqlParse` 通过且清单已清空，本轮补写下一单元 `EovaExpParam`。
- **In Progress**: 1
  - `LC-011`：eova-core 内核（后端）— `EovaExp`、`SqlParse` 已验证；本轮单元为 `EovaExpParam`
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

- 本 checkout / `dev` 上 `remis-eova/` 仍仅 `.gitkeep`；脚手架、`EovaExp`、`SqlParse` 在 draft PR `#3`（`cursor/eova-porting-abdb`）。本单元应基于该分支 **替换 stub**，不要另起空模块。
- `EovaExpParam` 为无 JFinal 依赖的 enum（A 直迁）：复制源文件分支/字段/getter，加 `ported from` 头注释。
- `EovaExp` 与 `SqlParse` 已验证，**禁止重 port**。`TableSource` 仍为 compile-stub，**禁止**把 `TableSource` 当本单元已 port。
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
| LC-011 整任务 | **未完成**（本轮改派 `EovaExpParam`） |

- Worker PR：https://github.com/zlw123/tky-eova/pull/3 （DRAFT，`port(LC-011): SqlParse`）
- Verifier docs PR：https://github.com/zlw123/tky-eova/pull/4 （DRAFT；`SqlParse` 已通过，清单已清空）
- 上一 Worker PR：https://github.com/zlw123/tky-eova/pull/1 （DRAFT，`EovaExp`）
- 上一 Verifier docs PR：https://github.com/zlw123/tky-eova/pull/2 （DRAFT，仅 `EovaExp`）

---

## 5. 当前已知结论

1. **Automation 模式**：Orchestrator → Worker → Verifier（见 `docs/automation/README.md`）。
2. **Git 远程**：
   - **GitHub（Cursor Cloud / Automation）**：`https://github.com/zlw123/tky-eova.git`（分支 **dev**）
   - **GitLab（内网备份）**：`http://10.20.110.206:45001/remis/modules/remis-eova.git`
   - submodule：`meta-eova/eova`
3. **Kingbase SQL 备份**：`docs/sql/kingbase/`。
4. **Automation**：本 run `bc-f3d27b81-b828-426c-9ed3-44319bef21c8`；上一 Orchestrator `bc-58935e16` IDLE（15:00Z 保留 `SqlParse`）；Worker `bc-8bc8dca0` IDLE 且 draft PR `#3`；Verifier `bc-4866cd2a` IDLE 且 PR `#4` 通过 `SqlParse`。
5. 试点顺序：**LC-011**（`EovaExp` + `SqlParse` 已验证 → 当前 `EovaExpParam`）→ 再内核单元或 **FE-001/FE-002**。
6. 源文件已核对存在：`meta-eova/eova/core/src/main/java/cn/eova/engine/EovaExpParam.java`。PR `#3` 中 `EovaExpParam.java` 仍为 compile-stub。

---

## 6. 后续锚点

下一 Worker 按本 JSON 单文件 port `EovaExpParam`（替换 stub）→ Verifier 核验。Orchestrator **不**再认领 FE-001，**不**开 PR。Verifier 通过前不派再下一单元。

---

## 7. 启动协议

1. `docs/automation/README.md`
2. `docs/DES-002-R1-code-level-migration.md`
3. `docs/ai-task-board.md`
