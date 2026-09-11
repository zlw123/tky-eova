/**
 * 元字段个性化页的行为等价判据（旧 `_view/meta/field/index.js` + `index.html` + `MetaController#field()`）。
 *
 * 钉的契约：
 *  ① `mode` 是 **URL 参数且默认 `query`**（`get("mode","query")`）；
 *  ② `page.limit = 99999` —— **这个值就是"关掉分页组件"的开关**（`EvTable` 的条件是 `limit < 99999`）；
 *  ③ `tableHeight = 视口高 - 65`（**减 65**，不是别的页面的 30/0）；
 *  ④ 4 个页签的 id/title 与各自 `where.mode` 逐字一致，表 props 除 `where` 外相同；
 *  ⑤ `where.object_code` 取元对象编码（引导数据 > URL 参数），缺失时**告警**（不静默查全表）；
 *  ⑥ `onBeforeMount` 回调扩展钩子；`query()` 把 `form` 交给表格。
 */
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MetaField from '../MetaField.vue'
import { setEovaTools, type EovaTools } from '@/compat/eova-runtime'
import { pickBootstrapString, resolveObjectCode } from '@/compat/page-bootstrap'
import { resetUzooWarning } from '@/compat/eova-ext'

// 查询串可逐用例改写
const routeState = vi.hoisted(() => ({ query: {} as Record<string, string> }))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: routeState.query }) }))

/** 造 `EovaTools` 替身（本页只用到 `dom.getViewSize`） */
function makeTools(viewHeight: number): EovaTools {
  return {
    isEmpty: (v: unknown) => v == null || String(v).trim() === '',
    dom: { getViewSize: () => ({ width: 1200, height: viewHeight }) },
    validate: { start: () => true, showMsg: () => '', addRules: vi.fn() }
  } as unknown as EovaTools
}

/** `ev-tab` / `ev-tab-item` / `ev-table` 替身（真实组件由 legacy 制品在装配期注册） */
const EvTabStub = {
  name: 'EvTab',
  props: ['modelValue', 'type'],
  emits: ['update:modelValue'],
  template: '<div class="eova-tab"><slot /></div>'
}
const EvTabItemStub = {
  name: 'EvTabItem',
  props: ['id', 'title'],
  template: '<div class="eova-tab-item" :data-id="id" :data-title="title"><slot /></div>'
}
const EvTableStub = {
  name: 'EvTable',
  props: ['object', 'biz', 'where', 'height', 'isEdit', 'page', 'size'],
  template: '<div class="eova-table-view" />'
}
const mountOpts = {
  global: { components: { EvTab: EvTabStub, EvTabItem: EvTabItemStub, EvTable: EvTableStub } }
}

describe('MetaField.vue（旧 _view/meta/field 的行为等价）', () => {
  beforeEach(() => {
    resetUzooWarning()
    delete (globalThis as unknown as Record<string, unknown>)['uzoo']
    routeState.query = { object: 'eova_user_code', mode: 'update' }
    setEovaTools(makeTools(800))
  })

  it('★ mode 取 URL 参数；缺省为 query（旧 `get("mode","query")`）', () => {
    expect((mount(MetaField, mountOpts).vm as never as { mode: string }).mode).toBe('update')
    routeState.query = { object: 'eova_user_code' }
    expect((mount(MetaField, mountOpts).vm as never as { mode: string }).mode).toBe('query')
  })

  it('currTab 由 mode 决定（旧钩子 `uzoo.app.currTab.value = \'#(mode)\'`）', () => {
    const w = mount(MetaField, mountOpts)
    expect((w.vm as never as { currTab: string }).currTab).toBe('update')
  })

  it('★ page.limit = 99999（这个值就是"关掉分页组件"的开关）', () => {
    const w = mount(MetaField, mountOpts)
    expect((w.vm as never as { page: { page: number; limit: number } }).page).toEqual({
      page: 1,
      limit: 99999
    })
  })

  it('★ tableHeight = 视口高 - 65（不是 30、不是 0）', () => {
    setEovaTools(makeTools(800))
    const w = mount(MetaField, mountOpts)
    expect((w.vm as never as { tableHeight: number }).tableHeight).toBe(800 - 65)
  })

  it('objectCode：URL 参数 object（引导端点未就绪时的回退）', () => {
    const w = mount(MetaField, mountOpts)
    expect((w.vm as never as { objectCode: string }).objectCode).toBe('eova_user_code')
    expect((w.vm as never as { form: { object_code: string } }).form.object_code).toBe(
      'eova_user_code'
    )
  })

  it('★★ 优先级可判：引导值 > URL 参数（两侧都给值且不同 ⇒ 顺序颠倒会被抓）', () => {
    // 组件内部用的是同一个纯函数；此处直接对纯函数判"顺序"，
    // 因为组件测试里引导数据恒为空（端点未就绪），**永远测不出顺序**
    expect(pickBootstrapString('from-server', 'from-url')).toBe('from-server')
    expect(pickBootstrapString(undefined, 'from-url')).toBe('from-url')
    expect(pickBootstrapString('   ', 'from-url')).toBe('from-url')
    expect(pickBootstrapString(undefined, '')).toBe('')
    expect(pickBootstrapString(null, undefined)).toBe('')
    expect(pickBootstrapString(0, 'x')).toBe('0') // 数字 0 是"有值"，不得当空
  })

  it('★★ 来源可判：resolveObjectCode 的 source 让"优先级颠倒"变成可观测', () => {
    const empty = { fromServer: true, url: {} } as never
    // 两侧都有且不同 ⇒ 必须取引导值，且 source 明确
    expect(resolveObjectCode({ fromServer: true, url: {}, object: { code: 'S' } } as never, 'U')).toEqual(
      { code: 'S', source: 'bootstrap' }
    )
    expect(resolveObjectCode(empty, 'U')).toEqual({ code: 'U', source: 'url' })
    expect(resolveObjectCode(empty, '', 'F')).toEqual({ code: 'F', source: 'fallback' })
    expect(resolveObjectCode(empty)).toEqual({ code: '', source: 'missing' })
    // 空白值不算有值（逐层跳过）
    expect(resolveObjectCode({ fromServer: true, url: {}, object: { code: '   ' } } as never, 'U')).toEqual(
      { code: 'U', source: 'url' }
    )
  })

  it('★ objectCode 缺失时响亮告警（不静默查全表）', () => {
    routeState.query = {}
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const w = mount(MetaField, mountOpts)
    expect((w.vm as never as { objectCode: string }).objectCode).toBe('')
    expect(warn.mock.calls.some((c) => String(c[0]).includes('[meta-field] 缺少 object.code'))).toBe(
      true
    )
    warn.mockRestore()
  })

  it('结构：4 个页签 id/title 逐字，且各自 where.mode 对应', async () => {
    const w = mount(MetaField, mountOpts)
    await w.vm.$nextTick()
    const items = w.findAll('.eova-tab-item')
    expect(items.map((i) => i.attributes('data-id'))).toEqual(['query', 'create', 'update', 'read'])
    expect(items.map((i) => i.attributes('data-title'))).toEqual(['查询', '新增', '修改', '详情'])
    const tables = w.findAllComponents(EvTableStub)
    expect(tables).toHaveLength(4)
    expect(tables.map((t) => (t.props('where') as { mode: string }).mode)).toEqual([
      'query',
      'create',
      'update',
      'read'
    ])
    // 除 where 外的 props 四表一致（与旧实现相同）
    for (const t of tables) {
      expect(t.props('object')).toBe('eova_field_diy')
      expect(t.props('biz')).toBe('eova_field_diy')
      expect(t.props('size')).toBe('s30')
      expect(t.props('isEdit')).toBe(true)
      expect(t.props('height')).toBe(800 - 65)
    }
    // where.object_code 四表都取同一个元对象编码
    for (const t of tables) {
      expect((t.props('where') as { object_code: string }).object_code).toBe('eova_user_code')
    }
  })

  it('★ onBeforeMount 回调扩展钩子 uzoo.vue.mountBefore（本页 index.html:79 正是定义它的一侧）', () => {
    const hook = vi.fn()
    ;(globalThis as unknown as Record<string, unknown>)['uzoo'] = {
      page: {},
      vue: { mountBefore: hook },
      app: {}
    }
    mount(MetaField, mountOpts)
    expect(hook).toHaveBeenCalledTimes(1)
  })

  it('★ query()：把 form 交给表格的 query（本页 form 就是 where 的来源）', () => {
    const tableQuery = vi.fn()
    const w = mount(MetaField, mountOpts)
    ;(w.vm as never as { tableRef: unknown }).tableRef = { query: tableQuery }
    ;(w.vm as never as { query: () => void }).query()
    expect(tableQuery).toHaveBeenCalledTimes(1)
    expect(tableQuery.mock.calls[0][0]).toEqual({ object_code: 'eova_user_code' })
  })

  it('引导数据到位后补齐 form.object_code（首帧缺失时的补偿路径）', async () => {
    routeState.query = { mode: 'query' }
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const w = mount(MetaField, mountOpts)
    await flushPromises()
    await flushPromises()
    // 端点未就绪 ⇒ 无引导数据 ⇒ 仍为空（诚实降级），但不应崩
    expect((w.vm as never as { form: { object_code: string } }).form.object_code).toBe('')
    warn.mockRestore()
  })

  it('uzoo.page.object_code 被写入（旧实现由 list partial 赋值，本页保持全局可读）', () => {
    mount(MetaField, mountOpts)
    const uzoo = (globalThis as unknown as Record<string, unknown>)['uzoo'] as {
      page: Record<string, unknown>
    }
    expect(uzoo.page['object_code']).toBe('eova_user_code')
  })
})
