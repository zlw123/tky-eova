/**
 * 宿主的**运行期加固**判据（第 131 轮）
 *
 * ## 防的是什么
 *
 * `state='ready'` 的分支是 `<component :is="TEMPLATE_COMPONENTS[template]">`。
 * 若"已迁移清单"与"组件表"发生**漂移**（模版名登记了、组件没挂），`is` 会是 `undefined`
 * ⇒ **渲染空白页**，而构建、单测、闸门全绿（这正是 r118 起一直在防的那类静默错页）。
 *
 * 静态一致性由 `registry.spec.ts` 钉住；本判据用 `vi.mock` **人为制造该漂移**，
 * 断言运行期的第二道防线生效：**退回明确诊断，而不是空白页**。
 *
 * ★ 为什么单独一个文件：`vi.mock('../registry')` 是**模块级**的，会影响同文件其它用例
 *   （`AppTemplateHost.spec.ts` 需要真实组件表来断言分派结果）。
 */
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AppTemplateHost from '../AppTemplateHost.vue'
import type { PageBootstrap } from '@/compat/page-bootstrap'
import { setEovaMe, setEovaTools, type EovaMe, type EovaTools } from '@/compat/eova-runtime'

vi.mock('vue-router', () => ({ useRoute: () => ({ params: { menuCode: 'menu_x' }, query: {} }) }))
vi.mock('axios')

/** 引导数据来源（可控） */
const boot = vi.hoisted(() => ({ value: null as unknown }))
vi.mock('@/compat/page-bootstrap', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/compat/page-bootstrap')>()
  return { ...actual, loadPageBootstrap: () => Promise.resolve(boot.value as PageBootstrap) }
})

/**
 * ★ 人为制造漂移：组件表里**只留 `table`**，而 `MIGRATED_TEMPLATES` 还包含 `tree`/`tree_table`
 * ⇒ 请求 `tree` 模版时"已迁移"为真、组件为 `undefined`。
 */
vi.mock('../registry', () => ({
  TEMPLATE_COMPONENTS: {
    table: defineComponent({
      name: 'StubTable',
      setup: () => () => h('div', { class: 'stub-table-page' })
    })
  }
}))

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

/** 造引导数据 */
function makeBootstrap(template: string): PageBootstrap {
  return {
    fromServer: true,
    url: {},
    menu: { code: 'menu_x', name: 'm', id: 1, template } as never,
    object: { code: 'eova_object_code', name: '对象', pk: 'id' } as never,
    btnList: [],
    loginUser: { isAdmin: false, id: 1 } as never
  }
}

describe('AppTemplateHost 运行期加固：组件表漂移时不得渲染空白页', () => {
  beforeEach(() => {
    boot.value = makeBootstrap('tree')
    ;(globalThis as unknown as Record<string, unknown>)['uzoo'] = { page: {}, vue: {}, app: {} }
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

  it('★ `tree` 在已迁移清单里、但组件表没有它 ⇒ 走 unmigrated 诊断（不是空白）', async () => {
    const w = mount(AppTemplateHost, { global: { components: stubs } })
    await flushPromises()
    const el = w.find('[data-eova-template]')
    expect(el.exists(), '组件缺失时必须渲染诊断，而不是什么都渲染不出来').toBe(true)
    expect(el.attributes('data-eova-template')).toBe('unmigrated')
    // 反空断言：诊断文本里必须点名模版（否则用户只知道"出错了"）
    expect(w.text()).toContain('tree')
  })

  it('对照：组件表里有的模版（table）照常渲染，不被加固误伤', async () => {
    boot.value = makeBootstrap('table')
    const w = mount(AppTemplateHost, { global: { components: stubs } })
    await flushPromises()
    expect(w.find('[data-eova-template]').exists()).toBe(false)
    // 组件表里有的模版：`<component :is>` 解析成功并真实挂载（本判据里 registry 被替换成替身）
    expect(w.find('.stub-table-page').exists()).toBe(true)
  })
})
