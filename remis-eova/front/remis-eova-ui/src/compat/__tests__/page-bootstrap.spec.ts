/**
 * 页面引导数据接缝的判据（第 105 轮，设计见 DES-004）
 *
 * 钉的契约：
 *  ① URL 参数只读**契约里声明的键**（多读会把无关参数当引导参数）；
 *  ② 无来源/拉取失败/返回空/非法 JSON/`state!=ok` ⇒ **降级为"仅 URL 参数"** 且**必须有可诊断告警**；
 *  ③ `requireObjectCode` 的优先级：引导数据 > URL 参数 > **显式回退**；都没有 ⇒ **抛错**（不返回空串）；
 *  ④ `buttonListOf`：**未就绪（null）与"就绪但空（[]）"是两回事**。
 */
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  BOOTSTRAP_URL_KEYS,
  buttonListOf,
  loadPageBootstrap,
  readUrlParams,
  requireObjectCode,
  type PageBootstrap
} from '../page-bootstrap'

describe('page-bootstrap · URL 参数', () => {
  it('只读契约里声明的键（其它查询参数不得被当作引导参数）', () => {
    const p = readUrlParams('?object=o1&menu=m1&biz=b&mode=update&id=9&rand=0.5&utm=x')
    expect(p).toEqual({ object: 'o1', menu: 'm1', biz: 'b', mode: 'update', id: '9' })
    expect(Object.keys(p).sort()).toEqual([...BOOTSTRAP_URL_KEYS].sort())
  })

  it('缺键就不出现（不是空串）——"没传"与"传了空"必须可区分', () => {
    expect(readUrlParams('?object=')).toEqual({ object: '' })
    expect(readUrlParams('')).toEqual({})
  })
})

describe('page-bootstrap · 装配与降级', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('未提供来源 ⇒ fromServer=false，且告警要点名受影响的占位家族', async () => {
    const warn = vi.fn()
    const bs = await loadPageBootstrap({ search: '?object=o1', warn })
    expect(bs.fromServer).toBe(false)
    expect(bs.url).toEqual({ object: 'o1' })
    const msg = warn.mock.calls[0][0] as string
    expect(msg).toContain('未提供服务端引导数据来源')
    expect(msg).toContain('object.*')
    expect(msg).toContain('btn.ui')
    expect(msg).toContain('loginUser.*')
  })

  it('来源成功 ⇒ 字段按旧 setAttr 名拆开，fromServer=true', async () => {
    const warn = vi.fn()
    const bs = await loadPageBootstrap({
      search: '?biz=b',
      warn,
      fetcher: async () =>
        JSON.stringify({
          state: 'ok',
          object: { code: 'eova_user_code', name: '用户' },
          menu: { code: 'm1', name: '菜单一' },
          btnList: [{ ui: '<button>新增</button>' }, { ui: '/eova/btn/x.js' }],
          loginUser: { isAdmin: true },
          isQuery: true
        })
    })
    expect(bs.fromServer).toBe(true)
    expect(bs.object).toEqual({ code: 'eova_user_code', name: '用户' })
    expect(bs.menu).toEqual({ code: 'm1', name: '菜单一' })
    // ★ btnList[].ui 保持字符串：一条 HTML 片段、一条 .js 路径（旧契约两种形态共存）
    expect(bs.btnList).toEqual([{ ui: '<button>新增</button>' }, { ui: '/eova/btn/x.js' }])
    expect(bs.loginUser).toEqual({ isAdmin: true })
    expect(bs.isQuery).toBe(true)
    expect(warn).not.toHaveBeenCalled()
  })

  it('来源抛错 ⇒ 降级且不抛（不阻塞页面）', async () => {
    const warn = vi.fn()
    const bs = await loadPageBootstrap({
      warn,
      fetcher: async () => {
        throw new Error('boom')
      }
    })
    expect(bs.fromServer).toBe(false)
    expect(warn.mock.calls[0][0]).toContain('拉取引导数据失败')
  })

  it('来源返回空 ⇒ 降级', async () => {
    const warn = vi.fn()
    expect((await loadPageBootstrap({ warn, fetcher: async () => null })).fromServer).toBe(false)
    expect(warn.mock.calls[0][0]).toContain('返回空')
  })

  it('非法 JSON / 非对象载荷 ⇒ 拒绝（不把脏数据当引导数据）', async () => {
    const warn = vi.fn()
    for (const bad of ['{bad', '[1,2]', '"str"']) {
      const bs = await loadPageBootstrap({ warn, fetcher: async () => bad })
      expect(bs.fromServer).toBe(false)
    }
    expect(warn).toHaveBeenCalledTimes(3)
  })

  it('state != ok ⇒ 按失败处理（沿用旧栈的 state 判定）', async () => {
    const warn = vi.fn()
    const bs = await loadPageBootstrap({
      warn,
      fetcher: async () => JSON.stringify({ state: 'fail', object: { code: 'x' } })
    })
    expect(bs.fromServer).toBe(false)
    expect(bs.object).toBeUndefined()
    expect(warn.mock.calls[0][0]).toContain('state=fail')
  })
})

describe('page-bootstrap · requireObjectCode 与 buttonListOf', () => {
  const base: PageBootstrap = { fromServer: false, url: {} }

  it('优先级：引导数据 object.code > URL 参数 object > 显式回退', () => {
    expect(
      requireObjectCode({ ...base, object: { code: 'from-server' }, url: { object: 'from-url' } }, 'fb')
    ).toBe('from-server')
    expect(requireObjectCode({ ...base, url: { object: 'from-url' } }, 'fb')).toBe('from-url')
    expect(requireObjectCode(base, 'fb')).toBe('fb')
  })

  it('都没有 ⇒ 抛错（拿空串拼 URL 只会 404 且难定位）', () => {
    expect(() => requireObjectCode(base)).toThrow(/缺少 object.code/)
    expect(() => requireObjectCode({ ...base, url: { object: '   ' } })).toThrow(/缺少 object.code/)
  })

  it('★ buttonListOf：未就绪（null）与"就绪但空（[]）"必须可区分', () => {
    expect(buttonListOf({ ...base, fromServer: false })).toBeNull()
    expect(buttonListOf({ ...base, fromServer: true })).toEqual([])
    const list = [{ ui: '<b/>' }]
    expect(buttonListOf({ ...base, fromServer: true, btnList: list })).toBe(list)
  })
})
