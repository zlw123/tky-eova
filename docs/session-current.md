# Session Current

## 1. 作用

`eova` 工作区当前热快照。

---

## 2. 当前任务快照

- **Worker 本轮**（2026-08-29T15:17Z，cron `*/15`）：已按清单 port `EovaExpParam`（替换 PR `#3` compile-stub），**待 Verifier**。
- **In Progress**: 1
  - `LC-011`：eova-core 内核（后端）— `EovaExp`、`SqlParse` 已验证；本轮单元 `EovaExpParam` 已 port，整任务未完成
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
  "status": "ported_awaiting_verifier",
  "acceptance": [
    "mvn -pl yudao-module-eova/eova-core -am compile -DskipTests",
    "无 JFinal Db 直调（网关占位可 TODO）",
    "替换 PR #3 中 EovaExpParam compile-stub，禁止把 stub 当已迁移"
  ]
}
```

Worker 完成状态：

- **单元**：`EovaExpParam` 已代码级移植（`// ported from` + 旧 FQCN）；A 直迁 enum，字段/常量注释/getter/setter 与源文件同源，无 JFinal `Db`/`Record`。
- **targetPath**：`remis-eova/backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java/cn/eova/engine/EovaExpParam.java`
- **脚手架**：本 checkout 基于 PR `#3`（`cursor/eova-porting-abdb`）的 `remis-eova/`，**未重 port** `EovaExp` / `SqlParse`。
- **编译桩**：`TableSource` **仍为** compile-stub；**未**把 `TableSource` 当已 port。
- **golden**：`EovaExpParamGoldenTest`（3 tests）；完整 API golden 等 LC-013。

---

## 4. 待 Verifier 项

1. `mvn -pl yudao-module-eova/eova-core -am compile -DskipTests`（cwd：`remis-eova/backend/yudao-cloud`）
2. 可选：`mvn -pl yudao-module-eova/eova-core -am test`（Worker 本轮：EovaExp 4 + SqlParse 5 + EovaExpParam 3，共 12 tests **BUILD SUCCESS**）
3. 新文件含 `ported from`；与源文件结构对应；无整文件重写
4. 无 JFinal `Db`/`Record` 直调
5. `TableSource` 仍为 stub，不得标 LC-011 整任务 Done
6. golden API：baseline 不存在 → `golden: skipped`（钩子已留）

---

## 5. Verifier 已确认（PR #3 / SqlParse）

| 检查项 | 结果 |
|--------|------|
| Java compile | **BUILD SUCCESS**（2026-08-29T15:05:12Z） |
| `ported from` | 通过 |
| 结构对应 | 通过（232 vs 源 229 行；方法 1:1） |
| JFinal `Db`/`Record` | 无 |
| stub 已替换 | 通过 |
| `TableSource` | 仍为 compile-stub，**未**当已 port |
| golden API | **golden: skipped** |
| LC-011 整任务 | **未完成**（当时 `EovaExpParam` 仍为 stub；现已替换，待本轮 Verifier） |

- 上一 Worker PR：https://github.com/zlw123/tky-eova/pull/3 （DRAFT，`port(LC-011): SqlParse`）
- Verifier docs PR：https://github.com/zlw123/tky-eova/pull/4 （DRAFT；`SqlParse` 已通过）
- 更早 Worker PR：https://github.com/zlw123/tky-eova/pull/1 （DRAFT，`EovaExp`）
- 更早 Verifier docs PR：https://github.com/zlw123/tky-eova/pull/2 （DRAFT，仅 `EovaExp`）

---

## 6. 当前已知结论

1. **Automation 模式**：Orchestrator → Worker → Verifier（见 `docs/automation/README.md`）。
2. **Git 远程**：
   - **GitHub（Cursor Cloud / Automation）**：`https://github.com/zlw123/tky-eova.git`（分支 **dev**）
   - **GitLab（内网备份）**：`http://10.20.110.206:45001/remis/modules/remis-eova.git`
   - submodule：`meta-eova/eova`（本轮只读，未提交）
3. **Kingbase SQL 备份**：`docs/sql/kingbase/`。
4. 试点顺序：**LC-011**（`EovaExp` + `SqlParse` 已验证 → `EovaExpParam` 已 port 待验证）→ 再内核单元或 **FE-001/FE-002**。
5. Worker 本轮验收：`mvn -pl yudao-module-eova/eova-core -am compile -DskipTests` → **BUILD SUCCESS**；`test` → 12 tests **BUILD SUCCESS**（2026-08-29T15:17:00Z）。

---

## 7. 后续锚点

Verifier 核验 `EovaExpParam` PR。通过前 Orchestrator **不**派下一单元、**不**认领 FE-001。LC-011 保持 In Progress。

---

## 8. 启动协议

1. `docs/automation/README.md`
2. `docs/DES-002-R1-code-level-migration.md`
3. `docs/ai-task-board.md`
