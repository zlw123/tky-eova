/**
 * 虚拟用户切换页的行为等价判据（旧 `_view/user/su/app.js` + `AdminController#su()/doSu()`）。
 *
 * 钉的契约：
 *  ① 未选中 ⇒ `me.layer.msg('请选择一个用户')` 且**不发请求**；
 *  ② `POST /eova/admin/doSu` 的载荷是**整行 user 对象**（不是挑字段）；
 *  ③ 成功 ⇒ **先** `emit('eova-layer-ok_done_data', \`${rid}【${name}】\`)`，**再** `emit('eova-layer-ok_done', id)`
 *     —— 顺序承重（宿主 done 会读 currentUser）；回传值是格式化字符串，不是对象；
 *  ④ 失败 ⇒ `me.layer.no(ret.msg)`；异常 ⇒ `me.layer.msg('客户端请求异常: ' + message)`；
 *  ⑤ `doResize` 用 `x.dom.getViewSize().height` **减 30**；
 *  ⑥ `onMounted` 注册 `eova-layer-ok`。
 */
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import Su from '../Su.vue'
import { setEovaMe, setEovaTools, type EovaMe, type EovaTools } from '@/compat/eova-runtime'

vi.mock('axios')
const post = axios.post as unknown as ReturnType<typeof vi.fn>

/** 造 `me` 替身 */
function makeMe(): EovaMe {
  return {
    layer: { msg: vi.fn(), no: vi.fn(), wa: vi.fn(), open: vi.fn() },
    cross: { on: vi.fn(), off: vi.fn(), emit: vi.fn() }
  } as unknown as EovaMe
}

/** 造 `EovaTools` 替身（`isEmpty` 与制品同义：null/undefined/空串/空数组都算空） */
function makeTools(viewHeight = 800): EovaTools {
  return {
    isEmpty: (v: unknown) =>
      v == null || v === '' || (Array.isArray(v) && v.length === 0),
    dom: { getViewSize: () => ({ width: 1200, height: viewHeight }) },
    validate: { start: () => true, showMsg: () => '', addRules: vi.fn() }
  } as unknown as EovaTools
}

/** `ev-form` / `ev-table` 的替身（真实组件由 legacy 制品在装配期注册） */
const EvFormStub = {
  name: 'EvForm',
  props: ['mode', 'object', 'modelValue'],
  emits: ['submit', 'resize'],
  template: '<form class="eova-form" :data-object="object"></form>'
}
const EvTableStub = {
  name: 'EvTable',
  props: ['object', 'page', 'height'],
  template: '<div class="eova-table-view" :data-object="object"><slot name="toolbar" /></div>'
}

const mountOpts = {
  global: { components: { EvForm: EvFormStub, EvTable: EvTableStub } }
}

/** 造一个"有选中行"的表格 ref 替身 */
function tableWith(rows: Array<Record<string, unknown>>) {
  return { query: vi.fn(), getSelectRows: vi.fn(() => rows) }
}

describe('Su.vue（旧 _view/user/su/app.js 的行为等价）', () => {
  let me: EovaMe

  beforeEach(() => {
    post.mockReset()
    me = makeMe()
    setEovaMe(me)
    setEovaTools(makeTools())
  })

  it('元对象编码：默认值取自 AdminController#su() 的文档默认 eova_user_code，并传到 ev-form/ev-table', () => {
    const w = mount(Su, mountOpts)
    expect((w.vm as never as { objectCode: string }).objectCode).toBe('eova_user_code')
    expect(w.find('form.eova-form').attributes('data-object')).toBe('eova_user_code')
    expect(w.find('.eova-table-view').attributes('data-object')).toBe('eova_user_code')
  })

  it('元对象编码：查询串 object 优先于默认值', () => {
    const spy = vi
      .spyOn(window, 'location', 'get')
      .mockReturnValue({ search: '?object=other_code' } as unknown as Location)
    try {
      const w = mount(Su, mountOpts)
      expect((w.vm as never as { objectCode: string }).objectCode).toBe('other_code')
    } finally {
      spy.mockRestore()
    }
  })

  it('分页初值：{page:1, limit:15}', () => {
    const w = mount(Su, mountOpts)
    expect((w.vm as never as { page: { page: number; limit: number } }).page).toEqual({
      page: 1,
      limit: 15
    })
  })

  it('onQuery：把查询表单数据交给表格 query()', () => {
    const w = mount(Su, mountOpts)
    const vm = w.vm as never as {
      refTable: unknown
      data: Record<string, unknown>
      onQuery: () => void
    }
    const table = tableWith([])
    vm.refTable = table
    vm.data.name = '曹'
    vm.onQuery()
    expect(table.query).toHaveBeenCalledWith(vm.data)
  })

  it('doResize：queryHeight 取表单高度；tableHeight = 视口高 - 表单高 - 30（不是 -0）', () => {
    setEovaTools(makeTools(800))
    const w = mount(Su, mountOpts)
    const vm = w.vm as never as {
      queryHeight: number
      tableHeight: number
      doResize: (h: number) => void
    }
    vm.doResize(88)
    expect(vm.queryHeight).toBe(88)
    expect(vm.tableHeight).toBe(800 - 88 - 30)
  })

  it('未选中用户 ⇒ me.layer.msg(\'请选择一个用户\') 且【不发请求】', async () => {
    const w = mount(Su, mountOpts)
    const vm = w.vm as never as { refTable: unknown; onSubmit: (id?: unknown) => Promise<void> }
    vm.refTable = tableWith([])
    await vm.onSubmit(3)
    expect(me.layer.msg).toHaveBeenCalledWith('请选择一个用户')
    expect(post).not.toHaveBeenCalled()
    expect(me.cross.emit).not.toHaveBeenCalled()
  })

  it('提交载荷是【整行 user 对象】（不是挑字段）', async () => {
    post.mockResolvedValue({ data: { state: 'ok' } })
    const w = mount(Su, mountOpts)
    const vm = w.vm as never as { refTable: unknown; onSubmit: (id?: unknown) => Promise<void> }
    const row = { id: 9, rid: 7, name: '林黛玉', org_id: 2, extra: 'x' }
    vm.refTable = tableWith([row])
    await vm.onSubmit(1)
    expect(post).toHaveBeenCalledWith('/eova/admin/doSu', row)
  })

  it('★ 成功：先回传数据、后回传关闭（顺序承重），且数据是格式化字符串 ${rid}【${name}】', async () => {
    post.mockResolvedValue({ data: { state: 'ok' } })
    const w = mount(Su, mountOpts)
    const vm = w.vm as never as { refTable: unknown; onSubmit: (id?: unknown) => Promise<void> }
    vm.refTable = tableWith([{ id: 9, rid: 7, name: '林黛玉' }])
    await vm.onSubmit(42)

    const emit = me.cross.emit as unknown as { mock: { calls: unknown[][] } }
    expect(emit.mock.calls).toEqual([
      ['eova-layer-ok_done_data', '7【林黛玉】'],
      ['eova-layer-ok_done', 42]
    ])
    expect(me.layer.no).not.toHaveBeenCalled()
  })

  it('业务失败：me.layer.no(ret.msg)，且不回传宿主', async () => {
    post.mockResolvedValue({ data: { state: 'fail', msg: '切换失败' } })
    const w = mount(Su, mountOpts)
    const vm = w.vm as never as { refTable: unknown; onSubmit: (id?: unknown) => Promise<void> }
    vm.refTable = tableWith([{ id: 1, rid: 2, name: 'X' }])
    await vm.onSubmit(1)
    expect(me.layer.no).toHaveBeenCalledWith('切换失败')
    expect(me.cross.emit).not.toHaveBeenCalled()
  })

  it('请求异常：me.layer.msg(\'客户端请求异常: \' + error.message)', async () => {
    post.mockRejectedValue(new Error('boom'))
    const w = mount(Su, mountOpts)
    const vm = w.vm as never as { refTable: unknown; onSubmit: (id?: unknown) => Promise<void> }
    vm.refTable = tableWith([{ id: 1, rid: 2, name: 'X' }])
    await vm.onSubmit(1)
    expect(me.layer.msg).toHaveBeenCalledWith('客户端请求异常: boom')
    expect(me.cross.emit).not.toHaveBeenCalled()
  })

  it('onMounted 注册 eova-layer-ok，且回调用事件带回的句柄提交', async () => {
    post.mockResolvedValue({ data: { state: 'ok' } })
    const w = mount(Su, mountOpts)
    expect(me.cross.on).toHaveBeenCalledWith('eova-layer-ok', expect.any(Function))
    const handler = (me.cross.on as unknown as { mock: { calls: unknown[][] } }).mock.calls[0][1] as (
      id: unknown
    ) => void
    ;(w.vm as never as { refTable: unknown }).refTable = tableWith([
      { id: 1, rid: 2, name: 'Y' }
    ])
    handler(77)
    await flushPromises()
    expect(me.cross.emit).toHaveBeenCalledWith('eova-layer-ok_done', 77)
  })

  it('运行时未装配时【响亮失败】', async () => {
    setEovaTools(null)
    const w = mount(Su, mountOpts)
    const vm = w.vm as never as { refTable: unknown; onSubmit: (id?: unknown) => Promise<void> }
    vm.refTable = tableWith([{ id: 1, rid: 2, name: 'X' }])
    await expect(vm.onSubmit(1)).rejects.toThrow(/未装配/)
    expect(post).not.toHaveBeenCalled()
  })
})
