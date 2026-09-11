/**
 * 树表模版页的行为等价判据（旧 `_view/template/tree_table/index.html` + `index.js`）。
 *
 * 钉的契约（括号内是旧行号）：
 *  ① 结构：左 `.box` **200px**（tree 是 250px）内 ev-tree（`:multiple=false`、`:object="conf.tree_object_code"`）；
 *     右嵌套 `.zone`（`calc(100% - 10px - 200px)`）内查询盒 + 表格盒（`:object/:biz` 取 **`conf.object_code`**）；
 *  ② 数据源与 table 的差别：`ev-form`/`ev-table` 用 **`conf.object_code`**，而 handler 里的
 *     `uzoo.page.object_code` 仍来自引导数据（旧 `_page/list.html` 的 `#(object.code)`）；
 *  ③ `doResize`(37) ★ **不包 `nextTick`**（table 包了）⇒ 调用后**立即**生效；
 *  ④ `onTeeClick`(60)：文案是 **"选择 <节点名>"**（tree 是"编辑"）；写 `data[conf.tree_query_field] = node.id`
 *     并**立即 onQuery()**；★ **不设置 treeId**（tree 会设）；
 *  ⑤ `onAdd`(79) **没有 `?ref=`**、`onUpdate`(86) **没有 `&biz=`**（table/tree 都有其一）、
 *     `id` 取**硬编码 `row.id`**；`onDetail`(101) done 是**空函数**；
 *  ⑥ 未选行文案：修改/查看是「请先选择一行数据」，删除/隐藏是「请先选择数据」；
 *  ⑦ 删除/隐藏：确认文案不同、URL 表不同、`removeRows` 用 **`object_pk`**；
 *     ★ 两处 `.catch((e))` 引用未声明的 `error` ⇒ ReferenceError（137/165，既有缺陷）；
 *  ⑧ 导出气泡：文案 **导出XLS文件/导出CSV文件**、类型 `xls`/`csv`；
 *     ★ 旧 `index.js` **没有实现 `onExport`** ⇒ SPA 侧响亮告警且不导出（已声明适配）；
 *  ⑨ `uzoo.app` 的**键集合与旧实现逐字一致**（21 个，含既有死值 `treeId`/`auths`/`refForm`/`treeChecked`）。
 */
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi, type Mock } from 'vitest'
import axios from 'axios'
import TemplateTreeTable from '../TemplateTreeTable.vue'
import { getUzooApp, getUzooPage } from '@/compat/eova-ext'
import { setEovaMe, setEovaTools, type EovaMe, type EovaTools } from '@/compat/eova-runtime'
import { PAGE_URLS } from '@/compat/ui-urls'
import type { PageBootstrap } from '@/compat/page-bootstrap'

const routeState = vi.hoisted(() => ({ params: { menuCode: 'meta_hotel' } as Record<string, string> }))
vi.mock('vue-router', () => ({ useRoute: () => ({ params: routeState.params, query: {} }) }))
vi.mock('axios')

const post = axios.post as unknown as ReturnType<typeof vi.fn>

/** `me` 替身（`confirm` 立即执行回调） */
interface MeSpy {
  layer: Record<string, Mock>
  cross: Record<string, Mock>
  urls: Record<string, Mock>
}

/** 造 `me` 替身 */
function makeMe(): MeSpy {
  return {
    layer: {
      msg: vi.fn(),
      no: vi.fn(),
      wa: vi.fn(),
      open: vi.fn(),
      confirm: vi.fn((_m: string, cb: () => void) => cb())
    },
    cross: { on: vi.fn(), off: vi.fn(), emit: vi.fn() },
    urls: { url: vi.fn(() => '/api/form/update/x') }
  }
}

/** 造 `EovaTools` 替身 */
function makeTools(viewHeight = 800): EovaTools {
  return {
    isEmpty: (v: unknown) => v == null || (Array.isArray(v) && v.length === 0),
    dom: { getViewSize: () => ({ width: 1200, height: viewHeight }) },
    json: { toStr: (v: unknown) => JSON.stringify(v), toObj: (s: string) => JSON.parse(s) },
    str: {
      template: (tpl: string, params: Record<string, unknown>) =>
        tpl.replace(/\{\{([\w.]+)\}\}/g, (m, k: string) =>
          params[k] != null ? String(params[k]) : m
        )
    },
    axios: { download: vi.fn(() => Promise.resolve()) },
    log: vi.fn(),
    validate: { start: () => true, showMsg: () => '', addRules: vi.fn() }
  } as unknown as EovaTools
}

/**
 * 造引导数据
 *
 * ★ 关键：三个编码**刻意取不同值**，好让"数据取自哪一处"可分辨：
 *   - `conf.tree_object_code = 'meta_area'`  ⇒ `ev-tree` 的 `:object`（模板取证）；
 *   - `conf.object_code = 'meta_hotel'`      ⇒ `ev-form`/`ev-table` 的 `:object`/`:biz`（模板取证）；
 *   - `object.code = 'eova_object_code'`     ⇒ 写进 `uzoo.page.object_code`，**handler 读的是它**
 *     （旧 `_page/list.html` 的 `#(object.code)`）。
 *
 * ⚠️ 现实中后两者同值（`AppController#index()` 正是用 `menu_conf.object_code` 查元对象，
 *    再把该元对象 `set("object", …)`）⇒ 夹具的差异**只用于分辨代码路径**，不代表真库形态。
 */
function makeBootstrap(over: Partial<PageBootstrap> = {}): PageBootstrap {
  return {
    fromServer: true,
    url: {},
    menu: {
      code: 'meta_hotel',
      name: '酒店',
      id: 1301,
      template: 'tree_table',
      conf: {
        object_code: 'meta_hotel',
        tree_object_code: 'meta_area',
        tree_query_field: 'area_id',
        id: 'id',
        pid: 'pid',
        name: 'name',
        root: '1'
      }
    } as never,
    object: { code: 'eova_object_code', name: '元对象', pk: 'hotel_id', id: 11 } as never,
    btnList: [],
    loginUser: { isAdmin: true, id: 1 } as never,
    ...over
  }
}

/** `ev-*` 替身 + 实例方法探针 */
function makeStubs() {
  const tableApi = {
    query: vi.fn(),
    getSelectRows: vi.fn((): Record<string, unknown>[] => []),
    removeRows: vi.fn()
  }
  const EvTree = defineComponent({
    name: 'EvTree',
    props: ['object', 'multiple', 'conf', 'checked'],
    emits: ['node-click', 'update:checked'],
    setup: () => () => h('div', { class: 'stub-tree' })
  })
  const EvForm = defineComponent({
    name: 'EvForm',
    props: ['modelValue', 'mode', 'object', 'biz'],
    emits: ['submit', 'resize', 'ready'],
    setup: () => () => h('div', { class: 'stub-form' })
  })
  const EvTable = defineComponent({
    name: 'EvTable',
    props: ['object', 'biz', 'design', 'page', 'height', 'isEdit', 'where'],
    emits: ['row-click'],
    setup(_p, { expose, slots }) {
      expose(tableApi)
      return () => h('div', { class: 'stub-table' }, slots.toolbar ? [slots.toolbar()] : [])
    }
  })
  const EvPopup = defineComponent({
    name: 'EvPopup',
    props: ['trigger', 'placement'],
    setup: (_p, { slots }) => () =>
      h('span', { class: 'stub-popup' }, [slots.default?.(), slots.content?.()])
  })
  return {
    tableApi,
    EvTree,
    EvForm,
    EvTable,
    EvPopup,
    mountOpts: { global: { components: { EvTree, EvForm, EvTable, EvPopup } } }
  }
}

describe('TemplateTreeTable.vue（旧 template/tree_table 的行为等价）', () => {
  let me: MeSpy
  let tools: EovaTools
  let stubs: ReturnType<typeof makeStubs>

  beforeEach(() => {
    post.mockReset()
    routeState.params = { menuCode: 'meta_hotel' }
    ;(globalThis as unknown as Record<string, unknown>)['uzoo'] = { page: {}, vue: {}, app: {} }
    me = makeMe()
    tools = makeTools()
    setEovaMe(me as unknown as EovaMe)
    setEovaTools(tools)
    stubs = makeStubs()
  })

  /** 挂载页面 */
  function mountPage(over: Partial<PageBootstrap> = {}) {
    return mount(TemplateTreeTable, {
      props: { bootstrap: makeBootstrap(over) },
      ...stubs.mountOpts
    })
  }

  it('① 结构：左树盒 200px + ev-tree(:multiple=false) + 右嵌套 zone 的两个 box + 超管面板', () => {
    const w = mountPage()
    const outerZone = w.find('.eova-layout').find('.zone')
    const leftBox = outerZone.find(':scope > .box')
    expect(leftBox.attributes('style')?.replace(/\s+/g, ' ')).toContain('width: 200px')
    const tree = w.findComponent(stubs.EvTree)
    expect(tree.props('multiple')).toBe(false)
    const innerZone = outerZone.find(':scope > .zone')
    expect(innerZone.attributes('style')?.replace(/\s+/g, ' ')).toContain(
      'width: calc(100% - 210px)'
    )
    expect(innerZone.findAll(':scope > .box')).toHaveLength(2)
    expect(w.find('.eova-admins').exists()).toBe(true)
  })

  it('② 数据源：ev-tree 用 conf.tree_object_code、ev-form/ev-table 用 **conf.object_code**（不是 object.code）', () => {
    const w = mountPage()
    expect(w.findComponent(stubs.EvTree).props('object')).toBe('meta_area')
    expect(w.findComponent(stubs.EvForm).props('object')).toBe('meta_hotel')
    expect(w.findComponent(stubs.EvForm).props('biz')).toBe('meta_hotel')
    expect(w.findComponent(stubs.EvTable).props('object')).toBe('meta_hotel')
    expect(w.findComponent(stubs.EvTable).props('biz')).toBe('meta_hotel')
    // 而 `uzoo.page.object_code` 来自引导数据的 `object.code`（handler 里用的是它）——
    // `onMounted` 的前半段是**同步**执行的（唯一 await 在最后），故 mount 完即已写入
    expect(getUzooPage()['object_code']).toBe('eova_object_code')
    expect(getUzooPage()['object_code']).not.toBe('meta_hotel')
  })

  it('② ev-table 少了 table 的 is-edit/where/@row-click，ev-form 少了 @ready', () => {
    const w = mountPage()
    const table = w.findComponent(stubs.EvTable)
    expect(table.props('isEdit')).toBeFalsy()
    expect(table.props('where')).toBeUndefined()
    expect(table.props('design')).toBe(true)
    expect(table.props('page')).toEqual({ page: 1, limit: 15 })
    expect(table.props('height')).toBe(600)
  })

  it('③ doResize ★ 不包 nextTick：调用后**立即**生效（与 table 的差异）', () => {
    const w = mountPage()
    const vm = w.vm as never as {
      doResize: (h: number) => void
      queryHeight: number
      tableHeight: number
    }
    vm.doResize(100)
    // ★ 这里**不** await nextTick：若实现被"顺手"包成 nextTick，立即读就还是旧值（变异守卫）
    expect(vm.queryHeight).toBe(100)
    expect(vm.tableHeight).toBe(670)
  })

  it('③ ev-form 的 resize 接线到 doResize（模板里是 @resize="doResize"）', async () => {
    const w = mountPage()
    w.findComponent(stubs.EvForm).vm.$emit('resize', 88)
    await nextTick()
    expect((w.vm as never as { queryHeight: number }).queryHeight).toBe(88)
  })

  it('④ onTeeClick：文案「选择 <节点名>」+ 写 data[tree_query_field] + 立即查询 + **不设 treeId**', () => {
    const w = mountPage()
    const vm = w.vm as never as {
      onTeeClick: (n: unknown) => void
      data: Record<string, unknown>
      treeId: number
    }
    vm.onTeeClick({ id: 77, name: '湖北' })
    expect(me.layer.msg).toHaveBeenCalledWith('选择 湖北')
    expect(vm.data['area_id']).toBe(77)
    expect(stubs.tableApi.query).toHaveBeenCalledTimes(1)
    expect(stubs.tableApi.query.mock.calls[0][0]).toBe(vm.data)
    // ★ tree 模版会设 treeId，本页不设
    expect(vm.treeId).toBe(0)
  })

  it('④ onQuery 只用 data 调 refTable.query（没有 table 的 showLinking 步骤）', () => {
    const w = mountPage()
    const vm = w.vm as never as { onQuery: () => void; data: Record<string, unknown> }
    vm.onQuery()
    expect(stubs.tableApi.query).toHaveBeenCalledWith(vm.data)
  })

  it('⑤ onAdd：URL **没有 ?ref=**，弹层 720×720，done 里提示并重查', async () => {
    const w = mountPage()
    await flushPromises()
    ;(w.vm as never as { onAdd: () => void }).onAdd()
    const [title, url, lw, lh, done] = me.layer.open.mock.calls[0] as [string, string, number, number, () => void]
    expect(title).toBe('新增数据')
    expect(url).toBe('/app/add/eova_object_code')
    expect([lw, lh]).toEqual([720, 720])
    done()
    expect(me.layer.msg).toHaveBeenCalledWith('操作成功！')
    expect(stubs.tableApi.query).toHaveBeenCalledTimes(1)
  })

  it('⑤ onUpdate：URL **没有 &biz=**、id 取 row.id；未选行 ⇒ 文案「请先选择一行数据」', async () => {
    const w = mountPage()
    await flushPromises()
    const vm = w.vm as never as { onUpdate: () => void }
    vm.onUpdate()
    expect(me.layer.msg).toHaveBeenCalledWith('请先选择一行数据')
    expect(me.layer.open).not.toHaveBeenCalled()

    // 行里 id 与 object_pk 取值不同 ⇒ 能分辨用的是哪一个
    stubs.tableApi.getSelectRows.mockReturnValue([{ id: 'ROW-1', hotel_id: 'PK-9' }])
    vm.onUpdate()
    const [title, url] = me.layer.open.mock.calls[0] as [string, string]
    expect(title).toBe('修改数据')
    expect(url).toBe('/app/update/eova_object_code?id=ROW-1')
  })

  it('⑤ onDetail：标题「查看数据」且 done 是空函数', async () => {
    const w = mountPage()
    await flushPromises()
    stubs.tableApi.getSelectRows.mockReturnValue([{ id: 'ROW-1' }])
    ;(w.vm as never as { onDetail: () => void }).onDetail()
    const [title, url, , , done] = me.layer.open.mock.calls[0] as [string, string, number, number, () => void]
    expect(title).toBe('查看数据')
    expect(url).toBe('/app/detail/eova_object_code?id=ROW-1')
    me.layer.msg.mockClear()
    stubs.tableApi.query.mockClear()
    done()
    expect(me.layer.msg).not.toHaveBeenCalled()
    expect(stubs.tableApi.query).not.toHaveBeenCalled()
  })

  it('⑥⑦ onDelete：确认文案 + urls.form.delete + removeRows 用 object_pk；未选行文案与修改不同', async () => {
    const w = mountPage()
    await flushPromises()
    const vm = w.vm as never as { onDelete: () => void; onHide: () => void }
    vm.onDelete()
    expect(me.layer.msg).toHaveBeenCalledWith('请先选择数据')

    post.mockResolvedValue({ data: { state: 'ok' } })
    stubs.tableApi.getSelectRows.mockReturnValue([{ id: 1, hotel_id: 'PK-9' }])
    vm.onDelete()
    expect(me.layer.confirm.mock.calls[0][0]).toBe('确认彻底删除, 不可恢复')
    await flushPromises()
    expect(post).toHaveBeenCalledWith('/api/form/delete/eova_object_code', {
      rows: [{ id: 1, hotel_id: 'PK-9' }]
    })
    expect(stubs.tableApi.removeRows).toHaveBeenCalledWith(['PK-9'])
    expect(me.layer.msg).toHaveBeenCalledWith('删除成功')

    // 非 ok ⇒ msg(ret.msg)
    post.mockResolvedValue({ data: { state: 'no', msg: '有引用' } })
    me.layer.msg.mockClear()
    stubs.tableApi.removeRows.mockClear()
    vm.onDelete()
    await flushPromises()
    expect(me.layer.msg).toHaveBeenCalledWith('有引用')
    expect(stubs.tableApi.removeRows).not.toHaveBeenCalled()
  })

  it('⑦ onHide：确认文案「确认删除」+ urls.form.hide', async () => {
    post.mockResolvedValue({ data: { state: 'ok' } })
    const w = mountPage()
    await flushPromises()
    stubs.tableApi.getSelectRows.mockReturnValue([{ id: 2, hotel_id: 'PK-2' }])
    ;(w.vm as never as { onHide: () => void }).onHide()
    expect(me.layer.confirm.mock.calls[0][0]).toBe('确认删除')
    await flushPromises()
    expect(post).toHaveBeenCalledWith('/api/form/hide/eova_object_code', {
      rows: [{ id: 2, hotel_id: 'PK-2' }]
    })
    expect(PAGE_URLS.form.hide).toBe('/api/form/hide/{{object_code}}')
  })

  it('⑦ ★ 网络异常 ⇒ `.catch` 里 `error` 未声明 ⇒ ReferenceError，且不弹提示（既有缺陷）', async () => {
    post.mockRejectedValue(new Error('boom'))
    const w = mountPage()
    await flushPromises()
    stubs.tableApi.getSelectRows.mockReturnValue([{ id: 1, hotel_id: 'PK-9' }])
    const captured: unknown[] = []
    const prev = process.listeners('unhandledRejection')
    process.removeAllListeners('unhandledRejection')
    process.on('unhandledRejection', (e) => captured.push(e))
    try {
      ;(w.vm as never as { onDelete: () => void }).onDelete()
      await flushPromises()
      await new Promise((r) => setTimeout(r, 0))
    } finally {
      process.removeAllListeners('unhandledRejection')
      for (const l of prev) {
        process.on('unhandledRejection', l as never)
      }
    }
    expect(captured).toHaveLength(1)
    expect(String(captured[0])).toContain('error is not defined')
    expect(me.layer.msg).not.toHaveBeenCalledWith(expect.stringContaining('客户端请求异常'))
  })

  it('⑧ 导出气泡：文案「导出XLS文件/导出CSV文件」、类型 xls/csv', () => {
    const w = mountPage()
    const lis = w.findComponent(stubs.EvTable).findAll('.eova-select_items li')
    expect(lis.map((li) => li.text())).toEqual(['导出XLS文件', '导出CSV文件'])
    expect(w.findComponent(stubs.EvTable).find('i').classes()).toContain('eova-icon-export')
  })

  it('⑧ ★ onExport：旧实现没有它 ⇒ 响亮告警且**不导出**（不静默假装成功）', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const w = mountPage()
    const lis = w.findComponent(stubs.EvTable).findAll('.eova-select_items li')
    await lis[0].trigger('click')
    expect(warn.mock.calls.some((c) => String(c[0]).includes('没有 onExport'))).toBe(true)
    // 制品下载器**不得**被调用（旧栈此处是 ReferenceError ⇒ 什么都不发生）
    expect((tools.axios.download as unknown as Mock).mock.calls).toHaveLength(0)
    warn.mockRestore()
  })

  it('⑨ uzoo.page：code/template=tree_table/form=query + 引导数据到达后的 8 项', async () => {
    const w = mountPage()
    expect(getUzooPage()['code']).toBe('meta_hotel')
    expect(getUzooPage()['template']).toBe('tree_table')
    expect(getUzooPage()['form']).toBe('query')
    await flushPromises()
    const page = getUzooPage()
    expect(page['menu_id']).toBe(1301)
    expect(page['menu_code']).toBe('meta_hotel')
    expect(page['object_id']).toBe(11)
    expect(page['object_code']).toBe('eova_object_code')
    expect(page['object_pk']).toBe('hotel_id')
    expect(page['menu_conf']).toMatchObject({ tree_object_code: 'meta_area' })
    void w
  })

  it('⑨ uzoo.app 的键集合与旧实现逐字一致（21 个，含既有死值）', () => {
    mountPage()
    expect(Object.keys(getUzooApp()).sort()).toEqual(
      [
        'data',
        'conf',
        'auths',
        'refTable',
        'queryHeight',
        'tableHeight',
        'page',
        'refTree',
        'refForm',
        'treeId',
        'treeConf',
        'treeChecked',
        'onQuery',
        'onTeeClick',
        'onAdd',
        'onUpdate',
        'onDelete',
        'onDetail',
        'onHide',
        'onImport',
        'doResize'
      ].sort()
    )
  })

  it('⑨ treeConf 映射与 tree 相同（title←conf.name，icon/spread 缺省空串）', () => {
    const w = mountPage()
    expect(w.findComponent(stubs.EvTree).props('conf')).toEqual({
      root: '1',
      id: 'id',
      pid: 'pid',
      title: 'name',
      icon: '',
      spread: ''
    })
  })

  it('⑨ onMounted 只打日志 + 注入 .js 按钮脚本；onImport 仍是旧栈的 stub', async () => {
    const log = vi.spyOn(console, 'log').mockImplementation(() => {})
    const w = mountPage({
      btnList: [{ id: 1, name: '自定义', event: 'test', ui: '/demo/test/btn.js', is_base: 0 }]
    })
    await flushPromises()
    expect(log.mock.calls.map((c) => String(c[0]))).toContain('tree_table js Mounted')
    const srcs = Array.from(document.querySelectorAll('script[src]')).map((s) =>
      s.getAttribute('src')
    )
    expect(srcs).toContain('/demo/test/btn.js')

    ;(w.vm as never as { onImport: () => void }).onImport()
    expect(log).toHaveBeenCalledWith('导入')
    expect(me.layer.msg).toHaveBeenCalledWith('待实现...')
    log.mockRestore()
  })

  it('toolbar：内置按钮点击走页面方法表（onQuery）', async () => {
    const w = mountPage({
      btnList: [{ id: 1, name: '查询', event: 'onQuery', icon: 'i', style: '', is_base: 1 }]
    })
    await w.findComponent(stubs.EvTable).find('.eova-tools_box button').trigger('click')
    expect(stubs.tableApi.query).toHaveBeenCalledTimes(1)
  })
})
