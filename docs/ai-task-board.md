# AI Task Board — meta-eova 技术栈迁移

> 同一时刻只允许 **1** 个 `In Progress`。  
> 总方案：`docs/DES-002-meta-eova-tech-stack-migration.md`  
> 新代码仓库：**remis-eova**（`/Users/zhouliwei/eova/remis-eova/`）

---

## 进度摘要

| 阶段 | 进度 |
|------|------|
| Phase 0 设计 | R3 评审通过，等待 persistence probe、manifest freeze 和 baseline |
| Phase 1 后端 port | 暂不计进度（已有 4 个 engine 文件合入，但传递依赖仍含 compile-stub） |
| Phase 1-F 前端 port | 0% |
| **整体** | **≈8%** |

---

## 任务池

### Phase 0 — 设计

| ID | 标题 | 状态 | 依赖 | 产出 |
|----|------|------|------|------|
| DES-002 | meta-eova 技术栈迁移总方案 | Done | — | `docs/DES-002-meta-eova-tech-stack-migration.md` |
| DES-002-01 | remis-eova 仓库落仓 | Done | — | 依赖 platform BOM，不进 platform 主仓 |
| DES-002-02 | 身份策略（先 EOVA 表） | Done | — | 迁移期保留 eova_user/eova_role |
| DES-002-03 | 前端策略（先独立 eova-ui） | Done | — | `remis-eova/fornt/eova-ui` |
| DES-003 | 身份并入 platform System | Idea | VAL-202 后 | 后续任务 |
| DES-004 | eova-ui 嵌入 yudao-ui | Idea | eova-ui 稳定后 | 后续任务 |
| DES-002-R1 | 代码级迁移方法论修订 | Done | — | `DES-002-R1-code-level-migration.md` |
| DES-002-R1-F | 前端代码级迁移方案 | Done | — | `DES-002-R1-frontend-code-level-migration.md` |
| DES-002-R2 | 267 Java 对照 + golden API 清单 | Idea | DES-002-R1 | 与 FE 共用 baseline；Java 总量已复核，逐文件 manifest 未冻结 |
| DES-002-R2-F | 132 个 JS/Vue/HTML 资产对照 + EovaUI 组件映射表 | Idea | DES-002-R1-F | `eova-ui-component-map.md`；业务文件子集待分类冻结 |
| DES-002-R3 | remis-eova 迁移总体重设计（垂直切片、manifest、证据门禁、Automation 控制面；评审已通过） | **In Progress** | DES-002-R1、DES-002-R2-EXEC | `DES-002-R3-overall-migration-redesign.md`、`automation/workspace-persistence-probe.md` |
| DES-002-R2-EXEC | 代码级迁移执行设计（单元、适配、证据、状态） | **Done** | DES-002-R1 / R1-F | `DES-002-R2-migration-execution-design.md` |
| DES-API-R2 | API 路径、JSON、HAR golden 基线设计 | **Done** | DES-002-R1 | `DES-API-R2.md`；设计完成，实际 baseline 仍待录制 |
| DES-ADAPTER-R2 | Kv、模板引擎、日志等非数据库旧底座适配设计 | **Done** | DES-002-R2-EXEC | `DES-ADAPTER-R2.md`；设计完成，适配实现作为 LC-011 的 S 类 support 单元 |
| DES-DB-ADAPTER | EovaDbGateway 最小方法集、事务、方言、Record 语义设计 | **Done** | DES-002-R1 | `DES-DB-ADAPTER.md`；设计完成，实现前须补测试 |
| DES-ENV-R2 | 旧 demo、测试库、凭据、脱敏和运行环境设计 | **Done** | DES-002-R2-EXEC | `DES-ENV-R2.md`；设计完成，environment readiness 仍待实测 |
| DES-BOUNDARY-R2 | Config/Render/Interceptor 等价替换边界设计 | **Done** | DES-002-R2-EXEC | `DES-BOUNDARY-R2.md`；设计完成，D 类实现前逐项细化 |

### Phase 1 — 脚手架

| ID | 标题 | 状态 | 依赖 |
|----|------|------|------|
| LC-001 | 创建 remis-eova/backend/yudao-module-eova-api/biz | **Deferred** | 降至 P5；先 port 内核 |
| LC-011 | eova-core 内核模块及必要旧底座适配（engine/sql/hook 直迁） | Idea | DES-002-R3、DES-ADAPTER-R2、Slice 0 |
| LC-012 | EovaDbGateway + MyBatis 适配层 | Idea | LC-011 |
| LC-013 | golden API baseline 录制（旧 demo） | Idea | DES-002-R2 |
| LC-002 | Nacos + application-dev 配置 | Idea | LC-001 |
| LC-003 | Gateway /admin-api/eova 路由 | Idea | LC-002 |
| LC-004 | dynamic-datasource 双库 | Idea | LC-001 |
| LC-005 | Redis 替换 EhCache | Idea | LC-001 |
| LC-006 | → 合并为 **FE-001** eova-ui 工程初始化 | Deferred | 见前端 FE-* |

### Phase 1-F — 前端脚手架与 port（见 DES-002-R1-F）

| ID | 标题 | 状态 | 依赖 |
|----|------|------|------|
| FE-001 | eova-ui Vite+TS+EP 工程初始化 | Idea | DES-002-R3、Slice 0 |
| FE-002 | eova-urls + eova-http 契约层 | Idea | FE-001、DES-API-R2 |
| FE-003 | port EovaLayer | Idea | FE-002 |
| FE-004 | port EovaTable（含 eova.table.js） | Idea | FE-003, LC-202-PORT |
| FE-005 | port 表格模板 useEovaTablePage | Idea | FE-004 |
| FE-006 | port 树/树表/表单模板 | Idea | FE-005 |
| FE-007 | port 主框架+登录 | Idea | FE-005, LC-205 |
| FE-008 | port meta/menu/role 页 | Idea | LC-301~306 |
| FE-009 | port demo 组件 | Idea | FE-006 |
| FE-010 | Playwright golden 套件 | Idea | FE-007, VAL-202 |
| LC-007 | Kingbase DO/Mapper 代码生成 | Idea | LC-004 |

### Phase 2 — 数据层

| ID | 标题 | 状态 | 依赖 |
|----|------|------|------|
| LC-101 | eova_meta 32 表 DO/Mapper | Idea | LC-007 |
| LC-102 | demo 25 表 DO/Mapper | Idea | LC-007 |
| LC-103 | SQL 方言层迁移 | Idea | LC-101 |
| LC-104 | EovaExp 表达式引擎移植 | Idea | LC-001 |
| LC-105 | 字段类型 Convertor 映射 | Idea | LC-103 |

### Phase 3 — 核心服务

| ID | 标题 | 状态 | 依赖 |
|----|------|------|------|
| LC-201 | MetaService 元数据读写 | Idea | LC-101, LC-104 |
| LC-202 | WidgetManager 动态查询 | Idea | LC-201, LC-103 |
| LC-203 | FormService 表单 | Idea | LC-201 |
| LC-204 | AuthService 菜单权限 | Idea | LC-101 |
| LC-205 | Login 迁移（eova_user/eova_role 自洽） | Idea | LC-002 |
| LC-206 | Hook 扩展机制 | Idea | LC-201 |
| LC-207 | 定时任务迁移 | Idea | LC-101 |
| LC-208 | Excel 导入导出 | Idea | LC-201 |
| LC-209 | 文件上传 | Idea | LC-002 |

### Phase 4 — API 层

| ID | 标题 | 状态 | 依赖 |
|----|------|------|------|
| LC-301 | MetaAdminController | Idea | LC-201 |
| LC-302 | MenuAdminController | Idea | LC-204 |
| LC-303 | ButtonAdminController | Idea | LC-204 |
| LC-304 | DictAdminController | Idea | LC-101 |
| LC-305 | UserAdminController | Idea | LC-101 |
| LC-306 | Role/Auth API | Idea | LC-204 |
| LC-307 | Grid/Table Widget API | Idea | LC-202 |
| LC-308 | Form Widget API | Idea | LC-203 |
| LC-309 | Tree Widget API | Idea | LC-202 |
| LC-310 | Widget 综合 API | Idea | LC-202 |
| LC-311 | SingleTemplate API | Idea | LC-202 |
| LC-312 | Task/Ops/SSE API | Idea | LC-207 |
| LC-313 | Login/Index API | Idea | LC-205 |
| LC-314 | Admin/Router API | Idea | LC-205 |

### Phase 5 — 前端

| ID | 标题 | 状态 | 依赖 |
|----|------|------|------|
| LC-401 | 登录页 | Idea | LC-006, LC-313 |
| LC-402 | 主框架 Layout | Idea | LC-006 |
| LC-403 | 元数据管理页 | Idea | LC-301 |
| LC-404 | 菜单管理页 | Idea | LC-302 |
| LC-405 | 角色授权页 | Idea | LC-306 |
| LC-406 | 表格模板 | Idea | LC-307 |
| LC-407 | 树模板 | Idea | LC-309 |
| LC-408 | 树表模板 | Idea | LC-307, LC-309 |
| LC-409 | 表单模板 | Idea | LC-308 |
| LC-410 | EovaUI→Element Plus 组件 | Idea | LC-406 |
| LC-411 | 动态 Widget 渲染引擎 | Idea | LC-410 |
| LC-412 | Demo 业务页 | Idea | LC-102, LC-411 |
| LC-413 | Axios/CommonResult 适配 | Idea | LC-006 |

### Phase 6 — 验收

| ID | 标题 | 状态 | 依赖 |
|----|------|------|------|
| VAL-201 | 双库数据完整性 | Idea | LC-101, LC-102 |
| VAL-202 | 登录→菜单→CRUD 旅程 | Idea | LC-401~411 |
| VAL-203 | 元数据驱动页 3 object | Idea | LC-411 |
| VAL-204 | 角色按钮/URI 权限 | Idea | LC-204, LC-405 |
| VAL-205 | Gateway+Nacos 联调 | Idea | LC-003 |
| VAL-206 | 性能基线 area 分页 | Idea | LC-202 |
| OPS-003 | 部署/runbook 文档 | Idea | VAL-202 |

### Phase A — Cursor Automations（Orchestrator + Worker + Verifier）

| ID | 标题 | 状态 | 依赖 | 产出 |
|----|------|------|------|------|
| AUTO-001 | 三层流水线文档 + 预填草稿 | **Done** | — | `docs/automation/*` |
| AUTO-002 | eova 根目录 git 初始化并 push | **Done** | AUTO-001 | GitHub `zlw123/tky-eova` + GitLab remis-eova |
| AUTO-003 | 创建并启用三条 Automation | Idea | AUTO-002、DES-002-R3、workspace persistence probe | orchestrator / worker / verifier |
| AUTO-004 | 首跑试点 LC-011 单单元 | Idea | AUTO-003, LC-011 | EovaExpConfig port 到 dev 并完成 Verifier 验证 |

### 已完成（历史）

| ID | 标题 | 状态 |
|----|------|------|
| OPS-000 | 54321 连通性 | Done |
| OPS-001 | system 密码 / MCP | Done |
| DES-001 | Kingbase 双库建库 | Done |
| OPS-002 / VAL-001 | 双库导入验收 | Done |

---

## 推进协议

1. 当前唯一 **In Progress** 为 **DES-002-R3**（`reviewStatus=approved`，剩余 persistence probe、manifest freeze 和 baseline 门禁）；LC-011 的 EovaExp / SqlParse / EovaExpParam / SqlCondition 虽已合入 **dev**，但传递依赖仍含 compile-stub，暂不计入切片 verified。Automation **已停**，待剩余门禁后再启；probe 当前 `not executed`。LC-001 Deferred。
2. 涉及新模块/新协议/新表结构 → 先 DES 后 LC。
3. 身份/System、前端/yudao-ui 整合 → DES-003 / DES-004，不阻塞当前迁移主线。
4. 每轮结束同步 rolling docs 三件套。
5. R3 评审已完成，但 persistence probe、manifest freeze 和旧 demo baseline 完成前不开放任何 Worker 单元；之后按 Slice 0 → Slice 1 只开放一个 Ready 单元。同一时刻仅 1 个 In Progress。

## Automation 控制面（2026-08-30）

- 机器状态事实源为同一 `dev` 分支的 `automation/` 目录：`state/current.json`、`queue/units.json`、`runs/index.json` 和 `runs/<runId>/`。
- 当前状态为 `controlPlaneStatus=blocked`、`activeRunId=null`、`stateRevision=0`；未创建 run，未开放业务派单。
- Orchestrator/Verifier 只能提交 `automation/`；Worker 先提交本单元代码，再提交对应 run 的状态；三者均禁止 force push、`cursor/*`、Draft PR 和自动 merge。
- 本地 rolling docs 和 `docs/.local/` 只作本地治理视图，不作为云端 Automation 跨 run 共享数据。
