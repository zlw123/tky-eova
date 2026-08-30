# DES-ENV-R2 迁移验证环境设计

> 状态：Design-only
> 目的：定义旧 demo、新服务、测试库和凭据，使 Automation 的“未执行”和“验证通过”可区分。

## 1. 环境矩阵

| 环境 | 用途 | 必须记录 |
|---|---|---|
| old-demo | 录制 baseline | 源码 revision、启动命令、端口、数据库快照 |
| new-local | Worker/Verifier 编译与单测 | JDK、Maven/Node、commit、配置 profile |
| integration | API/HAR 对照 | gateway、服务路由、数据源、账号角色 |
| browser | Playwright 交互 | 浏览器版本、baseURL、登录方式 |

所有环境使用测试数据和最小权限账号。凭据通过本机环境变量或 Cursor secret 注入，不写入仓库、日志和提示词输出。

## 2. Readiness 检查

在运行 golden 前依次检查：端口监听、HTTP 健康、数据库连接、目标 schema、迁移服务 revision、旧 demo 可访问、测试账号可登录。任一项失败标记 `environment_blocked`，不得降级为 skipped 后继续宣称 verified。

## 3. 证据快照

每次验证记录命令、时间、版本、配置 profile、数据库快照标识、commit hash 和日志路径。环境变化后必须重新录制或明确 baseline 不可比。

