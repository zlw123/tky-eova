# Session Current

## 1. 作用

`eova` 工作区当前热快照。

---

## 2. 当前任务快照

- **In Progress**: 0

- **Ready**: 2
  - `LC-011`：eova-core 内核（后端）
  - `FE-001`：eova-ui 工程初始化（前端）
  - `AUTO-003`：Agents Window 创建三条 Automation

- **Done（近期）**: AUTO-002、AUTO-001、DES-002-R1、DES-002-R1-F、DES-002-01~03

---

## 3. 当前已知结论

1. **Automation 模式**：Orchestrator → Worker → Verifier（见 `docs/automation/README.md`）。
2. **Git 远程**：`http://10.20.110.206:45001/remis/modules/remis-eova.git`（**dev** 开发分支，main 受保护；submodule meta-eova/eova）。
3. **Kingbase SQL 备份**：`docs/sql/kingbase/`（原 meta-eova 本地未跟踪文件已归档）。
4. **编辑器**：Agents Window 导入 `docs/automation/prefill-workflows.json` → AUTO-003。
5. 试点顺序：**LC-011**（EovaExp）→ 验证后 **FE-001/FE-002**。

---

## 4. 后续锚点

AUTO-003：Agents Window 创建三条 Automation → AUTO-004 首跑 LC-011。

---

## 5. 启动协议

1. `docs/automation/README.md`
2. `docs/DES-002-R1-code-level-migration.md`
3. `docs/ai-task-board.md`
