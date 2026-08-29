# Session Current

## 1. 作用

`eova` 工作区当前热快照。

---

## 2. 当前任务快照

- **In Progress**: 1
  - `LC-011`：eova-core 内核（后端）— 首单元 `EovaExp` **Worker 已 port**，待 Verifier

- **Ready**: 2
  - `FE-001`：eova-ui 工程初始化（前端，白名单；LC-011 进行中，不新认领）
  - `AUTO-003`：Agents Window 创建三条 Automation（**不在试点白名单，禁止认领**）

- **Done（近期）**: AUTO-002、AUTO-001、DES-002-R1、DES-002-R1-F、DES-002-01~03

---

## 3. Worker 完成状态（本 run 唯一单元）

```json
{
  "taskId": "LC-011",
  "unitType": "java",
  "unitName": "EovaExp",
  "workerStatus": "ported",
  "sourcePath": "meta-eova/eova/core/src/main/java/cn/eova/engine/EovaExp.java",
  "targetPath": "remis-eova/backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java/cn/eova/engine/EovaExp.java",
  "traceability": "cn.eova.engine.EovaExp",
  "infraReplacements": ["LogKit → slf4j", "Kv → Map（field_width）"],
  "jfinalDb": "none",
  "acceptanceRun": {
    "cwd": "remis-eova/backend/yudao-cloud",
    "compile": "mvn -pl yudao-module-eova/eova-core -am compile -DskipTests → BUILD SUCCESS",
    "test": "mvn -pl yudao-module-eova/eova-core -am test → Tests run: 4, Failures: 0"
  }
}
```

### 待 Verifier

1. 在 `remis-eova/backend/yudao-cloud` 执行：`mvn -pl yudao-module-eova/eova-core -am compile -DskipTests`
2. 确认 `EovaExp.java` 含 `ported from` + 旧 FQCN；结构与源文件对应；**无** `com.jfinal` / `Db` 直调
3. golden API：`docs/golden/` 与 DES-002-R2 均不存在 → 记 `golden: skipped`；类级钩子见 `EovaExpGoldenTest`（TODO LC-013）
4. **不要**把 LC-011 标 Done：仅完成首单元；`SqlParse` 等仍为 compile stub，需 Orchestrator 派下一单元

### stub（非 port 单元，仅本文件编译）

`SqlParse`、`EovaExpParam`、`EovaConfig`、`EovaOption`、`MetaField`、`MetaObject`、`SqlUtil.notNewLine`、`cn.eova.tools.x`。头注释均为 `compile-stub`，禁止当已迁移。

Maven：本环境无 platform 仓，parent 先 import Spring Boot **3.4.5** BOM；完整 `yudao-dependencies` 待接入。

---

## 4. 当前已知结论

1. **Automation 模式**：Orchestrator → Worker → Verifier（见 `docs/automation/README.md`）。
2. **Git 远程**：
   - **GitHub（Cursor Cloud / Automation）**：`https://github.com/zlw123/tky-eova.git`（分支 **dev**）
   - **GitLab（内网备份）**：`http://10.20.110.206:45001/remis/modules/remis-eova.git`
   - submodule：`meta-eova/eova`
3. **Kingbase SQL 备份**：`docs/sql/kingbase/`（原 meta-eova 本地未跟踪文件已归档）。
4. **Automation**：环境已切到 `tky-eova`；Worker 已 port `EovaExp`。
5. 试点顺序：**LC-011**（EovaExp 已 port，待验证）→ 验证后下一内核单元或 **FE-001/FE-002**。

---

## 5. 后续锚点

Verifier 编译/追溯检查本 PR → 通过后 Orchestrator 派 LC-011 下一单元（建议 `SqlParse`）或改认领 FE-001。

---

## 6. 启动协议

1. `docs/automation/README.md`
2. `docs/DES-002-R1-code-level-migration.md`
3. `docs/ai-task-board.md`
