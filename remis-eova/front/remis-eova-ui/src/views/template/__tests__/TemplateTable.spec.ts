/**
 * 单表模版页的行为等价判据（旧 `_view/template/table/index.html` + `index.js`）。
 *
 * 钉的契约（逐条对应旧实现，括号内是旧行号）：
 *  ① 结构：两个 `.box`（查询/表格）的样式串逐字；`ev-form`/`ev-table` 的属性；toolbar 左右两个 `.eova-tools_box`；
 *  ② `page = {page:1, limit:15}`（15）、`tableHeight` 初值 600（24）；
 *  ③ `doResize` **在 `nextTick` 里**算高度（44-51）：立即调用后值**还没变**，等一个 tick 才变；
 *  ④ `onQuery` 用**同一个 `data` 对象**调 `refTable.query`，并关掉级联显示（28-33）；
 *  ⑤ `onAdd`(59) / `onUpdate`(66) / `onDetail`(83) 的 URL 模板与实参 —— ★ 两处易被"顺手改对"的细节：
 *     `onUpdate`/`onDetail` 的 `id` 取**硬编码 `row.id`**（不是 `object_pk`），`biz` 取 `uzoo.page.code`（菜单编码）；
 *  ⑥ `onDetail` 的 done 是**空函数**（不是"操作成功！"+重查）；
 *  ⑦ 未选行文案三处不同：`请先选择一行数据` / `请先选择数据`（68-69/92-93/101-102…）；
 *  ⑧ 删除/隐藏：确认文案不同、URL 表不同（`urls.form.delete` / `urls.form.hide`）；
 *     ok ⇒ `removeRows(rows.map(r => r[object_pk]))`；非 ok ⇒ `msg(ret.msg)`（**不是** `no`）；
 *     ★ 两处 `.catch` 引用未声明的 `error` ⇒ **ReferenceError**（既有缺陷，121/149）；
 *  ⑨ `onExport`(179)：URL/文件名由 `object_code`/`object_name`/`menu_code` 拼，走 `x.axios.download(url, data, fileName, type)`；
 *  ⑩ `onImport`(186) 旧栈**就是 stub**（`console.log('导入')` + `msg('待实现...')`）；
 *  ⑪ `onRowClick`(206)：写 `currentRow`、`showLinking=true`、`cross.emit('eova-table-row_click', row.id)`；
 *  ⑫ `onMounted`(231)：`Object.assign(data, getUrlSearch())` 但**不重新查询**（reload 被注释）；
 *  ⑬ `uzoo.page` 的三项（`code`/`template`/`form`）与 `_page/list.html` 的 8 项；
 *  ⑭ `uzoo.app` = `uzoo.vue.setup()` 的返回值摊平 + 20 个页面对象（274-303）；`uzoo.vue.onReady` 存在才调（53-57）；
 *  ⑮ `.js` 按钮脚本注入（index.html:83-87）走 `loadButtonScripts`；
 *  ⑯ `is_celledit` 用的是 **`??`**（0 要保留，不能被 `||` 吃掉）；
 *  ⑰ 缺 `object.code` ⇒ 告警 + 仍渲染（**不编造** object code）。
 */
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi, type Mock } from 'vitest'
import axios from 'axios'
import TemplateTable from '../TemplateTable.vue'
import { getUzooApp, getUzooPage, type Uzoo } from '@/compat/eova-ext'
import { setEovaMe, setEovaTools, type EovaMe, type EovaTools } from '@/compat/eova-runtime'
import { PAGE_URLS } from '@/compat/ui-urls'
import type { PageBootstrap } from '@/compat/page-bootstrap'

const routeState = vi.hoisted(() => ({ params: { menuCode: 'menu_x' } as Record<string, string> }))
vi.mock('vue-router', () => ({ useRoute: () => ({ params: routeState.params, query: {} }) }))
vi.mock('axios')

const post = axios.post as unknown as ReturnType<typeof vi.fn>

/**
 * `me` 替身的类型：`layer`/`cross` 的成员都是 spy
 *
 * ★ 不能写成 `EovaMe & { layer: Record<string, Mock> }`：交叉类型里原签名仍占一头，
 *   访问 `.mock` 会被 TS 挡下（实测）。故本判据用自己的窄类型，仅在 `setEovaMe(...)` 处转一次。
 */
interface MeSpy {
  // eslint 风格说明：这里刻意用宽松的 `Mock`，因为各成员的签名不同（`open` 有 7 个形参、`msg` 有 2 个…）
  layer: Record<string, Mock>
  cross: Record<string, Mock>
}

/** 造 `me` 替身（`confirm` 立即执行回调，便于断言回调内部行为） */
function makeMe(): MeSpy {
  return {
    layer: {
      msg: vi.fn(),
      no: vi.fn(),
      wa: vi.fn(),
      open: vi.fn(),
      confirm: vi.fn((_m: string, cb: () => void) => cb())
    },
    cross: { on: vi.fn(), off: vi.fn(), emit: vi.fn() }
  }
}

/** 造 `EovaTools` 替身（`x.axios.download` 是旧 `onExport` 的依赖） */
function makeTools(viewHeight = 800): EovaTools & { axios: { download: ReturnType<typeof vi.fn> } } {
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
  } as unknown as EovaTools & { axios: { download: ReturnType<typeof vi.fn> } }
}

/** 造引导数据（工厂，避免用例间共享可变对象） */
function makeBootstrap(over: Partial<PageBootstrap> = {}): PageBootstrap {
  return {
    fromServer: true,
    url: {},
    menu: { code: 'menu_x', name: '商品', id: 7, template: 'table', conf: {} } as never,
    object: {
      code: 'eova_object_code',
      name: '元对象',
      pk: 'goods_id',
      id: 11
    } as never,
    btnList: [],
    loginUser: { isAdmin: true, id: 1, name: 'admin' } as never,
    ...over
  }
}

/** `ev-*` 组件替身 + 表格实例方法探针（工厂：避免用例间共享 spy） */
function makeStubs() {
  const tableApi = {
    query: vi.fn(),
    getSelectRows: vi.fn((): Record<string, unknown>[] => []),
    removeRows: vi.fn()
  }
  const EvForm = defineComponent({
    name: 'EvForm',
    props: ['modelValue', 'mode', 'name', 'object', 'biz'],
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
    setup: (_p, { slots }) => () => h('span', { class: 'stub-popup' }, [slots.default?.(), slots.content?.()])
  })
  return { tableApi, EvForm, EvTable, EvPopup, mountOpts: { global: { components: { EvForm, EvTable, EvPopup } } } }
}

/**
 * 造 `uzoo` 替身并装到全局（页面会在 setup 期写它）
 *
 * 直接写 `globalThis.uzoo`：冻结资产 `eova.meta.js` 就是这么定义的（`window.uzoo = {page,vue,app}`），
 * `getUzoo()` 见到对象就原样返回 ⇒ 替身即被采用。
 */
function makeUzoo(vue: Record<string, unknown> = {}): Uzoo {
  const uzoo: Uzoo = { page: {}, vue, app: {} }
  ;(globalThis as unknown as Record<string, unknown>)['uzoo'] = uzoo
  return uzoo
}

describe('TemplateTable.vue（旧 template/table 的行为等价）', () => {
  let me: MeSpy
  let tools: ReturnType<typeof makeTools>
  let stubs: ReturnType<typeof makeStubs>

  beforeEach(() => {
    post.mockReset()
    routeState.params = { menuCode: 'menu_x' }
    window.history.replaceState({}, '', '/app/menu_x')
    makeUzoo()
    me = makeMe()
    tools = makeTools()
    setEovaMe(me as unknown as EovaMe)
    setEovaTools(tools)
    stubs = makeStubs()
  })

  /** 挂载页面（默认带完整引导数据） */
  function mountPage(over: Partial<PageBootstrap> = {}, props?: Record<string, unknown>) {
    return mount(TemplateTable, {
      props: { bootstrap: makeBootstrap(over), ...props },
      ...stubs.mountOpts
    })
  }

  it('① 结构：两个 .box 的样式串、ev-form/ev-table 属性、toolbar 左右两个 tools_box', async () => {
    const w = mountPage()
    // 用一次真实的 resize 把 queryHeight 变成非 0：
    // jsdom 的 CSSOM 会把 `calc(100% - 10px - 0px)` 归一成 `calc(100% - 10px)`（`- 0px` 被约掉），
    // 用 0 值断言会变成"断言归一化结果"而不是断言模板 —— 所以这里先给一个非零值。
    ;(w.vm as never as { doResize: (h: number) => void }).doResize(88)
    // doResize 自己就在 nextTick 里算 ⇒ 值在第一个 tick 后更新，DOM 重渲染要再等一个
    await nextTick()
    await nextTick()
    const boxes = w.findAll('.zone > .box')
    expect(boxes).toHaveLength(2)
    expect(boxes[0].attributes('style')?.replace(/\s+/g, ' ')).toContain('height: 88px')
    // 表格盒：高度与 top 都由 queryHeight 参与计算。
    // ★ jsdom 的 CSSOM 会把常量项**合并**：模板里的 `calc(100% - 10px - 88px)` 读回来是
    //   `calc(100% - 98px)`、`calc(10px + 88px)` 读回来是 `calc(98px)`（已实测）。
    //   故这里断言的是"随 queryHeight 变化后的归一形态"—— 若模板没插值（写死 `- 10px`），
    //   这两个断言都会红。
    expect(boxes[1].attributes('style')?.replace(/\s+/g, ' ')).toContain(
      'height: calc(100% - 98px)'
    )
    expect(boxes[1].attributes('style')?.replace(/\s+/g, ' ')).toContain('top: calc(98px)')

    const form = w.findComponent(stubs.EvForm)
    expect(form.props('mode')).toBe('query')
    expect(form.props('name')).toBe('query_from')
    expect(form.props('object')).toBe('eova_object_code')
    expect(form.props('biz')).toBe('eova_object_code')

    const table = w.findComponent(stubs.EvTable)
    expect(table.props('object')).toBe('eova_object_code')
    expect(table.props('biz')).toBe('eova_object_code')
    expect(table.props('design')).toBe(true)

    // ★ 必须**限定在表格内**找：超管面板（`_block/admin.html`）自己也有一个 `.eova-tools_box`，
    //   全局找会数到 3 个（实测踩到过）
    const boxesTools = w.findComponent(stubs.EvTable).findAll('.eova-tools_box')
    expect(boxesTools).toHaveLength(2)
    expect(boxesTools[1].attributes('style')?.replace(/\s+/g, ' ')).toContain(
      'position: absolute; right: 0'
    )
    const exportLis = boxesTools[1].findAll('.eova-select_items li')
    expect(exportLis.map((li) => li.text())).toEqual(['导出Excel', '导出CSV'])
    expect(boxesTools[1].find('i').classes()).toContain('eova-icon-export')
  })

  it('② 初值：page={page:1,limit:15}、tableHeight=600、queryHeight=0（旧 15/23/24）', () => {
    const w = mountPage()
    expect((w.vm as never as { page: unknown }).page).toEqual({ page: 1, limit: 15 })
    // ★ 注意：`defineExpose` 暴露的 ref 在 `wrapper.vm` 上是**已解包**的值（不是 ref 对象）
    expect((w.vm as never as { tableHeight: number }).tableHeight).toBe(600)
    expect((w.vm as never as { queryHeight: number }).queryHeight).toBe(0)
    expect(w.findComponent(stubs.EvTable).props('page')).toEqual({ page: 1, limit: 15 })
    expect(w.findComponent(stubs.EvTable).props('height')).toBe(600)
  })

  it('③ doResize **在 nextTick 里**生效：调用后立即仍是旧值，等一个 tick 才算（旧 44-51）', async () => {
    const w = mountPage()
    const vm = w.vm as never as {
      doResize: (h: number) => void
      queryHeight: number
      tableHeight: number
    }
    vm.doResize(88)
    // ★ 这是"是否在 nextTick 里算"的判别点：旧实现把计算放进 nextTick
    expect(vm.queryHeight).toBe(0)
    expect(vm.tableHeight).toBe(600)
    await nextTick()
    expect(vm.queryHeight).toBe(88)
    // 800（视口） - 88 - 30 = 682
    expect(vm.tableHeight).toBe(682)
  })

  it('③ ev-form 的 resize/ready 事件接线：resize→doResize、ready→uzoo.vue.onReady(字段实例)', async () => {
    const onReady = vi.fn()
    makeUzoo({ onReady })
    const w = mountPage()
    const form = w.findComponent(stubs.EvForm)
    form.vm.$emit('resize', 100)
    await nextTick()
    expect((w.vm as never as { queryHeight: number }).queryHeight).toBe(100)
    form.vm.$emit('ready', ['f1'])
    expect(onReady).toHaveBeenCalledWith(['f1'])
  })

  it('③ uzoo.vue.onReady 未注册 ⇒ 什么都不做（存在才调，旧 53-57）', () => {
    const w = mountPage()
    expect(() => w.findComponent(stubs.EvForm).vm.$emit('ready', ['f1'])).not.toThrow()
  })

  it('④ onQuery：用**同一个 data 对象**调 refTable.query，并把 showLinking 关掉（旧 28-33）', async () => {
    const w = mountPage()
    await flushPromises()
    const vm = w.vm as never as {
      data: Record<string, unknown>
      showLinking: boolean
      onQuery: () => void
      onRowClick: (row: Record<string, unknown>) => void
    }
    vm.onRowClick({ id: 5 })
    expect(vm.showLinking).toBe(true)
    vm.onQuery()
    expect(stubs.tableApi.query).toHaveBeenCalledTimes(1)
    expect(stubs.tableApi.query.mock.calls[0][0]).toBe(vm.data)
    expect(vm.showLinking).toBe(false)
  })

  it('⑤ onAdd：URL 由 object_code + **code** 拼，弹层 720×660，done 里提示并重查（旧 59-65）', async () => {
    const w = mountPage()
    // `uzoo.page.object_code` 等在 onMounted 里写入（旧栈是渲染期插值）⇒ 处理器运行前先等它落定
    await flushPromises()
    ;(w.vm as never as { onAdd: () => void }).onAdd()
    expect(me.layer.open).toHaveBeenCalledTimes(1)
    const [title, url, lw, lh, done] = me.layer.open.mock.calls[0] as [
      string,
      string,
      number,
      number,
      () => void
    ]
    expect(title).toBe('新增数据')
    expect(url).toBe('/app/add/eova_object_code?biz=menu_x')
    expect([lw, lh]).toEqual([720, 660])
    done()
    expect(me.layer.msg).toHaveBeenCalledWith('操作成功！')
    expect(stubs.tableApi.query).toHaveBeenCalledTimes(1)
  })

  it('⑤ onUpdate：未选行 ⇒ 只提示不弹层；选中 ⇒ id 取 **row.id**（不是 object_pk）、biz 取菜单编码（旧 66-82）', async () => {
    const w = mountPage()
    await flushPromises()
    const vm = w.vm as never as { onUpdate: () => void }
    vm.onUpdate()
    expect(me.layer.msg).toHaveBeenCalledWith('请先选择一行数据')
    expect(me.layer.open).not.toHaveBeenCalled()

    // 行里 id 与 object_pk 的值不同 ⇒ 可以分辨用的是哪一个
    stubs.tableApi.getSelectRows.mockReturnValue([{ id: 'ROW-ID-1', goods_id: 'PK-9' }])
    vm.onUpdate()
    const [title, url, , , done] = me.layer.open.mock.calls[0] as [string, string, number, number, () => void]
    expect(title).toBe('修改数据')
    expect(url).toBe('/app/update/eova_object_code?id=ROW-ID-1&biz=menu_x')
    done()
    expect(me.layer.msg).toHaveBeenCalledWith('操作成功！')
  })

  it('⑥ onDetail：标题「查看数据」且 done 是**空函数**（不提示、不重查）（旧 83-97）', async () => {
    const w = mountPage()
    await flushPromises()
    stubs.tableApi.getSelectRows.mockReturnValue([{ id: 'ROW-ID-1' }])
    ;(w.vm as never as { onDetail: () => void }).onDetail()
    const [title, url, , , done] = me.layer.open.mock.calls[0] as [string, string, number, number, () => void]
    expect(title).toBe('查看数据')
    expect(url).toBe('/app/detail/eova_object_code?id=ROW-ID-1&biz=menu_x')
    me.layer.msg.mockClear()
    stubs.tableApi.query.mockClear()
    done()
    expect(me.layer.msg).not.toHaveBeenCalled()
    expect(stubs.tableApi.query).not.toHaveBeenCalled()
  })

  it('⑦ 删除/隐藏未选行 ⇒ 文案是「请先选择数据」（与修改/查看的文案不同）（旧 101/129）', () => {
    const w = mountPage()
    const vm = w.vm as never as { onDelete: () => void; onHide: () => void }
    vm.onDelete()
    vm.onHide()
    expect(me.layer.msg.mock.calls.map((c) => c[0])).toEqual(['请先选择数据', '请先选择数据'])
    expect(me.layer.confirm).not.toHaveBeenCalled()
  })

  it('⑧ onDelete：确认文案/URL 表/成功路径（removeRows 用 **object_pk**）与非 ok 走 msg（旧 98-125）', async () => {
    post.mockResolvedValue({ data: { state: 'ok' } })
    const w = mountPage()
    stubs.tableApi.getSelectRows.mockReturnValue([{ id: 1, goods_id: 'PK-9' }])
    ;(w.vm as never as { onDelete: () => void }).onDelete()
    expect(me.layer.confirm).toHaveBeenCalledTimes(1)
    expect(me.layer.confirm.mock.calls[0][0]).toBe('确认彻底删除, 不可恢复')
    await flushPromises()
    expect(post).toHaveBeenCalledWith(PAGE_URLS.form.delete, {
      rows: [{ id: 1, goods_id: 'PK-9' }]
    })
    expect(stubs.tableApi.removeRows).toHaveBeenCalledWith(['PK-9'])
    expect(me.layer.msg).toHaveBeenCalledWith('删除成功')

    // 非 ok ⇒ `msg(ret.msg)`（旧实现不是 `no`）
    post.mockResolvedValue({ data: { state: 'no', msg: '有引用，删不掉' } })
    me.layer.msg.mockClear()
    stubs.tableApi.removeRows.mockClear()
    ;(w.vm as never as { onDelete: () => void }).onDelete()
    await flushPromises()
    expect(me.layer.msg).toHaveBeenCalledWith('有引用，删不掉')
    expect(stubs.tableApi.removeRows).not.toHaveBeenCalled()
  })

  it('⑧ onHide：确认文案「确认删除」+ `urls.form.hide`（与删除不同表项）（旧 126-153）', async () => {
    post.mockResolvedValue({ data: { state: 'ok' } })
    const w = mountPage()
    stubs.tableApi.getSelectRows.mockReturnValue([{ id: 2, goods_id: 'PK-2' }])
    ;(w.vm as never as { onHide: () => void }).onHide()
    expect(me.layer.confirm.mock.calls[0][0]).toBe('确认删除')
    await flushPromises()
    expect(post).toHaveBeenCalledWith(PAGE_URLS.form.hide, { rows: [{ id: 2, goods_id: 'PK-2' }] })
    expect(stubs.tableApi.removeRows).toHaveBeenCalledWith(['PK-2'])
    void w
  })

  it('⑧ ★ 网络异常 ⇒ `.catch` 里引用未声明的 `error` ⇒ ReferenceError，且**不弹**提示（既有缺陷）', async () => {
    post.mockRejectedValue(new Error('boom'))
    const w = mountPage()
    stubs.tableApi.getSelectRows.mockReturnValue([{ id: 1, goods_id: 'PK-9' }])

    // 取走未处理拒绝，避免 vitest 因这条**故意的**缺陷判整轮红（r112 的教训）
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
    // 旧实现在这里**不会**给出"客户端请求异常"提示（它自己先抛了）
    expect(me.layer.msg).not.toHaveBeenCalledWith(expect.stringContaining('客户端请求异常'))
  })

  it('⑨ onExport：URL/文件名由 object_code/object_name/menu_code 拼，走 x.axios.download（旧 179-184）', async () => {
    const w = mountPage()
    await flushPromises()
    const vm = w.vm as never as { data: Record<string, unknown>; onExport: (t: string) => void }
    vm.onExport('xlsx')
    expect(tools.axios.download).toHaveBeenCalledTimes(1)
    const [url, payload, fileName, type] = tools.axios.download.mock.calls[0]
    expect(url).toBe('/excel/export/eova_object_code?type=xlsx&biz=menu_x')
    expect(payload).toBe(vm.data)
    expect(fileName).toBe('元对象.xlsx')
    expect(type).toBe('xlsx')
    vm.onExport('csv')
    expect(tools.axios.download.mock.calls[1][0]).toBe(
      '/excel/export/eova_object_code?type=csv&biz=menu_x'
    )
  })

  it('⑩ onImport：旧栈就是 stub（console.log + msg(待实现...)），不做别的事（旧 186-189）', () => {
    const log = vi.spyOn(console, 'log').mockImplementation(() => {})
    const w = mountPage()
    ;(w.vm as never as { onImport: () => void }).onImport()
    expect(log).toHaveBeenCalledWith('导入')
    expect(me.layer.msg).toHaveBeenCalledWith('待实现...')
    expect(me.layer.open).not.toHaveBeenCalled()
    log.mockRestore()
  })

  it('⑪ onRowClick：写 currentRow / showLinking，并 emit `eova-table-row_click` 带 row.id（旧 206-225）', async () => {
    const w = mountPage()
    await flushPromises()
    const vm = w.vm as never as {
      currentRow: Record<string, unknown>
      onRowClick: (row: Record<string, unknown>) => void
    }
    vm.onRowClick({ id: 9, name: 'x' })
    expect(vm.currentRow).toEqual({ id: 9, name: 'x' })
    expect(me.cross.emit).toHaveBeenCalledWith('eova-table-row_click', 9)
  })

  it('⑫ onMounted：URL 查询串覆盖进 data，但**不重新查询**（reload 被注释）（旧 231-241）', async () => {
    window.history.replaceState({}, '', '/app/menu_x?name=abc&id=5')
    const w = mountPage()
    await flushPromises()
    const data = (w.vm as never as { data: Record<string, unknown> }).data
    expect(data['name']).toBe('abc')
    expect(data['id']).toBe('5')
    expect(stubs.tableApi.query).not.toHaveBeenCalled()
  })

  it('⑬ uzoo.page：`code`/`template`/`form`（旧内联脚本）与 `_page/list.html` 的 8 项', async () => {
    const w = mountPage()
    expect(getUzooPage()['code']).toBe('menu_x')
    expect(getUzooPage()['template']).toBe('table')
    expect(getUzooPage()['form']).toBe('query')
    await flushPromises()
    const page = getUzooPage()
    expect(page['menu_id']).toBe(7)
    expect(page['menu_name']).toBe('商品')
    expect(page['menu_code']).toBe('menu_x')
    expect(page['object_id']).toBe(11)
    expect(page['object_code']).toBe('eova_object_code')
    expect(page['object_name']).toBe('元对象')
    expect(page['object_pk']).toBe('goods_id')
    // `menu_conf` 旧栈是对象字面量文本 ⇒ 这里必须是**对象**（不是字符串）
    expect(page['menu_conf']).toEqual({})
    void w
  })

  it('⑭ uzoo.app：`uzoo.vue.setup()` 的返回值摊平 + 页面对象/方法（旧 274-303）', () => {
    makeUzoo({ setup: () => ({ extraKey: 'EXTRA' }) })
    const w = mountPage()
    const app = getUzooApp()
    expect(app['extraKey']).toBe('EXTRA')
    for (const name of [
      'data',
      'page',
      'currentRow',
      'queryHeight',
      'tableHeight',
      'onQuery',
      'onAdd',
      'onUpdate',
      'onDetail',
      'onHide',
      'onDelete',
      'onImport',
      'onExport',
      'doResize',
      'onReady',
      'onRowClick',
      'showLinking'
    ]) {
      expect(app, `uzoo.app 缺 ${name}`).toHaveProperty(name)
    }
    expect(app['data']).toBe((w.vm as never as { data: unknown }).data)
    void w
  })

  it('⑭ `uzoo.vue.setup` 未注册 ⇒ 照常挂载（旧实现的存在才调）', () => {
    expect(() => mountPage()).not.toThrow()
    expect(getUzooApp()).toHaveProperty('onQuery')
  })

  it('⑮ `.js` 按钮脚本注入：按 btnList 里的 `ui` 动态加 <script src>（旧 index.html:83-87）', async () => {
    mountPage({
      btnList: [
        { id: 1, name: '自定义', event: 'test', ui: '/demo/test/btn.js', is_base: 0 },
        { id: 2, name: '内置', event: 'onQuery', ui: '', is_base: 1 }
      ]
    })
    await flushPromises()
    const srcs = Array.from(document.querySelectorAll('script[src]')).map((s) =>
      s.getAttribute('src')
    )
    expect(srcs).toContain('/demo/test/btn.js')
  })

  it('⑯ is_celledit 用 `??`：0 要保留（不能被 `||` 吃掉）', () => {
    const w = mountPage({ object: { code: 'o', is_celledit: 0 } as never })
    expect(w.findComponent(stubs.EvTable).props('isEdit')).toBe(0)
    const w2 = mountPage({ object: { code: 'o' } as never })
    expect(w2.findComponent(stubs.EvTable).props('isEdit')).toBe(false)
  })

  it('⑰ 缺 object.code ⇒ 告警 + 仍渲染（不编造 object code）', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const w = mountPage({ object: undefined, url: {} })
    await flushPromises()
    expect(warn.mock.calls.some((c) => String(c[0]).includes('缺少 object.code'))).toBe(true)
    expect(w.findComponent(stubs.EvForm).props('object')).toBe('')
    warn.mockRestore()
  })

  it('超管面板：loginUser.isAdmin ⇒ 渲染 `_block/admin.html` 面板，否则不渲染', () => {
    expect(mountPage().find('.eova-admins').exists()).toBe(true)
    expect(
      mountPage({ loginUser: { isAdmin: false } as never }).find('.eova-admins').exists()
    ).toBe(false)
  })

  it('toolbar：内置按钮（is_base=1）点击走页面方法表（onQuery 被调用）', async () => {
    const w = mountPage({
      btnList: [{ id: 1, name: '查询', event: 'onQuery', icon: 'i', style: '', is_base: 1 }]
    })
    const btn = w.find('.eova-tools_box button')
    expect(btn.text()).toContain('查询')
    await btn.trigger('click')
    expect(stubs.tableApi.query).toHaveBeenCalledTimes(1)
  })
})
