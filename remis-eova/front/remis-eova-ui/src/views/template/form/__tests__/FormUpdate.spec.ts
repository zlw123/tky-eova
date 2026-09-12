/**
 * 表单**修改**页的行为等价判据（切片 S6，第 295 轮）。
 *
 * 旧实现 = `_view/template/form/update/index.html`(28 行) + `index.js`(91 行) + `AppController#update()`。
 *
 * 本文件**只钉与 add 页不同的部分 + 自己独有的失败态**（同构部分由 `FormAdd.spec.ts` 覆盖；
 * 两页的公共契约由 `compat/__tests__/form-page.spec.ts` 覆盖）：
 *  ① `<ev-form>`：`mode="update"`、`name="update_from"`（★ **字面量**，不是对象编码）、
 *     ★ **有 `biz` 与 `pk`**（add 页没有这两项）；
 *  ② `data = reactive({})` —— ★ **不铺开 `uzoo.page.fixed`**（旧 update 的 html 里没有写
 *     `uzoo.page.fixed` 的那段脚本）⇒ 即使 URL 给了 `?ref=`，本页 `data` 也必须保持为空；
 *  ③ 提交走 **`form_update`** 键；校验失败不发请求；失败态两向；
 *  ④ `pk` 来自 `?id=`（旧 `get("id")`）；缺省空串（不是 undefined）。
 */
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import FormUpdate from '../FormUpdate.vue'
import { resetUzooWarning } from '@/compat/eova-ext'
import { setEovaMe, setEovaTools, type EovaMe, type EovaTools } from '@/compat/eova-runtime'

vi.mock('axios')

/** 路由参数（判据可改） */
const routeParams: { objectCode?: string } = { objectCode: 'eova_object_code' }

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: routeParams })
}))

const post = axios.post as unknown as ReturnType<typeof vi.fn>

/** 造 `me` 替身（`urls.url` 按制品的键回话） */
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
const getDataMock = vi.fn(() => ({ id: 1, name: '改后' }))

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
  window.history.replaceState({}, '', `/app/update/eova_object_code${search}`)
}

describe('FormUpdate.vue（旧 _view/template/form/update 的行为等价）', () => {
  let me: EovaMe

  beforeEach(() => {
    post.mockReset()
    validateMock.mockReset().mockReturnValue(true)
    getDataMock.mockReset().mockReturnValue({ id: 1, name: '改后' })
    routeParams.objectCode = 'eova_object_code'
    setSearch('')
    me = makeMe()
    setEovaMe(me)
    setEovaTools(makeTools())
    resetUzooWarning()
    delete (globalThis as unknown as Record<string, unknown>)['uzoo']
  })

  it('① ev-form：mode=update、name 是【字面量 update_from】、object=元对象编码、【有 biz 与 pk】', () => {
    setSearch('?id=12')
    const w = mount(FormUpdate, mountOpts)
    const form = w.findComponent(EvFormStub)
    expect(form.props('mode')).toBe('update')
    expect(form.props('name')).toBe('update_from')
    expect(form.props('object')).toBe('eova_object_code')
    // ★ 与 add 页的两处差别：biz 与 pk 都传了
    expect(form.props('biz')).toBe('eova_object_code')
    expect(form.props('pk')).toBe('12')
    expect(form.props('modelValue')).toBe(
      (w.vm as never as { data: Record<string, unknown> }).data
    )
  })

  it('④ pk 来自 `?id=`；缺省是空串（旧 `get("id")` 的既有形态，不是 undefined）', () => {
    const w1 = mount(FormUpdate, mountOpts)
    expect(w1.findComponent(EvFormStub).props('pk')).toBe('')
    delete (globalThis as unknown as Record<string, unknown>)['uzoo']

    setSearch('?id=99&biz=meta_menu')
    const w2 = mount(FormUpdate, mountOpts)
    expect(w2.findComponent(EvFormStub).props('pk')).toBe('99')
    expect((w2.vm as never as { props: Record<string, unknown> }).props['biz']).toBe('meta_menu')
    expect((w2.vm as never as { props: Record<string, unknown> }).props['form']).toBe('update')
  })

  it('② ★ data = reactive({}) —— 即使 URL 给了 `?ref=` 也【不铺开】（与 add/detail 的差别）', () => {
    setSearch('?id=12&ref=uid:7,type:9')
    const w = mount(FormUpdate, mountOpts)
    expect((w.vm as never as { data: Record<string, unknown> }).data).toEqual({})
    // 且 `uzoo.page.fixed` 也必须**没有**（旧 update 的 html 里没有那一段脚本）
    expect('fixed' in (w.vm as never as { props: Record<string, unknown> }).props).toBe(false)
  })

  it('③ 提交走 `form_update` 键，载荷是 getData()，成功只回传 eova-layer-ok_done', async () => {
    post.mockResolvedValue({ data: { state: 'ok' } })
    setSearch('?id=12')
    const w = mount(FormUpdate, mountOpts)
    await (w.vm as never as { onSubmit: (id?: unknown) => Promise<void> }).onSubmit(5)

    const urls = (me.urls.url as unknown as { mock: { calls: unknown[][] } }).mock
    expect(urls.calls[0][0]).toBe('form_update')
    expect(urls.calls[0][1]).toMatchObject({ object_code: 'eova_object_code' })
    expect(post).toHaveBeenCalledWith('/api/form/update/eova_object_code', { id: 1, name: '改后' })
    expect((me.cross.emit as unknown as { mock: { calls: unknown[][] } }).mock.calls).toEqual([
      ['eova-layer-ok_done', 5]
    ])
  })

  it('③ 失败态两向：state!=ok ⇒ layer.msg；异常 ⇒ layer.msg(客户端请求异常: …)；校验不过不发请求', async () => {
    post.mockResolvedValue({ data: { state: 'fail', msg: '名称重复' } })
    const w1 = mount(FormUpdate, mountOpts)
    await (w1.vm as never as { onSubmit: (id?: unknown) => Promise<void> }).onSubmit(1)
    expect(me.layer.msg).toHaveBeenCalledWith('名称重复')
    expect(me.layer.no).not.toHaveBeenCalled()

    post.mockRejectedValue(new Error('nope'))
    const w2 = mount(FormUpdate, mountOpts)
    await (w2.vm as never as { onSubmit: (id?: unknown) => Promise<void> }).onSubmit(1)
    expect(me.layer.msg).toHaveBeenCalledWith('客户端请求异常: nope')

    post.mockClear()
    validateMock.mockReturnValue(false)
    const w3 = mount(FormUpdate, mountOpts)
    await (w3.vm as never as { onSubmit: (id?: unknown) => Promise<void> }).onSubmit(1)
    expect(post).not.toHaveBeenCalled()
    expect(me.cross.emit).not.toHaveBeenCalled()
  })

  it('⑤ ★ 缺元对象编码 ⇒ 可声明降级（不渲染 ev-form、给出文案）', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    try {
      routeParams.objectCode = undefined as unknown as string
      const w = mount(FormUpdate, mountOpts)
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
    const w = mount(FormUpdate, mountOpts)
    expect(w.find('.eova-admins').exists()).toBe(false)
    expect((w.vm as never as { isAdmin: boolean }).isAdmin).toBe(false)
  })
})
