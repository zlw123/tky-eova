/**
 * 页面引导数据**消费端**的判据（第 124 轮）—— DES-004 §3.1 的请求侧 + 与 `loadPageBootstrap` 的接线。
 *
 * 钉的契约：
 *  ① 请求形态**逐条对齐设计**：`POST /api/page/bootstrap`，body 含 `path` 与
 *     **只有** `object`/`menu`/`biz`/`mode`/`id` 这 5 个键（不夹带别的）；
 *  ② `path` 取当前地址 pathname（注入点可替换）；
 *  ③ 响应体是对象或 JSON 串都接受（避免把"谁来解析"变成隐式约定）；
 *  ④ **非 2xx / 网络异常必须抛** ⇒ `loadPageBootstrap` 响亮告警并降级；
 *  ⑤ **默认来源**（`setDefaultBootstrapFetcher`）在未显式传 `fetcher` 时被使用；
 *     两者都没有 ⇒ 仍是原来的"未提供服务端引导数据来源"告警（**今天的行为不变**）；
 *  ⑥ 端点返回 `state !== 'ok'` ⇒ 按失败处理（沿用 `loadPageBootstrap` 既有语义）。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { BOOTSTRAP_ENDPOINT, createBootstrapFetcher, getDefaultBootstrapFetcher, setDefaultBootstrapFetcher } from '../page-bootstrap-fetcher'
import { loadPageBootstrap } from '../page-bootstrap'

/** 造 POST 探针 */
function makePost(status = 200, data: unknown = { state: 'ok' }) {
  return vi.fn(async (_url: string, _body: unknown) => ({ status, data }))
}

describe('createBootstrapFetcher（DES-004 §3.1 请求侧）', () => {
  afterEach(() => {
    setDefaultBootstrapFetcher(null)
  })

  it('① 请求形态：POST /api/page/bootstrap，body 只有 path + 约定的 5 个键', async () => {
    const post = makePost()
    const fetcher = createBootstrapFetcher({ post, path: () => '/app/meta_product' })
    await fetcher({ url: { object: 'meta_product', biz: 'menu_x', id: '7' } })
    expect(post).toHaveBeenCalledTimes(1)
    const [url, body] = post.mock.calls[0]
    // ★ 必须断言**字面量**（DES-004 §3.1 的原文），不能断言 `BOOTSTRAP_ENDPOINT` 常量 ——
    //   断常量会让"改常量"两边一起变、判据恒真（变异 M1"路径写成 /api/page/boot"实测未被捕获）。
    expect(url).toBe('/api/page/bootstrap')
    // 常量与字面量仍然相关（改常量而不改判据时，下面这条会红）
    expect(BOOTSTRAP_ENDPOINT).toBe('/api/page/bootstrap')
    expect(body).toEqual({ path: '/app/meta_product', object: 'meta_product', biz: 'menu_x', id: '7' })
    // ★ 不得夹带未约定的键（`mode` 允许，但没传就不该出现）
    expect(Object.keys(body as Record<string, unknown>).sort()).toEqual([
      'biz',
      'id',
      'object',
      'path'
    ])
  })

  it('① 5 个键全传时的顺序与内容（顺序固定便于日志比对）', async () => {
    const post = makePost()
    const fetcher = createBootstrapFetcher({ post, path: () => '/eova/meta/edit' })
    await fetcher({
      url: { object: 'o', menu: 'm', biz: 'b', mode: 'query', id: '1' }
    })
    const body = post.mock.calls[0][1] as Record<string, string>
    expect(Object.keys(body)).toEqual(['path', 'object', 'menu', 'biz', 'mode', 'id'])
    expect(body).toEqual({ path: '/eova/meta/edit', object: 'o', menu: 'm', biz: 'b', mode: 'query', id: '1' })
  })

  it('② path 默认取 window.location.pathname（注入点可替换）', async () => {
    const post = makePost()
    const fetcher = createBootstrapFetcher({ post })
    await fetcher({ url: {} })
    const body = post.mock.calls[0][1] as Record<string, string>
    expect(body.path).toBe(window.location.pathname)
  })

  it('③ 响应体是对象或 JSON 串都接受', async () => {
    const f1 = createBootstrapFetcher({
      post: makePost(200, { state: 'ok', object: { code: 'o' } }),
      path: () => '/x'
    })
    expect(JSON.parse((await f1({ url: {} })) as string)).toEqual({
      state: 'ok',
      object: { code: 'o' }
    })
    const raw = '{"state":"ok","menu":{"code":"m"}}'
    const f2 = createBootstrapFetcher({ post: makePost(200, raw), path: () => '/x' })
    expect(await f2({ url: {} })).toBe(raw)
  })

  it('④ 非 2xx ⇒ 抛错（消息里点名状态码），网络异常 ⇒ 原样抛', async () => {
    const f404 = createBootstrapFetcher({ post: makePost(404, ''), path: () => '/x' })
    await expect(f404({ url: {} })).rejects.toThrow(/HTTP 404/)
    const boom = new Error('ECONNREFUSED')
    const fErr = createBootstrapFetcher({
      post: vi.fn(async () => {
        throw boom
      }),
      path: () => '/x'
    })
    await expect(fErr({ url: {} })).rejects.toThrow('ECONNREFUSED')
  })
})

describe('与 loadPageBootstrap 的接线', () => {
  beforeEach(() => {
    setDefaultBootstrapFetcher(null)
  })
  afterEach(() => {
    setDefaultBootstrapFetcher(null)
  })

  it('⑤ 默认来源被使用：未显式传 fetcher 时也会请求端点并合并引导数据', async () => {
    const post = makePost(200, {
      state: 'ok',
      object: { code: 'meta_product', name: '产品' },
      menu: { code: 'menu_x', name: '产品管理', template: 'table' },
      btnList: [],
      loginUser: { isAdmin: true, id: 1 },
      isQuery: true
    })
    setDefaultBootstrapFetcher(createBootstrapFetcher({ post, path: () => '/app/menu_x' }))
    const warn = vi.fn()
    const bs = await loadPageBootstrap({ warn, search: '?object=meta_product' })
    expect(post).toHaveBeenCalledTimes(1)
    expect(bs.fromServer).toBe(true)
    expect(bs.object?.code).toBe('meta_product')
    expect(bs.menu?.template).toBe('table')
    expect(bs.isQuery).toBe(true)
    expect(warn).not.toHaveBeenCalled()
  })

  it('⑤ 端点失败（404）⇒ **响亮告警**且降级为"仅 URL 参数"（不静默假装成功）', async () => {
    setDefaultBootstrapFetcher(createBootstrapFetcher({ post: makePost(404, ''), path: () => '/x' }))
    const warn = vi.fn()
    const bs = await loadPageBootstrap({ warn, search: '?object=meta_product' })
    expect(bs.fromServer).toBe(false)
    expect(bs.url.object).toBe('meta_product')
    expect(warn).toHaveBeenCalledTimes(1)
    expect(String(warn.mock.calls[0][0])).toContain('拉取引导数据失败')
    expect(String(warn.mock.calls[0][0])).toContain('HTTP 404')
  })

  it('⑤ 既没显式传、也没装默认来源 ⇒ 仍是原来的"未提供"告警（今天的行为不变）', async () => {
    const warn = vi.fn()
    const bs = await loadPageBootstrap({ warn })
    expect(bs.fromServer).toBe(false)
    expect(String(warn.mock.calls[0][0])).toContain('未提供服务端引导数据来源')
    expect(getDefaultBootstrapFetcher()).toBeNull()
  })

  it('⑥ 端点返回 state!=="ok" ⇒ 按失败处理（既有语义）', async () => {
    setDefaultBootstrapFetcher(
      createBootstrapFetcher({ post: makePost(200, { state: 'no', msg: '无权访问' }), path: () => '/x' })
    )
    const warn = vi.fn()
    const bs = await loadPageBootstrap({ warn })
    expect(bs.fromServer).toBe(false)
    expect(String(warn.mock.calls[0][0])).toContain('state=no')
  })
})
