import { describe, expect, it, vi } from 'vitest'
import {
  SESSION_PROBE_ENDPOINT,
  createSessionProbe,
  installAuthGuard,
  LOGIN_PATH
} from '../auth-guard'

/** 假路由：抓住 `beforeEach` 回调，供判据直接调用 */
function fakeRouter() {
  let handler: ((to: { path: string }) => boolean | string | Promise<boolean | string>) | null = null
  return {
    beforeEach: (fn: (to: { path: string }) => boolean | string | Promise<boolean | string>) => {
      handler = fn
    },
    run: async (path: string): Promise<boolean | string> => {
      if (!handler) {
        throw new Error('未安装守卫')
      }
      return await handler({ path })
    }
  }
}

describe('未登录守卫（探针式；旧栈 LoginInterceptor 的 SPA 等价物）', () => {
  it('★ 探针判未登录 ⇒ 访问 / 重定向到 /user/login（旧栈是 302；不得渲染失败态）', async () => {
    const router = fakeRouter()
    installAuthGuard(router, { probe: async () => false })
    expect(await router.run('/')).toBe(LOGIN_PATH)
  })

  it('★ 探针判已登录 ⇒ 放行（这条防的是"已登录被锁在登录页"，r268 正是栽在这里）', async () => {
    const router = fakeRouter()
    installAuthGuard(router, { probe: async () => true })
    expect(await router.run('/')).toBe(true)
    expect(await router.run('/meta/reorder')).toBe(true)
  })

  it('登录页自豁免（否则未登录访问登录页会被反复重定向成死循环）', async () => {
    const router = fakeRouter()
    installAuthGuard(router, { probe: async () => false })
    expect(await router.run(LOGIN_PATH)).toBe(true)
  })

  it('探针只在需要时调用（登录页不触发探测）', async () => {
    const probe = vi.fn(async () => false)
    const router = fakeRouter()
    installAuthGuard(router, { probe })
    await router.run(LOGIN_PATH)
    expect(probe).not.toHaveBeenCalled()
  })
})

describe('会话探针：只看端点已实测的两种事实（401 / state=ok）', () => {
  it('state=ok ⇒ 已登录，且结果被缓存（不重复打请求）', async () => {
    const post = vi.fn(async (_url: string, _body: unknown) => ({ status: 200, data: { state: 'ok' } }))
    const probe = createSessionProbe(post)
    expect(await probe()).toBe(true)
    expect(await probe()).toBe(true)
    expect(post).toHaveBeenCalledTimes(1)
    expect(post.mock.calls[0][0]).toBe(SESSION_PROBE_ENDPOINT)
  })

  it('401 ⇒ 未登录（这是唯一判定"未登录"的依据）', async () => {
    const post = vi.fn(async () => {
      throw { response: { status: 401 } }
    })
    expect(await createSessionProbe(post)()).toBe(false)
  })

  it('★ fail-open：非 401 的异常一律放行（后端抖动不得把用户锁死在登录页）', async () => {
    const post = vi.fn(async () => {
      throw { response: { status: 500 } }
    })
    expect(await createSessionProbe(post)()).toBe(true)

    const post2 = vi.fn(async () => {
      throw new Error('network down')
    })
    expect(await createSessionProbe(post2)()).toBe(true)
  })

  it('200 但 state 非 ok ⇒ 视为未登录（信封口径与 S2b 判据一致）', async () => {
    const post = vi.fn(async () => ({ status: 200, data: { state: 'no', msg: '请先登录' } }))
    expect(await createSessionProbe(post)()).toBe(false)
  })
})
