/**
 * `/app/:menuCode` 模版分派宿主的判据（第 118 轮）
 *
 * 钉的契约（对应旧 `AppController#index()`）：
 *  ① 加载中不画诊断（旧栈没有这个状态，避免闪一屏）；
 *  ② 引导数据里没有 `loginUser` ⇒ 渲染旧栈原话 **"请先登录"**（不是跳登录页 —— 那是另一条契约）；
 *  ③ **取不到 `menu.template` ⇒ 明确诊断，不猜默认模版**（猜成 table 会让 tree 菜单渲染出错页且看起来正常）；
 *  ④ 模版未迁移 ⇒ 明确报出模版名与已/未迁移清单，**不降级**渲染成 table；
 *  ⑤ `menu.template='table'` ⇒ 渲染 `TemplateTable`，并把引导数据当 prop 传下去（**只取一次**）。
 */
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AppTemplateHost from '../AppTemplateHost.vue'
import TemplateTable from '../TemplateTable.vue'
import TemplateTree from '../TemplateTree.vue'
import TemplateTreeTable from '../TemplateTreeTable.vue'
import type { PageBootstrap } from '@/compat/page-bootstrap'
import { setEovaMe, setEovaTools, type EovaMe, type EovaTools } from '@/compat/eova-runtime'

const routeState = vi.hoisted(() => ({ params: { menuCode: 'menu_x' } as Record<string, string> }))
vi.mock('vue-router', () => ({ useRoute: () => ({ params: routeState.params, query: {} }) }))
vi.mock('axios')

/** 引导数据来源（宿主用 `loadPageBootstrap()`；本判据把它替换成可控值 + 计数） */
const boot = vi.hoisted(() => ({ value: null as unknown, calls: 0 }))
vi.mock('@/compat/page-bootstrap', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/compat/page-bootstrap')>()
  return {
    ...actual,
    loadPageBootstrap: () => {
      boot.calls += 1
      return Promise.resolve(boot.value as PageBootstrap)
    }
  }
})

/**
 * 取当前状态（`data-eova-template` 在**内层**分支元素上；`ready` 时没有该属性）
 *
 * @param w 挂载结果
 * @returns 状态名或 undefined
 */
function stateOf(w: ReturnType<typeof mount>): string | undefined {
  const el = w.find('[data-eova-template]')
  return el.exists() ? el.attributes('data-eova-template') : undefined
}

/** `ev-*` 替身（`TemplateTable` 在 ready 分支会被真实挂载） */
const stubs = {
  EvForm: defineComponent({ name: 'EvForm', props: ['modelValue', 'mode', 'name', 'object', 'biz'], setup: () => () => h('div') }),
  EvTable: defineComponent({
    name: 'EvTable',
    props: ['object', 'biz', 'design', 'page', 'height', 'isEdit', 'where'],
    setup: (_p, { expose, slots }) => {
      expose({ query: () => {}, getSelectRows: () => [], removeRows: () => {} })
      return () => h('div', [slots.toolbar?.()])
    }
  }),
  EvPopup: defineComponent({ name: 'EvPopup', props: ['trigger', 'placement'], setup: (_p, { slots }) => () => h('span', [slots.content?.()]) })
}

const mountOpts = { global: { components: stubs } }

/** 造引导数据 */
function makeBootstrap(over: Partial<PageBootstrap> = {}): PageBootstrap {
  return {
    fromServer: true,
    url: {},
    menu: { code: 'menu_x', name: '商品', id: 7, template: 'table' } as never,
    object: { code: 'eova_object_code', name: '元对象', pk: 'goods_id' } as never,
    btnList: [],
    loginUser: { isAdmin: false, id: 1 } as never,
    ...over
  }
}

describe('AppTemplateHost.vue（AppController#index 的分派等价）', () => {
  beforeEach(() => {
    routeState.params = { menuCode: 'menu_x' }
    ;(globalThis as unknown as Record<string, unknown>)['uzoo'] = { page: {}, vue: {}, app: {} }
    boot.value = makeBootstrap()
    boot.calls = 0
    // ready 分支会挂**真实**的模版页组件，它们的 `onMounted` 需要运行时接缝；
    // 不装的话 `getEovaTools()` 会**响亮抛出**（这是设计如此）——实测会让 `pnpm test` 退出码变 1
    // 而汇总行仍显示 "Tests … passed"（r112/r116 同族的假绿）。
    setEovaTools({
      isEmpty: () => true,
      dom: { getViewSize: () => ({ width: 0, height: 0 }) },
      json: { toStr: (v: unknown) => JSON.stringify(v), toObj: (t: string) => JSON.parse(t) },
      str: { template: (t: string) => t },
      axios: { download: () => Promise.resolve() },
      log: () => {},
      validate: { start: () => true, showMsg: () => '', addRules: () => {} }
    } as unknown as EovaTools)
    setEovaMe({
      layer: { msg: () => {}, open: () => {}, confirm: () => {} },
      cross: { on: () => {}, off: () => {}, emit: () => {} },
      urls: { url: () => '' }
    } as unknown as EovaMe)
  })

  it('① 加载中：不画任何诊断（避免闪一屏"缺模版"）', () => {
    const w = mount(AppTemplateHost, mountOpts)
    expect(stateOf(w)).toBe('loading')
    expect(w.text()).toBe('')
  })

  it('② 无 loginUser ⇒ 渲染旧栈原话「请先登录」（不是跳转）', async () => {
    boot.value = makeBootstrap({ loginUser: null as never })
    const w = mount(AppTemplateHost, mountOpts)
    await flushPromises()
    expect(stateOf(w)).toBe('anonymous')
    expect(w.text()).toContain('请先登录')
    // 未登录时**不渲染**模版页
    expect(w.findComponent(TemplateTable).exists()).toBe(false)
  })

  it('③ ★ 取不到 menu.template ⇒ 诊断（含 menuCode），且**不渲染任何模版页**', async () => {
    boot.value = makeBootstrap({ menu: { code: 'menu_x', name: '商品' } as never })
    const w = mount(AppTemplateHost, mountOpts)
    await flushPromises()
    expect(stateOf(w)).toBe('missing')
    expect(w.text()).toContain('无法确定模版类型')
    expect(w.text()).toContain('menu_x')
    expect(w.text()).toContain('menu.template')
    expect(w.findComponent(TemplateTable).exists()).toBe(false)
  })

  it('④ 未迁移/未知模版 ⇒ 报出模版名与清单，**不降级**成已迁移模版', async () => {
    // ★ 三个列表模版都已迁（r120）⇒ 用"引导数据里出现没见过的模版名"来走这条分支
    boot.value = makeBootstrap({ menu: { code: 'menu_x', template: 'weird_tpl' } as never })
    const w = mount(AppTemplateHost, mountOpts)
    await flushPromises()
    expect(stateOf(w)).toBe('unmigrated')
    expect(w.text()).toContain('weird_tpl')
    expect(w.text()).toContain('不在已知模版清单内')
    expect(w.text()).toContain('不降级')
    expect(w.findComponent(TemplateTable).exists()).toBe(false)
    expect(w.findComponent(TemplateTree).exists()).toBe(false)
    expect(w.findComponent(TemplateTreeTable).exists()).toBe(false)
  })

  it('⑤ template=table ⇒ 渲染 TemplateTable 并把引导数据传下去（只取一次）', async () => {
    const bs = makeBootstrap()
    boot.value = bs
    const w = mount(AppTemplateHost, mountOpts)
    await flushPromises()
    expect(stateOf(w)).toBeUndefined()
    const child = w.findComponent(TemplateTable)
    expect(child.exists()).toBe(true)
    // 模版页拿到的是引导数据里的模版信息（`ref()` 会把对象包成响应式代理 ⇒ 用取值断言，不用同一性）
    expect((child.props('bootstrap') as PageBootstrap).menu?.template).toBe('table')
    // ★ 只取一次：宿主取给分派用，模版页**不再**自己取（否则同页两次请求）
    expect(boot.calls).toBe(1)
    void bs
  })

  it('⑤ template=tree_table ⇒ 渲染 TemplateTreeTable（不是另外两个）', async () => {
    boot.value = makeBootstrap({
      menu: { code: 'menu_x', template: 'tree_table', conf: {} } as never
    })
    const w = mount(AppTemplateHost, mountOpts)
    await flushPromises()
    expect(stateOf(w)).toBeUndefined()
    expect(w.findComponent(TemplateTreeTable).exists()).toBe(true)
    expect(w.findComponent(TemplateTable).exists()).toBe(false)
    expect(w.findComponent(TemplateTree).exists()).toBe(false)
  })

  it('⑤ template=tree ⇒ 渲染 TemplateTree（不是 TemplateTable）', async () => {
    boot.value = makeBootstrap({ menu: { code: 'menu_x', template: 'tree', conf: {} } as never })
    const w = mount(AppTemplateHost, mountOpts)
    await flushPromises()
    expect(stateOf(w)).toBeUndefined()
    expect(w.findComponent(TemplateTree).exists()).toBe(true)
    expect(w.findComponent(TemplateTable).exists()).toBe(false)
  })
})
