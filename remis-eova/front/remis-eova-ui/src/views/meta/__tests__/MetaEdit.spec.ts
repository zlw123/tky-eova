/**
 * 元字段页的行为等价判据（旧 `_view/meta/edit/app.js` + `app.html` + `MetaController`）。
 *
 * 钉的契约：
 *  ① `page = {page:1, limit:99999, total:0}`；`tableHeight = 视口高 - 40 - 20`；
 *  ② `biz="meta_eidt"`（旧实现的**拼写**，不得"修正"为 edit）；
 *  ③ 5 个只读字段的标签/取值；toolbar 六按钮的文案与样式逐字；
 *  ④ `onMounted` **只打日志、不自动查询**（旧实现 `// query()` 被注释）；
 *  ⑤ `onReorder` 的 `me.layer.open` 三/四/五参数：done 里 `msg('操作成功！')`+`onQuery()`、confirm 是空函数、opts `{offset:'r'}`；
 *  ⑥ `onDelete`：未选行 `msg('请先选择元字段')`；删除 URL 由 `urls.form.delete` 模板 + `{object_code:'eova_field_code'}` 生成；
 *     成功先 `removeRows(pks)` 再 `msg('删除成功')`；**非 ok 走 `msg`（不是 `no`）**；
 *  ⑦ ★★ **四处 `.catch` 引用未声明的 `error` ⇒ ReferenceError**（既有缺陷）：
 *     网络异常时**不弹**"客户端请求异常"提示，且函数抛出 ReferenceError。
 */
import { readFileSync } from 'node:fs'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import MetaEdit from '../MetaEdit.vue'
import { setEovaMe, setEovaTools, type EovaMe, type EovaTools } from '@/compat/eova-runtime'
import { PAGE_URLS } from '@/compat/ui-urls'

const routeState = vi.hoisted(() => ({ query: {} as Record<string, string> }))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: routeState.query }) }))
vi.mock('axios')
const post = axios.post as unknown as ReturnType<typeof vi.fn>

/** 造 `me` 替身（带 `confirm`/`input`：本页会调用） */
function makeMe(): EovaMe & { layer: Record<string, ReturnType<typeof vi.fn>> } {
  return {
    layer: {
      msg: vi.fn(),
      no: vi.fn(),
      wa: vi.fn(),
      open: vi.fn(),
      // confirm / input 立即执行回调，便于断言回调内部行为
      confirm: vi.fn((_m: string, cb: () => void) => cb()),
      input: vi.fn((_p: string, _t: string, cb: (v: string) => void) => cb('vip_field'))
    },
    cross: { on: vi.fn(), off: vi.fn(), emit: vi.fn() }
  } as unknown as EovaMe & { layer: Record<string, ReturnType<typeof vi.fn>> }
}

/** 造 `EovaTools` 替身（本页用到 dom/json?/str.template/isEmpty） */
function makeTools(viewHeight = 800): EovaTools {
  return {
    isEmpty: (v: unknown) => v == null || (Array.isArray(v) && v.length === 0),
    dom: { getViewSize: () => ({ width: 1200, height: viewHeight }) },
    json: { toStr: (v: unknown) => JSON.stringify(v), toObj: (s: string) => JSON.parse(s) },
    // 与制品同语义：缺键保留 `{{key}}`
    str: {
      template: (tpl: string, params: Record<string, unknown>) =>
        tpl.replace(/\{\{([\w.]+)\}\}/g, (m, k: string) =>
          params[k] != null ? String(params[k]) : m
        )
    },
    log: vi.fn(),
    validate: { start: () => true, showMsg: () => '', addRules: vi.fn() }
  } as unknown as EovaTools
}

/**
 * `ev-table` 替身
 *
 * ★ 必须**带 `query` 方法**：模板 ref 在 `onMounted` 之前就已指向该实例，
 * 若替身没有 `query`，则"onMounted 里是否偷偷调了一次查询"在判据里**不可观测**
 * （变异实验证实：这一条曾因此漏网）。
 */
const EvTableStub = {
  name: 'EvTable',
  props: ['object', 'biz', 'where', 'height', 'isEdit', 'page', 'size'],
  template: '<div class="eova-table-view"><slot name="toolbar" /></div>',
  methods: {
    query(this: { $emit: (e: string) => void }) {
      this.$emit('stub-query')
    },
    getSelectRows() {
      return []
    },
    removeRows() {
      return undefined
    }
  }
}
const mountOpts = { global: { components: { EvTable: EvTableStub } } }

/** 造表格 ref 替身 */
function tableStub(rows: Array<Record<string, unknown>> = []) {
  return { query: vi.fn(), getSelectRows: vi.fn(() => rows), removeRows: vi.fn() }
}

describe('MetaEdit.vue（旧 _view/meta/edit 的行为等价）', () => {
  let me: ReturnType<typeof makeMe>

  beforeEach(() => {
    post.mockReset()
    delete (globalThis as unknown as Record<string, unknown>)['uzoo']
    routeState.query = { object: 'eova_user_code' }
    me = makeMe()
    setEovaMe(me)
    setEovaTools(makeTools())
  })

  it('★ page.limit = 99999 且 total=0；tableHeight = 视口高 - 40 - 20', () => {
    setEovaTools(makeTools(800))
    const w = mount(MetaEdit, mountOpts)
    const vm = w.vm as never as { page: Record<string, number>; tableHeight: number }
    expect(vm.page).toEqual({ page: 1, limit: 99999, total: 0 })
    expect(vm.tableHeight).toBe(800 - 40 - 20)
  })

  it('★ ev-table 的 props 逐字：object=eova_field_code、biz=meta_eidt（旧拼写）、page/is-edit/size', () => {
    const w = mount(MetaEdit, mountOpts)
    const t = w.findComponent(EvTableStub)
    expect(t.props('object')).toBe('eova_field_code')
    expect(t.props('biz')).toBe('meta_eidt')
    expect(t.props('size')).toBe('s30')
    expect(t.props('isEdit')).toBe(true)
  })

  it('★ where 由元对象编码生成（旧 `#(where)` 是 `{ object_code: "xxx" }`）', () => {
    const w = mount(MetaEdit, mountOpts)
    expect((w.findComponent(EvTableStub).props('where') as { object_code: string }).object_code).toBe(
      'eova_user_code'
    )
  })

  it('★ 标题：旧 `#(object.name)` ⇒ 注入元对象后标题**带名字**（否则"名字恒为空"两种实现同结果）', async () => {
    const w = mount(MetaEdit, mountOpts)
    await flushPromises()
    expect(document.title).toBe('📝元字段 - EovaMeta')
    // 注入引导数据（模拟端点就绪）后重设标题
    const vm = w.vm as never as {
      bootstrap: unknown
      applyTitle: () => void
    }
    vm.bootstrap = { fromServer: true, url: {}, object: { code: 'eova_user_code', name: '用户表' } }
    vm.applyTitle()
    expect(document.title).toBe('📝用户表元字段 - EovaMeta')
    expect(document.title).toContain('用户表')
  })

  it('5 个只读字段的标签与取值位', () => {
    const w = mount(MetaEdit, mountOpts)
    const labels = w.findAll('.eova-form-field[mode="detail"] label').map((l) => l.text())
    expect(labels).toEqual(['名称：', '编码：', '数据源：', '数据表：', '主键：'])
    expect(w.findAll('.eova-form-txt')).toHaveLength(5)
  })

  it('toolbar 六按钮文案与附带样式类逐字', async () => {
    const w = mount(MetaEdit, mountOpts)
    await w.vm.$nextTick()
    const btns = w.findAll('.eova-tools_box button')
    expect(btns.map((b) => b.text())).toEqual([
      '刷新',
      '字段排序',
      '彻底删除',
      '增量同步字段',
      '覆盖同步字段',
      '添加虚拟字段'
    ])
    expect(btns.map((b) => b.classes().join(' '))).toEqual([
      '',
      '',
      'eova-btn_error',
      'eova-btn_warn',
      'eova-btn_error',
      ''
    ])
  })

  it('★ onMounted 只打日志、**不自动查询**（旧实现 `// query()` 被注释）', async () => {
    // 模板 ref 在 onMounted 前已指向 EvTable 替身 ⇒ 直接对替身实例打 spy
    const spy = vi.spyOn(EvTableStub.methods, 'query')
    mount(MetaEdit, mountOpts)
    await flushPromises()
    expect(spy).not.toHaveBeenCalled()
    spy.mockRestore()
  })

  it('onQuery：把 form 交给表格', () => {
    const w = mount(MetaEdit, mountOpts)
    const t = tableStub()
    const vm = w.vm as never as { refTable: unknown; query?: unknown; onQuery: () => void }
    vm.refTable = t
    vm.onQuery()
    expect(t.query).toHaveBeenCalledWith({ object_code: 'eova_user_code' })
  })

  it('★ onReorder：me.layer.open 的参数（标题/URL/250/0.99/done/空 confirm/{offset:r}）', () => {
    const w = mount(MetaEdit, mountOpts)
    const t = tableStub()
    ;(w.vm as never as { refTable: unknown }).refTable = t
    ;(w.vm as never as { onReorder: () => void }).onReorder()
    const call = (me.layer.open as unknown as { mock: { calls: unknown[][] } }).mock.calls[0]
    expect(call[0]).toBe('元字段排序')
    expect(call[1]).toBe('/meta/reorder?object=eova_user_code')
    expect(call[2]).toBe(250)
    expect(call[3]).toBe(0.99)
    expect(typeof call[4]).toBe('function')
    expect(typeof call[5]).toBe('function')
    expect(call[6]).toEqual({ offset: 'r' })
    // done 回调：提示 + 重新查询
    ;(call[4] as () => void)()
    expect(me.layer.msg).toHaveBeenCalledWith('操作成功！')
    expect(t.query).toHaveBeenCalled()
    // confirm 是空函数
    expect(() => (call[5] as () => void)()).not.toThrow()
  })

  it('★ onDelete：未选行 ⇒ msg(\'请先选择元字段\') 且不请求', async () => {
    const w = mount(MetaEdit, mountOpts)
    ;(w.vm as never as { refTable: unknown }).refTable = tableStub([])
    await (w.vm as never as { onDelete: () => Promise<void> }).onDelete()
    expect(me.layer.msg).toHaveBeenCalledWith('请先选择元字段')
    expect(post).not.toHaveBeenCalled()
  })

  it('★ onDelete：删除 URL 来自 urls.form.delete 模板 + {object_code:eova_field_code}；成功**先 removeRows 再提示**（顺序可判）', async () => {
    post.mockResolvedValue({ data: { state: 'ok' } })
    const seq: string[] = []
    const w = mount(MetaEdit, mountOpts)
    const t = {
      query: vi.fn(),
      getSelectRows: vi.fn(() => [
        { id: 11, cn: 'A' },
        { id: 22, cn: 'B' }
      ]),
      removeRows: vi.fn(() => seq.push('removeRows'))
    }
    ;(me.layer.msg as unknown as { mockImplementation: (f: unknown) => void }).mockImplementation(
      () => seq.push('msg')
    )
    ;(w.vm as never as { refTable: unknown }).refTable = t
    await (w.vm as never as { onDelete: () => Promise<void> }).onDelete()
    await flushPromises()
    const expectedUrl = PAGE_URLS.form.delete.replace('{{object_code}}', 'eova_field_code')
    expect(post.mock.calls[0][0]).toBe(expectedUrl)
    expect(t.removeRows).toHaveBeenCalledWith([11, 22])
    // ★ 顺序：先移除行、后提示（对调会被这条抓住）
    expect(seq).toEqual(['removeRows', 'msg'])
  })

  it('★ onDelete 非 ok：走 msg（不是 no）', async () => {
    post.mockResolvedValue({ data: { state: 'fail', msg: '有依赖' } })
    const w = mount(MetaEdit, mountOpts)
    ;(w.vm as never as { refTable: unknown }).refTable = tableStub([{ id: 1 }])
    await (w.vm as never as { onDelete: () => Promise<void> }).onDelete()
    await flushPromises()
    expect(me.layer.msg).toHaveBeenCalledWith('有依赖')
    expect(me.layer.no).not.toHaveBeenCalled()
  })

  it('onVirtual：me.layer.input 的回调里按 object_code 提交虚拟字段名', async () => {
    post.mockResolvedValue({ data: { state: 'ok' } })
    const w = mount(MetaEdit, mountOpts)
    const t = tableStub()
    ;(w.vm as never as { refTable: unknown }).refTable = t
    ;(w.vm as never as { onVirtual: () => void }).onVirtual()
    await flushPromises()
    expect(post.mock.calls[0][0]).toBe('/meta/addVirtualField?object_code=eova_user_code')
    expect(post.mock.calls[0][1]).toEqual({ input: 'vip_field' })
    expect(me.layer.msg).toHaveBeenCalledWith('同步成功')
    expect(t.query).toHaveBeenCalled()
  })

  it('onSyncnew / onOverride：URL 与成功路径（msg + 重新查询）', async () => {
    post.mockResolvedValue({ data: { state: 'ok' } })
    const w = mount(MetaEdit, mountOpts)
    const vm = w.vm as never as { refTable: unknown; onSyncnew: () => void; onOverride: () => void }
    const t = tableStub()
    vm.refTable = t
    vm.onSyncnew()
    await flushPromises()
    expect(post.mock.calls[0][0]).toBe('/meta/syncnew/eova_user_code')
    expect(post.mock.calls[0][1]).toEqual({})
    post.mockClear()
    vm.onOverride()
    await flushPromises()
    expect(post.mock.calls[0][0]).toBe('/meta/override/eova_user_code')
    expect(me.layer.msg).toHaveBeenCalledWith('同步成功')
  })

  it('onVirtual/onSyncnew/onOverride 失败走 layer.no（与 onDelete 的 msg 不同）', async () => {
    post.mockResolvedValue({ data: { state: 'fail', msg: '后端拒绝' } })
    const w = mount(MetaEdit, mountOpts)
    const vm = w.vm as never as { refTable: unknown; onVirtual: () => void }
    vm.refTable = tableStub()
    vm.onVirtual()
    await flushPromises()
    expect(me.layer.no).toHaveBeenCalledWith('后端拒绝')
  })

  it('★★ 既有缺陷：四处 .catch 引用未声明的 `error` ⇒ ReferenceError，且**不弹**客户端请求异常提示', async () => {
    post.mockRejectedValue(new Error('net down'))
    const w = mount(MetaEdit, mountOpts)
    const vm = w.vm as never as {
      refTable: unknown
      onDelete: () => Promise<void>
      onVirtual: () => void
      onSyncnew: () => void
      onOverride: () => void
    }
    vm.refTable = tableStub([{ id: 1 }])

    // ★ 测试卫生：本用例**故意**触发四处未处理的 promise 拒绝（这正是旧栈的表现）。
    //   若不接管，vitest 会把它们记成 Unhandled Errors ⇒ **整套测试退出码为 1**，
    //   于是"变异是否被捕获"失去参照（基线本身红 ⇒ 任何变异都像"被捕获"）。
    //   故临时**独占** unhandledRejection 监听：记录后吞掉，用完还原。
    const saved = process.listeners('unhandledRejection')
    process.removeAllListeners('unhandledRejection')
    const rejections: unknown[] = []
    const handler = (e: unknown) => rejections.push(e)
    process.on('unhandledRejection', handler)
    try {
      await vm.onDelete()
      vm.onVirtual()
      vm.onSyncnew()
      vm.onOverride()
      await new Promise((r) => setTimeout(r, 0)) // 让拒绝落到监听器
    } finally {
      process.off('unhandledRejection', handler)
      saved.forEach((l) => process.on('unhandledRejection', l as never))
    }

    // ★ 缺陷本体可断言：四处都是 ReferenceError: error is not defined
    const refErrors = rejections.filter((e) => e instanceof ReferenceError)
    expect(refErrors).toHaveLength(4)
    expect(refErrors.every((e) => /error is not defined/.test((e as Error).message))).toBe(true)

    // ★ 可稳定观测的后果：**没有任何**"客户端请求异常"提示
    //   （`error.message` 在**参数求值阶段**就抛了，`me.layer.msg` 根本没被调用）
    const msgs = (me.layer.msg as unknown as { mock: { calls: unknown[][] } }).mock.calls.map((c) =>
      String(c[0])
    )
    expect(msgs.some((m) => m.includes('客户端请求异常'))).toBe(false)

    // 反向对照：非 ok 的正常失败路径**会**提示（证明上一条不是"整个函数没跑"）
    post.mockReset()
    post.mockResolvedValue({ data: { state: 'fail', msg: 'X' } })
    await vm.onDelete()
    await flushPromises()
    expect(me.layer.msg).toHaveBeenCalledWith('X')
  })

  it('★ 该缺陷由 `@ts-expect-error` 在**构建期**保护（把 `error` 改成 `_e` 会让构建失败）', () => {
    // 这里只做"标记存在"的静态断言；真正生效的是 vue-tsc：
    // 一旦有人"顺手修好"该缺陷，`@ts-expect-error` 变成未使用 ⇒ TS2578 ⇒ 构建红。
    // vitest 的 cwd 即工程根；直接按相对路径读，避免 import.meta.url 在转译后不是 file: 协议
    const src = readFileSync('src/views/meta/MetaEdit.vue', 'utf-8')
    // 只数**代码行**上的指令（行首缩进 + 指令），文件头注释里也提到过该指令，不能一起数
    expect(src.match(/^\s*\/\/ @ts-expect-error/mg)?.length ?? 0).toBe(4)
    // 同一条纪律：旧样式里那行**非法 CSS**（`//width: 220px;`）是既有内容，不得"顺手清理"。
    // ★ 必须**行锚定**：该模式在文件头与 HTML 注释里也出现过 ⇒ 用 `toContain` 会匹配到注释，
    //   导致"删掉真正的声明行"也判绿（实测教训，见 R67 同源纪律）。
    expect(src.match(/^\s*\/\/width: 220px;\s*$/gm)?.length ?? 0).toBe(1)
  })
})
