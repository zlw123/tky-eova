/**
 * 路径所有权的漂移判据（第 104 轮）
 *
 * 背景：SPA 按契约纪律 ② 接管**旧原路径**，而 dev 代理把 `/eova` 等前缀代理到后端。
 * 若只加路由、忘了加 `SPA_OWNED_PATHS`，症状是"**构建绿、单测绿、打开页面却是后端响应**"。
 * 本判据把这条漂移变成红的。
 */
import { describe, expect, it } from 'vitest'
import { routePathsOf } from './helpers'
import { BACKEND_ACTION_PATHS, SPA_OWNED_PATHS, isSpaOwnedPath, ownedPrefixOf } from '../routes'
import { routes } from '../index'
import { BACKEND_ROUTE_PREFIXES } from '../../compat/backend-routes'

/**
 * ★ r306（U2）：**与页面同前缀的后端动作白名单**（`BACKEND_ACTION_PATHS`）。
 *
 * 背景：所有权判定按**路径前缀、不分 HTTP 方法**。`/auth` 前缀下既有页面（`/auth/<rid>`，归 SPA）
 * 又有动作（`/auth/data` 等，归后端）⇒ 白名单是"页面能被 SPA 供给、动作又不被吞"的唯一办法。
 *
 * 这些动作的来源是**后端 `AuthController` 的公开动作**（判据从 Java 源码解析核对，见 dev-proxy 判据）。
 */
const EXPECTED_BACKEND_ACTION_PATHS: readonly string[] = [
  '/auth/data',
  '/auth/doAuth',
  '/auth/update',
  '/auth/button'
]

describe('router · SPA 拥有的路径', () => {
  it('router 里**每一条**路由都被 SPA 所有权规则覆盖（漏登记/漏接线 ⇒ dev 代理会把它送去后端）', () => {
    for (const p of routePathsOf(routes)) {
      // ★ r305（U1）：**兜底路由**（catch-all）不是一条真实路径，它接的是"所有未匹配"的情况
      //   （旧栈由 `/` 兜底：实测 `/zzz_unknown`、`/su` 都给首页）⇒ 不参与所有权判定。
      if (p.includes('*')) {
        continue
      }
      // 把动态段换成具体值得到一条真实可访问的 URL，再问**运行时那条规则**（与 vite 代理共用同一个函数）。
      // 例：`/app/:menuCode` ⇒ `/app/sample`（不能换成动作名 —— 那些按规则本来就该归后端）。
      const sample = p.replace(/:([A-Za-z_$][\w$]*)/g, 'sample')
      expect(
        isSpaOwnedPath(sample),
        `路由 ${p}（样例 URL ${sample}）未被 SPA 所有权规则覆盖 ⇒ 打开该页会拿到后端响应`
      ).toBe(true)
    }
  })

  it('★ r306：`/auth` 页面归 SPA，而同前缀**动作**仍归后端（两条都不能少）', () => {
    // 页面：旧原路径 `/auth/<rid>`（实测旧栈 `/auth`、`/auth/1248` = 200「功能权限分配」；
    //   `/eova/role/auth/1` = 404 ⇒ SPA 此前把**模板路径**当 URL，已纠正）
    expect(isSpaOwnedPath('/auth/1248')).toBe(true)
    expect(SPA_OWNED_PATHS).toContain('/auth')
    expect(SPA_OWNED_PATHS).not.toContain('/eova/role/auth')
    // 动作：一个都不能被 SPA 吞（否则页面自己的 `POST /auth/data` 会拿到 HTML）
    for (const a of EXPECTED_BACKEND_ACTION_PATHS) {
      expect(isSpaOwnedPath(a), `${a} 是后端动作，不得归 SPA`).toBe(false)
      expect(isSpaOwnedPath(a + '/x'), `${a}/x 同样归后端`).toBe(false)
    }
    expect(BACKEND_ACTION_PATHS).toEqual(EXPECTED_BACKEND_ACTION_PATHS)
    // 反空断言：白名单里每一条都真的在 `/auth` 之下（写错前缀的条目会让判据静默失效）
    expect(EXPECTED_BACKEND_ACTION_PATHS.every((a) => a.startsWith('/auth/'))).toBe(true)
  })

  it('★ r307（U3）：`/main`、`/ip` **不得**归 SPA 所有权（它们是后端页面/端点，且必须被代理）', () => {
    // 取证：SPA 首页把页签内容渲染成 `<iframe :src="m.link">`，初始页签 link 就是 `/main`
    //   （`utils/tab.ts:38`；旧首页 `eova/_view/index/index.js:21` 同款）
    //   ⇒ `/main` 必须是**后端渲染的主题页**；归 SPA 会让 iframe 里装 SPA 自己（U1 的实测回归）。
    // `/ip` 旧栈是 `renderText(getRealIp)` 的**纯文本端点**（不是页面）。
    for (const p of ['/main', '/ip']) {
      expect(isSpaOwnedPath(p), `${p} 不得归 SPA`).toBe(false)
      expect(SPA_OWNED_PATHS, `SPA_OWNED_PATHS 不得含 ${p}`).not.toContain(p)
      // 反向：它们必须仍**被代理到后端**，否则 dev 下 iframe/文本端点会拿到 SPA 的 index.html
      expect(
        BACKEND_ROUTE_PREFIXES.includes(p),
        `${p} 必须在后端代理前缀里（否则 dev 下拿不到后端页面）`
      ).toBe(true)
    }
    // 旧栈无此页的 `/theme` 与旧栈 500 的 `/sso`：两侧都不再认领
    expect(isSpaOwnedPath('/theme')).toBe(false)
    expect(isSpaOwnedPath('/sso')).toBe(false)
    // 对照：仍在 SPA 侧的 demo 族 URL 必须保持归 SPA（防"一刀切全撤"）
    expect(isSpaOwnedPath('/widget')).toBe(true)
    expect(isSpaOwnedPath('/test/sse')).toBe(true)
    expect(isSpaOwnedPath('/test')).toBe(true)
  })

  it('SPA_OWNED_PATHS 里**每一条**都有对应路由（多登记 ⇒ 该路径既不给 SPA 也不给后端）', () => {
    const routed = new Set(routePathsOf(routes).map(ownedPrefixOf))
    for (const p of SPA_OWNED_PATHS) {
      expect(routed.has(p), `SPA_OWNED_PATHS 里的 ${p} 没有对应路由`).toBe(true)
    }
  })

  it('ownedPrefixOf：去掉动态段及其后的内容（带参数路由与所有权前缀对齐）', () => {
    expect(ownedPrefixOf('/button/add/:menuCode')).toBe('/button/add')
    expect(ownedPrefixOf('/a/:b/c')).toBe('/a')
    expect(ownedPrefixOf('/user/login')).toBe('/user/login')
    expect(ownedPrefixOf('/')).toBe('/')
  })

  it('带参数的入口页：具体 URL 归 SPA，且**同前缀的后端路径仍归后端**', () => {
    expect(isSpaOwnedPath('/button/add/menu_x')).toBe(true)
    // 反例：不能因为 /button/add 归 SPA 就把整个 /button 吞掉
    expect(isSpaOwnedPath('/eova/button/quick/menu_x')).toBe(false)
    expect(isSpaOwnedPath('/button/doAdd')).toBe(false)
  })

  it('本页的口径：旧 URL 不加前缀（/eova/admin/su、/user/login、/user/password 逐字一致）', () => {
    expect(SPA_OWNED_PATHS).toContain('/eova/admin/su')
    expect(SPA_OWNED_PATHS).toContain('/user/login')
    expect(SPA_OWNED_PATHS).toContain('/user/password')
  })
})

describe('router · isSpaOwnedPath（dev 代理放行规则）', () => {
  it('精确命中 SPA 路径（含带查询串）', () => {
    expect(isSpaOwnedPath('/eova/admin/su')).toBe(true)
    expect(isSpaOwnedPath('/eova/admin/su?object=x')).toBe(true)
    expect(isSpaOwnedPath('/user/password')).toBe(true)
    expect(isSpaOwnedPath('/')).toBe(true)
  })

  it('子路径也归 SPA（`path + "/"` 前缀）', () => {
    expect(isSpaOwnedPath('/eova/admin/su/detail')).toBe(true)
  })

  it('★ r305 契约：动作页入口一律走**旧栈原路径**，加错前缀的路径不得再被 SPA 认领', () => {
    // 旧栈真浏览器/HTTP 实测（未登录时 302=存在，404=不存在）：
    //   /menu/add 302 · /eova/menu/add 404 ｜ /menu/auth/2 302 · /eova/menu/auth/2 404
    //   /button/add/meta_product 302 · /eova/button/add/... 404 ｜ /meta/imports 302 · /meta/import 404
    // 口径②是"接管旧 URL、不得加前缀" ⇒ SPA 必须认领**左侧**那组。
    expect(isSpaOwnedPath('/menu/toAdd')).toBe(true)
    // ⚠️ 反面：`/menu/add` 是**提交动作**（旧 MenuController#add() 带 @Before(Tx)）⇒ 必须留给后端；
    //    把它当成页面路径会让保存的 POST 也被 SPA 吞掉（实测纠正：旧页打开用的是 menu/toAdd?parent_id=）
    expect(isSpaOwnedPath('/menu/add')).toBe(false)
    expect(isSpaOwnedPath('/menu/auth/1248')).toBe(true)
    expect(isSpaOwnedPath('/button/add/meta_product')).toBe(true)
    expect(isSpaOwnedPath('/meta/imports')).toBe(true)

    // 反例（加了前缀的那组 = 旧栈 404）不得被认领，否则是把"我们的错路径"当成契约
    expect(isSpaOwnedPath('/eova/menu/add')).toBe(false)
    expect(isSpaOwnedPath('/eova/menu/auth/1248')).toBe(false)
    expect(isSpaOwnedPath('/eova/button/add/meta_product')).toBe(false)
    expect(isSpaOwnedPath('/meta/import')).toBe(false)

    // 同前缀的**后端**动作仍归后端（判据是段边界，不是模糊前缀）
    expect(isSpaOwnedPath('/menu/authData')).toBe(false)
    expect(isSpaOwnedPath('/menu/icon')).toBe(false)
    expect(isSpaOwnedPath('/meta/reorder_data')).toBe(false)
    // 反向：/meta/reorder 归 SPA（页面），/meta/reorder_data 归后端（动作）—— 二者只差一个下划线
    expect(isSpaOwnedPath('/meta/reorder')).toBe(true)
  })

  it('★ 不做前缀模糊匹配：同前缀的**后端**路径仍归后端', () => {
    // 这两个是后端渲染页（Home 里 window.open 打开），不能因为 /eova/admin/su 归 SPA 就被吞掉
    expect(isSpaOwnedPath('/eova/admin/showUserData')).toBe(false)
    expect(isSpaOwnedPath('/eova/admin/showRuntimeConfig')).toBe(false)
    expect(isSpaOwnedPath('/eova/lib/eova/eovaui.js')).toBe(false)
    expect(isSpaOwnedPath('/eova/ui/css/common.css')).toBe(false)
  })

  it('`/` 只精确匹配（否则一切路径都会被当成 SPA 而断掉代理）', () => {
    expect(isSpaOwnedPath('/')).toBe(true)
    expect(isSpaOwnedPath('/eova/meta/field')).toBe(false)
    expect(isSpaOwnedPath('/meta/table/x')).toBe(false)
    // ★ `/widget` 是**整体归 SPA** 的（第 123 轮迁了组件演示页）：后端在 `/widget` 之下只有那一个页面
    //   （`WidgetController` 在 `/api/widget`，不在 `/widget/**`）⇒ 子路径归 SPA 不吞任何后端页面。
    expect(isSpaOwnedPath('/widget')).toBe(true)
    expect(isSpaOwnedPath('/widget/data')).toBe(true)
    // 前缀不同则仍归后端（前缀规则要求 `p + '/'`，不做模糊匹配）
    expect(isSpaOwnedPath('/widgetx/data')).toBe(false)
  })
})
