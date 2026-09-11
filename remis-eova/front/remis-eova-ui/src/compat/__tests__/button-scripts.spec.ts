/**
 * 按钮脚本加载器的判据（第 115 轮，机制取证见 `button-mechanism-contract.json`）。
 *
 * 钉的契约：
 *  ① **只**处理 `ui` 以 `.js` 结尾（含 `.js` 子串，与旧栈 `btn.ui.contains(".js")` 同义）；
 *  ② 其余 `ui`（`'query'`/`'#'`/NULL/`'*.html'` **内置标记**）一律**跳过** —— 那些 html 文件在旧树里不存在；
 *  ③ 去重且保持顺序；幂等（模块级去重表）；
 *  ④ 失败**不中断**，但**告警点名 URL 与按钮**（既有数据缺陷：2 条 ui 指向不存在的脚本）；
 *  ⑤ 默认加载方式是**经典** `<script src>`（**不加 `type="module"`**）—— 派发器要求处理函数挂在 `window` 上。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  collectButtonScriptUrls,
  loadButtonScripts,
  resetLoadedButtonScripts
} from '../button-scripts'

/** 造一个"记录调用"的假加载器 */
function fakeLoader(failOn: string[] = []) {
  const calls: string[] = []
  return {
    calls,
    loadScript: async (url: string) => {
      calls.push(url)
      if (failOn.includes(url)) {
        throw new Error('404')
      }
    }
  }
}

describe('button-scripts · 筛选规则', () => {
  it('★ 只收 `.js`（与旧栈 `btn.ui.contains(".js")` 同义）', () => {
    expect(
      collectButtonScriptUrls([
        { id: 1, name: '新增', ui: '/eova/_view/menu/add/btn.js' },
        { id: 2, name: '查询', ui: 'query' },
        { id: 3, name: '修改', ui: '/eova/widget/form/btn/update.html' },
        { id: 4, name: '删除', ui: null },
        { id: 5, name: '无 ui' },
        { id: 6, name: '井号', ui: '#' }
      ])
    ).toEqual(['/eova/_view/menu/add/btn.js'])
  })

  it('★ 内置标记一律跳过（**包括 .html**：那些文件在旧树里不存在，不是片段）', () => {
    const urls = collectButtonScriptUrls([
      { id: 1, ui: '/eova/template/single/btn/delete.html' },
      { id: 2, ui: '/eova/widget/form/btn/add.html' },
      { id: 3, ui: '/eova/template/singletree/btn/add.html' },
      { id: 4, ui: 'query' }
    ])
    expect(urls).toEqual([])
  })

  it('★★ 判据是 contains（不是 endsWith）：带查询串/带后缀的写法**仍要加载**', () => {
    // 旧栈 `btn.ui.contains(".js")` ⇒ `/…/btn.js?v=1.0` 命中；若收窄成 endsWith 就会漏掉它
    expect(
      collectButtonScriptUrls([
        { ui: '/eova/_view/menu/add/btn.js?v=1.0' },
        { ui: '/eova/_view/x/btn.js?v=1.2' }
      ])
    ).toEqual(['/eova/_view/menu/add/btn.js?v=1.0', '/eova/_view/x/btn.js?v=1.2'])
    // 反例：`.js` 出现在中间（真实数据里少见，但 contains 语义下同样命中）
    expect(collectButtonScriptUrls([{ ui: '/a.js/x' }])).toEqual(['/a.js/x'])
  })

  it('非字符串 ui（数字/对象/数组）不参与', () => {
    expect(collectButtonScriptUrls([{ id: 1, ui: 1 }, { id: 2, ui: {} }, { id: 3, ui: [] }])).toEqual(
      []
    )
  })

  it('去重且保持首次出现顺序', () => {
    expect(
      collectButtonScriptUrls([
        { ui: '/a.js' },
        { ui: '/b.js' },
        { ui: '/a.js' }
      ])
    ).toEqual(['/a.js', '/b.js'])
  })

  it('非数组入参 ⇒ 空（不抛）', () => {
    expect(collectButtonScriptUrls(null)).toEqual([])
    expect(collectButtonScriptUrls(undefined)).toEqual([])
  })
})

describe('button-scripts · 加载行为', () => {
  beforeEach(() => {
    resetLoadedButtonScripts()
  })

  afterEach(() => {
    resetLoadedButtonScripts()
    vi.restoreAllMocks()
  })

  it('按 btnList 顺序加载并返回实际发起的 URL', async () => {
    const f = fakeLoader()
    const fired = await loadButtonScripts(
      [{ ui: '/a.js' }, { ui: 'query' }, { ui: '/b.js' }],
      { loadScript: f.loadScript }
    )
    expect(fired).toEqual(['/a.js', '/b.js'])
    expect(f.calls).toEqual(['/a.js', '/b.js'])
  })

  it('★ 幂等：同一 URL 只加载一次（第二次调用不再发起）', async () => {
    const f1 = fakeLoader()
    await loadButtonScripts([{ ui: '/a.js' }], { loadScript: f1.loadScript })
    const f2 = fakeLoader()
    const fired = await loadButtonScripts([{ ui: '/a.js' }], { loadScript: f2.loadScript })
    expect(f1.calls).toEqual(['/a.js'])
    expect(fired).toEqual([])
    expect(f2.calls).toEqual([])
  })

  it('★ 失败不中断：后续脚本继续加载，且告警点名 URL 与按钮', async () => {
    const f = fakeLoader(['/bad.js'])
    const warn = vi.fn()
    const fired = await loadButtonScripts(
      [
        { id: 7, name: '覆盖同步', ui: '/bad.js' },
        { id: 8, name: '增量同步', ui: '/good.js' }
      ],
      { loadScript: f.loadScript, warn }
    )
    expect(fired).toEqual(['/bad.js', '/good.js'])
    expect(f.calls).toEqual(['/bad.js', '/good.js'])
    expect(warn).toHaveBeenCalledTimes(1)
    const msg = warn.mock.calls[0][0] as string
    expect(msg).toContain('/bad.js')
    expect(msg).toContain('覆盖同步#7')
    expect(msg).toContain('旧栈此处同样是 404')
  })

  it('★ 已取证的既有数据缺陷：那两条 ui 必然失败（断言告警里点得出名字）', async () => {
    const f = fakeLoader(['/eova/_view/meta/override/btn.js', '/eova/_view/meta/syncnew/btn.js'])
    const warn = vi.fn()
    await loadButtonScripts(
      [
        { id: 1841, name: '覆盖同步', ui: '/eova/_view/meta/override/btn.js' },
        { id: 1842, name: '增量同步', ui: '/eova/_view/meta/syncnew/btn.js' }
      ],
      { loadScript: f.loadScript, warn }
    )
    expect(warn).toHaveBeenCalledTimes(2)
    const all = warn.mock.calls.map((c) => String(c[0])).join('\n')
    expect(all).toContain('覆盖同步#1841')
    expect(all).toContain('增量同步#1842')
  })

  it('★ 默认加载器插的是**经典** script（不带 type="module"）', async () => {
    const docs: HTMLScriptElement[] = []
    const realCreate = document.createElement.bind(document)
    const spy = vi.spyOn(document, 'createElement').mockImplementation((tag: string) => {
      const el = realCreate(tag)
      if (tag === 'script') {
        docs.push(el as HTMLScriptElement)
      }
      return el
    })
    // 不 await（真实加载在 jsdom 里不会触发 onload/onerror）
    void loadButtonScripts([{ ui: '/classic.js' }], { warn: () => undefined })
    spy.mockRestore()
    expect(docs).toHaveLength(1)
    expect(docs[0].getAttribute('type')).toBeNull()
    expect(docs[0].src).toContain('/classic.js')
    expect(docs[0].async).toBe(false)
  })
})
