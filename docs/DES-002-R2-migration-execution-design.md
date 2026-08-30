# DES-002-R2 代码级迁移执行设计

> 状态：Design-only
> 版本：2026-08-30
> 目的：冻结代码级迁移的单元边界、适配契约、验证证据和 Automation 状态协议，消除 Worker/Verifier 运行时的歧义。
> 适用范围：`meta-eova/eova` 到 `remis-eova` 的 Java 与前端逐文件迁移。

## 1. 设计结论

本项目采用“旧源码为基线、逐单元迁移、最小底座适配、行为证据闭环”的代码级迁移方式。

代码级迁移不等于重新设计、按功能重写、只补编译桩或只搭新框架。每个迁移单元必须能回答：

1. 从哪个旧文件、哪个旧 FQCN 迁移而来。
2. 新文件与旧文件哪些逻辑保持不变，哪些地方因底座差异而适配。
3. 对外 URL、参数、JSON、事件和页面行为是否保持兼容。
4. 用什么测试或 golden 证据证明迁移不是空壳。

## 2. 迁移单元协议

### 2.1 单元的最小边界

默认一个 Java 类、一个前端业务 JS 模块或一个明确的脚手架/契约层目录为一个单元。必要的共用底座适配可以作为 `S`（support）单元，但必须有独立 targetPaths、接口测试和依赖关系；它不是业务 port 完成的替代品。只有以下情况允许 1:N：

- 旧文件本身是框架启动胶水，必须拆成配置、注册和运行时适配多个文件。
- 旧文件超过一个清晰职责，但拆分不会改变业务规则。
- 前端单文件同时包含页面布局和可复用逻辑，分别落到 view 与 composable。

发生 1:N 时，必须在单元清单中记录 `parentSourcePath`、所有 `targetPath`、拆分原因和逻辑覆盖关系；不得隐式拆分。

### 2.2 Worker 清单必填字段

```json
{
  "taskId": "LC-011",
  "unitId": "LC-011-001",
  "unitType": "java",
  "unitName": "EovaExpConfig",
  "sourcePath": "meta-eova/eova/core/src/main/java/cn/eova/engine/EovaExpConfig.java",
  "targetPaths": [
    "remis-eova/backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java/cn/eova/engine/EovaExpConfig.java"
  ],
  "sourceFqcn": "cn.eova.engine.EovaExpConfig",
  "sourceRevision": "<git commit or submodule revision>",
  "sourceSha256": "<source file sha256>",
  "targetBeforeSha256": "<null or target file sha256 before run>",
  "unitClass": "A|B|C|D|E|F-A|F-B|...",
  "dependencies": [],
  "allowedAdaptations": ["package", "import", "slf4j"],
  "acceptanceProfile": "java-core",
  "workerStatus": "ready"
}
```

`sourceRevision` 和 `sourceSha256` 必须可复现；源文件变化后不得继续使用旧清单。`targetPaths` 即使只有一个文件也使用数组，以覆盖 1:N 拆分。Orchestrator 必须在派单前计算并写入这些值，Worker/Verifier 必须复核；`targetBeforeSha256` 用于防止覆盖其他 run 的改动。

计算方式固定为：

```bash
git -C meta-eova/eova rev-parse HEAD
shasum -a 256 meta-eova/eova/<source-file>
shasum -a 256 remis-eova/<target-file>  # 目标不存在时写 null
```

若源文件本身有未提交修改或无法确认归属 revision，必须停止；submodule 中与当前 sourcePath 无关的用户改动可以保留，但要在 handoff 中记录。

### 2.3 追溯要求

Java 文件必须包含：

```java
// ported from: <sourcePath>
// source FQCN: <sourceFqcn>
// source revision: <sourceRevision>
```

前端文件使用等价的 TypeScript 注释。所有迁移后保留的方法签名和新增公共函数必须有简短中文注释（包括从旧文件原样 port 的方法）。适配代码必须用注释说明“旧调用 → 新适配”的原因，不能只留下无法解释的重写结果。

## 3. 单元分级与顺序

### 3.0 acceptanceProfile 定义

| acceptanceProfile | 最低验证集合 |
|---|---|
| `java-core` | 目标模块 compile/test、结构对照、来源追溯 |
| `java-core-adapter` | 上述内容 + Kv/模板引擎/日志等最小适配单测 |
| `java-core-db-adapter` | 上述内容 + EovaDbGateway、事务、分页和双数据源测试 |
| `frontend-build` | `pnpm install`、`pnpm build`、契约静态检查 |
| `frontend-contract` | 上述内容 + URL/请求响应 envelope 对照 |
| `frontend-e2e` | 上述内容 + Playwright 操作、HAR 和关键 DOM 断言 |

队列中的 `acceptanceProfile` 是最低要求，Worker 不得自行降级；缺少依赖环境时标记 `blocked` 或 `not executed`。

### 3.1 后端

| 级别 | 范围 | 允许动作 | 前置设计 |
|---|---|---|---|
| S | 迁移所需的共用底座适配 | 只实现已冻结的最小适配契约 | DES-ADAPTER-R2 / DES-DB-ADAPTER |
| A | 无 JFinal 依赖的纯类 | 逐文件复制、改包和 import | R1 |
| B | `Kv`/模板引擎/`LogKit` 等非数据库底座依赖 | 复制逻辑，调用最小适配层 | DES-ADAPTER-R2 |
| C | `Db`/`Record`/Model 或 Service/Widget 业务类 | 复制逻辑，调用 EovaDbGateway | DES-DB-ADAPTER、API-R2 |
| D | Config/Render/Interceptor 胶水 | 等价重写为 Spring 配置或过滤器 | 独立 DES 任务 |
| E | ClassLoader、插件和暂不兼容能力 | 暂缓，不得用 stub 冒充完成 | 评估任务 |

顺序固定为：契约基线 → eova-core → DB 适配层 → Service/Widget → Controller 薄壳 → 前端 → 部署接入。任何依赖未 `verified` 的单元不得派发。

### 3.2 前端

前端按 `eova-urls`/`eova-http` → legacy 工具与事件 → EovaLayer/EovaTable/EovaForm → table/tree/form 模板 → layout/login → meta/menu/role → demo → Playwright golden 执行。旧 JS 中的函数、事件名、参数字段和副作用必须在对照表中逐项标记。

## 4. 底座适配契约

### 4.1 EovaDbGateway

`EovaDbGateway` 是 B/C 类迁移的唯一数据库访问边界。第一版只覆盖旧代码实际使用的最小集合：

- `find`、`findFirst`、`findCount`
- `save`、`update`、`delete`
- 命名数据源选择
- 参数绑定、事务边界和空结果语义
- `Record` 的列名访问、类型转换和 null 行为

适配层必须定义 SQL 参数顺序、分页字段、事务传播、异常类型和数据源选择规则。禁止在业务迁移单元中直接把每条 SQL 改写成新的 Mapper 业务实现；若网关能力不足，先创建 `DES-DB-ADAPTER`，不能在 Worker 中扩大范围。

### 4.2 其他基础设施

| 旧能力 | 迁移期边界 |
|---|---|
| `LogKit` | 只替换为 slf4j，不改日志级别和关键文本语义 |
| `Kv` | 使用最小等价 Map/值对象，保留缺省值行为 |
| JFinal Config | 归入 D 类，单独设计，不混入普通业务类 |
| JFinal Render | 保留响应状态、Content-Type、文件名和流式语义 |
| Interceptor | 先冻结执行顺序、短路条件和异常映射，再做 Spring 等价实现 |
| Model/Record | 先通过网关保持 ActiveRecord 查询语义，不直接改成全新 DO 语义 |

## 5. API 与前端契约冻结

### 5.1 API 基线字段

每个 API 条目必须记录：HTTP method、旧 URL、请求头、路径/查询/正文参数、响应 envelope、分页字段、错误状态、权限要求、示例 JSON、数据来源和兼容备注。

迁移期保持：

```json
{
  "state": "ok",
  "msg": "",
  "data": {}
}
```

如底层返回 Yudao `CommonResult`，只能在 `eova-http` 适配层转换，不得让每个业务页面各自判断两种 envelope。URL、字段名、空值、时间格式、分页起始值和错误文本均视为契约；改变它们必须先建 DES 任务并补 breaking 记录。

### 5.2 页面与组件契约

`uzoo.page` 字段、`window.urls` 路径、`me.layer`、`me.cross`、`me.table`、`me.form` 别名、组件 props/事件和表格行结构必须在 `docs/eova-ui-component-map.md` 中逐项映射。Element Plus 只作为渲染壳，不能改变旧逻辑的事件顺序、请求参数或保存行为。

## 6. Golden 基线与证据

### 6.1 基线组成

P0 必须先从旧 demo 固定：

- 旧系统 revision、启动参数和数据库快照标识；
- 至少 50 条关键 API 请求/响应；
- table/tree/form 各一条完整操作旅程；
- 关键请求的 HAR、脱敏响应 JSON 和输入数据；
- 字段类型、分页、权限和错误场景样例。

敏感值必须脱敏，不能把 token、密码、生产数据写入仓库或 Automation 输出。

### 6.2 Diff 规则

JSON 比较默认做键顺序无关、动态时间/ID 脱敏和明确的允许差异清单；不能整体 `ignore`。URL、HTTP method、请求参数、响应 `state/msg/data` 结构和业务字段必须严格比较。前端额外比较关键 DOM 语义、网络 HAR 和交互结果；截图只作为辅助证据，不能替代网络和数据对照。

### 6.3 证据目录

建议本地保存：

```text
docs/golden/<case-id>/
  manifest.json
  old-request.json
  old-response.json
  new-response.json
  diff.json
  run.log
  screenshot.png
```

治理文档和本地 golden 证据按 `AGENTS.md` 规则不提交；提交到 dev 的只应是迁移业务代码和明确需要的构建文件。

## 7. 验证门禁

每个单元按以下顺序验证：

1. 来源存在且 revision 一致。
2. 目标文件与清单一致，含追溯注释。
3. 静态结构对照通过，未删除业务分支。
4. 模块 compile/test 或前端 build 通过。
5. 适用时执行 golden API diff。
6. 适用时执行 Playwright 交互和 HAR 验证。
7. 记录命令、版本、commit、结果和未覆盖项。

`BUILD SUCCESS`、单元测试通过或 APK/静态资源存在，只能证明对应层级通过，不能自动标记整体 Done。没有 golden 时必须写 `golden: skipped`；没有浏览器、数据库或真实运行环境时必须明确 `not executed`。

## 8. Automation 状态协议

状态只允许沿以下方向变化：

```text
无清单/verified → ready → ported_awaiting_verifier → verified
                                      └──────────────→ blocked
```

- `ready`：只有 Orchestrator 能写入，Worker 才能执行。
- `ported_awaiting_verifier`：Worker 代码已 push 到 dev，等待 Verifier。
- `verified`：Verifier 证据完整，Orchestrator 才能派下一单元。
- `blocked`：任何失败、边界不清或环境缺失；只允许人工解除。

每次运行必须幂等：使用 `unitId + sourceRevision + sourceSha256` 判断是否重复；同一状态重复触发不得重复 append handoff、重复 commit 或重复 push。为避免两个 run 同时读到同一状态，写入前必须重新读取 session-current，并使用 `runId`、`leaseUntil` 和 `updatedAt` 做条件检查。`runId` 使用 `<automation>-<UTC timestamp>-<random suffix>`，`leaseUntil` 使用 ISO-8601 UTC 时间，租约最长 30 分钟；过期只能由新 run 重新读取后接管，不能覆盖仍在有效租约内的 run。发现状态、runId、revision 或目标 hash 已变化时停止并标记 blocked，不自行猜测修复。

三条 Automation 必须串行。Worker 不得由自身 push、PR 或高频 cron 触发；Verifier 允许使用 GitHub `New push to branch=dev` 做即时触发，但必须保留状态/hash/runId/lease 门禁和每日 Schedule 兜底。初期只允许 Manual Run；R3 放行且 Git 控制面、manifest 和 baseline 门禁通过后，才允许启用：Orchestrator 工作日 09:00、Worker 10:00、Verifier push 事件 + 14:00（Asia/Shanghai）Schedule。所有触发器只唤醒 run，状态不匹配时必须 no-op。

## 9. Git 与工作区规则

- 代码分支固定为 `dev`，Worker 直接 push GitHub 主 remote 的 `dev`；不得假设 remote 名称为 `origin`，必须按 URL `https://github.com/zlw123/tky-eova.git` 识别主 remote。`origin` 当前仅为内网 GitLab 备份。
- 禁止 `cursor/*` 分支、Draft PR、自动 merge 和 `git reset --hard`。
- Worker commit 只包含当前单元业务文件和必要构建文件。
- `meta-eova/` 只读，禁止提交 submodule 内容变化。
- `docs/session-current.md`、`docs/session-handoff.md`、`docs/ai-task-board.md` 和 golden 证据只本地维护，不进入 commit。
- 发现无关用户改动时保留并从当前 commit 排除。

## 10. 失败、重试与人工解除

### Worker 失败

当前单元范围内最多修复两轮。仍失败则 `blocked`，记录命令、日志摘要、根因分类和推荐动作；不得提交“为了编译的空实现”。

### Verifier 失败

Verifier 不改业务代码，只写 `blocked` 证据。修复必须由人工确认后重新派发同一 `unitId` 或创建明确的新修复单元；不得跳过失败单元派下一个。

### 环境失败

区分代码失败、依赖缺失、数据库不可用、凭据/权限缺失和工具不可用。环境证据缺失时只能标 `blocked` 或 `not executed`，不能标 `verified`。

## 11. 任务完成定义

### 单元 Done

代码已在 `dev`，源码映射、结构检查、适用测试和 golden/明确 skipped 记录齐全，且 Verifier 已将 workerStatus 标为 `verified`。

### 任务 Done

任务队列中的所有单元均 verified；任务级验收通过；没有未解释的 skipped、blocked 或 breaking contract；任务板、session-current、session-handoff 已同步。

### 阶段 Done

阶段内任务全部 Done，并完成阶段级 API、数据、部署或 UI 验收。阶段 Done 不得仅由代码数量或构建绿推导。

## 12. 仍需单独产出的设计任务

在进入对应实现前，必须补齐以下设计产物：

| 任务 | 产物 | 阻塞范围 |
|---|---|---|
| DES-002-R2 | 267 Java 文件对照表、依赖图、A-E 分级 | 所有后端大规模 port |
| DES-ADAPTER-R2 | Kv、模板引擎、日志和非数据库旧底座最小适配 | B 类后端 port |
| DES-002-R2-F | 132 个 JS/Vue/HTML 资产分类对照表、组件映射表 | FE-003 之后的前端 port |
| DES-API-R2 | API 路径/JSON/HAR 基线 | Controller、FE-002、golden |
| DES-DB-ADAPTER | EovaDbGateway 方法、事务、方言、Record 语义 | B/C 类后端 port |
| DES-ENV-R2 | 测试数据库、旧 demo、凭据和脱敏方案 | live/golden 验收 |
| DES-BOUNDARY-R2 | D 类 Config/Render/Interceptor 的等价替换边界 | D 类实现 |

在上述设计缺失时，Orchestrator 只能继续已明确的 A 类内核单元，不能把不确定性转嫁给 Worker。
