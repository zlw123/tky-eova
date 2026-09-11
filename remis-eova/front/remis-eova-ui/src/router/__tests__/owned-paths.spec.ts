/**
 * 路径所有权的漂移判据（第 104 轮）
 *
 * 背景：SPA 按契约纪律 ② 接管**旧原路径**，而 dev 代理把 `/eova` 等前缀代理到后端。
 * 若只加路由、忘了加 `SPA_OWNED_PATHS`，症状是"**构建绿、单测绿、打开页面却是后端响应**"。
 * 本判据把这条漂移变成红的。
 */
import { describe, expect, it } from 'vitest'
import { routePathsOf } from './helpers'
import { SPA_OWNED_PATHS, isSpaOwnedPath, ownedPrefixOf } from '../routes'
import { routes } from '../index'

describe('router · SPA 拥有的路径', () => {
  it('router 里**每一条**路由都被 SPA 所有权规则覆盖（漏登记/漏接线 ⇒ dev 代理会把它送去后端）', () => {
    for (const p of routePathsOf(routes)) {
      // 把动态段换成具体值得到一条真实可访问的 URL，再问**运行时那条规则**（与 vite 代理共用同一个函数）。
      // 例：`/app/:menuCode` ⇒ `/app/sample`（不能换成动作名 —— 那些按规则本来就该归后端）。
      const sample = p.replace(/:([A-Za-z_$][\w$]*)/g, 'sample')
      expect(
        isSpaOwnedPath(sample),
        `路由 ${p}（样例 URL ${sample}）未被 SPA 所有权规则覆盖 ⇒ 打开该页会拿到后端响应`
      ).toBe(true)
    }
  })

  it('SPA_OWNED_PATHS 里**每一条**都有对应路由（多登记 ⇒ 该路径既不给 SPA 也不给后端）', () => {
    const routed = new Set(routePathsOf(routes).map(ownedPrefixOf))
    for (const p of SPA_OWNED_PATHS) {
      expect(routed.has(p), `SPA_OWNED_PATHS 里的 ${p} 没有对应路由`).toBe(true)
    }
  })

  it('ownedPrefixOf：去掉动态段及其后的内容（带参数路由与所有权前缀对齐）', () => {
    expect(ownedPrefixOf('/eova/button/add/:menuCode')).toBe('/eova/button/add')
    expect(ownedPrefixOf('/a/:b/c')).toBe('/a')
    expect(ownedPrefixOf('/user/login')).toBe('/user/login')
    expect(ownedPrefixOf('/')).toBe('/')
  })

  it('带参数的入口页：具体 URL 归 SPA，且**同前缀的后端路径仍归后端**', () => {
    expect(isSpaOwnedPath('/eova/button/add/menu_x')).toBe(true)
    // 反例：不能因为 /eova/button/add 归 SPA 就把整个 /eova/button 吞掉
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
    expect(isSpaOwnedPath('/widget/data')).toBe(false)
  })
})
