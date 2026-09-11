/**
 * `uzoo` 扩展机制接缝的判据（第 107 轮）
 *
 * 钉的契约：
 *  ① `uzoo` 缺失时**可用 + 一次性告警**（不抛错：缺失是合法状态；但也不能静默）；
 *  ② 扩展钩子**存在才调**（旧实现守则 `typeof uzoo.vue.setup === 'function'`），
 *     非函数值（如误写成字符串）视作未注册；
 *  ③ 钩子**抛错必须上抛**（不吞 —— 吞掉会让"扩展失效"变成无差别现象）；
 *  ④ `uzoo.page` 是普通对象赋值语义：同名后写覆盖，`undefined` 也照写（旧实现没有过滤）。
 */
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  callUzooHook,
  getUzoo,
  getUzooPage,
  resetUzooWarning,
  setUzooPage,
  type Uzoo
} from '../eova-ext'

/** 造一个目标全局对象 */
function target(): Record<string, unknown> {
  return {}
}

describe('eova-ext · getUzoo', () => {
  afterEach(() => {
    resetUzooWarning()
    vi.restoreAllMocks()
  })

  it('已有全局 `uzoo` 时原样返回（不重建、不告警）', () => {
    const existing = { page: { a: 1 }, vue: {}, app: {} } as Uzoo
    const t = target()
    t['uzoo'] = existing
    const warn = vi.fn()
    expect(getUzoo(t, warn)).toBe(existing)
    expect(warn).not.toHaveBeenCalled()
  })

  it('缺失时**惰性创建**空结构并**告警一次**（后续调用不刷屏）', () => {
    const t = target()
    const warn = vi.fn()
    const u = getUzoo(t, warn)
    expect(u).toEqual({ page: {}, vue: {}, app: {} })
    expect(t['uzoo']).toBe(u)
    getUzoo(t, warn)
    getUzoo(t, warn)
    expect(warn).toHaveBeenCalledTimes(1)
    expect(warn.mock.calls[0][0]).toContain('未找到全局 `uzoo`')
    expect(warn.mock.calls[0][0]).toContain('eova.meta.js')
  })

  it('resetUzooWarning 后告警重新武装', () => {
    const warn = vi.fn()
    getUzoo(target(), warn)
    resetUzooWarning()
    getUzoo(target(), warn)
    expect(warn).toHaveBeenCalledTimes(2)
  })

  it('全局存在但**不是对象**（如被写成字符串）视作缺失', () => {
    const t = target()
    t['uzoo'] = 'nope'
    const warn = vi.fn()
    expect(getUzoo(t, warn)).toEqual({ page: {}, vue: {}, app: {} })
    expect(warn).toHaveBeenCalledTimes(1)
  })
})

describe('eova-ext · callUzooHook（存在才调；不吞异常）', () => {
  afterEach(() => {
    resetUzooWarning()
  })

  it('未注册 ⇒ 什么都不做并返回 undefined（旧实现：uzoo.vue 起步为空对象）', () => {
    const t = target()
    expect(callUzooHook('mountBefore', [], t)).toBeUndefined()
  })

  it('已注册 ⇒ 按参数调用并返回其返回值', () => {
    const hook = vi.fn((a: unknown, b: unknown) => `got:${String(a)}:${String(b)}`)
    const t = target()
    t['uzoo'] = { page: {}, vue: { setup: hook }, app: {} }
    expect(callUzooHook('setup', ['x', 2], t)).toBe('got:x:2')
    expect(hook).toHaveBeenCalledWith('x', 2)
  })

  it('★ 非函数值视作未注册（旧守则是 typeof === "function"，不是"存在即可"）', () => {
    const t = target()
    t['uzoo'] = { page: {}, vue: { setup: 'not-a-function' }, app: {} }
    expect(() => callUzooHook('setup', [], t)).not.toThrow()
    expect(callUzooHook('setup', [], t)).toBeUndefined()
  })

  it('★ 钩子抛错必须**上抛**（吞掉会让"扩展失效"无从诊断）', () => {
    const t = target()
    t['uzoo'] = {
      page: {},
      vue: {
        onReady: () => {
          throw new Error('hook boom')
        }
      },
      app: {}
    }
    expect(() => callUzooHook('onReady', [], t)).toThrow('hook boom')
  })

  it('各钩子名互不干扰（mountBefore/setup/onReady/useTemplateRef）', () => {
    const calls: string[] = []
    const t = target()
    t['uzoo'] = {
      page: {},
      vue: {
        mountBefore: () => calls.push('mountBefore'),
        setup: () => calls.push('setup'),
        onReady: () => calls.push('onReady')
      },
      app: {}
    }
    callUzooHook('mountBefore', [], t)
    callUzooHook('setup', [], t)
    callUzooHook('onReady', [], t)
    callUzooHook('useTemplateRef', [], t) // 未注册 ⇒ 不出现
    expect(calls).toEqual(['mountBefore', 'setup', 'onReady'])
  })
})

describe('eova-ext · uzoo.page', () => {
  afterEach(() => {
    resetUzooWarning()
  })

  it('setUzooPage 写 / getUzooPage 读（旧页面就是 `uzoo.page.code = "#(object.code)"`）', () => {
    const t = target()
    setUzooPage('code', 'eova_user_code', t)
    setUzooPage('biz', 'meta_user', t)
    expect(getUzooPage(t)).toEqual({ code: 'eova_user_code', biz: 'meta_user' })
  })

  it('同名后写覆盖前写；`undefined` 也照写（旧实现没有过滤）', () => {
    const t = target()
    setUzooPage('code', 'first', t)
    setUzooPage('code', 'second', t)
    expect(getUzooPage(t)['code']).toBe('second')
    setUzooPage('code', undefined, t)
    expect('code' in getUzooPage(t)).toBe(true)
    expect(getUzooPage(t)['code']).toBeUndefined()
  })
})
