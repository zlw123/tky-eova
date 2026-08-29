# Session Current

## 1. 作用

`eova` 工作区当前热快照。

---

## 2. 当前任务快照

- **Orchestrator 本轮**（2026-08-29T14:30Z，cron `*/10`）：已有 In Progress，**未新认领**；EovaExp 清单不缺，原样保留。Worker 已 port 并开 draft PR，Verifier 本 tick 并行跑，**不派下一单元**。
- **In Progress**: 1
  - `LC-011`：eova-core 内核（后端）— 单元仍为 `EovaExp`（14:11Z 认领；Worker `bc-e0817351` 已 port，待 Verifier）
- **Ready**: 2
  - `FE-001`：eova-ui 工程初始化（前端，白名单；LC-011 进行中，不新认领）
  - `AUTO-003`：Agents Window 创建三条 Automation（**不在试点白名单，禁止认领**）
- **Done（近期）**: AUTO-002、AUTO-001、DES-002-R1、DES-002-R1-F、DES-002-01~03

---

## 3. Worker 清单（本 run 唯一单元）

```json
{
  "taskId": "LC-011",
  "unitType": "java",
  "sourcePath": "meta-eova/eova/core/src/main/java/cn/eova/engine/EovaExp.java",
  "targetPath": "remis-eova/backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java/cn/eova/engine/EovaExp.java",
  "traceability": "cn.eova.engine.EovaExp",
  "acceptance": [
    "mvn -pl yudao-module-eova/eova-core -am compile -DskipTests",
    "无 JFinal Db 直调（网关占位可 TODO）"
  ]
}
```

Worker 状态：`bc-e0817351` 已 IDLE，draft PR `#1`（`cursor/eova-porting-143b` → `dev`）含 `EovaExp` 及编译 stub。本 checkout / `dev` 上 `remis-eova/` 仍仅 `.gitkeep`，产物在 PR 分支。Verifier `bc-5c23de0f` 本 tick 并行核验。Orchestrator **不写业务代码、不派 `SqlParse`、不开 PR**。`SqlParse` 等在 Worker 分支仅为 compile-stub，禁止当已迁移。

---

## 4. 当前已知结论

1. **Automation 模式**：Orchestrator → Worker → Verifier（见 `docs/automation/README.md`）。
2. **Git 远程**：
   - **GitHub（Cursor Cloud / Automation）**：`https://github.com/zlw123/tky-eova.git`（分支 **dev**）
   - **GitLab（内网备份）**：`http://10.20.110.206:45001/remis/modules/remis-eova.git`
   - submodule：`meta-eova/eova`
3. **Kingbase SQL 备份**：`docs/sql/kingbase/`（原 meta-eova 本地未跟踪文件已归档）。
4. **Automation**：环境 `tky-eova`（`2c986cd5-a3b2-11f1-a7d1-d6b4613131ce`）/`github.com/zlw123/tky-eova`；本 run `bc-056b4f6c-6751-4ea5-8ce4-b86beaab6234`。
5. 试点顺序：**LC-011**（EovaExp 已 port、待 Verifier）→ 通过后再派下一内核单元或 **FE-001/FE-002**。
6. 源文件已核对存在：`meta-eova/eova/core/src/main/java/cn/eova/engine/EovaExp.java`。
7. Worker 产物：https://github.com/zlw123/tky-eova/pull/1 （DRAFT，未合并）。

---

## 5. 后续锚点

Verifier 核验 PR `#1` → 通过后再由 Orchestrator 派 LC-011 下一单元（建议 `SqlParse`，仍属同一 In Progress）或改认领 FE-001。本轮 Orchestrator **不**派新单元、**不**新认领。

---

## 6. 启动协议

1. `docs/automation/README.md`
2. `docs/DES-002-R1-code-level-migration.md`
3. `docs/ai-task-board.md`
