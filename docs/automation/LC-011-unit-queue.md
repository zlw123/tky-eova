# LC-011 单元队列（仅 taskId=LC-011 时使用）

> **不是**全局任务队列。Orchestrator 先读 `unit-queue-index.md` 确认 taskId=LC-011 再打开本文。
>
> 表内 `targetPaths` 相对路径根为 `remis-eova/backend/yudao-cloud/yudao-module-eova/`。

---

## 已合入 dev（禁止重 port）

| 单元 | targetPath 片段 | 备注 |
|------|-----------------|------|
| EovaExp | `engine/EovaExp.java` | 已验证 |
| SqlParse | `engine/SqlParse.java` | 已验证 |
| EovaExpParam | `engine/EovaExpParam.java` | 已验证 |
| SqlCondition | `engine/SqlCondition.java` | 已验证 |
| 脚手架 | `backend/**/pom.xml` | 已存在，不属于业务 port |

## 已存在但仍是 compile-stub（不得计入 port 完成）

| 单元 | targetPath 片段 | 说明 |
|------|-----------------|------|
| MetaField / MetaObject / EovaOption | `model/*.java` | 当前只覆盖已迁移 engine 测试所需的最小 API |
| EovaConfig / SqlUtil / x | `config/`、`common/utils/db/`、`tools/` | 当前为最小支撑实现，不等价于旧源码 |

## compile-stub（不得标为已 port）

| 单元 | 说明 |
|------|------|
| TableSource | `sql/dql/TableSource.java` 仅为 compile-stub，待专门单元实 port |

---

## 待 port 顺序（engine 内核优先）

| 顺序 | unitId | unitName | unitClass | dependencies | acceptanceProfile | sourcePath | targetPaths |
|------|--------|----------|------------|--------------|------------------|------------|------------|
| 1 | LC-011-000 | EovaKvAdapter | S | `[]` | `java-core-adapter` | `null` | `eova-core/src/main/java/cn/eova/compat/EovaKv.java` |
| 2 | LC-011-000A | EovaTemplateAdapter | S | `[LC-011-000]` | `java-core-adapter` | `null` | `eova-core/src/main/java/cn/eova/compat/EovaTemplate.java` |
| 3 | LC-011-000B | EovaLegacyUtilityAdapter | S | `[]` | `java-core-adapter` | `null` | `eova-core/src/main/java/cn/eova/compat/EovaLegacySupport.java` |
| 4 | LC-011-001 | EovaExpConfig | B | `[LC-011-000]` | `java-core-adapter` | `meta-eova/eova/core/src/main/java/cn/eova/engine/EovaExpConfig.java` | `eova-core/src/main/java/cn/eova/engine/EovaExpConfig.java` |
| 5 | LC-011-002 | ExpUtil | B | `[LC-011-000, LC-011-000A, LC-011-000B, LC-011-001]` | `java-core-adapter` | `meta-eova/eova/core/src/main/java/cn/eova/engine/ExpUtil.java` | `eova-core/src/main/java/cn/eova/engine/ExpUtil.java` |
| 6 | LC-011-003 | EovaExpBuilder | C | `[LC-011-000, LC-011-000B, LC-011-002, DES-DB-ADAPTER]` | `java-core-db-adapter` | `meta-eova/eova/core/src/main/java/cn/eova/engine/EovaExpBuilder.java` | `eova-core/src/main/java/cn/eova/engine/EovaExpBuilder.java` |
| 7 | LC-011-004 | TableSource（实 port） | A | `[]` | `java-core` | `meta-eova/eova/core/src/main/java/cn/eova/sql/dql/TableSource.java` | `eova-core/src/main/java/cn/eova/sql/dql/TableSource.java` |

**EovaExpBuilder** 依赖 ExpUtil + Db 适配：若 ExpUtil 未 verified，**不得**派 EovaExpBuilder。

---

## targetPath 模板

```
remis-eova/backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java/cn/eova/engine/<UnitName>.java
```

（TableSource 将 `engine` 换为 `sql/dql`。）

---

## Orchestrator 派单算法

1. 读取 dev 上各 `targetPaths` 的当前状态，以及每个单元的依赖状态。
2. 从上表按顺序取**第一个依赖全部 verified 且尚未 verified 的单元**：A/B/C/D/E 单元必须 sourcePath 存在且 source revision/hash 可复核；S 类 support 单元 sourcePath 为 `null`，必须存在对应 DES 设计、适配方法契约和明确 targetPaths。
3. target 已存在但 targetBeforeSha256 不匹配时不得覆盖，标记 blocked。
4. 若全部队列单元已 verified，且没有遗漏的真实源文件，LC-011 才可标 Done；扩展队列需先完成 DES-002-R2。
