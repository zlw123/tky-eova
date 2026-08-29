# LC-011 单元队列（仅 taskId=LC-011 时使用）

> **不是**全局任务队列。Orchestrator 先读 `unit-queue-index.md` 确认 taskId=LC-011 再打开本文。

---

## 已合入 dev（禁止重 port）

| 单元 | targetPath 片段 | 备注 |
|------|-----------------|------|
| EovaExp | `engine/EovaExp.java` | 已验证 |
| SqlParse | `engine/SqlParse.java` | 已验证 |
| EovaExpParam | `engine/EovaExpParam.java` | 已验证 |
| SqlCondition | `engine/SqlCondition.java` | 已验证 |
| 脚手架 | `backend/**/pom.xml` | 已存在 |
| MetaField / MetaObject / EovaOption | `model/*.java` | Worker 依赖，已存在 |
| EovaConfig / SqlUtil / x | 支撑类 | 已存在 |

## compile-stub（不得标为已 port）

| 单元 | 说明 |
|------|------|
| TableSource | `sql/dql/TableSource.java` 仅为 compile-stub，待专门单元实 port |

---

## 待 port 顺序（engine 内核优先）

| 顺序 | unitName | sourcePath |
|------|----------|------------|
| 1 | EovaExpConfig | `meta-eova/eova/core/src/main/java/cn/eova/engine/EovaExpConfig.java` |
| 2 | ExpUtil | `meta-eova/eova/core/src/main/java/cn/eova/engine/ExpUtil.java` |
| 3 | EovaExpBuilder | `meta-eova/eova/core/src/main/java/cn/eova/engine/EovaExpBuilder.java` |
| 4 | TableSource（实 port） | `meta-eova/eova/core/src/main/java/cn/eova/sql/dql/TableSource.java` |

**EovaExpBuilder** 依赖 ExpUtil + Db 适配：若 ExpUtil 未 verified，**不得**派 EovaExpBuilder。

---

## targetPath 模板

```
remis-eova/backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java/cn/eova/engine/<UnitName>.java
```

（TableSource 将 `engine` 换为 `sql/dql`。）

---

## Orchestrator 派单算法

1. 读 dev 上 `remis-eova/.../engine/` 已有文件。
2. 从上表「待 port」取**第一个**源文件存在且 target 不存在的单元。
3. 若全部 engine 单元已 verified → LC-011 可标 Done，或扩展本表（需 DES-002-R2）。
