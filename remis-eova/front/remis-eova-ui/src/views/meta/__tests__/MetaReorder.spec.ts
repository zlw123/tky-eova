/**
 * 重新排序页的行为等价判据（旧 `_view/meta/reorder/app.js` + `app.html` + `MetaController`）。
 *
 * 钉的契约：
 *  ① `biz` **默认 `field`**（`get("biz","field")`）、`mode` **无默认**（只在 `field_diy` 时被 set）；
 *  ② `data` 是**数组 ref**，初值来自服务端（`#json(data)`）⇒ 端点未就绪时退化为 `[]`；
 *  ③ `onSubmit` **没有任何校验**，URL 由 **`uzoo.page`** 拼出（不是本地变量）；
 *  ④ 业务失败走 **`me.layer.msg`**（不是 `no` —— 与改密/su/button-add 三页都不同）；
 *  ⑤ `onBeforeMount` 回调扩展钩子（本页 `app.html:16` 正是定义它的一侧）；
 *  ⑥ 解析容错：数组 / JSON 字符串可用，其它形态告警并退化为 `[]`。
 */
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import MetaReorder from '../MetaReorder.vue'
import { parseReorderRows } from '@/utils/reorder'
import { setEovaMe, type EovaMe } from '@/compat/eova-runtime'
import { resetUzooWarning } from '@/compat/eova-ext'

vi.mock('axios')
// 查询串可逐用例改写（用 vi.hoisted 保证工厂能引用到同一个可变对象）
const routeState = vi.hoisted(() => ({ query: {} as Record<string, string> }))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: routeState.query }) }))

const post = axios.post as unknown as ReturnType<typeof vi.fn>

/** 造 `me` 替身 */
function makeMe(): EovaMe {
  return {
    layer: { msg: vi.fn(), no: vi.fn(), wa: vi.fn(), open: vi.fn() },
    cross: { on: vi.fn(), off: vi.fn(), emit: vi.fn() }
  } as unknown as EovaMe
}

/** `ev-sortable` 替身 */
const EvSortableStub = {
  name: 'EvSortable',
  props: ['modelValue', 'text_field', 'order_field'],
  template: '<div class="eova-sort" :data-text="text_field" :data-order="order_field"></div>'
}
const mountOpts = { global: { components: { EvSortable: EvSortableStub } } }

describe('MetaReorder.vue（旧 _view/meta/reorder 的行为等价）', () => {
  let me: EovaMe

  beforeEach(() => {
    post.mockReset()
    resetUzooWarning()
    delete (globalThis as unknown as Record<string, unknown>)['uzoo']
    me = makeMe()
    setEovaMe(me)
    routeState.query = { object: 'eova_user_code', biz: 'field_diy', mode: 'update' }
  })

  it('URL 参数：object/biz/mode 取自查询串（biz=field_diy、mode=update）', () => {
    const w = mount(MetaReorder, mountOpts)
    const vm = w.vm as never as { param: (k: 'object' | 'biz' | 'mode', f?: string) => string }
    expect(vm.param('object')).toBe('eova_user_code')
    expect(vm.param('biz', 'field')).toBe('field_diy')
    expect(vm.param('mode')).toBe('update')
  })

  it('★ 查询串为空时：`biz` 默认 field、`mode` 默认空串（旧 `get("biz","field")` 与"只在 field_diy 时 set mode"）', () => {
    routeState.query = {}
    mount(MetaReorder, mountOpts)
    const uzoo = (globalThis as unknown as Record<string, unknown>)['uzoo'] as {
      page: Record<string, unknown>
    }
    expect(uzoo.page['biz']).toBe('field')
    expect(uzoo.page['mode']).toBe('')
  })

  it('结构：ev-sortable 的 text_field=name、order_field=num 逐字一致', () => {
    const w = mount(MetaReorder, mountOpts)
    const s = w.findComponent(EvSortableStub)
    expect(s.props('text_field')).toBe('name')
    expect(s.props('order_field')).toBe('num')
  })

  it('`uzoo.page` 被写入 biz/mode（旧钩子如此，且 `onSubmit` 从那里读）', async () => {
    mount(MetaReorder, mountOpts)
    const uzoo = (globalThis as unknown as Record<string, unknown>)['uzoo'] as {
      page: Record<string, unknown>
    }
    expect(uzoo.page['biz']).toBe('field_diy')
    expect(uzoo.page['mode']).toBe('update')
  })

  it('★ onBeforeMount 回调扩展钩子 uzoo.vue.mountBefore（本页 app.html:16 正是定义它的一侧）', () => {
    const hook = vi.fn()
    ;(globalThis as unknown as Record<string, unknown>)['uzoo'] = {
      page: {},
      vue: { mountBefore: hook },
      app: {}
    }
    mount(MetaReorder, mountOpts)
    expect(hook).toHaveBeenCalledTimes(1)
  })

  it('数据：端点未就绪时 data 为 []（诚实降级，不装作有数据）', () => {
    const w = mount(MetaReorder, mountOpts)
    expect((w.vm as never as { data: unknown[] }).data).toEqual([])
  })

  it('★ 提交：无校验；URL 由 uzoo.page 拼出（含 biz 与 mode 两个查询参数）', async () => {
    post.mockResolvedValue({ data: { state: 'ok' } })
    const w = mount(MetaReorder, mountOpts)
    const vm = w.vm as never as {
      data: unknown[]
      onSubmit: (id?: unknown) => Promise<void>
    }
    vm.data.push({ id: 1, name: 'A', num: 1 })
    await vm.onSubmit(7)
    expect(post).toHaveBeenCalledTimes(1)
    expect(post.mock.calls[0][0]).toBe('/meta/updateReorder?biz=field_diy&mode=update')
    expect(post.mock.calls[0][1]).toBe(vm.data)
  })

  it('★ 成功：回传 eova-layer-ok_done', async () => {
    post.mockResolvedValue({ data: { state: 'ok' } })
    const w = mount(MetaReorder, mountOpts)
    await (w.vm as never as { onSubmit: (id?: unknown) => Promise<void> }).onSubmit(42)
    expect(me.cross.emit).toHaveBeenCalledWith('eova-layer-ok_done', 42)
  })

  it('★★ 业务失败走 me.layer.msg（不是 no —— 与改密/su/button-add 三页都不同）', async () => {
    post.mockResolvedValue({ data: { state: 'fail', msg: '排序保存失败' } })
    const w = mount(MetaReorder, mountOpts)
    await (w.vm as never as { onSubmit: (id?: unknown) => Promise<void> }).onSubmit(1)
    expect(me.layer.msg).toHaveBeenCalledWith('排序保存失败')
    expect(me.layer.no).not.toHaveBeenCalled()
    expect(me.cross.emit).not.toHaveBeenCalled()
  })

  it('异常：me.layer.msg(\'客户端请求异常: \' + message)', async () => {
    post.mockRejectedValue(new Error('boom'))
    const w = mount(MetaReorder, mountOpts)
    await (w.vm as never as { onSubmit: (id?: unknown) => Promise<void> }).onSubmit(1)
    expect(me.layer.msg).toHaveBeenCalledWith('客户端请求异常: boom')
  })

  it('onMounted 注册 eova-layer-ok，且回调用事件带回的句柄提交', async () => {
    post.mockResolvedValue({ data: { state: 'ok' } })
    mount(MetaReorder, mountOpts)
    expect(me.cross.on).toHaveBeenCalledWith('eova-layer-ok', expect.any(Function))
    const handler = (me.cross.on as unknown as { mock: { calls: unknown[][] } }).mock.calls[0][1] as (
      id: unknown
    ) => void
    handler(99)
    await flushPromises()
    expect(me.cross.emit).toHaveBeenCalledWith('eova-layer-ok_done', 99)
  })
})

describe('parseReorderRows（旧 `JSON.parse(\'#json(data)\')` 的等价 + 容错）', () => {
  it('数组原样返回（端点直接返回对象 —— DES-004 的推荐形态）', () => {
    const rows = [{ id: 1, name: 'A', num: 1 }]
    expect(parseReorderRows(rows)).toBe(rows)
  })

  it('JSON 字符串解析（端点逐字复刻 `#json` 时的形态）', () => {
    expect(parseReorderRows('[{"id":1,"name":"A","num":2}]')).toEqual([
      { id: 1, name: 'A', num: 2 }
    ])
  })

  it('null/undefined ⇒ 空数组（合法缺失，不告警）', () => {
    const warn = vi.fn()
    expect(parseReorderRows(null, warn)).toEqual([])
    expect(parseReorderRows(undefined, warn)).toEqual([])
    expect(warn).not.toHaveBeenCalled()
  })

  it('★ 非法 JSON / 非数组 ⇒ 告警 + 退化为 []（不把脏数据塞进拖拽列表）', () => {
    const warn = vi.fn()
    expect(parseReorderRows('{bad', warn)).toEqual([])
    expect(parseReorderRows('{"a":1}', warn)).toEqual([])
    expect(parseReorderRows(42, warn)).toEqual([])
    expect(warn).toHaveBeenCalledTimes(3)
    expect((warn.mock.calls[0][0] as string)).toContain('[meta-reorder]')
  })
})
