/**
 * 未登录守卫：SPA 侧补齐旧栈 `LoginInterceptor` 的"未登录 ⇒ 去登录页"语义（DES-005 §14.4）。
 *
 * ## 为什么必须由 SPA 自己判
 *
 * 旧栈由服务端渲染：未登录访问 `/` ⇒ 拦截器 302 到 `/user/login?back=…`（r263 实测）。
 * 分离后 `/` 由 Vite/静态托管给 SPA，**后端根本收不到这个请求** ⇒ 后端那条 302 永远不会发生。
 * 结果是 SPA 挂载 `Home.vue`、它去拉 `/api/home/menu` 拿到 401，然后把错误渲染成页面文案
 * —— 真浏览器实测正文是「首页 / 请求异常」，而构建/单测/闸门全绿。
 *
 * ## 判定依据（有实证，不是发明）
 *
 * 读 Cookie `eovasid`：移植版 `LoginService` 设置该 Cookie 时**没有任何 `setHttpOnly` 调用**，
 * 且对运行中后端抓到的响应头形态是 `Set-Cookie: eovasid=…; path=/; Max-Age=…`（**不含 HttpOnly**）
 * ⇒ JS 可读。经 dev 代理（9091 → 8080）时浏览器按前端同源存/发该 Cookie ⇒ dev 与生产同构。
 */

/** 会话 Cookie 名（与 `LoginService.CKSID` 逐字一致） */
export const SESSION_COOKIE = 'eovasid'

/** 登录页路径（旧 URL 不加前缀：旧栈就是 `/user/login`） */
export const LOGIN_PATH = '/user/login'

/** 判定登录态所需的注入点（判据用；默认读 `document.cookie`） */
export interface GuardDeps {
  /** 取当前 Cookie 串 */
  cookieOf?: () => string
}

/**
 * 是否持有会话 Cookie
 *
 * @param cookie Cookie 串（默认取当前文档）
 * @returns 含非空 `eovasid=` 即 true
 */
export function hasSession(cookie?: string): boolean {
  const raw = cookie ?? (typeof document === 'undefined' ? '' : document.cookie)
  const prefix = `${SESSION_COOKIE}=`
  return raw
    .split(';')
    .map((c) => c.trim())
    .some((c) => c.startsWith(prefix) && c.length > prefix.length)
}

/** 守卫所需的路由能力（只依赖 `beforeEach`，便于判据注入假路由） */
export interface GuardRouter {
  beforeEach: (fn: (to: { path: string }) => boolean | string) => void
}

/**
 * 安装未登录守卫：除登录页外，无会话一律重定向到登录页。
 *
 * @param router   路由实例
 * @param deps     注入点（判据用）
 */
export function installAuthGuard(router: GuardRouter, deps: GuardDeps = {}): void {
  const cookieOf = deps.cookieOf
  router.beforeEach((to) => {
    if (to.path === LOGIN_PATH) {
      return true
    }
    return hasSession(cookieOf ? cookieOf() : undefined) ? true : LOGIN_PATH
  })
}
