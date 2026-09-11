// @vitest-environment node
/**
 * dev 代理**覆盖**判据（第 121 轮）
 *
 * ## 它防的是什么
 *
 * `vite.config.ts` 的 `server.proxy` 只对**列进去的前缀**生效；没列的后端路径会落到
 * Vite 的 SPA 回退上（返回 `index.html`，HTTP **200**）⇒ 症状是"页面能打开、操作没反应/提示失败"，
 * 而**构建、单测、闸门全绿**（第 108 轮 `bypass` 漂移的更宽形态）。
 *
 * 本轮审计发现真实缺口（都是"少配前缀"）：
 *
 * | 路径 | 调用点 | 漏配的后果 |
 * |---|---|---|
 * | `/user/doLogin` | `Login.vue:123` | **登录页在 dev 环境完全不可用** |
 * | `/api/home/menu` | `Home.vue:275` | 主框架菜单空 |
 * | `/user/doPassword`、`/button/doAdd`、`/menu/add`、`/menu/authData`、`/auth/*` | 各入口页 | 提交无反应 |
 * | `/excel/export/…` | `x.axios.download`（导出） | 导出无反应 |
 * | `/demo/test/btn.js` | `ButtonAdd.vue` 初值 + 种子 `eova_button.ui` | `.js` 按钮脚本取不到 |
 * | `/main` | `Home.vue` 首页签 iframe 的 `link` | 首页内容区空白 |
 *
 * ## 三条判据（都不依赖手工维护的清单）
 *
 * ① **Java 源码 → 代理表**：解析 ported 后端的路由注册
 *    （`EovaWebRoutes.java` / `EovaApiRoutes.java` / `config/EovaConfig.java` 的 `add(...)`/`me.add(...)`），
 *    每个注册前缀的**首段**都必须在代理表里；
 * ② **SPA 源码 → 代理表**：扫 `src/**`（排除 `__tests__` 与 `src/legacy`）与 `index.html` 里
 *    以 `/` 开头的字面量路径（动态段 `:param` 换成 `sample`），每条要么**归 SPA**（`isSpaOwnedPath`），
 *    要么首段在代理表里；
 * ③ **不得代理 `/`**（它是 SPA 首页）。
 *
 * 两条扫描都带**反空断言**（扫到的条数下限 + 必须包含若干已知项），否则抽取规则失效时会"因为找不到而通过"。
 */
import { readFileSync, readdirSync } from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import * as viteConfigModule from '../../../vite.config'
import { BACKEND_ROUTE_PREFIXES } from '@/compat/backend-routes'
import { isSpaOwnedPath } from '../routes'

/** ported 后端的路由注册文件（相对前端工程根） */
const PORTED_ROUTE_FILES = [
  '../../backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java/cn/eova/EovaWebRoutes.java',
  '../../backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java/cn/eova/EovaApiRoutes.java',
  '../../backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java/cn/eova/config/EovaConfig.java'
]

/**
 * 扫描时**明确排除**的非 URL 字面量（每条都要有理由）
 *
 * 这些字符串长得像路径，但不是给后端发的请求；把它们列在这里是为了让"排除"是**显式决定**
 * 而不是把抽取规则写松（写松会让真缺口也漏过去）。
 */
const NON_URL_LITERALS: readonly string[] = [
  '/src/main.ts' // 来源文件里对"源码文件位置"的引用（注释/文档），不是请求路径
]

/** 取 dev 代理表 */
function proxyTable(): Record<string, unknown> {
  const cfg = (viteConfigModule as { default?: unknown }).default ?? viteConfigModule
  const server = (cfg as { server?: { proxy?: Record<string, unknown> } }).server
  expect(server?.proxy, 'vite.config.ts 里应有 server.proxy').toBeTruthy()
  return server!.proxy as Record<string, unknown>
}

/** 路径的首段（`/user/doLogin` ⇒ `/user`） */
function firstSegment(p: string): string {
  return '/' + p.split('?')[0].split('/').filter((s) => s !== '')[0]
}

/** 解析 Java 源码里的路由注册，返回注册前缀（`/api/home` 这种完整 controllerKey） */
function javaRoutePrefixes(): string[] {
  const out: string[] = []
  const re = /(?:^|\s)(?:me\.)?add\("([^"]+)"/gm
  for (const f of PORTED_ROUTE_FILES) {
    const src = readFileSync(f, 'utf-8')
    let m: RegExpExecArray | null
    while ((m = re.exec(src)) !== null) {
      out.push(m[1])
    }
  }
  return out
}

/** 扫 SPA 源码 + index.html 里的字面量路径（排除测试与冻结资产） */
function spaLiteralPaths(): string[] {
  const root = process.cwd()
  const files: string[] = []
  const walk = (dir: string): void => {
    for (const entry of readdirSync(path.join(root, dir), { withFileTypes: true })) {
      const rel = path.posix.join(dir, entry.name)
      if (entry.isDirectory()) {
        if (rel === 'src/legacy') {
          continue
        }
        walk(rel)
        continue
      }
      if (!/\.(vue|ts)$/.test(entry.name) || rel.includes('__tests__')) {
        continue
      }
      files.push(rel)
    }
  }
  walk('src')
  files.push('index.html')

  const out: string[] = []
  const re = /['"](\/[A-Za-z0-9_\-./{}:]*?)['"]/g
  for (const f of files) {
    const txt = readFileSync(f, 'utf-8')
    let m: RegExpExecArray | null
    while ((m = re.exec(txt)) !== null) {
      // 动态段换成具体值（路由 path 里的 `:param` 不是真实 URL）
      const p = m[1].replace(/:[A-Za-z_$][\w$]*/g, 'sample').split('?')[0]
      if (p === '/' || p === '') {
        continue
      }
      out.push(p)
    }
  }
  return [...new Set(out)]
}

describe('dev 代理覆盖', () => {
  it('③ 不得代理 `/`（那是 SPA 首页）', () => {
    expect(Object.keys(proxyTable())).not.toContain('/')
  })

  it('① ported 后端的每个路由注册前缀，其首段都在代理表里（含反空断言）', () => {
    const prefixes = javaRoutePrefixes()
    // 反空断言：解析规则失效时不能"因为找不到而通过"
    expect(prefixes.length).toBeGreaterThanOrEqual(18)
    expect(prefixes).toContain('/api/home')
    expect(prefixes).toContain('/eova/admin')
    expect(prefixes).toContain('/user')
    expect(prefixes).toContain('/app')

    const proxy = proxyTable()
    for (const p of prefixes) {
      const seg = firstSegment(p)
      expect(
        Object.prototype.hasOwnProperty.call(proxy, seg),
        `后端路由前缀 ${p}（首段 ${seg}）不在 dev 代理表里 ⇒ dev 环境该请求会落到 SPA 回退`
      ).toBe(true)
    }
  })

  it('② SPA 源码里的每个字面量路径，要么归 SPA、要么首段在代理表里（含反空断言）', () => {
    const paths = spaLiteralPaths().filter((p) => !NON_URL_LITERALS.includes(p))
    // 反空断言：扫到的条数下限 + 必须包含几个**已知真实调用**
    expect(paths.length).toBeGreaterThanOrEqual(40)
    for (const known of ['/user/doLogin', '/api/home/menu', '/user/doPassword', '/menu/add']) {
      expect(paths, `未扫到 ${known}（抽取规则可能已失效）`).toContain(known)
    }

    const proxy = proxyTable()
    const misses: string[] = []
    for (const p of paths) {
      if (isSpaOwnedPath(p)) {
        continue
      }
      const seg = firstSegment(p)
      if (!Object.prototype.hasOwnProperty.call(proxy, seg)) {
        misses.push(`${p}（首段 ${seg}）`)
      }
    }
    expect(
      misses,
      `以下路径在 dev 环境会落到 SPA 回退（既不归 SPA、首段也没配代理）：\n  ${misses.join('\n  ')}`
    ).toEqual([])
  })

  it('② 每个代理项都有 target 与 bypass（同一个放行口径）', () => {
    for (const [prefix, entry] of Object.entries(proxyTable())) {
      const e = entry as { target?: unknown; bypass?: unknown }
      expect(e.target, `${prefix} 缺 target`).toBeTruthy()
      expect(typeof e.bypass, `${prefix} 缺 bypass`).toBe('function')
    }
  })

  it('BACKEND_ROUTE_PREFIXES 与代理表键集合一致（清单就是唯一事实来源）', () => {
    expect(Object.keys(proxyTable()).sort()).toEqual([...BACKEND_ROUTE_PREFIXES].sort())
  })

  it('新增前缀后 bypass 语义仍然正确：SPA 路径放行、后端路径继续代理', () => {
    const proxy = proxyTable() as Record<string, { bypass?: (r: { url?: string }) => unknown }>
    // `/user/login` 归 SPA ⇒ 即便 `/user` 被代理，也必须放行
    expect(proxy['/user'].bypass!({ url: '/user/login' })).toBe('/user/login')
    // `/user/doLogin` 是后端 API ⇒ 继续代理
    expect(proxy['/user'].bypass!({ url: '/user/doLogin' })).toBeUndefined()
    // `/api/**` 下没有 SPA 拥有的路径 ⇒ 一律代理
    expect(proxy['/api'].bypass!({ url: '/api/home/menu' })).toBeUndefined()
    // `/app` 的两类路径（r117/r118 口径）在新表里仍然成立
    expect(proxy['/app'].bypass!({ url: '/app/meta_menu' })).toBe('/app/meta_menu')
    expect(proxy['/app'].bypass!({ url: '/app/add/eova_menu_code' })).toBeUndefined()
  })
})
