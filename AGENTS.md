# EOVA 项目协作规则

## 项目身份

- 维护者称呼：拿哥；协作者称呼：小郝。
- 默认使用中文；路径、命令、代码符号、错误串和第三方产品名保留原文。
- 所有程序方法签名必须配套简短中文注释。

## 迁移口径

- `meta-eova/eova/` 是只读旧源码基线；`remis-eova/` 是新代码落点。
- 本项目做代码级迁移：以旧源码逐文件、逐单元 port，保持职责、字段、方法、分支、异常语义和对外契约等价。
- 只允许新技术底座必需的适配；禁止按功能重新设计、删除业务分支、合并无关单元、改变 URL/JSON 契约或用 compile-stub 冒充完成。
- 每个目标文件必须保留 `ported from`、旧 FQCN 和 source revision 追溯信息。

## 任务治理

- 遵循 `docs/session-current.md`、`docs/session-handoff.md`、`docs/ai-task-board.md` 和 `docs/DES-002-R2-migration-execution-design.md`。
- 同一时刻只能有 1 个 `In Progress` 任务；未满足 scope、acceptance、artifact 和依赖条件的任务保持 `Idea`。
- 新 API、数据库表、协议、权限、运行时配置、第三方集成和持久化边界必须先建立 `DES-*` 设计任务。
- 治理文档和本地 golden 证据只在本地维护，不 commit、不 push。

## Git 与源仓库

- 代码工作固定在 `dev` 分支。
- GitHub 主 remote 由 URL `https://github.com/zlw123/tky-eova.git` 识别；当前本地 remote 名为 `github`。
- `origin` 当前是内网 GitLab 备份，不得把它当作 Automation 主 remote。
- 禁止创建 `cursor/*` 分支、Draft PR/MR、自动 merge 或使用 `git reset --hard`。
- Worker 只提交当前迁移单元业务代码和必要构建文件，并 push GitHub 主 remote 的 `dev`。
- 保留用户已有改动，不覆盖、不将无关改动加入 commit。

## Automation 串行协议

- 固定顺序：Orchestrator → Worker → Verifier。
- 首次和不稳定阶段只使用 Manual Run；禁止高频 cron 和并行 Worker/Verifier。
- `ready` 只允许 Worker 执行；`ported_awaiting_verifier` 只允许 Verifier 执行；`verified` 才能派下一单元；`blocked` 需要人工解除。
- 每个单元必须复核 `unitId`、`sourceRevision`、`sourceSha256`、`targetBeforeSha256`、`runId` 和 lease；状态或 hash 变化时停止。

## 验证口径

- 构建通过、单测通过、静态资源存在或进程启动成功不等于迁移完成。
- 按 acceptanceProfile 执行 compile/test、契约检查、golden API/HAR、Playwright 或 live readiness；未执行必须标记 `not executed`，没有 baseline 必须标记 `golden: skipped`。
- 验证失败不得自行修改业务代码或跳过单元，必须标记 `blocked` 并记录命令、日志摘要、根因分类和下一步。

