/**
 * 未登录守卫（**探针式**）：SPA 侧补齐旧栈 `LoginInterceptor` 的"未登录 ⇒ 去登录页"语义
 * （DES-005 §14.4 需求 / §14.6 方案）。
 *
 * ## 为什么不能读 Cookie（一次被实测推翻的实现，见 §14.6）
 *
 * 曾按"读 Cookie `eovasid`"实现，依据是"`LoginService` 里没有 `setHttpOnly` 调用"。
 * 对三个入口 `curl -i` 实测 `Set-Cookie` 后**证伪**：
 * 9091（经 Vite 代理）/ 8080（后端）/ 9090（旧栈）**全都带 `HttpOnly`**
 * ⇒ `document.cookie` 永远读不到 ⇒ 那个版本会把**已登录用户**一路锁在 `/user/login`
 * （真浏览器实测：登录 200 `{"state":"ok"}` 后重新导航 `/`，`pathname` 仍是 `/user/login`）。
 *
 * ## 现在的判定依据（已实测的端点语义）
 *
 * `POST /api/home/menu`：未登录 ⇒ **401**；已登录 ⇒ 200 + `state === 'ok'` + 33 条菜单
 * （S2b 判据与真栈实测均已确认）。判定只看这两个事实。
 *
 * ## 两条必须保留的语义
 *
 * 1. **fail-open**：只有"明确 401"才判定未登录；网络错误/后端抖动一律放行
 *    —— 否则后端一次抖动就把用户锁死在登录页（比原缺陷更严重）。
 * 2. 登录页自豁免：否则未登录访问登录页会被再次重定向成死循环。
 */
import axios from 'axios'

/** 登录页路径（旧 URL 不加前缀：旧栈就是 `/user/login`） */
export const LOGIN_PATH = '/user/login'

/** 会话探针端点（未登录 401 / 已登录 200 `state=ok`，均已实测） */
export const SESSION_PROBE_ENDPOINT = '/api/home/menu'

/** 判定登录态所需的注入点（判据用） */
export interface GuardDeps {
  /** 会话探针：true = 已登录，false = 未登录 */
  probe?: () => Promise<boolean>
}

/** 守卫所需的路由能力（只依赖 `beforeEach`，便于判据注入假路由） */
export interface GuardRouter {
  beforeEach: (fn: (to: { path: string }) => boolean | string | Promise<boolean | string>) => void
}

/**
 * 造会话探针（进程内缓存一次结果，避免每次导航都打一次请求）
 *
 * @param post POST 实现（默认 axios.post，判据可注入）
 * @returns 探针函数
 */
export function createSessionProbe(
  post: (url: string, body: unknown) => Promise<{ status?: number; data?: unknown }> = (url, body) =>
    axios.post(url, body)
): () => Promise<boolean> {
  let cached: boolean | null = null
  return async () => {
    if (cached !== null) {
      return cached
    }
    try {
      const res = await post(SESSION_PROBE_ENDPOINT, {})
      const data = res.data as { state?: string } | undefined
      cached = data?.state === 'ok'
    } catch (e) {
      // ★ fail-open：只有明确 401 才算未登录；其它异常放行（不把用户锁死在登录页）
      const status = (e as { response?: { status?: number } })?.response?.status
      cached = status !== 401
    }
    return cached
  }
}

/**
 * 安装未登录守卫：除登录页外，未登录一律重定向到登录页。
 *
 * @param router 路由实例
 * @param deps   注入点（判据用）
 */
export function installAuthGuard(router: GuardRouter, deps: GuardDeps = {}): void {
  const probe = deps.probe ?? createSessionProbe()
  router.beforeEach(async (to) => {
    if (to.path === LOGIN_PATH) {
      return true
    }
    return (await probe()) ? true : LOGIN_PATH
  })
}
