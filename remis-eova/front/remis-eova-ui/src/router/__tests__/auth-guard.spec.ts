import { describe, expect, it } from 'vitest'
import { hasSession, installAuthGuard, LOGIN_PATH } from '../auth-guard'

/** 假路由：抓住 `beforeEach` 的回调，供判据直接调用 */
function fakeRouter() {
  let handler: ((to: { path: string }) => boolean | string) | null = null
  return {
    beforeEach: (fn: (to: { path: string }) => boolean | string) => {
      handler = fn
    },
    run: (path: string): boolean | string => {
      if (!handler) {
        throw new Error('未安装守卫')
      }
      return handler({ path })
    }
  }
}

describe('未登录守卫（旧栈 LoginInterceptor 的 SPA 等价物）', () => {
  it('★ 无会话访问 / ⇒ 重定向到 /user/login（旧栈是 302；不得渲染失败态）', () => {
    const router = fakeRouter()
    installAuthGuard(router, { cookieOf: () => '' })
    expect(router.run('/')).toBe(LOGIN_PATH)
  })

  it('★ 有会话访问 / ⇒ 放行（不得把已登录用户赶去登录页）', () => {
    const router = fakeRouter()
    installAuthGuard(router, { cookieOf: () => 'eovasid=abc123; other=1' })
    expect(router.run('/')).toBe(true)
  })

  it('登录页本身永远放行（否则会自锁成死循环）', () => {
    const router = fakeRouter()
    installAuthGuard(router, { cookieOf: () => '' })
    expect(router.run(LOGIN_PATH)).toBe(true)
  })

  it('其它受保护页同样拦（不是只拦 /）', () => {
    const router = fakeRouter()
    installAuthGuard(router, { cookieOf: () => '' })
    expect(router.run('/meta/reorder')).toBe(LOGIN_PATH)
  })

  it('hasSession：空值/残缺形态都不算登录（`eovasid=` 空串不得当登录）', () => {
    expect(hasSession('')).toBe(false)
    expect(hasSession('eovasid=')).toBe(false)
    expect(hasSession('x=1; eovasid=; y=2')).toBe(false)
    expect(hasSession('x=1; eovasid=abc')).toBe(true)
  })
})
