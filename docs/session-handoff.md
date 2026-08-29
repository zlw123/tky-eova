# Session Handoff

## 2026-08-29T16:02Z - Verifier 验证 LC-011 单元 SqlCondition（通过）

- **时间**：2026-08-29T16:02:08Z（cron `*/35` Verifier，`bc-519612fb`）
- **对象**：Worker PR `#7` https://github.com/zlw123/tky-eova/pull/7
- **结论**：单元验证通过；`LC-011` 保持 In Progress；Worker 清单已清空
- **Java**：`mvn -pl yudao-module-eova/eova-core -am compile -DskipTests` → BUILD SUCCESS
- **ported from**：通过；结构 54 vs 源 55 行；构造器 3 + getter/setter 1:1；无 JFinal `Db`/`Record`
- **前端 pnpm build**：skipped（无 `remis-eova/fornt/eova-ui`）
- **golden: skipped**（无 `docs/golden/`、无 DES-002-R2 baseline）
- **未 merge**；Automation Tools 无法评论 Worker PR，摘要写在 Verifier docs PR `#8` https://github.com/zlw123/tky-eova/pull/8
- **下一步**：Orchestrator 派下一内核单元；并行 Worker `bc-5e0fea14` 禁止重 port `SqlCondition`

---

## 2026-08-29T16:00Z - Orchestrator 复核 LC-011 单元 SqlCondition（未新认领）

- **时间**：2026-08-29T16:00:00Z（cron `*/7`）
- **动作**：已有 In Progress=`LC-011`，**不新认领**；`SqlCondition` 清单已存在且源文件仍在，原样保留；Worker 已提交 PR `#7`，Verifier 未通过 `SqlCondition`（仍无新 Verifier PR），不派再下一单元
- **单元路径**：`meta-eova/eova/core/src/main/java/cn/eova/engine/SqlCondition.java` → `remis-eova/backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java/cn/eova/engine/SqlCondition.java`
- **traceability**：`cn.eova.engine.SqlCondition`
- **基线**：draft PR `#7`（`cursor/eova-porting-dc30`）已代码级新建 `SqlCondition`（A 直迁 POJO，非 stub）；`TableSource` 仍为 compile-stub
- **未派**：`EovaExpBuilder`（依赖 `ExpUtil`/`Db`，单单元会堆 stub）
- **Worker**：`bc-cc198c65` IDLE，draft PR `#7` `port(LC-011): SqlCondition`；本 tick 并行 Worker `bc-5e0fea14` RUNNING（`cursor/eova-porting-ed46`），禁止重 port
- **Verifier**：仍仅确认 `EovaExpParam`（PR `#6`）；本 tick 并行 Verifier `bc-519612fb` RUNNING（`cursor/eova-72d0`），结果未回；`EovaExp`/`SqlParse`/`EovaExpParam` 禁止重 port；`SqlCondition` 通过前禁止重 port
- **本 checkout / `dev`**：`remis-eova/` 仍空（仅 `.gitkeep`）；产物在 PR `#7` 分支
- **未认领**：`FE-001` 仍 Ready；`AUTO-003` Ready 但不在试点白名单
- **下一步**：等本 tick Verifier 核验 `SqlCondition`（PR `#7`）；Orchestrator 禁止开 PR、禁止写业务代码

---

## 2026-08-29T15:56Z - Orchestrator 复核 LC-011 单元 SqlCondition（未新认领）

- **时间**：2026-08-29T15:56:00Z（cron `*/7`）
- **动作**：已有 In Progress=`LC-011`，**不新认领**；`SqlCondition` 清单已存在且源文件仍在，原样保留；Worker 已提交 PR `#7`，Verifier 未通过 `SqlCondition`，不派再下一单元
- **单元路径**：`meta-eova/eova/core/src/main/java/cn/eova/engine/SqlCondition.java` → `remis-eova/backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java/cn/eova/engine/SqlCondition.java`
- **traceability**：`cn.eova.engine.SqlCondition`
- **基线**：draft PR `#7`（`cursor/eova-porting-dc30`）已代码级新建 `SqlCondition`（A 直迁 POJO，非 stub）；`TableSource` 仍为 compile-stub
- **未派**：`EovaExpBuilder`（依赖 `ExpUtil`/`Db`，单单元会堆 stub）
- **Worker**：`bc-cc198c65` IDLE，draft PR `#7` `port(LC-011): SqlCondition`；上一 Worker `bc-ff58e26f` IDLE，draft PR `#5`；15:49Z 之后无新 Worker
- **Verifier**：仍仅确认 `EovaExpParam`（PR `#6`）；15:49Z 之后无新 Verifier；`EovaExp`/`SqlParse`/`EovaExpParam` 禁止重 port；`SqlCondition` 通过前禁止重 port
- **本 checkout / `dev`**：`remis-eova/` 仍空（仅 `.gitkeep`）；产物在 PR `#7` 分支
- **未认领**：`FE-001` 仍 Ready；`AUTO-003` Ready 但不在试点白名单
- **下一步**：Verifier 核验 `SqlCondition`（PR `#7`）；Orchestrator 禁止开 PR、禁止写业务代码

---

## 2026-08-29T15:49Z - Orchestrator 复核 LC-011 单元 SqlCondition（未新认领）

- **时间**：2026-08-29T15:49:00Z（cron `*/7`）
- **动作**：已有 In Progress=`LC-011`，**不新认领**；`SqlCondition` 清单已存在且源文件仍在，原样保留；Worker 已提交 PR `#7`，Verifier 未通过 `SqlCondition`，不派再下一单元
- **单元路径**：`meta-eova/eova/core/src/main/java/cn/eova/engine/SqlCondition.java` → `remis-eova/backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java/cn/eova/engine/SqlCondition.java`
- **traceability**：`cn.eova.engine.SqlCondition`
- **基线**：draft PR `#7`（`cursor/eova-porting-dc30`）已代码级新建 `SqlCondition`（A 直迁 POJO，非 stub）；`TableSource` 仍为 compile-stub
- **未派**：`EovaExpBuilder`（依赖 `ExpUtil`/`Db`，单单元会堆 stub）
- **Worker**：`bc-cc198c65` IDLE，draft PR `#7` `port(LC-011): SqlCondition`；上一 Worker `bc-ff58e26f` IDLE，draft PR `#5`
- **Verifier**：仍仅确认 `EovaExpParam`（PR `#6`）；15:42Z 之后无新 Verifier；`EovaExp`/`SqlParse`/`EovaExpParam` 禁止重 port；`SqlCondition` 通过前禁止重 port
- **本 checkout / `dev`**：`remis-eova/` 仍空（仅 `.gitkeep`）；产物在 PR `#7` 分支
- **未认领**：`FE-001` 仍 Ready；`AUTO-003` Ready 但不在试点白名单
- **下一步**：Verifier 核验 `SqlCondition`（PR `#7`）；Orchestrator 禁止开 PR、禁止写业务代码

---

## 2026-08-29T15:42Z - Orchestrator 补派 LC-011 单元 SqlCondition（未新认领）

- **时间**：2026-08-29T15:42:00Z（cron `*/7`）
- **动作**：已有 In Progress=`LC-011`，**不新认领**；Verifier PR `#6` 确认 `EovaExpParam` 通过且清单已清空，本轮补写下一单元 `SqlCondition`
- **单元路径**：`meta-eova/eova/core/src/main/java/cn/eova/engine/SqlCondition.java` → `remis-eova/backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java/cn/eova/engine/SqlCondition.java`
- **traceability**：`cn.eova.engine.SqlCondition`
- **基线**：draft PR `#5`（`cursor/eova-porting-e293`）尚无 `SqlCondition`；`TableSource` 仍为 compile-stub
- **未派**：`EovaExpBuilder`（依赖 `ExpUtil`/`Db`，单单元会堆 stub）；Verifier 备选中优先 A 直迁
- **Worker**：`bc-cb9f1833` IDLE 跳过无 PR；上一 Worker `bc-ff58e26f` IDLE，draft PR `#5` `port(LC-011): EovaExpParam`
- **Verifier**：`bc-2082b094` IDLE，draft PR `#6` 通过 `EovaExpParam`；`EovaExp`/`SqlParse`/`EovaExpParam` 禁止重 port
- **本 checkout / `dev`**：`remis-eova/` 仍空（仅 `.gitkeep`）；产物在 PR `#5` 分支
- **未认领**：`FE-001` 仍 Ready；`AUTO-003` Ready 但不在试点白名单
- **下一步**：Worker 单文件 port `SqlCondition`；Orchestrator 禁止开 PR、禁止写业务代码

---

## 2026-08-29T15:37Z - Verifier 验证 LC-011 单元 EovaExpParam（通过）

- **时间**：2026-08-29T15:37:20Z（cron `*/35` Verifier，`bc-2082b094`）
- **对象**：Worker PR `#5` https://github.com/zlw123/tky-eova/pull/5
- **结论**：单元验证通过；`LC-011` 保持 In Progress；Worker 清单已清空
- **Java**：`mvn -pl yudao-module-eova/eova-core -am compile -DskipTests` → BUILD SUCCESS
- **ported from**：通过；结构 49 vs 源 46 行；方法 1:1；无 JFinal `Db`/`Record`；PR `#3` stub 已替换
- **前端 pnpm build**：skipped（无 `remis-eova/fornt/eova-ui`）
- **golden: skipped**（无 `docs/golden/`、无 DES-002-R2 baseline）
- **未 merge**；Automation Tools 无法评论 Worker PR，摘要写在 Verifier docs PR `#6` https://github.com/zlw123/tky-eova/pull/6
- **下一步**：Orchestrator 派下一内核单元（本轮已派 `SqlCondition`）

---

## 2026-08-29T15:35Z - Orchestrator 复核 LC-011 单元 EovaExpParam（未新认领）

- **时间**：2026-08-29T15:35:00Z（cron `*/7`）
- **动作**：已有 In Progress=`LC-011`，**不新认领**；`EovaExpParam` 清单已存在且源文件仍在，原样保留；Verifier 未通过 `EovaExpParam`，不派再下一单元
- **单元路径**：`meta-eova/eova/core/src/main/java/cn/eova/engine/EovaExpParam.java` → `remis-eova/backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java/cn/eova/engine/EovaExpParam.java`
- **traceability**：`cn.eova.engine.EovaExpParam`
- **基线**：draft PR `#5`（`cursor/eova-porting-e293`）已代码级 port `EovaExpParam`（非 stub）；`TableSource` 仍为 compile-stub
- **Worker**：15:30Z `bc-cb9f1833` IDLE，跳过重 port、无新 PR（分支 `cursor/eova-porting-282b`）；上一 Worker `bc-ff58e26f` IDLE，draft PR `#5` `port(LC-011): EovaExpParam`
- **Verifier**：仍仅确认 `SqlParse`（PR `#4`）；本 tick 并行 Verifier `bc-2082b094` RUNNING，结果未回；`EovaExp`/`SqlParse`/`EovaExpParam` 禁止重 port
- **本 checkout / `dev`**：`remis-eova/` 仍空（仅 `.gitkeep`）；产物在 PR `#5` 分支
- **未认领**：`FE-001` 仍 Ready；`AUTO-003` Ready 但不在试点白名单
- **下一步**：等本 tick Verifier 核验 `EovaExpParam`（PR `#5`）；Orchestrator 禁止开 PR、禁止写业务代码

---

## 2026-08-29T15:28Z - Orchestrator 复核 LC-011 单元 EovaExpParam（未新认领）

- **时间**：2026-08-29T15:28:00Z（cron `*/7`）
- **动作**：已有 In Progress=`LC-011`，**不新认领**；`EovaExpParam` 清单已存在且源文件仍在，原样保留；Worker 已提交 PR `#5`，Verifier 未通过 `EovaExpParam`，不派再下一单元
- **单元路径**：`meta-eova/eova/core/src/main/java/cn/eova/engine/EovaExpParam.java` → `remis-eova/backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java/cn/eova/engine/EovaExpParam.java`
- **traceability**：`cn.eova.engine.EovaExpParam`
- **基线**：draft PR `#5`（`cursor/eova-porting-e293`）已代码级 port `EovaExpParam`（非 stub）；`TableSource` 仍为 compile-stub
- **Worker**：`bc-ff58e26f` IDLE，draft PR `#5` `port(LC-011): EovaExpParam`；上一 Worker `bc-8bc8dca0` IDLE，draft PR `#3`
- **Verifier**：仍仅确认 `SqlParse`（PR `#4`）；15:21Z 之后无新 Verifier；`EovaExp`/`SqlParse` 已验证禁止重 port
- **本 checkout / `dev`**：`remis-eova/` 仍空（仅 `.gitkeep`）；产物在 PR `#5` 分支
- **未认领**：`FE-001` 仍 Ready；`AUTO-003` Ready 但不在试点白名单
- **下一步**：Verifier 核验 `EovaExpParam`；Orchestrator 禁止开 PR、禁止写业务代码

---

## 2026-08-29T15:21Z - Orchestrator 复核 LC-011 单元 EovaExpParam（未新认领）

- **时间**：2026-08-29T15:21:00Z（cron `*/7`）
- **动作**：已有 In Progress=`LC-011`，**不新认领**；`EovaExpParam` 清单已存在且源文件仍在，原样保留；Worker 已提交 PR `#5`，Verifier 未通过 `EovaExpParam`，不派再下一单元
- **单元路径**：`meta-eova/eova/core/src/main/java/cn/eova/engine/EovaExpParam.java` → `remis-eova/backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java/cn/eova/engine/EovaExpParam.java`
- **traceability**：`cn.eova.engine.EovaExpParam`
- **基线**：draft PR `#5`（`cursor/eova-porting-e293`）已代码级 port `EovaExpParam`（非 stub）；`TableSource` 仍为 compile-stub
- **Worker**：`bc-ff58e26f` IDLE，draft PR `#5` `port(LC-011): EovaExpParam`；上一 Worker `bc-8bc8dca0` IDLE，draft PR `#3`
- **Verifier**：仍仅确认 `SqlParse`（PR `#4`）；15:14Z 之后无新 Verifier；`EovaExp`/`SqlParse` 已验证禁止重 port
- **本 checkout / `dev`**：`remis-eova/` 仍空（仅 `.gitkeep`）；产物在 PR `#5` 分支
- **未认领**：`FE-001` 仍 Ready；`AUTO-003` Ready 但不在试点白名单
- **下一步**：Verifier 核验 `EovaExpParam`；Orchestrator 禁止开 PR、禁止写业务代码

---

## 2026-08-29T15:14Z - Orchestrator 复核 LC-011 单元 EovaExpParam（未新认领）

- **时间**：2026-08-29T15:14:00Z（cron `*/7`）
- **动作**：已有 In Progress=`LC-011`，**不新认领**；`EovaExpParam` 清单已存在且源文件仍在，原样保留；Worker 尚未接单，Verifier 未通过 `EovaExpParam`，不派再下一单元
- **单元路径**：`meta-eova/eova/core/src/main/java/cn/eova/engine/EovaExpParam.java` → `remis-eova/backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java/cn/eova/engine/EovaExpParam.java`
- **traceability**：`cn.eova.engine.EovaExpParam`
- **基线**：draft PR `#3`（`cursor/eova-porting-abdb`）上 `EovaExpParam` 仍为 compile-stub，Worker 应替换而非当已迁移
- **Worker**：`bc-8bc8dca0` IDLE，draft PR `#3` `port(LC-011): SqlParse`；15:07Z 之后无新 Worker
- **Verifier**：`bc-4866cd2a` IDLE，draft PR `#4` 通过 `SqlParse`；`EovaExp`/`SqlParse` 已验证禁止重 port
- **本 checkout / `dev`**：`remis-eova/` 仍空（仅 `.gitkeep`）；产物在 PR `#3` 分支
- **未认领**：`FE-001` 仍 Ready；`AUTO-003` Ready 但不在试点白名单
- **下一步**：Worker 单文件 port `EovaExpParam`；Orchestrator 禁止开 PR、禁止写业务代码

---

## 2026-08-29T15:07Z - Orchestrator 补派 LC-011 单元 EovaExpParam（未新认领）

- **时间**：2026-08-29T15:07:00Z（cron `*/7`）
- **动作**：已有 In Progress=`LC-011`，**不新认领**；Verifier PR `#4` 确认 `SqlParse` 通过且清单已清空，本轮补写下一单元 `EovaExpParam`
- **单元路径**：`meta-eova/eova/core/src/main/java/cn/eova/engine/EovaExpParam.java` → `remis-eova/backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java/cn/eova/engine/EovaExpParam.java`
- **traceability**：`cn.eova.engine.EovaExpParam`
- **基线**：draft PR `#3`（`cursor/eova-porting-abdb`）已有 `EovaExpParam` compile-stub，Worker 应替换而非当已迁移
- **Worker**：`bc-8bc8dca0` IDLE，draft PR `#3` `port(LC-011): SqlParse`
- **Verifier**：`bc-4866cd2a` IDLE，draft PR `#4` 通过 `SqlParse`；`EovaExp`/`SqlParse` 已验证禁止重 port
- **本 checkout / `dev`**：`remis-eova/` 仍空（仅 `.gitkeep`）；产物在 PR `#3` 分支
- **未认领**：`FE-001` 仍 Ready；`AUTO-003` Ready 但不在试点白名单
- **下一步**：Worker 单文件 port `EovaExpParam`；Orchestrator 禁止开 PR、禁止写业务代码

---

## 2026-08-29T15:00Z - Orchestrator 复核 LC-011 单元 SqlParse（未新认领）

- **时间**：2026-08-29T15:00:00Z（cron `*/10`）
- **动作**：已有 In Progress=`LC-011`，**不新认领**；`SqlParse` 清单已存在且源文件仍在，原样保留；Verifier 尚未通过 `SqlParse`，不派再下一单元
- **单元路径**：`meta-eova/eova/core/src/main/java/cn/eova/engine/SqlParse.java` → `remis-eova/backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java/cn/eova/engine/SqlParse.java`
- **traceability**：`cn.eova.engine.SqlParse`
- **Worker**：上一轮 `bc-9acdc25d` 仍 IDLE 无 PR；本 tick 并行 Worker `bc-8bc8dca0`（`cursor/eova-porting-abdb`）RUNNING，尚无 PR
- **Verifier**：仍仅确认 `EovaExp`（PR `#2`）；本 tick 并行 Verifier `bc-4866cd2a` RUNNING；`SqlParse` 在 PR `#1` 仍为 compile-stub
- **本 checkout / `dev`**：`remis-eova/` 仍空（仅 `.gitkeep`）；产物在 PR `#1` 分支
- **未认领**：`FE-001` 仍 Ready；`AUTO-003` Ready 但不在试点白名单
- **下一步**：本 tick Worker 按清单替换 `SqlParse` stub；Orchestrator 禁止开 PR、禁止写业务代码

---

## 2026-08-29T14:50Z - Orchestrator 复核 LC-011 单元 SqlParse（未新认领）

- **时间**：2026-08-29T14:50:00Z（cron `*/10`）
- **动作**：已有 In Progress=`LC-011`，**不新认领**；`SqlParse` 清单已存在且源文件仍在，原样保留；不派再下一单元
- **单元路径**：`meta-eova/eova/core/src/main/java/cn/eova/engine/SqlParse.java` → `remis-eova/backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java/cn/eova/engine/SqlParse.java`
- **traceability**：`cn.eova.engine.SqlParse`
- **Worker**：`bc-9acdc25d` IDLE，无 PR、远程无 `cursor/eova-porting-d1ac`（判定读到旧 `EovaExp` 清单后空跑）
- **Verifier**：仍仅确认 `EovaExp`（PR `#2`）；`SqlParse` 未验证
- **本 checkout / `dev`**：`remis-eova/` 仍空（仅 `.gitkeep`）；产物在 PR `#1` 分支
- **未认领**：`FE-001` 仍 Ready；`AUTO-003` Ready 但不在试点白名单
- **下一步**：下一 Worker 按清单替换 `SqlParse` stub；Orchestrator 禁止开 PR、禁止写业务代码

---

## 2026-08-29T14:40Z - Orchestrator 补派 LC-011 单元 SqlParse（未新认领）

- **时间**：2026-08-29T14:40:00Z（cron `*/10`）
- **动作**：已有 In Progress=`LC-011`，**不新认领**；Verifier PR `#2` 确认 `EovaExp` 通过且清单已清空，本轮补写下一单元 `SqlParse`
- **单元路径**：`meta-eova/eova/core/src/main/java/cn/eova/engine/SqlParse.java` → `remis-eova/backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java/cn/eova/engine/SqlParse.java`
- **traceability**：`cn.eova.engine.SqlParse`
- **基线**：draft PR `#1`（`cursor/eova-porting-143b`）已有 `SqlParse` compile-stub，Worker 应替换而非当已迁移
- **并行**：Worker `bc-9acdc25d` 本 tick 并行启动，可能读到旧 `EovaExp` 清单；`EovaExp` 已验证禁止重 port
- **未认领**：`FE-001` 仍 Ready；`AUTO-003` Ready 但不在试点白名单
- **下一步**：Worker 单文件 port `SqlParse`；Orchestrator 禁止开 PR、禁止写业务代码

---

## 2026-08-29T14:30Z - Verifier 验证 LC-011 单元 EovaExp（通过）

- **时间**：2026-08-29T14:30:00Z（cron `*/30` Verifier，`bc-5c23de0f`）
- **对象**：Worker PR `#1` https://github.com/zlw123/tky-eova/pull/1
- **结论**：单元验证通过；`LC-011` 保持 In Progress；Worker 清单已清空（见 draft PR `#2`）
- **Java**：`mvn -pl yudao-module-eova/eova-core -am compile -DskipTests` → BUILD SUCCESS
- **golden: skipped**

---

## 2026-08-29T14:30Z - Orchestrator 复核 LC-011 单元 EovaExp（未新认领）

- **时间**：2026-08-29T14:30:00Z（cron `*/10`）
- **动作**：已有 In Progress=`LC-011`，**不新认领**；Worker JSON 清单已存在且源文件仍在，原样保留；不派下一单元
- **单元路径**：`meta-eova/eova/core/src/main/java/cn/eova/engine/EovaExp.java` → `remis-eova/backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java/cn/eova/engine/EovaExp.java`
- **Worker**：`bc-e0817351` IDLE；draft PR `#1` `port(LC-011): EovaExp`（`cursor/eova-porting-143b`）
- **Verifier**：`bc-5c23de0f` 本 tick 并行跑，结果未回
- **本 checkout / `dev`**：`remis-eova/` 仍空（仅 `.gitkeep`）；产物在 PR 分支
- **未认领**：`FE-001` 仍 Ready；`AUTO-003` Ready 但不在试点白名单
- **下一步**：等 Verifier 通过后再派 `SqlParse` 或改认领 FE-001；Orchestrator 禁止开 PR、禁止写业务代码

---

## 2026-08-29T14:20Z - Orchestrator 复核 LC-011 单元 EovaExp（未新认领）

- **时间**：2026-08-29T14:20:00Z（cron `*/10`）
- **动作**：已有 In Progress=`LC-011`，**不新认领**；Worker JSON 清单已存在且源文件仍在，原样保留
- **单元路径**：`meta-eova/eova/core/src/main/java/cn/eova/engine/EovaExp.java` → `remis-eova/backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java/cn/eova/engine/EovaExp.java`
- **本 checkout**：`remis-eova/` 仍空（仅 `.gitkeep`）；同源 Worker `bc-e0817351` 并行 porting
- **未认领**：`FE-001` 仍 Ready；`AUTO-003` Ready 但不在试点白名单
- **下一步**：等 Worker 落地 EovaExp；Orchestrator 禁止开 PR、禁止写业务代码

---

## 2026-08-29T14:11Z - Orchestrator 认领 LC-011 单元 EovaExp

- **时间**：2026-08-29T14:11:31Z（cron `*/20`）
- **认领任务**：`LC-011`（Ready → In Progress；此前 0 个 In Progress）
- **单元路径**：`meta-eova/eova/core/src/main/java/cn/eova/engine/EovaExp.java` → `remis-eova/backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java/cn/eova/engine/EovaExp.java`
- **traceability**：`cn.eova.engine.EovaExp`
- **未认领**：`FE-001` 仍 Ready；`AUTO-003` Ready 但不在试点白名单
- **下一步**：Worker 按 `session-current.md` JSON 单文件 port；Orchestrator 禁止开 PR

---

## 2026-08-29 - 推送到 GitHub tky-eova

### 本轮目标

将工作区推送到 `https://github.com/zlw123/tky-eova`，便于 Cursor Cloud Agent 绑库。

### 远程策略

- **github** → `zlw123/tky-eova`（Automation / Cloud Agent 主 remote）
- **origin** → 内网 GitLab remis-eova（备份）

---

### 本轮目标

将 eova 工作区推送到内网 GitLab remis-eova.git。

### 本轮确认的事实

1. 本地 macOS Keychain 已有 `zhouliwei@10.20.110.206:45001` 凭据，无需拿哥再输密码。
2. 仓库根 = `/Users/zhouliwei/eova`；`meta-eova/eova` 为 submodule（gitee/eova）。
3. Kingbase 清洗 SQL 归档至 `docs/sql/kingbase/`（submodule 内副本为本地未提交文件）。

### 下一步

1. 日常开发在 **dev** 分支（Automations gitConfig.branch = dev）
2. **AUTO-003**：Agents Window 导入 `prefill-workflows.json`
3. **AUTO-004**：Orchestrator 认领 LC-011 → Worker port EovaExp

---

## 2026-08-29 - AUTO-001 Cursor Automations 三层流水线

### 本轮目标

按推荐模式落地 Orchestrator + Worker + Verifier，供 Agents Window 创建 Automation。

### 本轮产出

1. `docs/automation/README.md` — 架构与前置条件
2. `orchestrator-instructions.md` / `worker-instructions.md` / `verifier-instructions.md` — 完整 prompt
3. `prefill-workflows.json` — 三条 Automation 编辑器预填草稿
4. 任务板新增 AUTO-001~004

### 阻塞 / 待办

1. **AUTO-002**：拿哥在 GitLab 建空库 `http://10.20.110.206:45001/remis/modules/remis-eova.git` 后，按 `docs/AUTO-002-git-bootstrap.md` init + submodule + push
2. **Agents Window**：导入 prefill，补全 GitHub repo / PR 范围
3. 首跑试点：Orchestrator 认领 LC-011 → Worker port EovaExp

---

## 2026-08-29 - DES-002-R1-F 前端代码级迁移方案

### 本轮目标

与后端 R1 同口径，输出前端逐文件 port 方案。

### 本轮确认的事实

1. 平台 view **113** + demo **25** 文件；自研业务 JS ~55 个、~3500 行。
2. 已是 Vue3 setup，但契约依赖 `window.urls`、`{state,msg,data}`、`uzoo.page`。
3. 已写 `docs/DES-002-R1-frontend-code-level-migration.md`；FE-001 Ready。

### 下一步建议

1. DES-002-R2 / R2-F 完整对照表。
2. FE-001 与 LC-011 并行。

---

## 2026-08-29 - DES-002-R1 代码级迁移路线修订

### 本轮目标

回应拿哥「原方案做不到代码级迁移」的质疑，修订方法论。

### 本轮确认的事实

1. 原 DES-002 任务清单偏 **按功能重写**，缺文件级追溯与 golden 对照。
2. meta-eova/core：**267** Java，**149** 个直接 import JFinal；WidgetManager 等必须用 **Db 适配层 + 逻辑移植**。
3. 已写 `docs/DES-002-R1-code-level-migration.md`；LC-001 后移，LC-011/012 前置。

### 下一步建议

1. 拿哥确认 R1 修订路线。
2. DES-002-R2：267 文件对照表 + golden API 清单。

---

## 2026-08-29 - DES-002 三项决策落定

### 本轮目标

确认落仓命名、身份策略、前端形态，更新迁移方案。

### 本轮确认的事实

1. **remis-eova 仓库** = 原方案所称新代码落点（不再称「eova 仓」）。
2. **身份**：先迁移 `eova_user/eova_role`；并入 platform System → **DES-003 后续**。
3. **前端**：先独立 `remis-eova/fornt/eova-ui`；嵌入 yudao-ui → **DES-004 后续**。
4. **LC-001** 已进入 Ready，待放行执行脚手架。

### 下一步建议

1. 拿哥确认「开干脚手架」。
2. LC-001 初始化 `remis-eova/backend/yudao-cloud/yudao-module-eova/`。

---

## 2026-08-29 - DES-002 落仓目录调整为 remis-eova

### 本轮目标

按拿哥要求，将迁移方案推荐目录改为前后端均放在 `remis-eova/` 下。

### 本轮确认的事实

1. 新工程根：`/Users/zhouliwei/eova/remis-eova/`（当前为空目录，待 LC-001 初始化）。
2. 后端：`remis-eova/backend/yudao-cloud/yudao-module-eova/`
3. 前端：`remis-eova/fornt/yudao-ui/`
4. `DES-002-01` 标记 Done；Phase 0 进度 50%，整体 ≈3%。
5. 仍待决：身份体系、前端独立站 vs 嵌入 yudao-ui。

### 下一步建议

1. 确认身份 + 前端形态。
2. LC-001 在 `remis-eova/backend/` 初始化 Maven 模块。

---

## 2026-08-29 - DES-002 meta-eova 技术栈迁移方案

### 本轮目标

输出与 platform 完全一致技术栈的代码级迁移方案、任务规划、详细清单与进度表。

### 本轮确认的事实

1. meta-eova：JFinal 5.2.6 + JDK8 + ActiveRecord + Enjoy/Vue3/EovaUI；~288 Java、~27 Controller、32+25 表。
2. platform：Spring Boot 3.4.5 + JDK17 + MyBatis-Plus + Vue3/TS/Element Plus + Gateway/Nacos/Kingbase/Redis。
3. 已写 `docs/DES-002-meta-eova-tech-stack-migration.md`，任务板 59 项，整体进度约 2%。
4. 阻塞：落仓位置、用户权限融合、前端形态三项决策。

### 下一步建议

1. 拿哥确认 DES-002 第 8 节 3 件事。
2. LC-001 创建 `yudao-module-eova` 脚手架。

---

## 2026-08-25 - DES-001 执行完成（建库+导入+VAL）

### 本轮目标

按 DES-001 在 54321 创建并导入 `eova_meta` / `demo`。

### 本轮确认的事实

1. 两库已创建，均为 `database_mode=mysql`。
2. `eova_meta` 公共表 32 张；抽样：`eova_user=21`（含补丁列 `status`）、`eova_menu=33`、`eova_button=211`、`eova_object=42`、`eova_role=9`。
3. `demo` 公共表 25 张；抽样：`users=34`、`area=3410`、`orders=13`、`address=4`、`data_10=2`。
4. 清洗产出：`meta-eova/eova/demo/sql/kingbase/`；执行记录：`docs/DES-001-exec-log.md`。

### 当前剩余问题

- 应用 JDBC / `dev.txt` 尚未切换到新库。
- Tabularis 未新增指向 `eova_meta`/`demo` 的连接（可选）。

### 下一步建议

1. 配置 Demo 数据源指向 54321 两库并启动验证登录。
2. 需要时在 Tabularis 增加两库连接便于日常查数。

---

## 2026-08-25 - 54321 system 新密码验证


### 本轮目标

用拿哥提供的 `system` 新密码验证 54321 连通。

### 本轮确认的事实

1. Node PG 直连 `base.platform:54321` / 库 `kingbase` / 用户 `system` **成功**。
2. `database_mode=mysql`；版本 KingbaseES V008R006C008B0020。
3. 已更新 `~/.cursor/mcp.json` 中 `kingbase-baseplatform` 连接串；当前 MCP 进程仍报旧密码失败，需重载。

### 当前剩余问题

- 等拿哥确认「按 DES-001 执行」后建 `eova_meta` / `demo`。

### 下一步建议

1. 重载 Cursor MCP 后复测 `kingbase-baseplatform`。
2. 放行后用已验证密码执行 DES-001 建库导入。

---

## 2026-08-25 - DES-001 建库评估（eova_meta / demo）

### 本轮目标

摸清 MySQL 脚本与 54321 现状，给出建库导入设计，不直接动库。

### 本轮确认的事实

1. 脚本：`eova_meta.sql`（32 表）、`demo.sql`（25 表），均为 MySQL8 Navicat 导出。
2. 54321 现有库无 `eova_meta`/`demo`；`system` 可建库；`database_mode=mysql`。
3. 已写 `docs/DES-001-kingbase-eova-dbs.md`：推荐双 DATABASE + 轻量清洗导入。

### 当前剩余问题

- 等待拿哥确认库名、放行写入、导入通道（Tabularis 新连接 / 密码 CLI）。

### 下一步建议

1. 拿哥确认 DES-001 后执行建库与导入。
2. 导入后做表数量与关键表抽样 VAL。

---

## 2026-08-25 - Kingbase 54321 连通性验证

### 本轮目标

确认能否链接 Kingbase 端口 `54321`。

### 本轮确认的事实

1. `base.platform` 解析到 `10.20.110.206`；`54321` / `34321` TCP 均通。
2. Tabularis `baseplatform-db` 查询成功：`kingbase` / `system` / 端口 `54321` / `KingbaseES V008R006C008B0020`。
3. MCP `user-kingbase-baseplatform` 查询失败：`password authentication failed for user "system"`。
4. MCP `user-kingbase-34321` `test_connection` 成功（端口 `34321`，schema `gd-biz-lz`）。

### 当前剩余问题

- `kingbase-baseplatform` MCP 凭据与 Tabularis 已成功凭据不同步。

### 下一步建议

1. 用 Tabularis `baseplatform-db` 继续查 54321。
2. 若需要 MCP 直连：更新 mcp.json 密码后重载，再跑一次 `SELECT version()`。
