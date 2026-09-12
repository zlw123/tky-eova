/**
 * 表单**查看**页的行为等价判据（切片 S6，第 295 轮）。
 *
 * 旧实现 = `_view/template/form/detail/index.html`(27 行) + `index.js`(91 行) + `AppController#detail()`。
 *
 * 本文件**只钉与 add/update 不同的部分 + 自己独有的既有怪癖**：
 *  ① `<ev-form>`：★ **`mode="read"`**、`name="detail_from"`（字面量）、有 `biz` 与 `pk`；
 *  ② `data = reactive({...props.fixed})`（与 add 同构）—— ★ 但 detail 页的 html **没有**写
 *     `uzoo.page.fixed` 的那段脚本 ⇒ `props.fixed` 恒为 `undefined` ⇒ 实际结果必须是 `{}`
 *     （"铺了但恒空" —— 两条叠加后的**可观测结果**，判据按结果钉）；
 *  ③ ★★ **提交走 `form_update` 键（不是 `form_detail`）** —— 这是旧实现的既有行为，原样保留；
 *     `me.urls` 里确实存在 `form_detail` 键，所以"顺手改对"会**静默偏离**旧栈 ⇒ 必须钉住；
 *  ④ 失败态两向 + 缺参降级 + 超管面板不渲染。
 */
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import FormDetail from '../FormDetail.vue'
import { resetUzooWarning } from '@/compat/eova-ext'
import { setEovaMe, setEovaTools, type EovaMe, type EovaTools } from '@/compat/eova-runtime'

vi.mock('axios')

/** 路由参数（判据可改） */
const routeParams: { objectCode?: string } = { objectCode: 'eova_object_code' }

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: routeParams })
}))

const post = axios.post as unknown as ReturnType<typeof vi.fn>

/** 造 `me` 替身（★ URL 表里**同时**有 `form_detail` 与 `form_update`，才能分辨用错了哪个） */
function makeMe(): EovaMe {
  return {
    layer: { msg: vi.fn(), no: vi.fn(), wa: vi.fn(), open: vi.fn() },
    cross: { on: vi.fn(), off: vi.fn(), emit: vi.fn() },
    urls: {
      url: vi.fn((key: string, params?: Record<string, unknown>) => {
        const table: Record<string, string> = {
          form_add: '/api/form/add/{{object_code}}',
          form_update: '/api/form/update/{{object_code}}',
          form_detail: '/api/form/detail/{{object_code}}'
        }
        return String(table[key] ?? `/unknown/${key}`).replace(
          /\{\{(\w+)\}\}/g,
          (_m, k: string) => String(params?.[k])
        )
      })
    }
  } as unknown as EovaMe
}

/** 造 `EovaTools` 替身 */
function makeTools(): EovaTools {
  return {
    isEmpty: (v: unknown) => v == null || String(v).trim() === '' || String(v) === 'undefined',
    validate: { start: vi.fn(() => true), showMsg: vi.fn(() => ''), addRules: vi.fn() },
    dom: { getViewSize: () => ({ width: 1200, height: 800 }) },
    log: vi.fn()
  } as unknown as EovaTools
}

/** `ev-form` 的替身 */
const validateMock = vi.fn(() => true)
const getDataMock = vi.fn(() => ({ id: 3, name: '只读' }))

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

const EvPopupStub = defineComponent({
  name: 'EvPopup',
  props: ['trigger', 'placement'],
  template: '<span class="eova-popup"><slot /><slot name="content" /></span>'
})

const mountOpts = { global: { components: { EvForm: EvFormStub, EvPopup: EvPopupStub } } }

/**
 * 把地址栏换成给定 URL
 *
 * @param search 查询串（含 `?`）
 */
function setSearch(search: string): void {
  window.history.replaceState({}, '', `/app/detail/eova_object_code${search}`)
}

describe('FormDetail.vue（旧 _view/template/form/detail 的行为等价）', () => {
  let me: EovaMe

  beforeEach(() => {
    post.mockReset()
    validateMock.mockReset().mockReturnValue(true)
    getDataMock.mockReset().mockReturnValue({ id: 3, name: '只读' })
    routeParams.objectCode = 'eova_object_code'
    setSearch('')
    me = makeMe()
    setEovaMe(me)
    setEovaTools(makeTools())
    resetUzooWarning()
    delete (globalThis as unknown as Record<string, unknown>)['uzoo']
  })

  it('① ev-form：mode=read、name 是【字面量 detail_from】、object=元对象编码、有 biz 与 pk', () => {
    setSearch('?id=3')
    const w = mount(FormDetail, mountOpts)
    const form = w.findComponent(EvFormStub)
    expect(form.props('mode')).toBe('read')
    expect(form.props('name')).toBe('detail_from')
    expect(form.props('object')).toBe('eova_object_code')
    expect(form.props('biz')).toBe('eova_object_code')
    expect(form.props('pk')).toBe('3')
  })

  it('② data 必须是空表（旧代码铺了 props.fixed，但 detail 页恒不写 fixed ⇒ 结果为空）', () => {
    setSearch('?id=3&ref=uid:7')
    const w = mount(FormDetail, mountOpts)
    expect((w.vm as never as { data: Record<string, unknown> }).data).toEqual({})
    expect('fixed' in (w.vm as never as { props: Record<string, unknown> }).props).toBe(false)
    // `uzoo.page.form` 是 `read`（不是 `create`）⇒ 连 `writeFormPageUzooPage` 都不该写 fixed
    expect((w.vm as never as { props: Record<string, unknown> }).props['form']).toBe('read')
  })

  it('③ ★★ 提交走 `form_update`（不是 `form_detail`）—— 旧实现的既有行为，原样保留', async () => {
    post.mockResolvedValue({ data: { state: 'ok' } })
    setSearch('?id=3')
    const w = mount(FormDetail, mountOpts)
    await (w.vm as never as { onSubmit: (id?: unknown) => Promise<void> }).onSubmit(8)

    const urls = (me.urls.url as unknown as { mock: { calls: unknown[][] } }).mock
    expect(urls.calls[0][0], '★ 旧实现用的是 form_update —— 改成 form_detail 就是偏离旧栈').toBe(
      'form_update'
    )
    expect(post).toHaveBeenCalledWith('/api/form/update/eova_object_code', { id: 3, name: '只读' })
    expect((me.cross.emit as unknown as { mock: { calls: unknown[][] } }).mock.calls).toEqual([
      ['eova-layer-ok_done', 8]
    ])
  })

  it('④ 失败态两向：state!=ok ⇒ layer.msg；异常 ⇒ layer.msg(客户端请求异常: …)', async () => {
    post.mockResolvedValue({ data: { state: 'fail', msg: '无权限' } })
    const w1 = mount(FormDetail, mountOpts)
    await (w1.vm as never as { onSubmit: (id?: unknown) => Promise<void> }).onSubmit(1)
    expect(me.layer.msg).toHaveBeenCalledWith('无权限')
    expect(me.layer.no).not.toHaveBeenCalled()

    post.mockRejectedValue(new Error('gone'))
    const w2 = mount(FormDetail, mountOpts)
    await (w2.vm as never as { onSubmit: (id?: unknown) => Promise<void> }).onSubmit(1)
    expect(me.layer.msg).toHaveBeenCalledWith('客户端请求异常: gone')
  })

  it('⑤ ★ 缺元对象编码 ⇒ 可声明降级（不渲染 ev-form、给出文案、响亮告警）', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    try {
      routeParams.objectCode = undefined as unknown as string
      const w = mount(FormDetail, mountOpts)
      expect(w.findComponent(EvFormStub).exists()).toBe(false)
      expect(w.find('.eova-form-degraded').exists()).toBe(true)
      expect(w.text()).toContain('缺少元对象编码')
      expect(warn.mock.calls.map((c) => String(c[0])).some((t) => t.includes('缺少元对象编码'))).toBe(
        true
      )
    } finally {
      warn.mockRestore()
    }
  })

  it('⑥ 超管面板当前必须不渲染（动作页没有 isAdmin 来源 ⇒ 不得猜 true）', () => {
    const w = mount(FormDetail, mountOpts)
    expect(w.find('.eova-admins').exists()).toBe(false)
    expect((w.vm as never as { isAdmin: boolean }).isAdmin).toBe(false)
  })
})
