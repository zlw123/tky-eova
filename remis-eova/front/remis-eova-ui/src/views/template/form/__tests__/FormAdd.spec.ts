/**
 * 表单**新增**页的行为等价判据（切片 S6，第 295 轮）。
 *
 * 旧实现 = `_view/template/form/add/index.html`(27 行) + `index.js`(97 行) + `AppController#add()`。
 *
 * 钉的契约（每条都对应一种"页面看起来正常但行为不对"）：
 *  ① `<ev-form>` 的 5 个契约：`mode="create"`、`name`/**`object` 都是元对象编码**、
 *     `v-model="data"`、`@ready`；★ **add 页没有 `biz`、没有 `pk`**（update/detail 才有）；
 *  ② `data = reactive({...props.fixed})` —— ★ **铺开的是 `uzoo.page.fixed`**（由 `?ref=` 派生），
 *     不是随便一个空对象；update 页必须**不**铺（那页是 `reactive({})`）；
 *  ③ `uzoo.page` 的四条赋值（`code`/`form`/`biz`/`fixed`）+ `_page/form.html` 的四个元对象键；
 *     ★ 缺来源的三个键必须**被点名**（页面据此告警），且**不得写成 undefined**；
 *  ④ 提交：`me.urls.url('form_add', props)`（★ 传的是**同一个 `uzoo.page` 引用**，
 *     `{{object_code}}` 靠它替换 ⇒ 少了就会请求 `/api/form/add/undefined`）；
 *     载荷是 **`refForm.getData()`**（不是 `data` 本身）；成功 **只** 回传 `eova-layer-ok_done`；
 *  ⑤ 失败态两向：`state!=='ok'` ⇒ `me.layer.msg(ret.msg)`（★ **不是 `no`**）；
 *     异常 ⇒ `me.layer.msg('客户端请求异常: ' + message)`；
 *  ⑥ `validate() === false` ⇒ **不发请求**（`== false` 是旧写法，这里按等价语义钉住）；
 *  ⑦ ★ **缺元对象编码 ⇒ 可声明降级**（不渲染 `ev-form`、给出文案、不发请求、响亮告警）——
 *     这是"不得静默白屏"的唯一可判形态；
 *  ⑧ `uzoo.vue.setup()` 的返回值摊平进 `uzoo.app`，且 `uzoo.app` 含 `props/data/refForm/onReady/onSubmit`；
 *  ⑨ 运行时未装配（`me` 缺失）⇒ **响亮失败**，不得静默降级。
 */
import { defineComponent, h, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import FormAdd from '../FormAdd.vue'
import { resetUzooWarning } from '@/compat/eova-ext'
import {
  setEovaMe,
  setEovaTools,
  type EovaMe,
  type EovaTools
} from '@/compat/eova-runtime'

vi.mock('axios')

/** 路由参数（判据可改；`useRoute` 的 mock 在下面读它） */
const routeParams: { objectCode?: string } = { objectCode: 'eova_object_code' }

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: routeParams })
}))

const post = axios.post as unknown as ReturnType<typeof vi.fn>

/** 造 `me` 替身（`urls.url` 按制品的 3 个键回话，便于断言"传进去的参数里有没有 object_code"） */
function makeMe(): EovaMe {
  return {
    layer: { msg: vi.fn(), no: vi.fn(), wa: vi.fn(), open: vi.fn() },
    cross: { on: vi.fn(), off: vi.fn(), emit: vi.fn() },
    urls: {
      url: vi.fn((key: string, params?: Record<string, unknown>) => {
        const table: Record<string, string> = {
          form_add: '/api/form/add/{{object_code}}',
          form_update: '/api/form/update/{{object_code}}'
        }
        return String(table[key] ?? `/unknown/${key}`).replace(
          /\{\{(\w+)\}\}/g,
          (_m, k: string) => String(params?.[k])
        )
      })
    }
  } as unknown as EovaMe
}

/** 造 `EovaTools` 替身（只用到 `isEmpty` 的字符串分支） */
function makeTools(): EovaTools {
  return {
    isEmpty: (v: unknown) => v == null || String(v).trim() === '' || String(v) === 'undefined',
    validate: { start: vi.fn(() => true), showMsg: vi.fn(() => ''), addRules: vi.fn() },
    dom: { getViewSize: () => ({ width: 1200, height: 800 }) },
    log: vi.fn()
  } as unknown as EovaTools
}

/** `ev-form` 的替身（真实组件由 legacy 制品在装配期注册） */
const validateMock = vi.fn(() => true)
const getDataMock = vi.fn((): Record<string, unknown> => ({ name: '测试' }))

const EvFormStub = defineComponent({
  name: 'EvForm',
  props: ['mode', 'name', 'object', 'biz', 'pk', 'modelValue'],
  emits: ['update:modelValue', 'ready', 'resize', 'submit'],
  setup(_props, { expose }) {
    expose({
      validate: () => validateMock(),
      getData: () => getDataMock(),
      reset: vi.fn(),
      getFieldInstance: vi.fn()
    })
    return () => h('div', { class: 'ev-form' })
  }
})

/** `ev-popup` 的替身（`EovaAdminForm` 是**真组件**，它要用到） */
const EvPopupStub = defineComponent({
  name: 'EvPopup',
  props: ['trigger', 'placement'],
  template: '<span class="eova-popup"><slot /><slot name="content" /></span>'
})

const mountOpts = {
  global: { components: { EvForm: EvFormStub, EvPopup: EvPopupStub } }
}

/**
 * 把地址栏换成给定 URL（`resolveFormPageParams` 默认读 `window.location.search`）
 *
 * @param search 查询串（含 `?`）
 */
function setSearch(search: string): void {
  window.history.replaceState({}, '', `/app/add/eova_object_code${search}`)
}

/** 取被测组件暴露的 `data` */
function dataOf(w: ReturnType<typeof mount>): Record<string, unknown> {
  return (w.vm as never as { data: Record<string, unknown> }).data
}

/** 取被测组件暴露的 `props`（= `uzoo.page`） */
function propsOf(w: ReturnType<typeof mount>): Record<string, unknown> {
  return (w.vm as never as { props: Record<string, unknown> }).props
}

describe('FormAdd.vue（旧 _view/template/form/add 的行为等价）', () => {
  let me: EovaMe

  beforeEach(() => {
    post.mockReset()
    validateMock.mockReset().mockReturnValue(true)
    getDataMock.mockReset().mockReturnValue({ name: '测试' })
    routeParams.objectCode = 'eova_object_code'
    setSearch('')
    me = makeMe()
    setEovaMe(me)
    setEovaTools(makeTools())
    // 每个用例都从"干净的 uzoo"起步（SPA 的 uzoo 跨路由常驻）
    resetUzooWarning()
    delete (globalThis as unknown as Record<string, unknown>)['uzoo']
  })

  it('① ev-form 的五个契约：mode=create、name/object 都是元对象编码、v-model=data、@ready；且【没有 biz/pk】', () => {
    const w = mount(FormAdd, mountOpts)
    const form = w.findComponent(EvFormStub)
    expect(form.exists()).toBe(true)
    expect(form.props('mode')).toBe('create')
    expect(form.props('name')).toBe('eova_object_code')
    expect(form.props('object')).toBe('eova_object_code')
    // ★ add 页**没有** biz/pk（逐字对齐旧 index.html 的 <ev-form> 属性集）
    expect(form.props('biz')).toBeUndefined()
    expect(form.props('pk')).toBeUndefined()
    // v-model 绑的是组件自己的 data **本体**
    expect(form.props('modelValue')).toBe(dataOf(w))
  })

  it('② data = {...uzoo.page.fixed}：由 `?ref=` 派生（空 ref ⇒ 空对象）', () => {
    let t = mount(FormAdd, mountOpts)
    expect(dataOf(t)).toEqual({})

    delete (globalThis as unknown as Record<string, unknown>)['uzoo']
    setSearch('?ref=uid:7,type:9')
    t = mount(FormAdd, mountOpts)
    expect(dataOf(t)).toEqual({ uid: '7', type: '9' })
    expect(propsOf(t)['fixed']).toEqual({ uid: '7', type: '9' })
  })

  it('③ uzoo.page 的四条赋值 + `_page/form.html` 的元对象键；缺来源的三个键被点名且未写 undefined', () => {
    setSearch('?biz=meta_menu')
    const w = mount(FormAdd, mountOpts)
    const page = propsOf(w)
    expect(page['code']).toBe('eova_object_code')
    expect(page['object_code']).toBe('eova_object_code')
    expect(page['form']).toBe('create')
    expect(page['biz']).toBe('meta_menu')
    expect(page['fixed']).toEqual({})
    // 无引导数据来源（DES-005 §16.1 非范围②）⇒ 三个键是缺口，**不得**写成 undefined
    for (const k of ['object_id', 'object_name', 'object_pk']) {
      expect(k in page, `${k} 不得被写成 undefined`).toBe(false)
    }
    expect((w.vm as never as { missingPageKeys: string[] }).missingPageKeys).toEqual([
      'object_id',
      'object_name',
      'object_pk'
    ])
    // ★ 缺口必须**响亮告警**（否则"超管面板不出现"会被当成"用户不是超管"）
    expect(console.warn).toBeDefined()
  })

  it('③ 缺缺口告警：挂在 onMounted 上且点名了具体键（用 spy 钉住文案内容）', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    try {
      mount(FormAdd, mountOpts)
      const texts = warn.mock.calls.map((c) => String(c[0]))
      expect(texts.some((t) => t.includes('object_id/object_name/object_pk'))).toBe(true)
    } finally {
      warn.mockRestore()
    }
  })

  it('④ 提交：url 由 `me.urls.url("form_add", props)` 拼出，载荷是 getData() 的结果', async () => {
    post.mockResolvedValue({ data: { state: 'ok' } })
    getDataMock.mockReturnValue({ name: '新品', price: 9 })
    const w = mount(FormAdd, mountOpts)
    await (w.vm as never as { onSubmit: (id?: unknown) => Promise<void> }).onSubmit(42)

    const urls = (me.urls.url as unknown as { mock: { calls: unknown[][] } }).mock
    expect(urls.calls[0][0]).toBe('form_add')
    // ★ 传进去的必须是**同一个 uzoo.page 引用**（`{{object_code}}` 靠它替换）
    expect(urls.calls[0][1]).toBe(propsOf(w))
    expect(urls.calls[0][1]).toMatchObject({ object_code: 'eova_object_code' })
    expect(post).toHaveBeenCalledWith('/api/form/add/eova_object_code', { name: '新品', price: 9 })
    // ★ 成功**只**回传 eova-layer-ok_done（不像 su 页还有 `_data` 那条）
    expect((me.cross.emit as unknown as { mock: { calls: unknown[][] } }).mock.calls).toEqual([
      ['eova-layer-ok_done', 42]
    ])
    expect(me.layer.msg).not.toHaveBeenCalled()
  })

  it('④ 反向：请求 URL 必须随元对象编码变化（写死字面量会红）', async () => {
    post.mockResolvedValue({ data: { state: 'ok' } })
    routeParams.objectCode = 'meta_product'
    setSearch('')
    const w = mount(FormAdd, mountOpts)
    await (w.vm as never as { onSubmit: (id?: unknown) => Promise<void> }).onSubmit(1)
    expect(post.mock.calls[0][0]).toBe('/api/form/add/meta_product')
  })

  it('⑤ 失败态两向：state!=ok ⇒ layer.msg(ret.msg)；异常 ⇒ layer.msg(客户端请求异常: …)', async () => {
    post.mockResolvedValue({ data: { state: 'fail', msg: '名称已存在' } })
    const w1 = mount(FormAdd, mountOpts)
    await (w1.vm as never as { onSubmit: (id?: unknown) => Promise<void> }).onSubmit(1)
    expect(me.layer.msg).toHaveBeenCalledWith('名称已存在')
    // ★ 旧实现用 `msg` 而不是 `no` —— 用错级别就红
    expect(me.layer.no).not.toHaveBeenCalled()
    expect(me.cross.emit).not.toHaveBeenCalled()

    post.mockRejectedValue(new Error('boom'))
    const w2 = mount(FormAdd, mountOpts)
    await (w2.vm as never as { onSubmit: (id?: unknown) => Promise<void> }).onSubmit(1)
    expect(me.layer.msg).toHaveBeenCalledWith('客户端请求异常: boom')
    expect(me.cross.emit).not.toHaveBeenCalled()
  })

  it('⑥ validate() === false ⇒ 不发请求、不回传关层', async () => {
    validateMock.mockReturnValue(false)
    const w = mount(FormAdd, mountOpts)
    await (w.vm as never as { onSubmit: (id?: unknown) => Promise<void> }).onSubmit(7)
    expect(post).not.toHaveBeenCalled()
    expect(me.cross.emit).not.toHaveBeenCalled()
    expect(me.layer.msg).not.toHaveBeenCalled()
  })

  it('⑦ ★ 缺元对象编码 ⇒ 可声明降级：不渲染 ev-form、给出文案、不发请求', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    try {
      routeParams.objectCode = undefined as unknown as string
      const w = mount(FormAdd, mountOpts)
      expect(w.findComponent(EvFormStub).exists(), '缺参时不得渲染 ev-form').toBe(false)
      expect(w.find('.eova-form-degraded').exists(), '必须给出可声明降级文案（不得静默白屏）').toBe(
        true
      )
      expect(w.text()).toContain('缺少元对象编码')
      expect(warn.mock.calls.map((c) => String(c[0])).some((t) => t.includes('缺少元对象编码'))).toBe(
        true
      )
      await nextTick()
    } finally {
      warn.mockRestore()
    }
  })

  it('⑧ uzoo.vue.setup 的返回值摊平进 uzoo.app，且 uzoo.app 含五个契约成员', () => {
    const setupHook = vi.fn(() => ({ extra: 'v' }))
    ;(globalThis as unknown as Record<string, unknown>)['uzoo'] = {
      page: {},
      vue: { setup: setupHook },
      app: {}
    }
    const w = mount(FormAdd, mountOpts)
    expect(setupHook).toHaveBeenCalledTimes(1)
    const uzoo = (globalThis as unknown as Record<string, unknown>)['uzoo'] as {
      app: Record<string, unknown>
    }
    expect(uzoo.app['extra']).toBe('v')
    expect(uzoo.app['props']).toBe(propsOf(w))
    expect(uzoo.app['data']).toBe(dataOf(w))
    for (const k of ['refForm', 'onReady', 'onSubmit']) {
      expect(k in uzoo.app, `uzoo.app 缺 ${k}`).toBe(true)
    }
  })

  it('⑧ onReady 回调 `uzoo.vue.onReady`（**存在才调**，与旧实现同一条守则）', () => {
    const onReadyHook = vi.fn()
    ;(globalThis as unknown as Record<string, unknown>)['uzoo'] = {
      page: {},
      vue: { onReady: onReadyHook },
      app: {}
    }
    const w = mount(FormAdd, mountOpts)
    const fields = new Map([['name', {}]])
    ;(w.vm as never as { onReady: (f: unknown) => void }).onReady(fields)
    expect(onReadyHook).toHaveBeenCalledWith(fields)

    // 钩子未注册时**不得**抛错（旧实现 `typeof === 'function'` 才调）
    delete (globalThis as unknown as Record<string, unknown>)['uzoo']
    const w2 = mount(FormAdd, mountOpts)
    expect(() =>
      (w2.vm as never as { onReady: (f: unknown) => void }).onReady(fields)
    ).not.toThrow()
  })

  it('⑨ onMounted 注册 eova-layer-ok，且回调用事件带回的句柄提交', async () => {
    post.mockResolvedValue({ data: { state: 'ok' } })
    mount(FormAdd, mountOpts)
    expect(me.cross.on).toHaveBeenCalledWith('eova-layer-ok', expect.any(Function))
    const handler = (me.cross.on as unknown as { mock: { calls: unknown[][] } }).mock.calls[0][1] as (
      id: unknown
    ) => void
    handler(99)
    await nextTick()
    expect(post).toHaveBeenCalledTimes(1)
  })

  it('⑩ ★ 超管面板当前**必须不渲染**（动作页没有 isAdmin 来源 ⇒ 不得猜 true）', () => {
    const w = mount(FormAdd, mountOpts)
    expect(w.find('.eova-admins').exists()).toBe(false)
    expect((w.vm as never as { isAdmin: boolean }).isAdmin).toBe(false)
  })

  it('⑪ 运行时未装配时【响亮失败】（不得静默降级成"没反应"）', async () => {
    // 卸载前先把替换件拿掉，否则 `onMounted` 里的 `getEovaMe()` 会先抛
    setEovaMe(null)
    expect(() => mount(FormAdd, mountOpts), '缺运行时必须在挂载期响亮失败').toThrow(/未装配/)
  })
})
