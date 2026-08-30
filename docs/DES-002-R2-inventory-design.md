# DES-002-R2 迁移清单冻结设计

> 状态：Design-only
> 版本：2026-08-30
> 目的：冻结旧源码到新工程的逐文件清单、分类和 hash 规则。清单未冻结前，Automation 只能运行已明确的试点队列，不能按历史估算扩展派单。

## 1. 当前可复核基线

以下计数由仓库工作区实时目录得到，不代表已完成迁移：

| 范围 | 目录 | 数量 | 说明 |
|---|---|---:|---|
| Java | `meta-eova/eova/core/src/main/java/**/*.java` | 267 | 旧 core 源文件总数 |
| 平台 view 资产 | `meta-eova/eova/view/src/main/resources/webapp/eova/` | 105 | 6 个 `ui` 共享文件、9 个 `lib` 文件、86 个 `_view` 文件、4 个错误页 |
| Demo 资产 | `meta-eova/eova/demo/src/main/webapp/` | 27 | `_component`、`_eova`、`_view`、demo、excel、hotel、product |
| 前端资产合计 | 上述 view + demo | 132 | 84 JS、46 HTML、2 Vue |

复核命令固定为：

```bash
find meta-eova/eova/core/src/main/java -type f -name '*.java' | wc -l
find meta-eova/eova -type f \( -name '*.js' -o -name '*.vue' -o -name '*.html' \) | wc -l
find meta-eova/eova/view/src/main/resources/webapp/eova -type f \( -name '*.js' -o -name '*.vue' -o -name '*.html' \) | wc -l
find meta-eova/eova/demo/src/main/webapp -type f \( -name '*.js' -o -name '*.vue' -o -name '*.html' \) | wc -l
```

历史文档中的“平台 113、demo 25、自研 JS 55”属于旧估算。它们可以保留在历史复盘中，但不得出现在当前派单总量、进度百分比或完成条件中。

## 2. Manifest 结构

DES-002-R2 和 DES-002-R2-F 的最终产物必须是可审计的 `manifest.jsonl`（一行一个源单元），而不是只列目录或总数。每行至少包含：

```json
{
  "unitId": "R2-JAVA-0001",
  "unitType": "java|frontend|asset|support",
  "sourcePath": "meta-eova/eova/core/src/main/java/.../Foo.java",
  "sourceFqcn": "cn.eova....Foo",
  "targetPaths": ["remis-eova/backend/.../Foo.java"],
  "parentSourcePath": null,
  "classification": "A|B|C|D|E|S|frontend-core|frontend-template|demo|vendor|shell|error|deferred",
  "sourceRevision": "<submodule HEAD>",
  "sourceSha256": "<sha256>",
  "lineCount": 123,
  "directDependencies": ["cn.eova....Bar"],
  "contractRefs": ["case-id or URL entry"],
  "migrationStatus": "unmapped|queued|ported|verified|deferred|excluded",
  "exclusionReason": null
}
```

规则：

1. `sourcePath` 对 A-E 和前端真实 port 必须存在；S 类适配单元可以为 `null`，但必须引用对应 DES 设计和 targetPaths。
2. `targetPaths` 必须是数组；1:N 拆分必须填写 `parentSourcePath`、拆分原因和逻辑覆盖关系。
3. vendor、错误页、纯 shell 或重复构建入口不能静默删除，必须写 `migrationStatus=excluded/deferred` 和原因。
4. 仅存在目标 compile-stub 不能将 `migrationStatus` 写成 `ported/verified`。
5. Java 单元的 `sourceFqcn` 从 `package` + `class/interface/enum/record` 声明解析；不得靠文件名猜测。

## 3. 分类规则

### 3.1 Java

- `A`：无 JFinal、数据库和启动胶水依赖的纯类。
- `B`：Kv、模板、JSON、日志或工具依赖，但不直接访问数据库。
- `C`：Db、Record、Model、Service、Widget 或 SQL 执行逻辑。
- `D`：Config、Render、Interceptor、Route、Plugin 等生命周期/HTTP 胶水。
- `E`：ClassLoader、插件加载或暂不具备等价证明的能力，默认 deferred。
- `S`：为上述单元提供的最小旧底座适配；必须独立测试，不算业务 port。

分类至少交叉检查 import、继承关系、静态调用和调用方；仅根据包名分类不通过。

### 3.2 前端

- `frontend-core`：`view/.../eova/ui/**` 中实际被页面调用的共享逻辑。
- `frontend-template`：`view/.../eova/_view/**` 中的页面、模板、按钮和公共脚本。
- `demo`：`demo/src/main/webapp/**` 下的演示组件、主题和示例业务页。
- `vendor`：`lib/**`、压缩库和第三方运行时；只做依赖替换或许可证登记，不按业务 JS port。
- `shell`：include、主题壳、构建入口和仅负责注入的文件；必须映射到路由/布局/配置任务。
- `error`：403/404/500/503 等错误页；按产品错误页任务单独处理，不混入业务模板。

`frontend` 单元还必须记录旧 URL、请求函数、全局变量、事件名、DOM 选择器和副作用；单纯把 HTML 改成 SFC 不算完成。

## 4. 依赖和派单门禁

1. Orchestrator 只能从 manifest 中选择 `migrationStatus=unmapped` 且依赖已 `verified` 的单元。
2. sourceRevision、sourceSha256 和目标 `targetBeforeSha256` 在派单时重新计算；任一变化即 `blocked`。
3. `vendor`、`error`、`shell` 和 `E` 类不能被普通 A/B/C/前端队列隐式吸收；必须有对应 taskId 或明确 deferred 记录。
4. 一个旧文件同时被两个单元引用时，只允许一个单元拥有 port 权；另一个单元只能声明 dependency，防止重复 port。
5. manifest 变更必须带 `manifestRevision` 和变更原因；Automation 运行期间 revision 变化时立即停止。

## 5. 交付顺序

1. 先提交本地（不进 Git）Java/前端 manifest 和分类审计记录。
2. 再由人工确认遗漏、重复、vendor 和 deferred 项。
3. 冻结 `manifestRevision` 后，才能把 DES-002-R2 / DES-002-R2-F 置为设计完成，并扩展 `unit-queue-index.md`。
4. API/golden 条目引用 manifest 的 `unitId`；没有对应源单元的 API 只保留 `baseline_pending`，不伪造归属。

在 manifest 冻结前，`LC-011` 只按 `LC-011-unit-queue.md` 的显式试点单元运行；前端只允许 FE-001 脚手架单元。

