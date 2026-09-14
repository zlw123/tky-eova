/**
 * **切片 B 挂载判据**（r327）：表单页按**元对象编码**命中自定义 app 时"叠加挂载"，
 * 未命中时**不挂载**。
 *
 * ## 口径（r318 用 CDP 实测的旧栈机制 + r327 拿哥裁定切片 B 开工）
 *
 * 旧 `template/form/{add,update,detail}/index.js` 结尾都是 `me.vue.mount(app, uzoo.page.code)`
 * —— 键 = **元对象编码**；命中时把自定义模版（`/hotel/app.vue`、`/product/app.vue`）
 * **叠加**到页面上（**不是替换**：标准 `<ev-form>` 仍在）。
 *
 * ⇒ 本判据三向都要有（只测一侧会假通过）：
 *   ① `meta_product` 命中 ⇒ 挂载 `ProductApp` **且**标准表单仍在；
 *   ② `meta_hotel` 命中 ⇒ 挂载 `MetaHotelFormApp`（= 恒隐藏的 `<sword-coming>`，旧栈里
 *      `currentRow`/`showLinking` 在表单页作用域解析为 undefined ⇒ `v-show` 为假）；
 *   ③ 未命中的对象（如 `eova_object_code`）⇒ **一个都不挂载**（防"所有表单页凭空多出组件"）。
 *
 * ★ 顺带钉"钩子确实装了"：命中页面的 `uzoo.vue.onReady` 必须是该键脚本的实现
 *   （旧栈由脚本自赋值；SPA 由宿主 `installCustomAppHooks` 安装）。
 */
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import FormUpdate from '../FormUpdate.vue'
import ProductApp from '@/views/custom/ProductApp.vue'
import SwordComing from '@/views/custom/SwordComing.vue'
import { metaHotelAppHooks } from '@/views/custom/metaHotelApp'
import { metaProductAppHooks } from '@/views/custom/metaProductApp'
import { getUzoo, resetUzooWarning } from '@/compat/eova-ext'
import { setEovaMe, setEovaTools, type EovaMe, type EovaTools } from '@/compat/eova-runtime'
import { setDefaultBootstrapFetcher } from '@/compat/page-bootstrap-fetcher'

vi.mock('axios')

/** 路由参数（用例前重置） */
const routeParams: { objectCode?: string } = { objectCode: 'meta_product' }
vi.mock('vue-router', () => ({ useRoute: () => ({ params: routeParams }) }))

const post = axios.post as unknown as ReturnType<typeof vi.fn>

/** `ev-form` 替身（真实组件由 legacy 制品在装配期注册） */
const stubs = {
  EvForm: {
    name: 'EvForm',
    props: ['mode', 'name', 'object', 'biz', 'pk', 'modelValue'],
    emits: ['ready'],
    template: '<div class="ev-form"></div>'
  },
  EvPopup: {
    name: 'EvPopup',
    props: ['trigger', 'placement'],
    template: '<span class="eova-popup"><slot /><slot name="content" /></span>'
  },
  // 制品注册的表格组件（`SwordComing` 面板里的两个子表用到；不 stub 会打"未解析组件"告警）
  EvTable: {
    name: 'EvTable',
    props: ['object', 'biz', 'page', 'height', 'initQuery'],
    template: '<div class="ev-table"></div>'
  }
}
const mountOpts = { global: { components: stubs } }

beforeEach(() => {
  post.mockReset()
  post.mockResolvedValue({ data: { state: 'ok' } })
  routeParams.objectCode = 'meta_product'
  setEovaMe({
    layer: { msg: vi.fn(), no: vi.fn(), wa: vi.fn(), notify: vi.fn(), open: vi.fn() },
    cross: { on: vi.fn(), off: vi.fn(), emit: vi.fn() },
    urls: { url: vi.fn(() => '/api/form/update/meta_product') },
    conf: { putAll: vi.fn(), get: vi.fn(), getAll: () => ({}) }
  } as unknown as EovaMe)
  setEovaTools({
    isEmpty: (v: unknown) => v == null || String(v).trim() === '',
    validate: { start: vi.fn(() => true), showMsg: vi.fn(() => ''), addRules: vi.fn() },
    dom: { getViewSize: () => ({ width: 1200, height: 800 }) },
    log: vi.fn()
  } as unknown as EovaTools)
  setDefaultBootstrapFetcher(null)
  resetUzooWarning()
  delete (globalThis as unknown as Record<string, unknown>)['uzoo']
})

describe('表单页自定义 app 的叠加挂载（r327 切片 B）', () => {
  it('① meta_product 命中 ⇒ 挂载 ProductApp，且标准表单仍在（叠加而非替换）', () => {
    const w = mount(FormUpdate, mountOpts)
    expect(w.findComponent(ProductApp).exists()).toBe(true)
    expect(w.find('.ev-form').exists()).toBe(true)
    // 旧 `/product/app.vue` 的活代码就是 `<br>{{ data }}`（data = 表单数据本体）
    expect(w.findComponent(ProductApp).props('data')).toBeTruthy()
    expect(getUzoo().vue.setup).toBe(metaProductAppHooks.setup)
    expect(getUzoo().vue.onReady).toBeUndefined() // 旧 product/app.js **没有** onReady
  })

  it('② ★ meta_hotel 命中 ⇒ 只叠加**空的原生 `sword-coming` 标签**（旧配置无 components ⇒ 面板不挂）', () => {
    // CDP 取证（旧栈三张表单页一致）：DOM 里是 `<sword-coming></sword-coming>`（0 子节点、0×0、
    // 父链 SWORD-COMING→DIV#app），**没有** `.sword-go`/`#imageContainer`，也**不发**
    // `POST /api/meta/table/hotel_stock|hotel_bed`（旧配置 `vue.app('meta_hotel',…)` 无 `components`）。
    routeParams.objectCode = 'meta_hotel'
    const w = mount(FormUpdate, mountOpts)
    const tag = w.find('sword-coming')
    expect(tag.exists()).toBe(true)
    expect(tag.element.childNodes.length).toBe(0)
    // ★ 反向：不许把真面板挂上来（挂上就会多两次元数据调用 —— 这是本轮实测抓到的唯一缺口）
    expect(w.findComponent(SwordComing).exists()).toBe(false)
    expect(w.find('.sword-go').exists()).toBe(false)
    expect(w.find('#imageContainer').exists()).toBe(false)
    // 标准表单仍在（叠加而非替换）
    expect(w.find('.ev-form').exists()).toBe(true)
    // 钩子照样装（旧栈 `/hotel/app.js` 确实被加载并执行）
    expect(getUzoo().vue.onReady).toBe(metaHotelAppHooks.onReady)
  })

  it('③ ★ 反向：未命中的对象 ⇒ 一个自定义组件都不挂载', () => {
    routeParams.objectCode = 'eova_object_code'
    const w = mount(FormUpdate, mountOpts)
    expect(w.findComponent(ProductApp).exists()).toBe(false)
    expect(w.findComponent(SwordComing).exists()).toBe(false)
    expect(w.find('.ev-form').exists()).toBe(true)
    expect(getUzoo().vue.setup).toBeUndefined()
    expect(getUzoo().vue.onReady).toBeUndefined()
  })

  it('④ ★ 换页不残留：meta_hotel ⇒ eova_object_code，酒店脚本的钩子必须被撤掉', () => {
    routeParams.objectCode = 'meta_hotel'
    mount(FormUpdate, mountOpts)
    expect(getUzoo().vue.onReady).toBe(metaHotelAppHooks.onReady)
    routeParams.objectCode = 'eova_object_code'
    mount(FormUpdate, mountOpts)
    expect(getUzoo().vue.onReady).toBeUndefined()
  })
  it('⑤ ★ 页面级行为：`@ready` 派发后，自定义脚本的钩子**真的跑了**（通知 + 联动装配）', async () => {
    routeParams.objectCode = 'meta_hotel'
    const me = {
      layer: { msg: vi.fn(), no: vi.fn(), wa: vi.fn(), notify: vi.fn(), open: vi.fn() },
      cross: { on: vi.fn(), off: vi.fn(), emit: vi.fn() },
      urls: { url: vi.fn(() => '/api/form/update/meta_hotel') },
      conf: { putAll: vi.fn(), get: vi.fn(), getAll: () => ({}) }
    }
    setEovaMe(me as unknown as EovaMe)
    const w = mount(FormUpdate, mountOpts)
    await flushPromises()

    const city = { setDisabled: vi.fn(), setOption: vi.fn() }
    const region = { setDisabled: vi.fn(), setOption: vi.fn() }
    w.findComponent({ name: 'EvForm' }).vm.$emit('ready', {
      get: (n: string) => (n === 'city' ? city : region)
    })
    await flushPromises()

    // 通知：旧 `/hotel/app.js` 按 `uzoo.page.form` 分支（本页 = update）
    expect(me.layer.notify).toHaveBeenCalledWith('自定义逻辑', '/hotel/app.js form:update', 'ok')
    // 联动装配：初始禁用市/区
    expect(city.setDisabled).toHaveBeenCalledWith(true)
    expect(region.setDisabled).toHaveBeenCalledWith(true)
  })

})
