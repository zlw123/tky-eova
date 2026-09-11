# src/legacy —— 旧前端 **164 个资产**的冻结副本

本目录是 `DES-002-R4` **阶段 2** 的起点：把旧 EOVA 前端的源码资产**逐字节**搬进独立前端工程。

| 项 | 值 |
|---|---|
| 资产总数 | **164**（四本账本合计，见下表） |
| 来源 | `meta-eova/eova/{view,demo}/src/main/resources/webapp/**` |
| source revision | `1b1d39e7350f7e031b216aad0399fc8cc55dce08` |
| 落点 | 即本目录（按账本冻结的 `targetPath`，**不是**自选路径） |
| 校验 | 落地时 sha256 逐条比对；复核工具 `docs/.local/spikes/verify-frontend-legacy-assets.py`（**四本账本一起复核**） |

| 账本（`docs/.local/ledger/`） | 条数 | 单元号段 | 说明 |
|---|---|---|---|
| `frontend-assets.jsonl` | 128 | `R4-FE-****` | html / js / vue / css（第 94 轮补入 8 个 view 侧 css） |
| `frontend-styles.jsonl` | 16 | `R4-CSS-***` | 样式台账（view 侧 7 + demo 侧 9，第 101 轮补齐 demo 侧落点） |
| `frontend-vendor.jsonl` | 13 | `R4-VD-***` | 第三方库（`isVendor=true`，第 94 轮补 `targetPath`） |
| `frontend-images.jsonl` | 7 | `R4-IMG-***` | **静态图片**（第 101 轮新增账本，见下"覆盖缺口"） |

## 规矩

1. **本目录内容不得手改。** 阶段 1 的口径是"前端一行不动并冻结基线"，
   这些文件就是那份基线的副本；改动会让 `verify-frontend-baseline.py`（以旧树 sha 为准）与
   本目录的复核同时失效。
2. **新代码不要 import 这里。** 改造后的页面放 `src/views`、`src/components`（见工程 README §5）。
   本目录的用途是：① 可逐字节复核的起点；② 迁移期的对照物（旧渲染结果与新 SFC 的行为对照）。
   例外：样式文件按"**只引用不修改**"引入（如 `Login.vue` 引 `login.css`，见第 94 轮）。
3. **删除时机**：由阶段 2 的迁移清单逐项确认替代完成后统一删除，不在本轮清理。
4. **新增账本必须同时登记到两个复核脚本**（`verify-frontend-legacy-assets.py` 的 `LEDGERS`、
   `verify-frontend-baseline.py` 的 `FRONT_LEDGERS`），否则新落地的资产会成为"没人复核"的盲区。

## 覆盖缺口（两类，均由判据当场抓出，不是"印象"）

### 缺口 A：**类型过滤把整类资产挡在账本之外**（第 93 / 101 轮）

账本的 `assetType` 白名单原先只有 `{html, js, vue}`，于是：
- **css**：view 侧 8 个 `.css` 全未收录（第 93 轮因"Login.vue 引 login.css 编译失败"暴露）；
- **png 等二进制**：`webapp/**` 下 7 个 `.png` 全未收录（第 101 轮因"Home.vue 引用 meta.png
  被 Vite 当资产解析而失败"暴露）。

第 101 轮已把 7 个 png 落地并单开账本 `frontend-images.jsonl`。其中 **6 个有实证引用**：

| 资产 | 引用点 |
|---|---|
| `_view/index/meta.png` | `_view/index/index.html:45` `#(app_logo??'/eova/_view/index/meta.png')` |
| `ui/images/tab_ding.png` | `ui/css/index.css:232` `background-image: url(...)` |
| `menu/add/img/{default,table,tree,tree_table}.png` | `_view/menu/add/app.html:62` 动态拼 `/eova/_view/menu/add/img/${selectTemplate?.img}`；`img` 值来自 `eova_template.img`（`demo/sql/eova_meta.sql:3324-3326`） |

`ui/images/file.png` **未取证到引用**（同批落地，随批复核）。另 5 个非运行时文件**未迁移**并登记：
`_view/Note.txt`、`_view/TODO.txt`（开发笔记）、`_view/js/jquery.js.del`、`jquery.min.map.del`
（`.del` 后缀 = 旧栈已禁用）、`_view/menu/add/img/package.json`（图标字体描述，非运行时资产）。

### 缺口 B：**账本有行、落点却没落地**（第 101 轮）

`frontend-styles.jsonl` 共 16 行，此前只落地了 view 侧 7 行，**demo 侧 9 行从未落地**
（`_component/{EovaProps,EovaTags}.css`、`_component/city/city.css`、`_eova/assets/eova.ui.ext.css`、
`_eova/theme/eova.theme.{dark,default,orange,purple}.css`、`ui/css/SwordComing.css`）。

为什么此前没被发现：`verify-frontend-legacy-assets.py` 当时**只读 `frontend-assets.jsonl`**，
而 baseline 脚本只用账本 sha 去比对**旧树**（旧树本来就对）—— "落点是否落地"这一层**没有任何判据**。
第 101 轮把复核集合扩到四本账本后，9 行缺失立刻全部报出并已逐字节补齐。

## 与工程 README §5 的差异（已记录，第 90 轮已归并）

工程 README 的目标目录结构里没有 `legacy/`，而冻结账本的 `targetPath` 指向 `src/legacy/**`。
两者都是早前轮次定下的口径 —— 第 90 轮按**账本为准**落地并回填 README §5，差异已归并。
