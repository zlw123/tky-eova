# DES-API-R2 API 与 Golden 基线设计

> 状态：Design-only
> 目的：冻结旧 EOVA API 的可比输入输出，供后端 Controller、前端契约层和 Verifier 共用。

## 1. API 条目格式

每条接口一个 `caseId`，保存以下字段：

```json
{
  "caseId": "meta-table-demo",
  "method": "GET",
  "url": "/api/meta/table/demo",
  "headers": {},
  "pathParams": {},
  "query": {},
  "body": null,
  "authProfile": "test-admin",
  "oldRevision": "<old git revision>",
  "expected": {
    "status": 200,
    "contentType": "application/json",
    "envelope": ["state", "msg", "data"]
  },
  "allowedDiff": []
}
```

必须覆盖 `/api/meta/*`、`/api/form/*`、`/grid/*`、登录、菜单、权限、上传和导出中实际被迁移单元调用的接口。不能凭 URL 猜接口；以旧 Controller、旧 JS 和运行录制三方交叉确认。

## 2. 录制和脱敏

旧 demo 固定启动命令、数据库快照、账号角色和源码 revision。录制请求/响应/HAR 时脱敏 token、密码、手机号、身份证、真实业务数据和文件内容；manifest 记录脱敏规则和输入数据来源。缺少旧运行环境时只能建立条目模板，状态为 `baseline_pending`，不得把新服务结果当 baseline。

## 3. 比较规则

- HTTP method、路径、查询参数、正文结构、分页字段严格比较。
- JSON 键顺序无关；动态时间、随机 ID 只能按 manifest 中的字段规则归一化。
- `state/msg/data` envelope 必须保留；错误状态码、错误文本分类和空结果语义不能静默忽略。
- `allowedDiff` 必须逐字段列出原因；禁止使用全局 ignore。
- 前端 case 还需保存 HAR、关键 DOM 断言和操作结果；截图只作辅助证据。

## 4. 状态

`planned → baseline_pending → baseline_ready → port_verified → regression_verified`。任何实际差异进入 `blocked`，由人工决定是修复实现、补充允许差异还是建立 breaking 变更设计。

