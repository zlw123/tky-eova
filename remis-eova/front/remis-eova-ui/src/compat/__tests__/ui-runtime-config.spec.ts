/**
 * UI 运行时配置的判据（第 103 轮）
 *
 * 两张表 + 一个 conf 接缝，钉的都是"错了会静默坏掉"的点：
 *  - `window.urls` 必须**挂回全局**且**不覆盖**既有值；`pageUrl` 缺键必须抛错（不返回空串）；
 *  - 表内容与旧内联脚本**逐字符相同**（含 `form.validate` 无占位、`meta.form` 带 `?mode=` 这两个异类）；
 *  - `me.conf` 装配走 `putAll`；非法载荷/来源失败**只告警不阻塞**；未提供来源时**必须有可诊断告警**。
 */
import { afterEach, describe, expect, it, vi } from 'vitest'
import { PAGE_URLS, installWindowUrls, pageUrl } from '../ui-urls'
import { applyUiConf, loadUiConf, type EovaConf } from '../ui-conf'

describe('ui-urls · 页面级 URL 表（旧 window.urls）', () => {
  it('表内容与旧内联脚本逐字一致（_view/_block/meta.html:12-29）', () => {
    expect(PAGE_URLS).toEqual({
      meta: {
        table: '/api/meta/table/{{object}}',
        form: '/api/meta/form/{{object}}?mode={{mode}}',
        query: '/api/meta/query/{{object}}',
        option: '/api/meta/option/{{option}}',
        setting: '/api/meta/setting/{{biz}}'
      },
      form: {
        data: '/api/form/data/{{object_code}}?pk={{pk}}',
        delete: '/api/form/delete/{{object_code}}',
        hide: '/api/form/hide/{{object_code}}',
        add: '/api/form/add/{{object_code}}',
        update: '/api/form/update/{{object_code}}',
        detail: '/api/form/detail/{{object_code}}',
        validate: '/api/meta/validate'
      }
    })
  })

  it('form.validate 是【无占位】的全路径（与同组其它项不同，别"统一"成 {{object_code}}）', () => {
    expect(PAGE_URLS.form.validate).toBe('/api/meta/validate')
    expect(PAGE_URLS.form.validate).not.toContain('{{')
  })

  it('两层的键集合与数量固定（多一个 = 宣称一个旧栈没有的契约）', () => {
    expect(Object.keys(PAGE_URLS)).toEqual(['meta', 'form'])
    expect(Object.keys(PAGE_URLS.meta).sort()).toEqual([
      'form',
      'option',
      'query',
      'setting',
      'table'
    ])
    expect(Object.keys(PAGE_URLS.form).sort()).toEqual([
      'add',
      'data',
      'delete',
      'detail',
      'hide',
      'update',
      'validate'
    ])
  })

  it('installWindowUrls 挂回全局（旧栈的读法：全局 urls）', () => {
    const target: Record<string, unknown> = {}
    expect(installWindowUrls(target)).toBe(true)
    expect(target['urls']).toBe(PAGE_URLS)
  })

  it('installWindowUrls 【不覆盖】既有全局（覆盖会让"哪份表生效"不可判定）', () => {
    const existing = { meta: {}, form: {} }
    const target: Record<string, unknown> = { urls: existing }
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    expect(installWindowUrls(target)).toBe(false)
    expect(target['urls']).toBe(existing)
    expect(warn).toHaveBeenCalled()
    warn.mockRestore()
  })

  it('pageUrl 取项；缺键【抛错】而不是返回空串', () => {
    expect(pageUrl('form', 'delete')).toBe('/api/form/delete/{{object_code}}')
    expect(pageUrl('meta', 'table')).toBe('/api/meta/table/{{object}}')
    expect(() => pageUrl('form', 'nope')).toThrow(/未知的 URL 项/)
    expect(() => pageUrl('nope' as never, 'x')).toThrow(/未知的 URL 项/)
  })
})

describe('ui-conf · me.conf 装配接缝', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  /** 造 me.conf 替身 */
  function makeConf() {
    return { putAll: vi.fn(), get: vi.fn() } as unknown as EovaConf
  }

  it('applyUiConf 走 putAll（旧写法就是 me.conf.putAll(\'<json>\')）', () => {
    const conf = makeConf()
    const json = '{"web_cdn":"http://cdn","ui.skin":"ele"}'
    expect(applyUiConf({ conf }, json)).toBe(true)
    expect(conf.putAll).toHaveBeenCalledWith(json)
  })

  it('applyUiConf：缺失 putAll 时【告警并返回 false】，不抛错（不阻塞启动）', () => {
    const warn = vi.fn()
    expect(applyUiConf({}, '{}', warn)).toBe(false)
    expect(warn).toHaveBeenCalledWith(expect.stringContaining('me.conf.putAll 不存在'))
  })

  it('applyUiConf：非法/非对象载荷【拒绝写入】（别把数组或标量塞进 me.conf）', () => {
    const conf = makeConf()
    const warn = vi.fn()
    expect(applyUiConf({ conf }, '[1,2]', warn)).toBe(false)
    expect(applyUiConf({ conf }, '"str"', warn)).toBe(false)
    expect(applyUiConf({ conf }, '{bad json', warn)).toBe(false)
    expect(conf.putAll).not.toHaveBeenCalled()
    expect(warn).toHaveBeenCalledTimes(3)
  })

  it('loadUiConf：来源返回 JSON ⇒ 写入 me.conf', async () => {
    const conf = makeConf()
    const ok = await loadUiConf({
      me: { conf },
      fetcher: async () => '{"web_file":"http://f"}'
    })
    expect(ok).toBe(true)
    expect(conf.putAll).toHaveBeenCalledWith('{"web_file":"http://f"}')
  })

  it('loadUiConf：【未提供来源】必须有可诊断告警（点名 EvUpload 的具体后果）', async () => {
    const warn = vi.fn()
    const conf = makeConf()
    expect(await loadUiConf({ me: { conf }, warn })).toBe(false)
    const msg = warn.mock.calls[0][0] as string
    expect(msg).toContain('未提供 conf 来源')
    expect(msg).toContain('EvUpload')
    expect(conf.putAll).not.toHaveBeenCalled()
  })

  it('loadUiConf：来源抛错 ⇒ 告警且不抛（应用可用性优先）', async () => {
    const warn = vi.fn()
    const conf = makeConf()
    const ok = await loadUiConf({
      me: { conf },
      warn,
      fetcher: async () => {
        throw new Error('boom')
      }
    })
    expect(ok).toBe(false)
    expect(warn.mock.calls[0][0]).toContain('拉取 conf 失败')
  })

  it('loadUiConf：来源返回空 ⇒ 告警且不写入（端点未上线时的预期路径）', async () => {
    const warn = vi.fn()
    const conf = makeConf()
    expect(await loadUiConf({ me: { conf }, warn, fetcher: async () => null })).toBe(false)
    expect(warn.mock.calls[0][0]).toContain('返回空')
    expect(conf.putAll).not.toHaveBeenCalled()
  })
})
