/**
 * 导入元数据页的行为等价判据（旧 `_view/meta/import/app.js` + `app.html`）。
 *
 * 钉的契约（多处"与其它页不同"的地方，正是最容易被顺手统一掉的）：
 *  ① `data` 初值**只有四个键**（没有 `table`）；
 *  ② `rules` 4 项，且 `code` 用的是 **`eova_code`（带下划线）** —— 并断言它**不在冻结规则表内**（等价于只有 required）；
 *  ③ `watch(() => [ds, type])`：两者都非空才请求 `/meta/findJson/{ds}-{type}`；成功后**原地清空** `tables` 再逐条 push；
 *     catch **只给固定文案**（不带 message）；
 *  ④ `onBeforeMount` 回调扩展钩子；`dss` 由引导数据注入；
 *  ⑤ `onSubmit`：校验不过 **只调 `showMsg` 且结果被丢弃 ⇒ 没有任何提示**（既有缺陷）；
 *     请求 `POST /meta/doImports`；判定用 **宽松相等 `ret.state == 'ok'`**。
 */
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import MetaImport from '../MetaImport.vue'
import { setEovaMe, setEovaTools, type EovaMe, type EovaTools } from '@/compat/eova-runtime'
import { resetUzooWarning } from '@/compat/eova-ext'

vi.mock('axios')
const post = axios.post as unknown as ReturnType<typeof vi.fn>

/** 冻结的校验规则表（证据 validate-rules-contract.json 的键） */
const FROZEN_RULE_KEYS = [
  'abc', 'bankcard', 'cn', 'date', 'email', 'eovacode', 'idcard', 'int', 'intfs', 'intzs',
  'ip', 'mobile', 'money', 'num', 'password', 'port', 'qq', 'tel', 'time', 'url', 'username',
  'zipcode'
]

/** 造 `me` 替身 */
function makeMe(): EovaMe {
  return {
    layer: { msg: vi.fn(), no: vi.fn(), wa: vi.fn(), open: vi.fn() },
    cross: { on: vi.fn(), off: vi.fn(), emit: vi.fn() }
  } as unknown as EovaMe
}

/** 造 `EovaTools` 替身 */
function makeTools(validateOk = true): EovaTools {
  return {
    isEmpty: (v: unknown) => v == null || String(v).trim() === '',
    dom: { getViewSize: () => ({ width: 1200, height: 800 }) },
    json: { toStr: (v: unknown) => JSON.stringify(v), toObj: (s: string) => JSON.parse(s) },
    str: { template: (t: string) => t },
    log: vi.fn(),
    validate: {
      start: () => validateOk,
      showMsg: vi.fn(() => '名称不能为空<br>'),
      addRules: vi.fn()
    }
  } as unknown as EovaTools
}

/** `ev-select` / `ev-input` 替身 */
const stubs = {
  EvSelect: { name: 'EvSelect', props: ['modelValue', 'items', 'type', 'name'], template: '<select class="eova-select" />' },
  EvInput: { name: 'EvInput', props: ['modelValue', 'name'], template: '<input class="eova-value" />' }
}
const mountOpts = { global: { components: stubs } }

describe('MetaImport.vue（旧 _view/meta/import 的行为等价）', () => {
  let me: EovaMe

  beforeEach(() => {
    post.mockReset()
    resetUzooWarning()
    delete (globalThis as unknown as Record<string, unknown>)['uzoo']
    me = makeMe()
    setEovaMe(me)
    setEovaTools(makeTools())
  })

  it('★ data 初值只有四个键（**没有 table**，尽管模板绑定了 v-model="data.table"）', () => {
    const w = mount(MetaImport, mountOpts)
    const d = (w.vm as never as { data: Record<string, unknown> }).data
    expect(d.ds).toBe('')
    expect(d.type).toBe('table')
    expect(d.name).toBe('测试xxx')
    expect(d.code).toBe('test_xxx')
    expect(Object.keys(d)).toEqual(['ds', 'type', 'name', 'code'])
  })

  it('types 两项且 val 与 txt 相同', () => {
    const w = mount(MetaImport, mountOpts)
    expect((w.vm as never as { types: unknown[] }).types).toEqual([
      { val: 'table', txt: 'table' },
      { val: 'view', txt: 'view' }
    ])
  })

  it('★ rules 4 项，code 用 eova_code（带下划线）——与 menu/add 的 eovacode 不同', () => {
    const w = mount(MetaImport, mountOpts)
    const rules = (w.vm as never as { rules: Record<string, { label: string; rules: string[] }> })
      .rules
    expect(Object.keys(rules)).toEqual(['type', 'ds', 'name', 'code'])
    expect(rules.type).toMatchObject({ label: '元类型', rules: ['required'] })
    expect(rules.ds).toMatchObject({ label: '数据源', rules: ['required'] })
    expect(rules.name).toMatchObject({ label: '名称', rules: ['required', 'len[2~15]'] })
    expect(rules.code).toMatchObject({ label: '编码', rules: ['required', 'eova_code'] })
  })

  it('★★ 取证交叉核对：`eova_code` **不在**冻结规则表内 ⇒ 该规则被 validate 跳过（等价于只有 required）', () => {
    // 这条把"两个页面拼写不同、其中一个不生效"这一既有事实钉住
    expect(FROZEN_RULE_KEYS).toContain('eovacode')
    expect(FROZEN_RULE_KEYS).not.toContain('eova_code')
    const w = mount(MetaImport, mountOpts)
    const rules = (w.vm as never as { rules: Record<string, { rules: string[] }> }).rules
    expect(rules.code.rules).toContain('eova_code')
    expect(rules.code.rules).not.toContain('eovacode')
  })

  it('结构：5 个字段行，name 属性逐字（type/ds/table/code/name），无 fieldset', async () => {
    const w = mount(MetaImport, mountOpts)
    await w.vm.$nextTick()
    expect(w.findAll('.eova-form-field')).toHaveLength(5)
    expect(w.findAll('fieldset')).toHaveLength(0)
    const selects = w.findAllComponents(stubs.EvSelect)
    expect(selects.map((s) => s.props('name'))).toEqual(['type', 'ds', 'table'])
    const inputs = w.findAllComponents(stubs.EvInput)
    expect(inputs.map((i) => i.props('name'))).toEqual(['code', 'name'])
    expect(w.findAll('label.eova-form-label.required')).toHaveLength(5)
  })

  it('★ watch：ds 与 type 都非空才请求；成功后**原地清空** tables 再逐条 push', async () => {
    post.mockResolvedValue({ data: { data: [{ table_name: 't_a' }, { table_name: 't_b' }] } })
    const w = mount(MetaImport, mountOpts)
    const vm = w.vm as never as { data: Record<string, unknown>; tables: Array<{ val: unknown }> }
    // 先塞脏数据，验证是"原地清空"而不是"重新赋值"（后者会让外部持有的旧引用看不到变化）
    const holder = vm.tables
    holder.push({ val: 'dirty' })
    vm.data.ds = 'eova'
    await flushPromises()
    expect(post.mock.calls[0][0]).toBe('/meta/findJson/eova-table')
    expect(vm.tables).toBe(holder) // 同一个数组（原地）
    expect(vm.tables.map((t) => t.val)).toEqual(['t_a', 't_b'])
  })

  it('★ watch：ds 与 type **两个条件必须同时成立** —— 分别各造一条缺一的反例', async () => {
    // 反例一：ds 为空、type 有值
    const w1 = mount(MetaImport, mountOpts)
    const vm1 = w1.vm as never as { data: Record<string, unknown> }
    vm1.data.type = 'view'
    await flushPromises()
    expect(post).not.toHaveBeenCalled()

    // 反例二：**ds 有值、type 为空**（★ 这条才能区分 `ds && type` 与只判 `ds`）
    const w2 = mount(MetaImport, mountOpts)
    const vm2 = w2.vm as never as { data: Record<string, unknown> }
    post.mockClear()
    vm2.data.type = ''
    vm2.data.ds = 'eova'
    await flushPromises()
    expect(post).not.toHaveBeenCalled()
  })

  it('★ findJson 失败：catch 只给固定文案（**不带 message**）', async () => {
    post.mockRejectedValue(new Error('net'))
    const w = mount(MetaImport, mountOpts)
    ;(w.vm as never as { data: Record<string, unknown> }).data.ds = 'eova'
    await flushPromises()
    expect(me.layer.msg).toHaveBeenCalledWith('客户端请求异常')
    expect(me.layer.msg).not.toHaveBeenCalledWith('客户端请求异常: net')
  })

  it('onBeforeMount 回调扩展钩子 uzoo.vue.mountBefore（本页 app.html:40 正是定义它的一侧）', () => {
    const hook = vi.fn()
    ;(globalThis as unknown as Record<string, unknown>)['uzoo'] = {
      page: {},
      vue: { mountBefore: hook },
      app: {}
    }
    mount(MetaImport, mountOpts)
    expect(hook).toHaveBeenCalledTimes(1)
  })

  it('dss 由引导数据注入（端点未就绪时为空，诚实降级）', () => {
    expect((mount(MetaImport, mountOpts).vm as never as { dss: unknown[] }).dss).toEqual([])
  })

  it('★★ 既有缺陷：校验不过时**只调 showMsg、结果被丢弃** ⇒ 没有任何提示', async () => {
    setEovaTools(makeTools(false))
    const w = mount(MetaImport, mountOpts)
    await (w.vm as never as { onSubmit: (id?: unknown) => Promise<void> }).onSubmit(1)
    const x = (
      (await import('@/compat/eova-runtime')) as unknown as { getEovaTools: () => EovaTools }
    ).getEovaTools()
    expect(x.validate.showMsg).toHaveBeenCalled()
    // 关键：没有 wa / msg / no 任何一个提示（与其它页的 wa(txt) 写法不同）
    expect(me.layer.wa).not.toHaveBeenCalled()
    expect(me.layer.msg).not.toHaveBeenCalled()
    expect(me.layer.no).not.toHaveBeenCalled()
    expect(post).not.toHaveBeenCalled()
  })

  it('提交：POST /meta/doImports，载荷是 data；成功回传 ok_done', async () => {
    post.mockResolvedValue({ data: { state: 'ok' } })
    const w = mount(MetaImport, mountOpts)
    const vm = w.vm as never as { data: Record<string, unknown>; onSubmit: (id?: unknown) => Promise<void> }
    await vm.onSubmit(42)
    expect(post).toHaveBeenCalledWith('/meta/doImports', vm.data)
    expect(me.cross.emit).toHaveBeenCalledWith('eova-layer-ok_done', 42)
  })

  it('★ 判定用宽松相等 `==`：state 为字符串 "ok" 之外，数字/其它可松散等于 "ok" 的值也走成功分支', async () => {
    // `'ok' == 'ok'` 为真；这里用"state 恰好为 'ok'"正面钉住，并用一个**不会被严格相等接受**的
    // 边界（state 为对象但 toString 返回 'ok'）证明用的是 `==` 而不是 `===`
    post.mockResolvedValue({ data: { state: { toString: () => 'ok' } } })
    const w = mount(MetaImport, mountOpts)
    await (w.vm as never as { onSubmit: (id?: unknown) => Promise<void> }).onSubmit(7)
    expect(me.cross.emit).toHaveBeenCalledWith('eova-layer-ok_done', 7)
    expect(me.layer.no).not.toHaveBeenCalled()
  })

  it('业务失败：layer.no(ret.msg)；异常：layer.msg(\'客户端请求异常: \' + message)', async () => {
    post.mockResolvedValue({ data: { state: 'fail', msg: '导入失败' } })
    const w1 = mount(MetaImport, mountOpts)
    await (w1.vm as never as { onSubmit: (id?: unknown) => Promise<void> }).onSubmit(1)
    expect(me.layer.no).toHaveBeenCalledWith('导入失败')

    post.mockRejectedValue(new Error('boom'))
    const w2 = mount(MetaImport, mountOpts)
    await (w2.vm as never as { onSubmit: (id?: unknown) => Promise<void> }).onSubmit(1)
    expect(me.layer.msg).toHaveBeenCalledWith('客户端请求异常: boom')
  })

  it('onMounted 注册 eova-layer-ok', () => {
    mount(MetaImport, mountOpts)
    expect(me.cross.on).toHaveBeenCalledWith('eova-layer-ok', expect.any(Function))
  })
})
