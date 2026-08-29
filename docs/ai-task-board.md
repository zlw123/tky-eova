# AI Task Board — meta-eova 技术栈迁移

> 同一时刻只允许 **1** 个 `In Progress`。  
> 总方案：`docs/DES-002-meta-eova-tech-stack-migration.md`  
> 新代码仓库：**remis-eova**（`/Users/zhouliwei/eova/remis-eova/`）

---

## 进度摘要

| 阶段 | 进度 |
|------|------|
| Phase 0 设计 | 100%（R1/R1-F Done；R2/R2-F 待出对照表） |
| Phase 1 后端 port | ≈5%（eova-core 4 单元 + 脚手架已合入 dev） |
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
| DES-002-R2 | 267 Java 对照 + golden API 清单 | Idea | DES-002-R1 | 与 FE 共用 baseline |
| DES-002-R2-F | 55 JS 对照 + EovaUI 组件映射表 | Idea | DES-002-R1-F | `eova-ui-component-map.md` |

### Phase 1 — 脚手架

| ID | 标题 | 状态 | 依赖 |
|----|------|------|------|
| LC-001 | 创建 remis-eova/backend/yudao-module-eova-api/biz | **Deferred** | 降至 P5；先 port 内核 |
| LC-011 | eova-core 内核模块（engine/sql/hook 直迁） | **In Progress** | DES-002-R1 |
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
| FE-001 | eova-ui Vite+TS+EP 工程初始化 | Ready | DES-002-R1-F |
| FE-002 | eova-urls + eova-http 契约层 | Idea | FE-001 |
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
| AUTO-003 | 创建并启用三条 Automation | **Ready** | AUTO-002 | orchestrator / worker / verifier |
| AUTO-004 | 首跑试点 LC-011 单单元 | Idea | AUTO-003, LC-011 | EovaExp port PR |

### 已完成（历史）

| ID | 标题 | 状态 |
|----|------|------|
| OPS-000 | 54321 连通性 | Done |
| OPS-001 | system 密码 / MCP | Done |
| DES-001 | Kingbase 双库建库 | Done |
| OPS-002 / VAL-001 | 双库导入验收 | Done |

---

## 推进协议

1. Phase 0 核心决策已 Done；**LC-011 In Progress**（EovaExp / SqlParse / EovaExpParam / SqlCondition 已合入 **dev**；TableSource 仍为 compile-stub）。Automation **已停**，待改规则后再启。LC-001 Deferred。
2. 涉及新模块/新协议/新表结构 → 先 DES 后 LC。
3. 身份/System、前端/yudao-ui 整合 → DES-003 / DES-004，不阻塞当前迁移主线。
4. 每轮结束同步 rolling docs 三件套。
5. DES-002-R2 完成前只跑试点白名单：LC-011 / FE-001 / FE-002。同一时刻仅 1 个 In Progress。
