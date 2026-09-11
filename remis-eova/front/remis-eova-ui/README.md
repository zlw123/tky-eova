# remis-eova-ui

EovaMeta 技术栈迁移后的**独立前端工程**。

> 本工程属 `DES-002-R4` **阶段 2（前后端分离）**，当前只落**地基骨架**。
> **阶段 1（技术栈迁移）全部切片 `verified` 之前，本工程不接任何业务页面。**

---

## 1. 定位与落点

| 项 | 值 |
|---|---|
| 工程名 | `remis-eova-ui` |
| 落点 | `remis-eova/front/remis-eova-ui/` |
| 后端对应 | `remis-eova/backend/yudao-cloud/yudao-module-eova/` |
| 设计依据 | `docs/DES-002-R4-techstack-migration-master-plan.md` §2.5 / §6bis |

**为什么是独立前端工程：** 独立工程避免了"先写一遍、集成时再并入平台 UI 库"的二次重写。platform 侧已验证具备承接条件 —— `platform/fornt/yudao-ui` 支持**双构建模式**（Web 应用 + 库模式发布 `@zlw8282003/yudao-ui`），并自带**跨应用 iframe 通信 SDK**（`useIframeBridge` / `IframeBridge` / `IframeModal`）。

---

## 2. 阶段门禁（不可绕过）

```text
阶段 1：技术栈迁移（后端）  ← 前端一行不动，产出 golden baseline
        │
        ▼  阶段 1 全部 verified 后才启动
阶段 2：前后端分离
        ├─ T01 前端工程化底座        ← 本工程当前所在位置
        ├─ T02 模板转 SFC（25 页逐页）
        ├─ T03 手写 JS 模块化（66 个）
        └─ T04 服务端模板退役（淘汰 Enjoy）
        │
        ▼
阶段 3：yudao 集成
```

**为什么阶段 1 前端必须一行不动：** 前端不变时，新旧系统的差异 **100% 归因于后端**。若前后端同时改，失败将无法归因（后端 port 错 / 契约没对上 / 前端写法不同，三者症状相同）。阶段 1 产出的 golden baseline 是阶段 2 唯一的规格书与回归网。

---

## 3. 技术选型

工程底座**对齐 `platform/fornt/yudao-ui`**，把集成期摩擦降到最低：

| 项 | 选型 | 对齐依据 |
|---|---|---|
| 框架 | Vue 3.5 + TypeScript 5.3 | platform: Vue 3.5 + TS 5.3 |
| 构建 | Vite 5 | platform: Vite 5.1 |
| 包管理 | pnpm 10 | platform: `packageManager: pnpm@10.33.0` |
| 状态 | Pinia 2.1 | platform: Pinia 2.1 |
| 路由 | Vue Router 4.4 | platform: Vue Router 4.4 |
| HTTP | Axios 1.6 | platform: Axios（自定义拦截器） |
| 后端运行时 | Java 17 / Spring Boot 3.4.5 | **与 platform `2.6.0-SNAPSHOT` 完全一致** |

### 尚未决定（T01 待办）

- **UI 组件层**：`EovaUI`（已购买授权，Vue3 + layui-vue，与本工程同代）vs 平台系 `Element Plus`。
  - 倾向 **EovaUI**：授权已在手，且它就是旧系统的组件层，保真度最高。
  - 风险：若集成期要求与 `yudao-ui`（Element Plus）统一视觉，可能需二次调整。
- **`window.urls` 全局入口处置**：保留（契约兼容）还是重构为 API 模块（又一次契约变化）。**必须显式决策，不能默认发生。**
- **EovaUI 挂载方式**：`eovaui.js` 是 UMD bundle，外部依赖 `Vue` / `@layui/layui-vue` / `@eova/eova-tools` / `axios`。需决定走 npm 依赖还是 `public/lib/` 静态托管。

---

## 4. 阶段 2 三条硬口径

来自 `DES-002-R4` §2.5，实现时不得违反：

1. **`#(btn.ui)` 类插值不改 JSON 契约。** 按钮 HTML 由后端元数据生成，分离后保持"后端返回 HTML 片段 + 前端 `v-html` 渲染"。改成 JSON 等于改动 API 契约，会破坏判据锚点。
2. **旧 URL 不加前缀。** `/eova/*`、`/meta/*`、`/widget/*` 原样保留（66 个旧 JS 与 EovaUI 内硬编码了路径）。
3. **判据是行为等价 + API 契约一致 + 视觉对照**，不再是 DOM 一致。新 SFC 渲染的 DOM 不可能与 Enjoy 渲染结果一致。

---

## 5. 目标目录结构

```text
remis-eova-ui/
├── src/
│   ├── api/            # [T01] 接口定义（按后端 /meta、/widget 分模块）
│   ├── assets/         # [T01] 静态资源
│   ├── components/     # [T01] 通用组件
│   ├── config/         # [T01] axios 实例与运行时配置
│   ├── layout/         # [T02] 主框架布局（菜单 / Tab）
│   ├── router/         # [T01] 路由（接管旧 URL）
│   ├── store/          # [T01] Pinia modules
│   ├── styles/         # [T01] 全局样式
│   ├── types/          # [T01] 类型定义
│   ├── utils/          # [T01] 工具函数
│   ├── views/          # [T02] 页面视图（25 个 Enjoy 模板迁入目标）
│   └── legacy/         # [r90 归并] 旧前端 120 个资产的【冻结副本】（迁移期对照物，见其内 README）
├── index.html
├── vite.config.ts
├── tsconfig.json
└── package.json
```

> **为什么 `legacy/` 也在这张图里（第 90 轮归并）**：它**不是**目标架构的一部分，
> 而是阶段 2 的**起点与对照物** —— 120 个旧前端资产按冻结账本
> （`docs/.local/ledger/frontend-assets.jsonl`）的 `targetPath` 逐字节落地在此，
> 用于 ① 可逐字节复核的基线；② 旧渲染结果与新 SFC 的行为对照。
> 早前轮次本文与账本对 `legacy/` 的落点口径不一致（本文没写、账本指向 `src/legacy/**`）；
> 第 90 轮按"**账本为准**（资产单元的 hash 复核以账本为准）+ 本文补齐说明"归并，
> 清理由阶段 2 的迁移清单逐项确认替代完成后统一进行。

## 5.1 迁移范围口径（第 91 轮裁定）

"要迁多少页"此前的两处说法不一致（本文 §5 写 25；按账本 `_view/**/*.html` 直接数是 33～38）。
本轮以**功能判据**重新划一次 —— **"是否需要独立路由/入口"**（不是"文件个数"）：

| 类别 | 判据 | 数量 | 处置 |
|---|---|---|---|
| **入口页** | 自身含完整文档壳（`<html>`/`<body>`） | **24** | **迁入 `src/views`，各占一条路由** |
| 嵌入件 | 无文档壳，被 include | 6 | 随宿主页迁移，不单独建路由 |
| 局部页 | 落在 `_page/` | 3 | 同上 |
| 片段 | 落在 `_block/` | 5 | 同上 |

⇒ **迁移范围 = 24 个入口页**（本文 §5 写的"25"是早前的近似值，口径略异；即以本轮 24 为准）。
逐页清单与各页的后端调用面见 `docs/.local/baseline/evidence/frontend-page-classification.json`
与 `frontend-migration-inventory.json`（本地证据，不随工程发布）。

## 6. 命令

```bash
pnpm install
pnpm dev          # 开发（端口 9090，已代理 /eova、/meta、/widget）
pnpm build        # 类型检查 + 构建
pnpm ts:check     # 仅类型检查
```

---

## 7. 账本关联

本工程承接旧系统前端资产的迁移，账本口径见 `DES-002-R4` §9.4：

| 对象 | 数量 |
|---|---|
| 待迁前端源码资产 | **120**（`view` 93 + `demo` 27） |
| 其中 Enjoy 模板（→ SFC） | 25 个 HTML，104 处 `#(...)` 插值 |
| 其中手写 JS（→ 模块化） | 66 个 |
| vendor 外部依赖 | 1 条 `EXT-EOVAUI`（授权已购买，黑盒依赖） |
| 死文件 | `jquery.min.js`、`json2-min.js`（已有 `.del` 废弃副本，核对后移出账本） |
