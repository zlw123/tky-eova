/**
 * `/app` 路径归属判定（第 117 轮）
 *
 * ## 为什么需要它
 *
 * 旧栈里 `/app` 是**一个控制器**（旧 `EovaConfig.java:269` `me.add("/app", AppController.class)`，
 * 已 port 到 `cn.eova.config.EovaConfig:289`）。同一前缀下挂着一堆**形态完全不同**的 URL：
 *
 * | URL | 旧栈由谁处理 | 前后分离后应由谁处理 |
 * |---|---|---|
 * | `/app/<menu.code>` | `AppController#index()`（`Menu.getUrl()` 对 `template` 非空的菜单返回它） | **SPA 路由页** |
 * | `/app/add\|update\|detail/<object_code>` | `AppController#add()/update()/detail()` 渲染 `_view/template/form/{add,update,detail}/index.html` | **仍由后端渲染**（`me.layer.open` 以 iframe 弹层打开） |
 * | `/app/errors/<status>`、`/app/diy/<cmd>` | 后端动作（读 `get(0)`） | 后端 |
 * | `/app/live`、`/app/status` | 后端动作（运维心跳/实例状态） | 后端 |
 *
 * ★ 两侧**前缀逐字相同**，只靠"段数 + 动作名"区分。这条规则不写下来就会静默出错：
 *   - 把 `/app/update/eova_menu_code` 判成 SPA 页 ⇒ 冻结脚本 `eova.template.js:50` 的
 *     `me.layer.open('菜单配置', '/app/update/eova_menu_code?id=' + …)` 会弹出一个 SPA 外壳，
 *     而不是后端渲染的元数据表单页（**构建、单测、页面加载全都不报错**）；
 *   - 把 `/app/<menu.code>` 判成后端 ⇒ 菜单页在开发环境被代理回后端 HTML（同上，静默）。
 *   两个方向都不会让任何"编译/构建"判据变红 ⇒ 必须有**独立判据**钉住它。
 *
 * ## 规则来源（是取证，不是发明）
 *
 * 1. **`/app/<code>` 的形态**：`Menu.getUrl()`（旧 `cn/eova/model/Menu.java:91`，新 `cn.eova.model.Menu:112`）
 *    对 `template` 非空的菜单返回 `String.format("/app/%s", code)`；
 * 2. **动作名集合**：`cn.eova.core.AppController` + `cn.eova.core.IndexController` 的**公开无参方法**
 *    （JFinal 以**方法名**注册 actionKey；旧 `EovaConfig.java:271` 还开了 `setMappingSuperClass(true)`，
 *    故继承来的 `index`/`code`/`diy` 同样是 `/app` 之下的动作）；
 * 3. **段数**：该动作是否读 `get(0)` —— `add`/`update`/`detail`/`errors`/`diy` 都读（`get(0)` 是那段参数），
 *    故它们的完整 URL 是 2 段；`live`/`status` 不读，是 1 段。
 *
 * ## 两处**必须说清楚的判断**（不得当成"显然"）
 *
 * - **`/app/index`（1 段、动作名恰好是 `index`）判为 `backend`，理由是"旧栈歧义"**：
 *   `/app/<code>` 能命中 `index()` 这一点，**要求** JFinal 的动作表里存在键 `/app` 本身
 *   （`ActionMapping#getAction` 字节码：精确查不到时取 `lastIndexOf('/')` 前缀再查一次，
 *   命中则把后缀塞进 `urlPara[0]` —— 这正是 `index()` 里 `get(0)` 拿到菜单编码的原因）。
 *   但 `/app/index` 是否**同时**也是一个键，取决于 JFinal 给 `index` 注册的是 `/app` 还是
 *   `/app` + `/app/index` 两者 —— 旧栈没有跑起来（无 DB/容器），这一条**未取证**。
 *   故本模块**不猜**：判为 `backend`（= 保留旧路径），并把 `reason` 标为 `ambiguous-action-name`。
 * - **`/app/OK`（`BaseController.OK()`）不在表内**：它同样是"公开无参方法"，
 *   `setMappingSuperClass(true)` 下 JFinal 也会注册。但菜单 code 的校验规则
 *   `eovacode` 是 `/^\w{3,50}$/`（证据 `validate-rules-contract.json`）⇒ **2 个字符的 `OK`
 *   不可能是合法菜单 code**，冲突不可能发生。这是"可证明无害"，不是"忘了"。
 *
 * ## 与旧栈的关系
 *
 * 旧栈的这条优先级由 JFinal 的动作表实现（动作名优先于菜单 code）；本模块用**显式动作表**
 * 表达同一优先级 —— 属**适配**（新旧底座必需），不是重新设计：可见行为一致，
 * 且把"未取证的歧义"显式标出来而不是猜一个。
 *
 * ## 接线状态（第 117 轮）
 *
 * 本模块是**契约**：`/app/:menuCode` 路由与 dev 代理的 `/app` 前缀接线
 * 在菜单模版页（`table`/`tree`/`tree_table`）落地时一并做（见 `docs/DES-002-R4-…` §117）。
 * 在那之前 `/app/**` **仍应整体代理给后端**（否则会把今天还能用的后端菜单页弄坏），
 * 故 `router/routes.ts` 现在**不**引用本模块 —— 这条"不得提前接管"由
 * `__tests__/app-routes.spec.ts` 的接线判据钉住。
 */

/** 1 段路径（`/app/<seg>`）的归属 */
export type OneSegmentOwner = 'backend' | 'spa' | 'ambiguous'

/** `/app` 控制器上的一个动作 */
export interface AppAction {
  /** 动作名（JFinal actionKey 的最后一段） */
  name: string
  /** 该动作的完整 URL 形态（文档 + 判据用） */
  url: string
  /** ★ 动作名与 `/app/<菜单code>` 在 1 段路径上是否冲突（`index` 是未取证的那一个） */
  oneSegment: OneSegmentOwner
  /** 溯源：旧源码 `文件:行` */
  source: string
}

/**
 * `/app` 控制器上的动作表（公开无参方法 ⇒ JFinal 注册的 actionKey）。
 *
 * 判据 `__tests__/app-routes.spec.ts` 会**解析 ported 后端源码**核对本表：
 * `cn/eova/core/AppController.java` + `cn/eova/core/IndexController.java` 里每个
 * `public void xxx()`（无参）都必须在本表内、且名字集合双向一致。
 */
export const APP_ACTIONS: readonly AppAction[] = [
  {
    name: 'index',
    url: '/app/<menu.code>',
    oneSegment: 'ambiguous',
    source: 'core/src/main/java/cn/eova/core/AppController.java:70（+ IndexController.java:21 被覆盖；URL 形态见 model/Menu.java:91）'
  },
  {
    name: 'code',
    url: '/app/code',
    oneSegment: 'backend',
    source: 'core/src/main/java/cn/eova/core/IndexController.java:44'
  },
  {
    name: 'diy',
    url: '/app/diy/<cmd>',
    oneSegment: 'backend',
    source: 'core/src/main/java/cn/eova/core/IndexController.java:61（读 get(0)）'
  },
  {
    name: 'live',
    url: '/app/live',
    oneSegment: 'backend',
    source: 'core/src/main/java/cn/eova/core/AppController.java:32'
  },
  {
    name: 'status',
    url: '/app/status',
    oneSegment: 'backend',
    source: 'core/src/main/java/cn/eova/core/AppController.java:38'
  },
  {
    name: 'errors',
    url: '/app/errors/<status>',
    oneSegment: 'backend',
    source: 'core/src/main/java/cn/eova/core/AppController.java:98（读 getInt(0)）'
  },
  {
    name: 'add',
    url: '/app/add/<object_code>',
    oneSegment: 'backend',
    source: 'core/src/main/java/cn/eova/core/AppController.java:109（读 get(0)）'
  },
  {
    name: 'update',
    url: '/app/update/<object_code>',
    oneSegment: 'backend',
    source: 'core/src/main/java/cn/eova/core/AppController.java:135（读 get(0)）'
  },
  {
    name: 'detail',
    url: '/app/detail/<object_code>',
    oneSegment: 'backend',
    source: 'core/src/main/java/cn/eova/core/AppController.java:164（读 get(0)）'
  }
]

/** `/app` 之下的路径归属 */
export type AppUrlKind = 'menu-page' | 'backend' | 'foreign'

/** 归属判定的结果（带 `reason`：分支必须可观测，否则判据只能测到"结果"测不到"走哪条路"） */
export interface ResolvedAppUrl {
  /** 归属 */
  kind: AppUrlKind
  /** 命中的动作名（`backend` 且首段是已知动作时） */
  action?: string
  /** 菜单编码（`menu-page` 时） */
  menuCode?: string
  /** 判定分支（判据断言它） */
  reason:
    | 'not-app'
    | 'app-root'
    | 'empty-segment'
    | 'menu-page'
    | 'backend-action'
    | 'ambiguous-action-name'
    | 'backend-sub-path'
}

/** 去掉查询串与 hash，得到纯路径 */
function pathOf(url: string): string {
  return url.split('?')[0].split('#')[0]
}

/** 按名字查动作 */
function findAction(name: string): AppAction | undefined {
  return APP_ACTIONS.find((a) => a.name === name)
}

/**
 * 判定一个 `/app` 路径该由谁处理。
 *
 * 规则（顺序即优先级）：
 * ① 不以 `/app` 开头 ⇒ `foreign`（与本模块无关）；
 * ② 恰好是 `/app` ⇒ `backend`（`app-root`）；
 * ③ `/app/` 或出现空段（`//`）⇒ `backend`（`empty-segment`；菜单 code 是 `\w{3,50}`，不含 `/`）；
 * ④ 只有 1 段 ⇒ 段名是已知动作且该动作**独占** 1 段 ⇒ `backend`；`ambiguous` ⇒ `backend`（见文件头）；
 *    否则 ⇒ `menu-page`（`menuCode` = 该段）；
 * ⑤ 2 段及以上 ⇒ `backend`（`/app/add/<object_code>`、`/app/diy/<cmd>` …）。
 *
 * @param url 请求 URL（可带查询串/hash）
 * @returns 归属 + 分支原因
 */
export function resolveAppUrl(url: string): ResolvedAppUrl {
  const path = pathOf(url)
  if (path !== '/app' && !path.startsWith('/app/')) {
    return { kind: 'foreign', reason: 'not-app' }
  }
  if (path === '/app') {
    return { kind: 'backend', reason: 'app-root' }
  }
  const segs = path.slice('/app/'.length).split('/')
  if (segs.some((s) => s === '')) {
    return { kind: 'backend', reason: 'empty-segment' }
  }
  if (segs.length === 1) {
    const action = findAction(segs[0])
    if (action && action.oneSegment === 'backend') {
      return { kind: 'backend', action: action.name, reason: 'backend-action' }
    }
    if (action && action.oneSegment === 'ambiguous') {
      return { kind: 'backend', action: action.name, reason: 'ambiguous-action-name' }
    }
    return { kind: 'menu-page', menuCode: segs[0], reason: 'menu-page' }
  }
  return { kind: 'backend', action: findAction(segs[0])?.name, reason: 'backend-sub-path' }
}

/**
 * 判断某路径是否应由 SPA 渲染（即"菜单模版页"）。
 *
 * @param url 请求 URL（可带查询串/hash）
 * @returns 是否由 SPA 渲染
 */
export function isSpaOwnedAppPage(url: string): boolean {
  return resolveAppUrl(url).kind === 'menu-page'
}
