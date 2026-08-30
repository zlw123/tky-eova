# S01-login-shell：登录与主框架切片

> 状态：`preparing`
> manifest：`automation/slices/S01-login-shell/manifest.jsonl`（`S01-v0-provisional`）
> baseline：`docs/.local/slices/S01-login-shell/`（尚未录制）

本切片执行顺序以 `automation/plan/migration-plan.json` 的 `planRevision=20260830-v1`、`dispatchOrder=1` 和 `unitOrder` 为准。Orchestrator 只能按该顺序派单；切片范围或单元顺序变更必须先由拿哥和主协作者更新计划并递增 `planRevision`。

## 目标

把可运行的 `eova-meta` 登录与主框架迁移到已确定的新技术栈，保持旧系统的功能、接口、状态和 UI 效果。代码级迁移是实现约束：旧 Java 方法和旧 JS/Vue 逻辑逐方法、逐函数 port；只替换 Spring、Kingbase/MyBatis、Vue 3、Element Plus 等底座调用。

## 范围

- 后端：`LoginService`、`LoginController` 及登录/session 所需的最小适配依赖。
- 前端：登录页逻辑、登录页结构与样式、主框架菜单/Tab/退出逻辑。
- 不包含：全量元数据 CRUD、完整 267 Java/132 前端资产冻结、platform System 身份合并和主题重新设计。

## 局部 manifest 冻结前检查

1. 每个 S01 源文件的 `sourceRevision` 和 `sourceSha256` 与旧 submodule revision 一致。
2. 每个 `targetPaths` 有明确目标；1:N 拆分写明逻辑覆盖关系。
3. `directDependencies`、`allowedAdaptations`、`contractRefs` 已逐项复核。
4. 没有重复 owner、未解释 deferred 或把 compile-stub 标为 verified 的记录。
5. 复核后将 manifestRevision 从 `S01-v0-provisional` 更新为新的冻结版本，并将切片状态保留为 `preparing`，直到 baseline 也完成。

## 基线分工：源码证据 + 最小运行态

登录分支、菜单组装、Tab 状态机和退出调用链以固定旧源码为语义基线，不要求通过重复运行去“证明”代码已经写明的分支。manifest 中必须记录对应旧文件、方法、分支和契约；源码证据不足时才补充分析，不以猜测替代。

仍需在旧源码 revision `1b1d39e7350f7e031b216aad0399fc8cc55dce08` 上启动一次 `demo/eova.sh`，只验证源码无法可靠推出的运行态事实：Java/端口/数据源 readiness、真实 HTTP envelope、cookie/session 形态、静态资源加载和固定 viewport 的实际页面几何。所有结果写入本地 `docs/.local/slices/S01-login-shell/`，不提交。

最小运行态录制：

| caseId | 证据 |
|---|---|
| `S01-login-page` | 登录页截图、viewport、静态资源和初始网络请求 |
| `S01-login-success` | 脱敏请求/响应、状态码、headers、cookie/session 形态和日志时间 |
| `S01-login-failure` | 错误密码的实际状态码、错误文本和 envelope（分支语义以源码为准） |
| `S01-home-menu` | `/api/home/menu` 实际响应、菜单层级/顺序和主框架加载结果 |
| `S01-main-shell-tabs` | 至少一次菜单打开、Tab 切换或刷新 smoke；完整状态机以源码证据为准 |
| `S01-logout` | 退出请求、session 失效和回到登录页的实际结果 |
| `S01-login-visual-baseline` | 登录页和主框架在固定 viewport 的完整截图 |

不要求在 S01 录制全量 CRUD、上传、导出或所有 API；这些属于后续切片。新增、删除或权限变化不属于本切片，另建切片或单元；不要为了录 baseline 修改旧库中的业务数据。

## `ready=true` 准入

只有以下条件同时满足，才能把 `automation/slices/index.json` 中 S01 更新为 `manifestStatus=frozen`、`baselineStatus=ready`、`ready=true`，并把对应单元放入 Ready 白名单：

1. S01 局部 manifest 已冻结。
2. manifest 已为登录、菜单、Tab、退出和 session 分支保留可追溯的源码证据。
3. 旧 demo 最小运行态 smoke 已完成：启动、登录成功/失败、菜单、退出和 session 失效可访问。
4. 旧页面截图和新页面验收视口已固定；实际 cookie/session、HTTP envelope 和资源加载结果已记录。
5. 需要运行态对照的 API/HAR、UI 证据路径和验收步骤可被 Verifier 直接读取；仅由源码确定的逻辑不重复造运行证据。

`ready=true` 之前不得让 Orchestrator 派 S01 单元；不得仅通过修改 JSON 字段放行。

## 迁移后验收

Verifier 必须在同一测试数据和同一 viewport 下对照旧/新系统：

- 登录成功/失败的状态、提示、session/cookie 语义一致。
- 菜单请求、层级、顺序、显示权限一致。
- 主框架、Tab、刷新、退出和回到登录页行为一致。
- 登录页和主框架关键布局、尺寸、颜色、间距、按钮位置与视觉状态无未经批准的漂移。
- API/HAR 和 Playwright/UI 证据齐全；任一行为或视觉差异都标记 `blocked`，不由 Worker 自行修正验收结论。
