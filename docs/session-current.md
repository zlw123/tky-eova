# Session Current

## 1. 作用

`eova` 工作区当前热快照。

---

## 2. 当前任务快照

- **In Progress**: 1
  - `LC-011`：eova-core 内核（后端）— 本轮已认领，单元 = `EovaExp`

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

Worker 注意：`remis-eova/` 当前为空目录，需先建 `eova-core` Maven 模块（依赖 platform BOM），再 port 本文件；包名保持 `cn.eova.engine`。`Db`/`Record` 改 `EovaDbGateway` stub；`Kv`/`LogKit` 按 R1 换 Map / slf4j。禁止一次 port 多个文件。

---

## 4. 当前已知结论

1. **Automation 模式**：Orchestrator → Worker → Verifier（见 `docs/automation/README.md`）。
2. **Git 远程**：
   - **GitHub（Cursor Cloud / Automation）**：`https://github.com/zlw123/tky-eova.git`（分支 **dev**）
   - **GitLab（内网备份）**：`http://10.20.110.206:45001/remis/modules/remis-eova.git`
   - submodule：`meta-eova/eova`
3. **Kingbase SQL 备份**：`docs/sql/kingbase/`（原 meta-eova 本地未跟踪文件已归档）。
4. **Automation**：环境已切到 `tky-eova`（`github.com/zlw123/tky-eova`）；本 run 已能读仓库并认领任务。
5. 试点顺序：**LC-011**（EovaExp，进行中）→ 验证后 **FE-001/FE-002**。

---

## 5. 后续锚点

Worker 按上方 JSON port `EovaExp` → Verifier 编译/追溯检查 → 通过后再由 Orchestrator 派下一单元（仍属 LC-011）或改认领 FE-001。

---

## 6. 启动协议

1. `docs/automation/README.md`
2. `docs/DES-002-R1-code-level-migration.md`
3. `docs/ai-task-board.md`
