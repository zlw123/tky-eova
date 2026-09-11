// @vitest-environment node
/**
 * dev 代理 `bypass` 的漂移判据（第 108 轮）
 *
 * 背景：SPA 按契约纪律 ② 接管**旧原路径**，而 dev 代理把 `/eova`、`/meta`、`/widget`
 * 送到后端。`SPA_OWNED_PATHS` 只声明"这些路径归 SPA"，**不会自动让代理放行** ——
 * 代理里每个前缀都必须自己配 `bypass`。
 *
 * 实测教训：r104 只给 `/eova` 配了 bypass；本轮新增 `/meta/reorder`（归 SPA）时，
 * 若不同时给 `/meta` 配 bypass，症状就是"**构建绿、单测绿、打开页面却是后端响应**"，
 * 而既有的所有权判据**完全看不出来**（它只比路径清单）。
 *
 * ★ 本文件用 **node 环境**（`// @vitest-environment node`）：它要 import `vite.config.ts`，
 * 而该配置会拉起 vite 内部实现，在 jsdom 环境下会触发 `TextEncoder` 不变式；node 环境无此问题。
 *
 * 本判据直接读 `vite.config.ts` 的解析结果：凡"有 SPA 归其下"的代理前缀，
 * 必须带 `bypass`；并断言 `bypass` 的语义与 `isSpaOwnedPath` 一致（SPA 路径放行、后端路径不放行）。
 */
import { describe, expect, it } from 'vitest'
import * as viteConfigModule from '../../../vite.config'
import { SPA_OWNED_PATHS, isSpaOwnedPath } from '../routes'

/** 取代理表（`defineConfig` 在对象入参时原样返回） */
function proxyTable(): Record<string, { bypass?: (req: { url?: string }) => unknown }> {
  const cfg = (viteConfigModule as { default?: unknown }).default ?? viteConfigModule
  const server = (cfg as { server?: { proxy?: Record<string, unknown> } }).server
  expect(server?.proxy, 'vite.config.ts 里应有 server.proxy').toBeTruthy()
  return server!.proxy as Record<string, { bypass?: (req: { url?: string }) => unknown }>
}

describe('vite dev 代理 · bypass 覆盖', () => {
  it('★ 凡"有 SPA 归其下"的代理前缀都必须配 bypass（只配 /eova 是不够的）', () => {
    const proxy = proxyTable()
    // 对每个 /xxx 前缀：若存在 SPA 路径以它开头，则该前缀必须有 bypass
    for (const prefix of Object.keys(proxy)) {
      const hasOwned = SPA_OWNED_PATHS.some(
        (p) => p === prefix || p.startsWith(prefix.endsWith('/') ? prefix : prefix + '/')
      )
      if (hasOwned) {
        expect(
          typeof proxy[prefix].bypass,
          `代理前缀 ${prefix} 下有 SPA 接管的路径，但该前缀没有配 bypass`
        ).toBe('function')
      }
    }
  })

  it('★ bypass 语义与 isSpaOwnedPath 一致：SPA 路径放行、后端路径不放行', () => {
    const proxy = proxyTable()
    for (const [prefix, entry] of Object.entries(proxy)) {
      const bypass = entry.bypass
      if (typeof bypass !== 'function') {
        continue
      }
      // SPA 接管的路径 ⇒ 返回原 url（放行给 SPA）
      const owned = SPA_OWNED_PATHS.find((p) => p.startsWith(prefix + '/'))
      if (owned) {
        expect(bypass({ url: owned }), `${prefix} 的 bypass 未放行 SPA 路径 ${owned}`).toBe(owned)
      }
      // 后端路径 ⇒ 返回 undefined（继续走代理）
      expect(
        bypass({ url: prefix + '/__definitely_backend__' }),
        `${prefix} 的 bypass 误放行了后端路径`
      ).toBeUndefined()
    }
  })

  it('bypass 与 isSpaOwnedPath 判定一致（同一套口径，不是两套）', () => {
    const proxy = proxyTable()
    const bypass = proxy['/meta'].bypass!
    expect(typeof bypass).toBe('function')
    expect(bypass({ url: '/meta/reorder?biz=field' })).toBe('/meta/reorder?biz=field')
    expect(isSpaOwnedPath('/meta/reorder?biz=field')).toBe(true)
    expect(bypass({ url: '/meta/table/x' })).toBeUndefined()
    expect(isSpaOwnedPath('/meta/table/x')).toBe(false)
  })
})
