/**
 * SPA 路由表（**单一事实来源**）
 *
 * ★ 为什么把路径单独抽出来
 *
 * 阶段 2 的口径是"旧 URL 不加前缀"（契约纪律 ②）⇒ SPA 必须**接管旧栈的原路径**
 * （`/user/login`、`/user/password`、`/eova/admin/su` …）。但开发期 `vite.config.ts` 里
 * `'/eova'`、`'/meta'`、`'/widget'` 是**代理到后端**的 —— 于是同一批路径同时"归 SPA"和"归后端"。
 *
 * 如果只在 router 里加路由、忘了在代理里放行，症状是：**开发环境能构建、能跑测试，
 * 但打开该页面却返回后端的 404/HTML** —— 属"配置漂移"，且不会让任何单测变红。
 *
 * 故此处把"SPA 拥有的路径"抽成**一个数组**，router 与 vite 代理**共用它**，
 * 并有判据（`src/router/__tests__/owned-paths.spec.ts`）断言"router 里每条路由都在数组内"，
 * 让漂移在测试里立刻暴露。
 *
 * 命名注意：这里只声明**路径所有权**，不声明组件（组件在 `router/index.ts` 里懒/直引）。
 *
 * ★ 第 118 轮：`/app/**` 的归属**不在这张清单里**（它不能用"前缀"规则表达，见 `isSpaOwnedPath`），
 *   由 `compat/app-routes.ts` 的动作表判定；清单与它的接线一致性由
 *   `__tests__/owned-paths.spec.ts` 与 `compat/__tests__/app-routes.spec.ts` 共同钉住。
 */
// ★ 用**相对**路径：本文件被 `vite.config.ts` 直接 import，而配置文件加载期没有 `@` 别名
import { isSpaOwnedAppPage } from '../compat/app-routes'

/** SPA 拥有的路径（router 与 dev 代理共用；新增路由必须同时出现在这里） */
export const SPA_OWNED_PATHS: readonly string[] = [
  // ★ r274 口径④：这些 demo 工程 URL 归 SPA 所有
  '/main',
  '/theme',
  '/test',
  '/ip',
  '/sso',
  '/',
  // ★ r305：旧栈 `/su` 与 `/` 同为落地页（实测两者都跳 /main）⇒ SPA 接管，避免被代理去后端
  '/su',
  '/placeholder',
  '/user/login',
  '/user/password',
  '/eova/admin/su',
  // 带参数的入口页写**所有权前缀**（`/button/add/<menuCode>` 的 `<menuCode>` 是路径段）—— 见 ownedPrefixOf()
  // ★ r305 纠正（旧栈实测）：这四个路径**此前写成 `/eova/...` 或 `/meta/import`，都是加错前缀/写错段** ——
  //   旧栈实测：`/menu/add` 302（存在）而 `/eova/menu/add` **404**；`/menu/auth/2` 302 而 `/eova/menu/auth/2` **404**；
  //   `/button/add/meta_product` 302 而 `/eova/button/add/...` **404**；`/meta/imports` 302 而 `/meta/import` **404**。
  //   口径②是"接管旧 URL、不得加前缀" ⇒ 必须改成旧原路径（`/eova/admin/su` 那种确实是 `/eova` 开头的除外）。
  '/button/add',
  // ★ `/meta` 也是 dev 代理前缀之一 ⇒ 不登记就会被代理去后端（见本文件顶部说明）
  '/meta/reorder',
  '/menu/auth',
  '/meta/field',
  // ⚠️ 是 `/menu/toAdd`（**页面**），不是 `/menu/add`（**提交动作**，旧 `MenuController#add()` 带 @Before(Tx)）
  //    —— 旧页 js 实测：打开用 `menu/toAdd?parent_id=`，`'/menu/add'` 是提交 URL。
  //    写错会把保存动作的 POST 也吞进 SPA（dev 代理按所有者放行，不分方法）。
  '/menu/toAdd',
  '/meta/edit',
  '/meta/imports',
  // ★ r306（U2 取证）：**移除了** `/eova/role/auth` —— 旧页面 URL 是 `/auth/<rid>`（实测 `/auth`、
  //   `/auth/1248` = 200「功能权限分配」；`/eova/role/auth/1` = **404**），旧条目是照着模板路径抄的。
  //   而 `/auth` 本身**不能**登记所有权：同前缀下还有动作 `/auth/data`、`/auth/doAuth`、`/auth/update`，
  //   而 dev 代理按**前缀**放行、不分方法 ⇒ 登记会把动作一起吞掉。
  //   页面 URL 的壳由后端供给（`AuthController#index()` 已退役为壳）。
  // EovaUI 组件演示页：旧路径 /widget（demo `AppController#widget()` 渲染 `_view/widget/index.html`）
  '/widget',
  // SSE 演示页：旧路径 /test/sse（`TestController#sse()` 渲染 `_view/sse/index.html`）
  // ★ `/test` 是后端前缀（已配代理）⇒ 不登记就会被代理去后端（拿到后端 HTML）
  '/test/sse',
  // ★ r306（U2）：功能权限分配页。旧原路径是 `/auth/<rid>`（实测旧栈 `/auth`、`/auth/1248` = 200
  //   「功能权限分配」；`/eova/role/auth/1` = **404** ⇒ SPA 此前那条路由是把**模板路径**当 URL）。
  //   页面入口已在后端退役为壳（`AuthController#index()`）⇒ dev 归 SPA、生产由后端给壳，两边一致。
  //   ⚠️ 同前缀的**动作**（`/auth/data` 等）由 `BACKEND_ACTION_PATHS` 显式留给后端 —— 见该常量的说明。
  '/auth'
]

/**
 * ★ r306（U2）：**与页面同前缀的后端动作**白名单（dev 代理层面必须继续代理给后端）。
 *
 * 为什么需要它：所有权判定按**路径前缀**、不分 HTTP 方法。`/auth` 前缀下既有页面（`/auth/<rid>`）
 * 又有动作（下面这四条），若只做前缀判定，认领页面就会把动作一起喂给 SPA。
 *
 * 为什么不能反过来（不认领页面）：dev 下 `/auth/<rid>` 的**文档请求**会被代理到后端，
 * 浏览器拿到的是后端供给的**生产 dist 产物** —— 实测症状是"dev 里该页渲染成上一个版本的 bundle"
 * （真浏览器取证时红过一次：SPA 只渲染 168 字符，因为旧 bundle 里还没有 `/auth/:rid` 这条路由）。
 *
 * 维护口径：这里登记 `AuthController` 的**全部公开动作**（`index` 是页面、不属于此列）。
 * `/auth/button` 当前在 SPA 与冻结旧资产里都**没有调用点**，但仍登记 —— 动作的可达性不该取决于
 * "现在有没有人调"，少登记一条就是给它留了一个"dev 静默失效"的坑。
 *
 * @see isSpaOwnedPath
 */
export const BACKEND_ACTION_PATHS: readonly string[] = [
  '/auth/data',
  '/auth/doAuth',
  '/auth/update',
  '/auth/button'
]

/**
 * 把路由 path 归一化为**所有权前缀**：去掉动态段（`:xxx`）与其后的内容。
 *
 * ★ r305：`SPA_OWNED_PATHS` 里的入口页路径一律是**旧栈原路径**（`/menu/add`、`/button/add`、
 * `/meta/imports`…），不是 `/eova/...` 前缀版本 —— 后者在旧栈是 404（实测）。
 *
 * 例：`/button/add/:menuCode` → `/button/add`（与 `SPA_OWNED_PATHS` 里的写法一致）。
 *
 * @param path 路由 path
 * @returns 所有权前缀
 */
export function ownedPrefixOf(path: string): string {
  const segs = path.split('/').filter((s) => s !== '')
  const keep: string[] = []
  for (const s of segs) {
    if (s.startsWith(':')) {
      break
    }
    keep.push(s)
  }
  return '/' + keep.join('/')
}

/**
 * 判断某请求路径是否应由 SPA 处理（而非代理给后端）。
 *
 * 规则：**精确匹配**或**以 `path + '/'` 开头**（覆盖子路径）。
 * 不做前缀模糊匹配 —— `/eova/admin/su` 归 SPA 不代表 `/eova/admin/showUserData` 也归 SPA
 * （后者是后端渲染页，仍应走代理）。
 *
 * ★ 第 118 轮增补：`/app/**` **不在** `SPA_OWNED_PATHS` 里，也不能用上面的"前缀"规则 ——
 *   同一个 `/app` 前缀下既有 SPA 菜单模版页（`/app/<menu.code>`，1 段），
 *   也有**后端渲染页**（`/app/add|update|detail/<object_code>`，2 段；由冻结脚本
 *   `eova.template.js:31/50` 以 `me.layer.open` 弹 iframe）。
 *   它们靠"段数 + 动作名"区分，规则与取证见 `src/compat/app-routes.ts`。
 *   ⇒ 归属判定委托给 `isSpaOwnedAppPage`，**共用同一份动作表**（单一事实来源）。
 *
 * @param url 请求 URL（可带查询串）
 * @returns 是否由 SPA 处理
 */
export function isSpaOwnedPath(url: string): boolean {
  if (isSpaOwnedAppPage(url)) {
    return true
  }
  const path = url.split('?')[0].split('#')[0]
  // ★ r306（U2）：与页面**同前缀的后端动作**优先判给后端（在所有权匹配之前判）。
  //   背景：`/auth/<rid>` 是页面（归 SPA），而 `/auth/data`、`/auth/doAuth`、`/auth/update`
  //   是同前缀的**动作**；dev 代理的所有权判定按前缀、**不分方法**（见 `/menu/add` 的教训）
  //   ⇒ 若不做这条例外，认领 `/auth` 会把这三个 POST 一起放给 SPA ⇒ 页面自己的请求拿到 HTML ⇒ 功能直接坏。
  //   为什么不干脆不认领 `/auth`：那样 dev 下 `/auth/<rid>` 的**文档请求**会被代理到后端，
  //   拿到后端供给的**生产 dist 产物**（实测症状：dev 里该页渲染成上一个版本的 bundle ⇒ 静默串版本）。
  if (BACKEND_ACTION_PATHS.some((p) => path === p || path.startsWith(p + '/'))) {
    return false
  }
  return SPA_OWNED_PATHS.some((p) => path === p || (p !== '/' && path.startsWith(p + '/')))
}
