# DES-002-R3 Slice 0 基线输入

> 状态：baseline_pending / Design-only
> 目的：把旧 demo 的源码语义和最小运行态事实固定成可录制输入；当前只有静态来源证据，尚未执行旧 demo。

## 1. 静态确认的旧 demo 入口

来源：`meta-eova/eova` submodule，revision `1b1d39e7350f7e031b216aad0399fc8cc55dce08`。

| 项 | 静态来源 | 当前结论 |
|---|---|---|
| 启动类 | `demo/src/main/java/cn/eova/meta/RunEovaMeta.java` | 待旧 demo 实际启动确认 |
| 启动脚本 | `demo/eova.sh` | Undertow 默认端口 `9090`，`APP_NAME=eova-meta-9090` |
| Java | `demo/eova.sh` | 旧脚本显式使用 Java 8 |
| 平台库 | `demo/src/main/resources/eova/dev.txt` | 配置键为 `eova.url`，目标 schema `eova_meta` |
| 业务库 | `demo/src/main/resources/eova/dev.txt` | 配置键为 `main.url`，目标 schema `demo` |
| 浏览器入口 | `meta-eova/eova/README.md` | `http://127.0.0.1:9090/` |
| Router API | `demo/src/test/java/api/UserApi.http` | `/router`，`demo.user.*` 方法 |

静态 README 中的 MySQL 连接串不能直接作为当前运行环境事实；仍需最小启动确认 Kingbase schema、账号、端口和数据快照，但登录分支、菜单组装和 session 调用链以固定源码为主要语义依据。

## 2. 首批 golden case（只定义，不伪造响应）

从 `demo/src/test/java/api/UserApi.http` 提取以下 case：

| caseId | 请求 | 目的 |
|---|---|---|
| `router-user-query-ok` | POST `/router?app_key=10000&method=demo.user.query&timestamp=0&sign=devnocheck`，空 JSON | 正常查询、响应 envelope、空结果 |
| `router-user-add-ok` | POST `/router?app_key=10000&method=demo.user.add&timestamp=0&sign=devnocheck`，`login_id/nickname` | 新增字段、影响行数和响应 |
| `router-user-query-bad-sign` | 同 query，错误 sign | 鉴权失败状态码、错误文本和 envelope |
| `router-unknown-app-key` | 未知 `app_key` + `demo.user.err` | app_key 校验和错误短路 |
| `router-user-login` | POST `/router?app_key=10000&method=demo.user.login...` | 登录成功/失败、token 或 session 语义 |

API baseline 还必须从旧前端真实调用中补充 `/grid/*`、`/api/meta/*`、`/api/home/menu`、表单、上传和导出 case；不能只依赖 `.http` 文件。

## 3. 源码证据与最小运行态 checklist

1. 核对 submodule revision、旧 demo 启动命令和静态 source-to-target 语义证据。
2. 从源码确认登录成功/失败、菜单、Tab、退出和 session 分支、URL、字段和异常路径。
3. 只启动一次旧 demo，确认实际端口、Java、Kingbase schema、最小权限账号和数据快照。
4. 访问 `/` 完成一次成功登录、一次失败登录、菜单加载和退出，记录运行态 cookie/session/token、HTTP envelope 和日志时间戳。
5. 保存登录页和主框架固定 viewport 截图；Tab 至少做一次 smoke，完整状态机以源码证据为准。
6. 不为 S01 录制全量 CRUD；新增/更新/删除等数据变更留给对应切片并使用专用测试数据，避免污染基线。
7. 记录失败 case，不得只保留成功请求；错误文本、状态码和空值均是契约。

## 4. 当前门禁

- `old-demo-readiness`: `not executed`
- `api-golden`: `baseline_pending`（仅记录 S01 运行态需要的 API；源码已明确的逻辑不要求重复运行）
- `database-snapshot`: 需要实时确认，不能直接复用旧结论
- `workspace-persistence-probe`: `optional_diagnostic / not run`
- Slice 0 manifest：已生成 `provisional`，未冻结

在 old-demo-readiness、api-golden、database-snapshot 和 manifest 转为 `ready/frozen` 前，不得启用 Worker 批量迁移，也不得把现有 engine 类升级为切片 `verified`。workspace persistence probe 不再是正式门禁。
