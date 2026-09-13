/**
 * **生产态页面供给的名单同步**判据（第 305 轮 · U1）。
 *
 * ## 为什么需要它
 *
 * U1 把"页面入口"从服务端模板改成 **SPA 壳**：SPA 拥有的页面 URL 必须由后端**认得**，
 * 否则生产态直接 404（实测：`/su`、`/main`、`/widget`、`/test/sse` 等 9 个 URL 在 U1 前
 * 带会话直连 8080 **全部 404**，而 `dist/index.html` 明明已经构建好）。
 *
 * 认得有两条路：
 *  ① 该 URL 本来就有后端控制器（如 `/app/**` → `AppController`、`/meta/edit` → `MetaController`）；
 *  ② 它只属于 SPA（旧栈由 demo 工程或前端跳转处理）⇒ 登记到 **壳控制器**
 *     `SpaShellController`（仍走同一套拦截器链，只换响应体）。
 *
 * 本条判据把这两侧钉在一起（**双向**）：
 *  - 前端每个 `SPA_OWNED_PATHS` 项，后端必须"认得"（有控制器前缀，或有壳路由）；
 *  - 后端 `SpaShellController` 的每条壳路由，前端都必须登记为 SPA 所有 ——
 *    否则就是把后端动作/接口当成壳，属"整站兜底"式的越界（U1 明确不做）。
 */
import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { SPA_OWNED_PATHS } from '../routes'

/** 后端路由表文件（相对前端工程根；dev 期与 CI 都在同一台机器上跑） */
const WEB_ROUTES = '../../backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java/cn/eova/EovaWebRoutes.java'
const CONFIG = '../../backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java/cn/eova/config/EovaConfig.java'

/**
 * 解析后端登记的路由：控制器路径 → 控制器类名
 *
 * @param text Java 源码
 * @returns `[controllerPath, className]` 列表
 */
function backendRoutes(text: string): Array<[string, string]> {
  const out: Array<[string, string]> = []
  for (const m of text.matchAll(/add\("([^"]+)",\s*(\w+)\.class\)/g)) {
    out.push([m[1], m[2]])
  }
  for (const m of text.matchAll(/add\(([A-Z_]+),\s*(\w+)\.class\)/g)) {
    // EOVA_INDEX（`/`）这类常量路径
    out.push(['/', m[2]])
  }
  return out
}

/**
 * 显式覆盖（**排除兜底**）：`EovaConfig` 把 `IndexController` 注册在 `/`，而分发器是最长前缀匹配
 * ⇒ `/` 会匹配**任何**路径（旧 JFinal 同语义：实测旧栈 `/zzz_unknown`、`/su` 都返回首页）。
 * 故"被兜底接住"不算"被显式供给" —— 本判据要求 SPA 独有 URL 出现在**非 `/` 的路由**上，
 * 否则名单会退化成"反正什么都能兜"，届时不登记新 URL 也不会红（那正是 M1 变异未被捕获的原因）。
 *
 * @param owned  SPA 所有权路径
 * @param routes 后端路由
 * @returns 是否有**显式**路由覆盖
 */
function explicitlyCovered(owned: string, routes: Array<[string, string]>): boolean {
  return routes
    .filter(([p]) => p !== '/')
    .some(([p]) => owned === p || owned.startsWith(p.endsWith('/') ? p : p + '/'))
}

describe('生产态页面供给 · 名单同步（U1）', () => {
  const webRoutes = readFileSync(WEB_ROUTES, 'utf-8')
  const config = readFileSync(CONFIG, 'utf-8')
  const routes = backendRoutes(webRoutes).concat(backendRoutes(config))

  it('① 后端至少登记了一批路由（反空断言：解析失效时本判据不得空过）', () => {
    expect(routes.length).toBeGreaterThanOrEqual(20)
    expect(routes.some(([, c]) => c === 'SpaShellController')).toBe(true)
  })

  it('② 前端每个 SPA 所有权路径，后端都必须**显式**认得（控制器前缀或壳路由，不得只靠兜底 `/`）', () => {
    // `/` 例外：它由 `EovaConfig` 的 `me.add(EOVA_INDEX, IndexController.class)` 显式注册
    // （EOVA_INDEX 默认就是 `/`）—— 它就是那条兜底路由本身，不存在"只靠兜底"的问题。
    const missing = SPA_OWNED_PATHS.filter((p) => p !== '/' && !explicitlyCovered(p, routes))
    expect(missing, `这些 SPA 路径没有显式供给（只被兜底接住 ⇒ 名单失去约束力）：${missing.join(', ')}`).toEqual([])
  })

  it('②b 壳路由名单冻结（删一条即红 —— 判据的灵敏度自证）', () => {
    const shells = routes.filter(([, c]) => c === 'SpaShellController').map(([p]) => p).sort()
    // ★ r306（U2）：U1 登记的 `/eova/role/auth` 已删除 —— 它基于错误前提（旧页面 URL 是 `/auth/<rid>`，
    //   `/eova/role/auth/1` 实测 404）。`/auth` 的页面入口改由 `AuthController#index()` 退役为壳，
    //   该路由本来就已注册 ⇒ 不再需要壳路由条目。名单从 10 条降为 9 条。
    expect(shells).toEqual([
      '/ip',
      '/main',
      '/placeholder',
      '/sso',
      '/su',
      '/test',
      '/test/sse',
      '/theme',
      '/widget'
    ])
  })

  it('③ 后端壳路由必须**全部**是 SPA 所有的路径（不得借壳把后端动作/接口兜掉）', () => {
    const shells = routes.filter(([, c]) => c === 'SpaShellController').map(([p]) => p)
    expect(shells.length).toBeGreaterThanOrEqual(9)
    const notOwned = shells.filter(
      (p) => !SPA_OWNED_PATHS.some((o) => o === p || p === o || p.startsWith(o + '/'))
    )
    expect(notOwned, `这些壳路由不属 SPA 所有 ⇒ 会把后端路径吞成壳：${notOwned.join(', ')}`).toEqual([])
  })

  it('④ 壳路由不得覆盖后端动作/接口前缀（`/api`、`/eova`、`/_eova`、`/menu/add` 等）', () => {
    const shells = routes.filter(([, c]) => c === 'SpaShellController').map(([p]) => p)
    for (const forbidden of ['/api', '/eova', '/_eova', '/menu/add', '/meta/reorder_data']) {
      expect(
        shells.includes(forbidden),
        `${forbidden} 不得由壳接管（它是接口/动作/静态空间）`
      ).toBe(false)
    }
  })
})
