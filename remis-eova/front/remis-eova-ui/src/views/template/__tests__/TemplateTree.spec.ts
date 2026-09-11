/**
 * 树模版页的行为等价判据（旧 `_view/template/tree/index.html` + `index.js`）。
 *
 * 钉的契约（括号内是旧行号）：
 *  ① 结构：左 `.box`(250px) 内 ev-tree；右**嵌套 `.zone`**内工具条盒(43px)+表单盒；`treeId>0` 才渲染 ev-form；
 *  ② `treeConf` 的键映射：`title` 取 `conf.name`，`icon`/`spread` 缺省为**空串**（33-40）；
 *  ③ `onTeeClick`(86)：`msg(\`编辑 ${node.name}\`)` + `treeId = node.id`；
 *  ④ `onAdd`(111)：`ref` 参数是 **`${menu_conf.pid}:${treeId}`**；弹层默认 **720×720**（与 table 的 660 不同）；
 *  ⑤ `onSave`(124)：`treeId==0` ⇒ 提示且**不发请求**；`validate()==false` ⇒ 直接返回；
 *     URL 走 **`me.urls.url('form_update', props)`**（制品 URL 表）；非 ok ⇒ `msg(ret.msg)` + `console.log('加载错误')`；
 *  ⑥ `onHide`(167)/`onDelete`(208)：勾选数为 0 ⇒ 提示；确认文案不同、URL 不同；
 *     rows 由 `treeChecked` 映射成 `{[object_pk]: id}`；
 *     ★ 两处 `.catch((e))` 引用未声明的 `error` ⇒ **ReferenceError**（既有缺陷，202/237）；
 *  ⑦ `onQuery`(243) 是 **`refTree.reload()`**（不是表格查询）；
 *  ⑧ `onImport`(247) 旧栈就是 stub；
 *  ⑨ `uzoo.page` 三项 + `_page/list.html` 的 8 项；`uzoo.app` 的**键集合与旧实现逐字一致**（21 个）。
 */
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi, type Mock } from 'vitest'
import axios from 'axios'
import TemplateTree from '../TemplateTree.vue'
import { getUzooApp, getUzooPage } from '@/compat/eova-ext'
import { setEovaMe, setEovaTools, type EovaMe, type EovaTools } from '@/compat/eova-runtime'
import { PAGE_URLS } from '@/compat/ui-urls'
import type { PageBootstrap } from '@/compat/page-bootstrap'

const routeState = vi.hoisted(() => ({ params: { menuCode: 'meta_menu' } as Record<string, string> }))
vi.mock('vue-router', () => ({ useRoute: () => ({ params: routeState.params, query: {} }) }))
vi.mock('axios')

const post = axios.post as unknown as ReturnType<typeof vi.fn>

/** `me` 替身（含制品 URL 表 `me.urls`） */
interface MeSpy {
  layer: Record<string, Mock>
  cross: Record<string, Mock>
  urls: Record<string, Mock>
}

/** 造 `me` 替身（`confirm` 立即执行回调） */
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
    // 制品 URL 表：只断言"按 key 取用"，替换结果由制品负责（本工程不重建该表）
    urls: { url: vi.fn(() => '/api/form/update/eova_object_code') }
  }
}

/** 造 `EovaTools` 替身 */
function makeTools(): EovaTools {
  return {
    isEmpty: (v: unknown) => v == null || (Array.isArray(v) && v.length === 0),
    dom: { getViewSize: () => ({ width: 1200, height: 800 }) },
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

/** 造引导数据（默认带 `menu_conf`：树配置四件套 + `pid`） */
function makeBootstrap(over: Partial<PageBootstrap> = {}): PageBootstrap {
  return {
    fromServer: true,
    url: {},
    menu: {
      code: 'meta_menu',
      name: '菜单管理',
      id: 1196,
      template: 'tree',
      conf: { object_code: 'eova_menu_code', id: 'id', pid: 'pid', name: 'name', root: '1', tips: '请选择节点' }
    } as never,
    // ★ `pk` 刻意不叫 `id`：删除路径用 `{[object_pk]: id}`，若主键列恰好是 `id`，
    //   这条判据就**分辨不出**"用 object_pk"与"写死 id"（变异 M10 实测如此）
    object: { code: 'eova_menu_code', name: '菜单', pk: 'menu_id', id: 3 } as never,
    btnList: [],
    loginUser: { isAdmin: true, id: 1 } as never,
    ...over
  }
}

/** `ev-*` 替身 + 实例方法探针 */
function makeStubs() {
  const treeApi = { reload: vi.fn() }
  const formApi = { validate: vi.fn(() => true), getData: vi.fn(() => ({ id: 1, name: 'x' })) }
  const EvTree = defineComponent({
    name: 'EvTree',
    props: ['object', 'multiple', 'conf', 'checked'],
    emits: ['node-click', 'update:checked'],
    setup(_p, { expose }) {
      expose(treeApi)
      return () => h('div', { class: 'stub-tree' })
    }
  })
  const EvForm = defineComponent({
    name: 'EvForm',
    props: ['mode', 'name', 'object', 'biz', 'pk'],
    setup(_p, { expose }) {
      expose(formApi)
      return () => h('div', { class: 'stub-form' })
    }
  })
  return {
    treeApi,
    formApi,
    EvTree,
    EvForm,
    mountOpts: { global: { components: { EvTree, EvForm } } }
  }
}

describe('TemplateTree.vue（旧 template/tree 的行为等价）', () => {
  let me: MeSpy
  let tools: EovaTools
  let stubs: ReturnType<typeof makeStubs>

  beforeEach(() => {
    post.mockReset()
    routeState.params = { menuCode: 'meta_menu' }
    ;(globalThis as unknown as Record<string, unknown>)['uzoo'] = { page: {}, vue: {}, app: {} }
    me = makeMe()
    tools = makeTools()
    setEovaMe(me as unknown as EovaMe)
    setEovaTools(tools)
    stubs = makeStubs()
  })

  /** 挂载页面 */
  function mountPage(over: Partial<PageBootstrap> = {}) {
    return mount(TemplateTree, { props: { bootstrap: makeBootstrap(over) }, ...stubs.mountOpts })
  }

  it('① 结构：左树盒 250px + 右嵌套 zone（工具条 43px / 表单盒 calc）+ 超管面板', () => {
    const w = mountPage()
    const layout = w.find('.eova-layout')
    expect(layout.exists()).toBe(true)
    const outerZone = layout.find('.zone')
    expect(outerZone.attributes('style')?.replace(/\s+/g, ' ')).toContain('width: 100%')
    const boxes = outerZone.findAll(':scope > .box')
    expect(boxes).toHaveLength(1)
    expect(boxes[0].attributes('style')?.replace(/\s+/g, ' ')).toContain('width: 250px')
    // 右侧是**嵌套**的 .zone（不是 .box）
    const innerZone = outerZone.find(':scope > .zone')
    expect(innerZone.attributes('style')?.replace(/\s+/g, ' ')).toContain(
      'width: calc(100% - 260px)'
    )
    const innerBoxes = innerZone.findAll(':scope > .box')
    expect(innerBoxes).toHaveLength(2)
    expect(innerBoxes[0].attributes('style')?.replace(/\s+/g, ' ')).toContain('height: 43px')
    expect(innerBoxes[0].find('.eova-tools_box').exists()).toBe(true)
    expect(innerBoxes[1].attributes('style')?.replace(/\s+/g, ' ')).toContain(
      'height: calc(100% - 53px)'
    )
    expect(w.find('.eova-admins').exists()).toBe(true)
  })

  it('① treeId=0 ⇒ 渲染 `conf.tips` 的 h2（v-html）且**不渲染** ev-form', () => {
    const w = mountPage()
    expect(w.findComponent(stubs.EvForm).exists()).toBe(false)
    const h2 = w.find('h2')
    expect(h2.exists()).toBe(true)
    // ★ jsdom 会把颜色转成 rgb：`#b3afaf` 读回来是 `rgb(179, 175, 175)`（与 calc 归一化同类，已实测）
    expect(h2.attributes('style')?.replace(/\s+/g, ' ')).toContain('color: rgb(179, 175, 175)')
    expect(h2.html()).toContain('请选择节点')
  })

  it('② treeConf 键映射：title 取 conf.name，icon/spread 缺省空串', () => {
    const w = mountPage()
    const conf = w.findComponent(stubs.EvTree).props('conf')
    expect(conf).toEqual({ root: '1', id: 'id', pid: 'pid', title: 'name', icon: '', spread: '' })
    // 有值时原样用
    const w2 = mountPage({
      menu: {
        code: 'meta_menu',
        template: 'tree',
        conf: { id: 'id', pid: 'pid', name: 'name', root: 0, icon: 'eova-icon-x', spread: 'true' }
      } as never
    })
    expect(w2.findComponent(stubs.EvTree).props('conf')).toEqual({
      root: 0,
      id: 'id',
      pid: 'pid',
      title: 'name',
      icon: 'eova-icon-x',
      spread: 'true'
    })
  })

  it('② ev-tree 的 object/multiple 与 ev-form 的 mode/name/biz/pk（treeId>0 后）', async () => {
    const w = mountPage()
    const tree = w.findComponent(stubs.EvTree)
    expect(tree.props('object')).toBe('eova_menu_code')
    expect(tree.props('multiple')).toBe(true)
    ;(w.vm as never as { onTeeClick: (n: { id: number; name: string }) => void }).onTeeClick({
      id: 5,
      name: '菜单'
    })
    await nextTick()
    const form = w.findComponent(stubs.EvForm)
    expect(form.exists()).toBe(true)
    expect(form.props('mode')).toBe('update')
    expect(form.props('name')).toBe('tree_from')
    expect(form.props('biz')).toBe('meta_menu')
    expect(form.props('object')).toBe('eova_menu_code')
    expect(form.props('pk')).toBe(5)
  })

  it('③ onTeeClick：提示「编辑 <节点名>」并记下 treeId', () => {
    const w = mountPage()
    ;(w.vm as never as { onTeeClick: (n: unknown) => void }).onTeeClick({ id: 9, name: '省市区' })
    expect(me.layer.msg).toHaveBeenCalledWith('编辑 省市区')
    expect((w.vm as never as { treeId: number }).treeId).toBe(9)
  })

  it('④ onAdd：ref 参数是 `${menu_conf.pid}:${treeId}`，弹层默认 720×720，done 里提示并 reload', async () => {
    const w = mountPage()
    await flushPromises()
    ;(w.vm as never as { onTeeClick: (n: unknown) => void }).onTeeClick({ id: 7, name: 'x' })
    ;(w.vm as never as { onAdd: () => void }).onAdd()
    const [title, url, lw, lh, done] = me.layer.open.mock.calls[0] as [string, string, number, number, () => void]
    expect(title).toBe('新增数据')
    expect(url).toBe('/app/add/eova_menu_code?ref=pid:7')
    // ★ tree 的默认高是 720（table 是 660）
    expect([lw, lh]).toEqual([720, 720])
    done()
    expect(me.layer.msg).toHaveBeenCalledWith('操作成功！')
    expect(stubs.treeApi.reload).toHaveBeenCalledTimes(1)
  })

  it('④ onAdd 的宽高取 menu_conf 的 layer_width/height', async () => {
    const w = mountPage({
      menu: {
        code: 'meta_menu',
        template: 'tree',
        conf: { object_code: 'o', id: 'id', pid: 'pid', name: 'name', layer_width: 900, layer_height: 500 }
      } as never
    })
    await flushPromises()
    ;(w.vm as never as { onAdd: () => void }).onAdd()
    const [, , lw, lh] = me.layer.open.mock.calls[0] as [string, string, number, number]
    expect([lw, lh]).toEqual([900, 500])
  })

  it('⑤ onSave：未选节点 ⇒ 提示且**不发请求**；validate 未过 ⇒ 也不发请求', async () => {
    const w = mountPage()
    await flushPromises()
    const vm = w.vm as never as { onSave: () => void }
    vm.onSave()
    expect(me.layer.msg).toHaveBeenCalledWith('请选择需要编辑的树节点')
    expect(post).not.toHaveBeenCalled()

    ;(w.vm as never as { onTeeClick: (n: unknown) => void }).onTeeClick({ id: 3, name: 'x' })
    await nextTick()
    stubs.formApi.validate.mockReturnValue(false)
    vm.onSave()
    expect(post).not.toHaveBeenCalled()
  })

  it('⑤ onSave：ok ⇒ me.urls.url("form_update", props) + reload + 提示；非 ok ⇒ msg + console.log("加载错误")', async () => {
    const log = vi.spyOn(console, 'log').mockImplementation(() => {})
    const w = mountPage()
    await flushPromises()
    ;(w.vm as never as { onTeeClick: (n: unknown) => void }).onTeeClick({ id: 3, name: 'x' })
    await nextTick()

    post.mockResolvedValue({ data: { state: 'ok' } })
    ;(w.vm as never as { onSave: () => void }).onSave()
    await flushPromises()
    // ★ 走制品的 URL 表，不自己拼串
    expect(me.urls.url).toHaveBeenCalledWith('form_update', expect.objectContaining({ object_code: 'eova_menu_code' }))
    expect(post.mock.calls[0][0]).toBe('/api/form/update/eova_object_code')
    expect(post.mock.calls[0][1]).toEqual({ id: 1, name: 'x' })
    expect(stubs.treeApi.reload).toHaveBeenCalledTimes(1)
    expect(me.layer.msg).toHaveBeenCalledWith('操作成功！')

    post.mockResolvedValue({ data: { state: 'no', msg: '名称重复' } })
    me.layer.msg.mockClear()
    log.mockClear()
    ;(w.vm as never as { onSave: () => void }).onSave()
    await flushPromises()
    expect(me.layer.msg).toHaveBeenCalledWith('名称重复')
    expect(log.mock.calls.map((c) => String(c[0]))).toContain('加载错误')
    log.mockRestore()
  })

  it('⑤ onSave 的网络异常：形参就是 error ⇒ **正常**提示（此处无既有缺陷）', async () => {
    post.mockRejectedValue(new Error('boom'))
    const w = mountPage()
    await flushPromises()
    ;(w.vm as never as { onTeeClick: (n: unknown) => void }).onTeeClick({ id: 3, name: 'x' })
    await nextTick()
    ;(w.vm as never as { onSave: () => void }).onSave()
    await flushPromises()
    expect(me.layer.msg).toHaveBeenCalledWith('客户端请求异常: boom')
  })

  it('⑥ onHide/onDelete：未勾选 ⇒ 提示；勾选 ⇒ 确认文案不同、URL 不同、rows 用 **object_pk**', async () => {
    const w = mountPage()
    await flushPromises()
    const vm = w.vm as never as { onHide: () => void; onDelete: () => void }
    vm.onHide()
    vm.onDelete()
    expect(me.layer.msg.mock.calls.map((c) => c[0])).toEqual([
      '请先勾选需要删除的数据',
      '请先勾选需要删除的数据'
    ])
    expect(me.layer.confirm).not.toHaveBeenCalled()

    ;(w.vm as never as { treeChecked: unknown[] }).treeChecked = [3, 4]
    post.mockResolvedValue({ data: { state: 'ok' } })
    vm.onHide()
    expect(me.layer.confirm.mock.calls[0][0]).toBe('确认删除')
    await flushPromises()
    expect(post).toHaveBeenCalledWith('/api/form/hide/eova_menu_code', {
      rows: [{ menu_id: 3 }, { menu_id: 4 }]
    })
    expect(stubs.treeApi.reload).toHaveBeenCalledTimes(1)
    expect(me.layer.msg).toHaveBeenCalledWith('删除成功')

    post.mockClear()
    me.layer.confirm.mockClear()
    vm.onDelete()
    expect(me.layer.confirm.mock.calls[0][0]).toBe('确认彻底删除')
    await flushPromises()
    // ★ `urls.form.delete` 是**模板串**，旧实现同样过 `x.str.template(..., props)` 后才提交
    expect(post).toHaveBeenCalledWith('/api/form/delete/eova_menu_code', {
      rows: [{ menu_id: 3 }, { menu_id: 4 }]
    })
    expect(PAGE_URLS.form.delete).toBe('/api/form/delete/{{object_code}}')
  })

  it('⑥ ★ onDelete 的网络异常 ⇒ `.catch` 里 `error` 未声明 ⇒ ReferenceError，且**不弹**提示（既有缺陷）', async () => {
    post.mockRejectedValue(new Error('boom'))
    const w = mountPage()
    await flushPromises()
    ;(w.vm as never as { treeChecked: unknown[] }).treeChecked = [3]
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

  it('⑦ onQuery 是 refTree.reload()（树页没有表格查询）', () => {
    const w = mountPage()
    ;(w.vm as never as { onQuery: () => void }).onQuery()
    expect(stubs.treeApi.reload).toHaveBeenCalledTimes(1)
  })

  it('⑧ onImport：旧栈就是 stub（console.log + msg(待实现...)，不弹层）', () => {
    const log = vi.spyOn(console, 'log').mockImplementation(() => {})
    const w = mountPage()
    ;(w.vm as never as { onImport: () => void }).onImport()
    expect(log).toHaveBeenCalledWith('导入')
    expect(me.layer.msg).toHaveBeenCalledWith('待实现...')
    expect(me.layer.open).not.toHaveBeenCalled()
    log.mockRestore()
  })

  it('⑨ uzoo.page：code/template=tree/form=query + 引导数据到达后的 8 项（含 menu_conf 为对象）', async () => {
    const w = mountPage()
    expect(getUzooPage()['code']).toBe('meta_menu')
    expect(getUzooPage()['template']).toBe('tree')
    expect(getUzooPage()['form']).toBe('query')
    await flushPromises()
    const page = getUzooPage()
    expect(page['menu_id']).toBe(1196)
    expect(page['menu_code']).toBe('meta_menu')
    expect(page['object_id']).toBe(3)
    expect(page['object_code']).toBe('eova_menu_code')
    expect(page['object_pk']).toBe('menu_id')
    expect(page['menu_conf']).toMatchObject({ pid: 'pid', name: 'name' })
    void w
  })

  it('⑨ uzoo.app 的键集合与旧实现逐字一致（21 个，含既有死值）', () => {
    mountPage()
    // 旧 index.js:255-284 的返回对象（**一个不多一个不少**）
    expect(Object.keys(getUzooApp()).sort()).toEqual(
      [
        'conf',
        'auths',
        'refTable',
        'queryHeight',
        'tableHeight',
        'page',
        'form',
        'json',
        'cityData',
        'refTree',
        'refForm',
        'treeId',
        'treeConf',
        'treeChecked',
        'onQuery',
        'onAdd',
        'onTeeClick',
        'onSave',
        'onDelete',
        'onHide',
        'onImport'
      ].sort()
    )
  })

  it('⑨ 既有死值原样带出：cityData 11 条、json 演示串', () => {
    const w = mountPage()
    const vm = w.vm as never as {
      cityData: { id: number; name: string }[]
      json: string
    }
    expect(vm.cityData).toHaveLength(11)
    expect(vm.cityData[0]).toEqual({ id: 1, pid: 0, name: '中国', lv: 1, spread: true })
    expect(vm.json).toBe('{"objectCode":"goods_style","params":{}}')
  })

  it('⑨ onMounted 只打日志 + 注入 .js 按钮脚本（旧实现里取设置/取权限全被注释）', async () => {
    const log = vi.spyOn(console, 'log').mockImplementation(() => {})
    mountPage({
      btnList: [{ id: 1, name: '自定义', event: 'test', ui: '/demo/test/btn.js', is_base: 0 }]
    })
    await flushPromises()
    expect(log.mock.calls.map((c) => String(c[0]))).toContain('crud.js init')
    const srcs = Array.from(document.querySelectorAll('script[src]')).map((s) =>
      s.getAttribute('src')
    )
    expect(srcs).toContain('/demo/test/btn.js')
    log.mockRestore()
  })

  it('缺 object.code ⇒ 告警 + 仍渲染（不编造）', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const w = mountPage({ object: undefined, url: {} })
    await flushPromises()
    expect(warn.mock.calls.some((c) => String(c[0]).includes('缺少 object.code'))).toBe(true)
    expect(w.findComponent(stubs.EvTree).props('object')).toBe('')
    warn.mockRestore()
  })
})
