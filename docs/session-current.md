# Session Current

## 1. 作用

`eova` 工作区当前热快照。

---

## 2. 当前任务快照

- **In Progress**: 1
  - `LC-011`：eova-core 内核 — 已合入 **dev** 的 port 单元见下表

- **Ready**: 2
  - `FE-001`：eova-ui 工程初始化
  - `AUTO-003`：Automation 手建（**已停**，待改规则后再启）

- **Done（近期）**: AUTO-002、AUTO-001、DES-002-R1、DES-002-R1-F

- **Blocked**: 无

---

## 3. LC-011 已合入 dev 的 port 成果

| 单元 | 目标路径 | 状态 |
|------|----------|------|
| EovaExp | `remis-eova/backend/.../engine/EovaExp.java` | 已合入 dev |
| SqlParse | 同上目录 `SqlParse.java` | 已合入 dev |
| EovaExpParam | 同上目录 `EovaExpParam.java` | 已合入 dev |
| SqlCondition | 同上目录 `SqlCondition.java` | 已合入 dev |
| 脚手架 | `remis-eova/backend/**/pom.xml` | 已合入 dev |
| TableSource | `.../sql/dql/TableSource.java` | **compile-stub**，非实 port |

- golden 单测：4 类 × 各 4 tests（Worker 自检通过，待本地复验）
- **Automation 已全部停用**；14 条 `cursor/*` 分支待清理

---

## 4. 当前已知结论

1. **GitHub 主 remote**：`https://github.com/zlw123/tky-eova.git`（**dev**）；GitLab 内网备份仍保留。
2. **乱因**：Orchestrator cron 过密 + Worker Draft PR 未 merge + 并行 run → 分支爆炸；**已人工收敛**。
3. 下一单元（R2 完成前仍白名单）：内核下一 Java 类或 **FE-001**，**禁止**再开 `*/7` cron。

---

## 5. 后续锚点

1. 本地 `mvn -pl yudao-module-eova/eova-core -am test` 复验
2. 修订 Automation 规则（Worker merge 到 dev、Orchestrator 禁止直 push）
3. DES-002-R2 对照表

---

## 6. 启动协议

1. `docs/DES-002-R1-code-level-migration.md`
2. `docs/ai-task-board.md`
3. `remis-eova/backend/yudao-cloud/yudao-module-eova/eova-core/`
