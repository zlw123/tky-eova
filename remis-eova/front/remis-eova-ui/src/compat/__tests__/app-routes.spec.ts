// @vitest-environment node
/**
 * `/app` 归属判定的判据（第 117 轮）
 *
 * 为什么这个文件用 **node 环境**：它要 import `vite.config.ts`（接线 canary），
 * 与 `router/__tests__/proxy-bypass.spec.ts` 同因（jsdom 下 vite 内部会触发 `TextEncoder` 不变式）。
 * 本文件全部是纯函数/文本判据，不需要 DOM。
 *
 * 钉的契约（每条都对应一种"构建绿、单测绿、页面静默出错"）：
 *  ① **动作表 ↔ ported 后端源码双向一致**：新增/改名一个 `/app` 动作而不更新本表 ⇒ 红；
 *  ② `resolveAppUrl` 的分支矩阵（含"未取证⇒保留后端"的 `ambiguous-action-name`）；
 *  ③ **冻结脚本里的 `/app/**` 字面量一律是 `backend`**：`eova.template.js` 用它们开弹层，
 *     一旦被判成 SPA 页，弹层里会变成 SPA 外壳（冻结资产不可改，故只能钉住判据）；
 *  ④ **接线 canary**：`/app` 前缀不得在"没有 SPA 路由接管"的情况下被加进 dev 代理
 *     （半接线 = 今天还能用的后端菜单页被吞掉）。
 */
import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import * as viteConfigModule from '../../../vite.config'
import { ownedPrefixOf } from '@/router/routes'
import { APP_ACTIONS, isSpaOwnedAppPage, resolveAppUrl } from '../app-routes'

/**
 * 读 `router/index.ts` 的路由 path 列表（**文本扫描**）。
 *
 * 为什么不 `import { routes }`：该模块在顶层 `createWebHistory()`，
 * 而本文件必须是 node 环境（要 import `vite.config.ts`）⇒ `window is not defined`。
 * 文本扫描的弱点由 `router/__tests__/owned-paths.spec.ts` 补上（那边在 jsdom 下 import 真实路由表
 * 并断言它与 `SPA_OWNED_PATHS` 一致），故此处只回答"路由器里有没有 /app 的所有权声明"。
 *
 * @returns 路由 path 列表
 */
function routerPaths(): string[] {
  const src = readFileSync('src/router/index.ts', 'utf-8')
  const out: string[] = []
  const re = /^\s*path:\s*'([^']+)'/gm
  let m: RegExpExecArray | null
  while ((m = re.exec(src)) !== null) {
    out.push(m[1])
  }
  return out
}

/** ported 后端 `AppController`（`/app` 路由注册的那个类） */
const PORTED_APP_CONTROLLER =
  '../../backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java/cn/eova/core/AppController.java'
/** ported 后端 `IndexController`（`AppController` 的父类，`index`/`code`/`diy` 来自它） */
const PORTED_INDEX_CONTROLLER =
  '../../backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java/cn/eova/core/IndexController.java'
/** 冻结的旧运行时脚本（逐字节复制，提供服务端插值时期的全局处理函数） */
const FROZEN_TEMPLATE_JS = 'src/legacy/eova/_view/template/eova.template.js'

/**
 * 解析 Java 源码里的"公开无参 void 方法"（= JFinal 会注册成 actionKey 的那些）。
 *
 * @param file 源码路径
 * @returns 方法名数组
 */
function publicNoArgVoidNames(file: string): string[] {
  const src = readFileSync(file, 'utf-8')
  const names: string[] = []
  const re = /public\s+void\s+([A-Za-z_$][\w$]*)\s*\(\s*\)\s*\{/g
  let m: RegExpExecArray | null
  while ((m = re.exec(src)) !== null) {
    names.push(m[1])
  }
  return names
}

describe('APP_ACTIONS ↔ ported 后端源码（双向）', () => {
  it('ported AppController/IndexController 的公开无参方法集合与本表逐名一致', () => {
    const fromJava = [
      ...publicNoArgVoidNames(PORTED_APP_CONTROLLER),
      ...publicNoArgVoidNames(PORTED_INDEX_CONTROLLER)
    ]
    // 反空判据：解析结果必须非空，否则"两边都空"会让本判据形同不存在
    expect(fromJava.length, '未从 ported 控制器解析出任何动作方法').toBeGreaterThan(0)
    expect([...new Set(fromJava)].sort()).toEqual(APP_ACTIONS.map((a) => a.name).sort())
  })

  it('每条动作都带溯源信息（ported from 纪律：不得有无出处的动作）', () => {
    for (const a of APP_ACTIONS) {
      expect(a.source, `${a.name} 缺溯源`).toMatch(/\.java:\d+/)
    }
  })

  it('只有 index 是 ambiguous（1 段歧义），其余 1 段动作都独占名称', () => {
    expect(APP_ACTIONS.filter((a) => a.oneSegment === 'ambiguous').map((a) => a.name)).toEqual([
      'index'
    ])
  })
})

describe('resolveAppUrl · 分支矩阵', () => {
  it('① 非 /app 路径 ⇒ foreign', () => {
    for (const u of ['/', '/user/login', '/eova/admin/su', '/meta/reorder?object=x', '/appx/1']) {
      expect(resolveAppUrl(u).kind, u).toBe('foreign')
      expect(resolveAppUrl(u).reason).toBe('not-app')
    }
  })

  it('② /app 本身 ⇒ backend（app-root）', () => {
    expect(resolveAppUrl('/app')).toEqual({ kind: 'backend', reason: 'app-root' })
  })

  it('③ 空段 ⇒ backend（菜单 code 是 \\w{3,50}，绝不含 /）', () => {
    expect(resolveAppUrl('/app/')).toEqual({ kind: 'backend', reason: 'empty-segment' })
    expect(resolveAppUrl('/app//meta_goods_style')).toEqual({
      kind: 'backend',
      reason: 'empty-segment'
    })
  })

  it('④ 1 段、非动作名 ⇒ menu-page（菜单模版页），并带查询串也不受影响', () => {
    expect(resolveAppUrl('/app/meta_goods_style')).toEqual({
      kind: 'menu-page',
      menuCode: 'meta_goods_style',
      reason: 'menu-page'
    })
    expect(resolveAppUrl('/app/meta_goods_style?page=2&limit=15#a')).toEqual({
      kind: 'menu-page',
      menuCode: 'meta_goods_style',
      reason: 'menu-page'
    })
    expect(isSpaOwnedAppPage('/app/meta_goods_style')).toBe(true)
  })

  it('④ 1 段、动作名独占 ⇒ backend（live/status/code/diy/errors/add/update/detail）', () => {
    for (const name of ['live', 'status', 'code', 'diy', 'errors', 'add', 'update', 'detail']) {
      expect(resolveAppUrl(`/app/${name}`), name).toEqual({
        kind: 'backend',
        action: name,
        reason: 'backend-action'
      })
      expect(isSpaOwnedAppPage(`/app/${name}`), name).toBe(false)
    }
  })

  it('④ 1 段、动作名是未取证的 index ⇒ backend 且 reason 显式标歧义（不猜）', () => {
    expect(resolveAppUrl('/app/index')).toEqual({
      kind: 'backend',
      action: 'index',
      reason: 'ambiguous-action-name'
    })
  })

  it('⑤ 2 段及以上 ⇒ backend（表单页/动作参数）', () => {
    expect(resolveAppUrl('/app/add/eova_object_code')).toEqual({
      kind: 'backend',
      action: 'add',
      reason: 'backend-sub-path'
    })
    expect(resolveAppUrl('/app/update/eova_menu_code?id=1')).toEqual({
      kind: 'backend',
      action: 'update',
      reason: 'backend-sub-path'
    })
    expect(resolveAppUrl('/app/errors/404')).toEqual({
      kind: 'backend',
      action: 'errors',
      reason: 'backend-sub-path'
    })
    // 首段不是已知动作也仍是后端（旧栈 404，不是 SPA 页）
    expect(resolveAppUrl('/app/whatever/x')).toEqual({
      kind: 'backend',
      action: undefined,
      reason: 'backend-sub-path'
    })
  })
})

describe('冻结脚本 eova.template.js 里的 /app 字面量', () => {
  it('★ 每一个 /app/** 字面量都必须判成 backend（否则弹层会弹成 SPA 外壳）', () => {
    const src = readFileSync(FROZEN_TEMPLATE_JS, 'utf-8')
    const found = src.match(/\/app\/[^\s'"+`]*/g) ?? []
    // 反空判据：抽取规则失效时本判据不能"因为找不到而通过"
    expect(found, '未从冻结脚本里抽到 /app 字面量，抽取规则可能已失效').toContain(
      '/app/update/eova_object_code?id='
    )
    expect(found.length).toBeGreaterThanOrEqual(2)
    for (const url of found) {
      const r = resolveAppUrl(url)
      expect(r.kind, `${url} 被判成 ${r.kind}（应为 backend）`).toBe('backend')
    }
  })
})

describe('接线 canary：/app 不得半接线', () => {
  /** 取 dev 代理表 */
  function proxyTable(): Record<string, unknown> {
    const cfg = (viteConfigModule as { default?: unknown }).default ?? viteConfigModule
    const server = (cfg as { server?: { proxy?: Record<string, unknown> } }).server
    expect(server?.proxy, 'vite.config.ts 里应有 server.proxy').toBeTruthy()
    return server!.proxy as Record<string, unknown>
  }

  it('加了 /app 代理前缀 ⇒ 必须同时有 SPA 路由接管 /app；反之两者都没有（今天的状态）', () => {
    const hasProxy = Object.prototype.hasOwnProperty.call(proxyTable(), '/app')
    const paths = routerPaths()
    // 反空判据：路径抽取失效时本判据不能"因为抽不到而通过"
    expect(paths, '未从 router/index.ts 抽到任何 path').toContain('/')
    expect(paths.length).toBeGreaterThanOrEqual(13)
    const hasRoute = paths.some((p) => ownedPrefixOf(p) === '/app')
    expect(
      hasRoute,
      hasProxy
        ? 'dev 代理已放行 /app，但 router 里没有接管 /app 的路由 ⇒ 菜单模版页会被 SPA 吞成空页'
        : '/app 尚未接线：router 不应有 /app 路由（菜单模版页还没迁）'
    ).toBe(hasProxy)
  })
})
