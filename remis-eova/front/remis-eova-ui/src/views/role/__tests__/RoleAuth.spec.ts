/**
 * 功能权限分配页的行为等价判据（旧 `_view/role/auth/app.js` + `app.html` + `AuthController`）。
 *
 * 钉的契约（多处"看起来像 bug、但确实是既有行为"）：
 *  ① `rid` 来自 **URL 第 0 段**（`AuthController#index()` 的 `get(0)`），并写回 `uzoo.page.rid`；
 *  ② 7 个按钮；**"反选"实际调 `onSelectAll(false)`（= 全不选）**——文案与行为都不得"修正"；
 *  ③ `onSelectType` 只**置 true**（不切换），且用 `filter` 做副作用；
 *  ④ 菜单复选框 `@click="onSelectMenu(m.code, m.checked)"` 传的是**旧值**，内部取反 ⇒ 恰好是新值；
 *     首次点击时 `m.checked` 为 `undefined` ⇒ `!undefined === true` ⇒ 全选本菜单；
 *  ⑤ 按钮是**每个菜单行内按 `menu_code` 过滤渲染**（O(n²)），且**不绑定授权事件**（授权靠提交批量保存）；
 *  ⑥ `onSubmit` **无校验**，`auth_btns` 是**逗号拼接的字符串**（空集为空串）；异常分支**先 `console.log(e)`**；
 *  ⑦ 服务端只给 `btns[].checked`，**不给 `menus[].checked`**。
 */
import { readFileSync } from 'node:fs'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import RoleAuth from '../RoleAuth.vue'
import { setEovaMe, type EovaMe } from '@/compat/eova-runtime'
import { resetUzooWarning } from '@/compat/eova-ext'

vi.mock('axios')
vi.mock('vue-router', () => ({ useRoute: () => ({ params: { rid: '7' } }) }))
const post = axios.post as unknown as ReturnType<typeof vi.fn>

/** 造 `me` 替身 */
function makeMe(): EovaMe {
  return {
    layer: { msg: vi.fn(), no: vi.fn(), wa: vi.fn(), open: vi.fn() },
    cross: { on: vi.fn(), off: vi.fn(), emit: vi.fn() }
  } as unknown as EovaMe
}

/**
 * `/auth/data` 的成功响应 —— ★ 必须是**工厂**（组件会把数组元素原地改 `checked`，
 * 共用常量会跨用例污染）。
 */
function dataOk() {
  return {
    data: {
      state: 'ok',
      menus: [
        { id: 1, code: 'm_dir', name: '目录一', type: 'dir' },
        { id: 2, code: 'm_a', name: '菜单A', type: 'menu' },
        { id: 3, code: 'm_b', name: '菜单B', type: 'menu' }
      ],
      // ★ 服务端已给 checked；menus 没有 checked
      btns: [
        { id: 11, name: '查询', menu_code: 'm_a', checked: true },
        { id: 12, name: '新增', menu_code: 'm_a', checked: false },
        { id: 13, name: '查询', menu_code: 'm_b', checked: false }
      ]
    }
  }
}

const mountOpts = { global: { stubs: { transition: false } } }

describe('RoleAuth.vue（旧 _view/role/auth 的行为等价）', () => {
  let me: EovaMe

  beforeEach(() => {
    post.mockReset()
    resetUzooWarning()
    delete (globalThis as unknown as Record<string, unknown>)['uzoo']
    me = makeMe()
    setEovaMe(me)
  })

  it('★ rid 来自 URL 第 0 段，并写回 uzoo.page.rid', () => {
    const w = mount(RoleAuth, mountOpts)
    expect((w.vm as never as { rid: string }).rid).toBe('7')
    const uzoo = (globalThis as unknown as Record<string, unknown>)['uzoo'] as {
      page: Record<string, unknown>
    }
    expect(uzoo.page['rid']).toBe('7')
  })

  it('init：POST /auth/data 体为 {rid}，并把 menus/btns 赋上（不动 checked）', async () => {
    post.mockResolvedValue(dataOk())
    const w = mount(RoleAuth, mountOpts)
    await flushPromises()
    expect(post.mock.calls[0][0]).toBe('/auth/data')
    expect(post.mock.calls[0][1]).toEqual({ rid: '7' })
    const vm = w.vm as never as { menus: unknown[]; btns: Array<{ checked?: boolean }> }
    expect(vm.menus).toHaveLength(3)
    expect(vm.btns.map((b) => b.checked)).toEqual([true, false, false])
  })

  it('★ 7 个按钮的文案与回调参数逐字（"反选"绑的是 onSelectAll(false)）', async () => {
    post.mockResolvedValue(dataOk())
    const w = mount(RoleAuth, mountOpts)
    await flushPromises()
    const btns = w.findAll('.eova-tools_box button')
    expect(btns.map((b) => b.text())).toEqual([
      '全选',
      '反选',
      '所有查询',
      '所有新增',
      '所有修改',
      '所有查看',
      '所有删除'
    ])
    const vm = w.vm as never as { btns: Array<{ checked?: boolean }> }
    // ★ "反选" ⇒ 全部未勾选（不是"取反"）
    await btns[1].trigger('click')
    expect(vm.btns.map((b) => b.checked)).toEqual([false, false, false])
    await btns[0].trigger('click')
    expect(vm.btns.map((b) => b.checked)).toEqual([true, true, true])
  })

  it('★ onSelectType：只置 true（不切换），且按 name 精确匹配', async () => {
    post.mockResolvedValue(dataOk())
    const w = mount(RoleAuth, mountOpts)
    await flushPromises()
    const vm = w.vm as never as {
      btns: Array<{ checked?: boolean }>
      onSelectType: (n: string) => void
    }
    vm.onSelectType('查询')
    expect(vm.btns.map((b) => b.checked)).toEqual([true, false, true])
    // 再点一次仍是 true（不反转）
    vm.onSelectType('查询')
    expect(vm.btns.map((b) => b.checked)).toEqual([true, false, true])
  })

  it('★ onSelectMenu：传"旧值"、内部取反 ⇒ 首次（undefined）点击 ⇒ 全选本菜单', async () => {
    post.mockResolvedValue(dataOk())
    const w = mount(RoleAuth, mountOpts)
    await flushPromises() // 先让 init() 把 btns 填上（否则是对空数组操作，测不出东西）
    const vm = w.vm as never as {
      btns: Array<{ checked?: boolean }>
      onSelectMenu: (code: string, status: boolean | undefined) => void
    }
    vm.onSelectMenu('m_a', undefined)
    expect(vm.btns.map((b) => b.checked)).toEqual([true, true, false])
    // 传入 true（旧值为勾选）⇒ 取消本菜单
    vm.onSelectMenu('m_a', true)
    expect(vm.btns.map((b) => b.checked)).toEqual([false, false, false])
  })

  it('★★ 通过 DOM 点击菜单复选框：绑的必须是 `@click`（取旧值）⇒ 首次点击**全选本菜单**', async () => {
    post.mockResolvedValue(dataOk())
    const w = mount(RoleAuth, mountOpts)
    await flushPromises()
    await w.vm.$nextTick()
    const vm = w.vm as never as { btns: Array<{ checked?: boolean }> }
    expect(vm.btns.map((b) => b.checked)).toEqual([true, false, false])
    // 菜单A 行的第 1 个复选框 = 菜单级复选框（初始未勾选 ⇒ m.checked 为 undefined）
    const menuBox = w.findAll('tbody tr')[1].findAll('input[type="checkbox"]')[0]
    await menuBox.trigger('click')
    // ★ 若绑的是 `@change`（取到新值 true），这里会得到 [false,false,false] —— 语义正好反掉
    expect(vm.btns.map((b) => b.checked)).toEqual([true, true, false])
  })

  it('结构：目录行显示 folder 图标且**无菜单复选框**；普通行显示三角图标且有复选框', async () => {
    post.mockResolvedValue(dataOk())
    const w = mount(RoleAuth, mountOpts)
    await flushPromises()
    await w.vm.$nextTick()
    const rows = w.findAll('tbody tr')
    expect(rows).toHaveLength(3)
    expect(rows[0].find('.eova-icon-folder-open').exists()).toBe(true)
    expect(rows[0].findAll('input[type="checkbox"]')).toHaveLength(0) // 目录行：无复选框
    expect(rows[1].find('.eova-icon-triangle-r').exists()).toBe(true)
    // 菜单A 行：1 个菜单复选框 + 2 个功能复选框（m_a 有两个按钮）
    expect(rows[1].findAll('input[type="checkbox"]')).toHaveLength(3)
    // 菜单B 行：1 + 1
    expect(rows[2].findAll('input[type="checkbox"]')).toHaveLength(2)
    // 按钮是"每行内按 menu_code 过滤"渲染的：m_b 的按钮不出现在菜单A 行
    expect(rows[1].text()).toContain('菜单A')
    expect(rows[1].text()).not.toContain('菜单B')
  })

  it('★ 菜单复选框带 title 与 float:right 内联样式（逐字）', async () => {
    post.mockResolvedValue(dataOk())
    const w = mount(RoleAuth, mountOpts)
    await flushPromises()
    await w.vm.$nextTick()
    const row = w.findAll('tbody tr')[1]
    const menuBox = row.findAll('input[type="checkbox"]')[0]
    expect(menuBox.attributes('title')).toBe('选择本菜单所有功能')
    expect(menuBox.attributes('style')).toContain('float: right')
  })

  it('★★ onSubmit：无校验；auth_btns 是**逗号拼接字符串**（服务端 checked 参与）', async () => {
    post.mockResolvedValue(dataOk())
    const w = mount(RoleAuth, mountOpts)
    await flushPromises()
    const vm = w.vm as never as {
      btns: Array<{ checked?: boolean }>
      onSubmit: (id?: unknown) => Promise<void>
    }
    // 初始只有 11 被服务端标为 checked
    post.mockClear()
    post.mockResolvedValue({ data: { state: 'ok' } })
    await vm.onSubmit(9)
    expect(post.mock.calls[0][0]).toBe('/auth/doAuth')
    expect(post.mock.calls[0][1]).toEqual({ rid: '7', auth_btns: '11' })
    expect(me.cross.emit).toHaveBeenCalledWith('eova-layer-ok_done', 9)
  })

  it('★ onSubmit：全不选时 auth_btns 为**空串**（不是 undefined、不是空数组）', async () => {
    post.mockResolvedValue(dataOk())
    const w = mount(RoleAuth, mountOpts)
    await flushPromises()
    const vm = w.vm as never as {
      btns: Array<{ checked?: boolean }>
      onSelectAll: (s: boolean) => void
      onSubmit: (id?: unknown) => Promise<void>
    }
    vm.onSelectAll(false)
    post.mockClear()
    post.mockResolvedValue({ data: { state: 'ok' } })
    await vm.onSubmit(1)
    expect((post.mock.calls[0][1] as { auth_btns: unknown }).auth_btns).toBe('')
  })

  it('★ onSubmit：多个勾选按 btns 顺序逗号拼接', async () => {
    post.mockResolvedValue(dataOk())
    const w = mount(RoleAuth, mountOpts)
    await flushPromises()
    const vm = w.vm as never as {
      btns: Array<{ checked?: boolean }>
      onSelectAll: (s: boolean) => void
      onSubmit: (id?: unknown) => Promise<void>
    }
    vm.onSelectAll(true)
    post.mockClear()
    post.mockResolvedValue({ data: { state: 'ok' } })
    await vm.onSubmit(1)
    expect((post.mock.calls[0][1] as { auth_btns: string }).auth_btns).toBe('11,12,13')
  })

  it('★ onSubmit 异常：先 console.log(e) 再提示（顺序可判）', async () => {
    post.mockResolvedValue(dataOk())
    const w = mount(RoleAuth, mountOpts)
    await flushPromises()
    const seq: string[] = []
    const logSpy = vi.spyOn(console, 'log').mockImplementation(() => seq.push('log'))
    ;(me.layer.msg as unknown as { mockImplementation: (f: unknown) => void }).mockImplementation(
      () => seq.push('msg')
    )
    post.mockRejectedValue(new Error('boom'))
    await (w.vm as never as { onSubmit: (id?: unknown) => Promise<void> }).onSubmit(1)
    logSpy.mockRestore()
    expect(seq).toEqual(['log', 'msg'])
    expect(me.layer.msg).toHaveBeenCalledWith('客户端请求异常: boom')
  })

  it('init 失败：业务失败走 layer.no；异常走 layer.msg（带 message）', async () => {
    post.mockResolvedValue({ data: { state: 'fail', msg: '参数缺失!' } })
    mount(RoleAuth, mountOpts)
    await flushPromises()
    expect(me.layer.no).toHaveBeenCalledWith('参数缺失!')

    post.mockRejectedValue(new Error('net'))
    mount(RoleAuth, mountOpts)
    await flushPromises()
    expect(me.layer.msg).toHaveBeenCalledWith('客户端请求异常: net')
  })

  it('★ 页面级样式的选择器是 `body input`（与 menu/auth 的 `.eova-table input` **不同**，不得"统一"）', () => {
    const src = readFileSync('src/views/role/RoleAuth.vue', 'utf-8')
    // 行锚定：该选择器在文件头注释里也出现过，用 toContain 会匹配到注释（R67/R112 同源教训）
    expect(src.match(/^\s*body input \{/gm)?.length ?? 0).toBe(1)
    expect(src.match(/^\s*\.eova-table input \{/gm)?.length ?? 0).toBe(0)
  })

  it('onMounted 注册 eova-layer-ok', () => {
    post.mockResolvedValue(dataOk())
    mount(RoleAuth, mountOpts)
    expect(me.cross.on).toHaveBeenCalledWith('eova-layer-ok', expect.any(Function))
  })
})
