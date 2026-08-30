# DES-002-R3 remis-eova 代码级迁移总体重设计

> 状态：Review approved / Design-only（评审通过；按切片准备局部 manifest 和 baseline；workspace persistence probe 已降级为可选诊断）
> 版本：2026-08-30
> reviewStatus：`approved`（拿哥确认：R3 评审通过）
> 适用仓库：`https://github.com/zlw123/tky-eova.git`
> 目标：把之前“按模块和文件持续堆叠”的迁移方案，重构为可暂停、可追溯、可验证的垂直切片迁移方案。

## 1. 重设计结论

本项目不再以“已经创建了多少目标文件”“Maven 是否 BUILD SUCCESS”作为迁移进度，而以**可运行垂直切片**作为推进单位：

```text
源文件基线
  -> 兼容适配
  -> 真实内核/业务逻辑
  -> 薄 API 壳
  -> 前端契约层
  -> 旧版/新版行为对照
  -> Verifier 证据闭环
```

只有当一条切片的传递依赖闭包没有未声明的 compile-stub，且适用的结构、测试、契约和运行证据齐全，才允许把该切片标记为 `verified`。

这份 R3 是总体执行设计；既有 `DES-002-R2-migration-execution-design.md`、`DES-API-R2.md`、`DES-ADAPTER-R2.md`、`DES-DB-ADAPTER.md` 和 `DES-ENV-R2.md` 保留为局部约束，但其派单顺序、完成定义和 Automation 门禁以 R3 为准。

## 2. 最近几轮的真实审计

### 2.1 Git 与代码状态

| 项 | 当前事实 |
|---|---|
| GitHub 主仓库 | `https://github.com/zlw123/tky-eova.git` |
| 主分支 | `dev` |
| 本地主 remote | `github` |
| 备份 remote | `origin`，内网 GitLab，不作为 Automation 发布目标 |
| 旧源码 | `meta-eova/eova`，submodule revision `1b1d39e7350f7e031b216aad0399fc8cc55dce08` |
| 新代码落点 | `remis-eova/` |
| 旧 Java 文件 | 267 |
| 旧前端资产 | 132（84 JS、46 HTML、2 Vue） |

最近 dev 提交主要是 Automation 治理和 PR 收敛。实际业务迁移已合入：

- `EovaExp`
- `SqlParse`
- `EovaExpParam`
- `SqlCondition`
- Maven 模块脚手架及相关测试

但这些类仍有 `JFinal`、旧工具、旧模型和 compile-stub 依赖，不能因为已经合入 `dev` 就宣称整条 engine 链等价完成。`TableSource` 仍是 compile-stub。

### 2.2 已确认的流程问题

1. 任务按 Controller、Service、表数量拆分，缺少 source-to-target 的真实依赖闭包。
2. Worker 可以通过补最小 stub 让 Maven 变绿，导致“编译成功”被误认为“代码级迁移完成”。
3. Golden API、旧 demo readiness 和数据库 Record 语义没有成为派单前置门禁。
4. Orchestrator 使用高频 cron，Worker/Verifier 并行，Draft PR 和 `cursor/*` 分支造成状态、代码和验证结果分叉。
5. 治理状态放在未提交本地文档，但没有验证三个 Automation 是否共享同一持久化工作区；若不共享，下一次 run 看不到上一 run 的 `workerStatus`。
6. 前端把第三方库、模板壳、错误页和业务 JS 混在“55 个业务 JS”估算中，不能据此派单。

## 3. 新的范围与边界

### 3.1 保持不变的业务语义

- 元数据对象、字段、模板、选项和动态 Widget 规则。
- 查询拼装、分页、排序、过滤、树结构和权限短路条件。
- `window.urls`、旧 URL、请求参数、`state/msg/data` 响应 envelope、空值和错误语义。
- EOVA 用户、角色、菜单和按钮权限模型（迁移期不并入 platform System）。
- 旧前端事件名、参数字段、全局入口和保存副作用。

### 3.2 明确不在普通 port 中做的事

- 不按新框架习惯删除旧分支、合并业务方法或重写 SQL 规则。
- 不把 `Db`/`Record` 直接改写为各业务自己的 Mapper；统一经过 `EovaDbGateway`。
- 不把 Enjoy 页面简单改成 SFC 后就算完成；必须保留网络和交互语义。
- 不在没有旧版 baseline 时把新服务结果当 golden。
- 不在 R3 评审前启动批量 Worker，不自动扩展队列。
- ClassLoader/Mod、System 身份融合、yudao-ui 嵌入和 breaking API 另立 DES，不混入当前切片。

## 4. 目标架构：四层加两类证据

```text
legacy source (read-only)
        |
        v
eova-compat      Kv / template / JSON / legacy utility / Record semantics
        |
        v
eova-core        表达式、SQL 解析、元数据规则、Widget 逻辑
        |
        v
eova-db-adapter  EovaDbGateway + Kingbase/MyBatis 实现
        |
        v
yudao-module-eova-biz   Spring Service + thin Controller
        |
        v
eova-ui          URL/HTTP adapter + Vue/Element Plus view

unit evidence: source/hash/structure/test
slice evidence: API/HAR/DB/UI journey
```

约束：

1. `eova-core` 不依赖 Spring、JFinal、HTTP 或业务数据库；需要旧能力时依赖 `eova-compat` 接口。
2. `eova-db-adapter` 是唯一允许接触 MyBatis、数据源和事务的边界。
3. Controller 只做 HTTP 绑定、鉴权入口和响应包装，不能重新实现旧业务分支。
4. `eova-ui` 的 Element Plus 只是渲染壳；旧组件 props、事件和请求顺序属于兼容层职责。

## 5. 迁移单元模型

### 5.1 单元类型

| 类型 | 内容 | 完成条件 |
|---|---|---|
| `S` | 非业务兼容适配（Kv、模板、JSON、旧工具） | 方法契约、边界测试和调用替换闭合 |
| `K` | 框架无关内核类 | 源结构对应、传递依赖无未声明 stub、核心 golden 通过 |
| `D` | 数据访问和 Record/事务适配 | 双数据源、分页、null、事务回滚和方言测试通过 |
| `B` | Service/Widget/Model 业务逻辑 | 旧分支逐条对应，API 或服务 golden 通过 |
| `A` | Controller/Route/Interceptor 薄壳 | URL、状态码、响应 envelope、异常短路一致 |
| `F` | 前端 core/template/demo 单元 | 请求 HAR、事件顺序、关键 DOM 和操作结果一致 |
| `V` | 只读验证单元 | 只产生证据，不修改业务代码 |

### 5.2 每个单元的强制字段

`unitId`、`unitType`、`sourcePath`、`sourceFqcn`、`targetPaths`、`sourceRevision`、`sourceSha256`、`targetBeforeSha256`、`directDependencies`、`allowedAdaptations`、`contractRefs`、`acceptanceProfile`、`manifestRevision`、`runId`、`leaseUntil`、`workerStatus`。

S 类没有单文件源路径时，必须有对应 DES 方法契约；不能把 `sourcePath=null` 当成随意设计新 API 的授权。

## 6. 垂直切片顺序

### 切片局部门禁（替代全量 Slice 0 前置）

完整 267 Java / 132 前端资产 manifest 和完整 baseline 仍是最终审计账本，但不再阻塞第一个功能切片。每个垂直切片单独登记：

1. `automation/slices/<sliceId>/manifest.jsonl`：只覆盖该用户旅程实际触达的源文件、目标路径、依赖、契约和 hash。
2. `baselinePath`：只覆盖该切片的旧系统启动、API/HAR、数据库或 UI 证据。
3. 只有该切片 `manifestStatus=frozen`、`baselineStatus=ready`、依赖已满足且 `ready=true`，Orchestrator 才能派发其中一个单元。
4. 某个切片未准备好只阻塞该切片；Orchestrator 可以等待或选择其他已经 `ready` 的切片，但仍保持单 run 串行。

### Slice 1：登录与主框架切片（S01-login-shell）

范围：旧 `LoginService`、`LoginController`、登录页逻辑/视图/样式、主框架菜单/Tab/退出逻辑，以及它们的最小依赖闭包。验收必须覆盖登录成功、登录失败、session/cookie、菜单加载、主框架结构、退出登录和旧新截图/HAR 对照。

当前状态：`preparing`；局部 manifest 为 `S01-v0-provisional`，旧 demo readiness、API/HAR、截图和 logout 证据尚未完成。

退出条件：S01 manifest 冻结、S01 baseline ready、所有 S01 单元按依赖完成并通过功能/UI 验收。

### Slice 2：表达式内核兼容切片

顺序：

1. `S-KV`
2. `S-TEMPLATE`
3. `S-LEGACY-UTILITY`
4. `K-EOVA-EXP`（含 `EovaExp`、`SqlParse`、`EovaExpParam`、`SqlCondition`、`TableSource` 实 port）
5. `K-EXP-CONFIG`
6. 纯 Java golden 和 SQL 解析 golden

退出条件：不再依赖 `cn.eova.*` compile-stub 才能通过；每个 JFinal 调用都有明确 compat 替换或阻塞记录。

### Slice 2：数据库语义切片

顺序：

1. `D-RECORD`
2. `D-EOVA-DB-GATEWAY`
3. `D-KINGBASE-DATASOURCE`
4. `B-EOVA-OPTION`
5. `B-EOVA-EXP-BUILDER`

退出条件：`find/findFirst/paginate/save/update/delete`、双库、事务回滚、分页边界和 null 语义均有实测证据。

### Slice 3：一个最小元数据业务闭环

只选择一个旧对象，例如 `eova_object` 或一个 demo object，完成：

```text
Meta read -> Widget query -> permission filter -> API response -> table page
```

退出条件：旧版和新版同一输入的 HTTP/HAR/关键 DOM 结果一致。没有该闭环，不继续铺开全部 32+25 表。

### Slice 4：表单和权限闭环

在 Slice 3 通过后，再迁移一个对象的 add/update/detail、按钮权限、角色切换和错误场景。退出条件是登录→菜单→列表→新增→保存→编辑→删除完整旅程。

### Slice 5：横向扩展

按 manifest 依赖扩展到剩余 Meta、Form、Auth、Upload、Excel、Task、Demo 和前端模板；每扩展一类，都复用 Slice 3/4 的 golden 结构，不单独发明验收标准。

### Slice 6：基础设施和后续整合

在至少一条业务切片完成后再接 Gateway、Nacos、Redis、部署和 platform System；这部分不能反过来成为业务逻辑未迁移时的“空壳完成”。

## 7. 重新定义 Done

### 单元 `verified`

- 源 revision/hash 与目标 before hash 已复核。
- `ported from`、旧 FQCN、source revision 和中文方法注释齐全。
- 传递依赖闭包中没有未声明 compile-stub；明确允许的 S/D 适配除外。
- 结构/分支/异常检查通过。
- 对应 `acceptanceProfile` 的测试已实际执行。
- 适用的 golden 通过；没有 baseline 必须记录 `golden: skipped`，不能隐藏。

### 切片 `verified`

- 切片中所有单元 verified。
- API、数据库或 UI 旅程达到切片验收标准。
- 没有未解释的 blocked、breaking contract 或环境缺失。

### 阶段 `Done`

只能由切片证据汇总推导，不能由文件数量、commit 数量、编译绿或静态资源存在推导。

## 8. Automation 新协议

### 8.1 Git 控制面为正式持久化边界

三条 Automation 的正式共享状态统一写入同一仓库 `dev` 分支的 `automation/` 目录，不依赖未提交的 `session-current.md` 或 `docs/.local/`：

1. `automation/state/current.json` 保存唯一活动 run、`stateRevision`、lease 和正式门禁状态。
2. `automation/queue/units.json` 保存单元队列及 manifest/baseline 门禁。
3. `automation/runs/index.json` 与 `automation/runs/<runId>/` 保存 run 摘要、任务快照、结果和事件。
4. 每次写入前 fetch/rebase 并复核 revision、runId、lease 和 hash；push 冲突必须重新读取，禁止 force push。

`docs/.local/persistence-probe-*.json` 降级为可选诊断工具，用于排查云端工作区路径问题，不再作为正式派单前置条件，也不改变 `workerStatus` 或业务队列。若正式控制面不可读，才记录 `control-plane-not-persistent` 并停止。

### 8.2 串行状态机

```text
design_ready -> ready -> ported_awaiting_verifier -> verified
                                      \-> blocked
```

- 每次只允许一个 `unitId` 和一个有效 lease。
- 首次和不稳定阶段只 Manual Run；R3 放行且 manifest、baseline 和控制面门禁通过后，Orchestrator 工作日 09:00、Worker 10:00 使用错峰 Schedule，Verifier 使用 GitHub `New push to branch=dev` 即时触发并保留工作日 14:00（Asia/Shanghai）Schedule 兜底。
- Schedule 和 GitHub 事件都只负责唤醒，不能绕过 `workerStatus`、hash、runId、lease 和单元依赖门禁；不满足条件时必须 no-op，禁止 Worker 监听自身 push，禁止并行抢占。
- Worker 直接推 GitHub `dev`；不建 `cursor/*`、不建 Draft PR。
- Verifier 只验证，不修复业务代码。
- 失败最多重试两轮；之后 `blocked`，由人工确认新 run 或新 DES。

### 8.3 Worker 的范围

Worker 只能修改清单 `targetPaths` 和必要构建文件；不能顺便补全依赖、改 URL、改数据库表或修改任务板状态。发现依赖未冻结时必须停止，而不是“先加一个 stub”。

## 9. 新的任务板状态

R3 评审期间，唯一 `In Progress` 应为 `DES-002-R3`。所有迁移 Worker 单元暂停为 `Idea` 或 `Deferred`，不保留一个看似正在执行但实际上没有 Automation run 的 `LC-011 In Progress`。

R3 评审通过后按切片 registry 的 `order` 选择第一个 `ready=true` 的切片，再只开放该切片中依赖满足的一个单元；任何单元不能绕过自身 manifest、baseline 和控制面门禁抢跑。完整全量 manifest 不再是首单前置。

## 10. R3 评审通过标准

1. 拿哥确认“垂直切片 + manifest + 依赖闭包 + evidence gate”作为主路线。
2. 确认 `automation/` Git 控制面可读写；可选 persistence probe 不再作为硬门禁。
3. 完成首个功能切片（S01-login-shell）的局部 manifest 和旧 demo baseline 设计输入。
4. 把现有 LC-011 的 4 个已合入类重新标记为“代码已合入、切片未验证”，不再计入完整迁移百分比。
5. 更新切片 registry、三份 Automation 提示词和 rolling docs；全局 Git 控制面可用即可手动运行 Automation，业务派单仍由各切片局部门禁决定。
