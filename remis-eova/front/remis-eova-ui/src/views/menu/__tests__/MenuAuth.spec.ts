/**
 * 功能授权页的行为等价判据（旧 `_view/menu/auth/app.js` + `app.html` + `MenuController`）。
 *
 * 钉的契约（都是"改写时最容易被顺手改进"的地方）：
 *  ① 弹层「确认」**不做任何提交**，只回传 `eova-layer-ok_done`（授权是逐勾选即时提交的）；
 *  ② 勾选状态是**命令式**设置的（`getElementById(\`CK_${bid}_${rid}\`)` + `.checked = true`），且在 `nextTick` 之后；
 *  ③ `/auth/update` 的字段名是 **`is_check`**（带下划线），载荷 `{is_check, bid, rid}`；
 *  ④ `initCheckedStatus` 用 `POST /menu/authData`，体 `{id}`；ok ⇒ 四项赋值，非 ok ⇒ `layer.no`；
 *  ⑤ `menu?.name` 用了**可选链**（未就绪时不报错）；
 *  ⑥ 分组顺序即界面顺序（`groupByCat` 的结果按插入顺序迭代）。
 */
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import MenuAuth from '../MenuAuth.vue'
import { groupByCat } from '@/utils/button-cat'
import { setEovaMe, type EovaMe } from '@/compat/eova-runtime'
import { resetUzooWarning } from '@/compat/eova-ext'

vi.mock('axios')
vi.mock('vue-router', () => ({ useRoute: () => ({ params: { id: '42' } }) }))

const post = axios.post as unknown as ReturnType<typeof vi.fn>

/** 造 `me` 替身 */
function makeMe(): EovaMe {
  return {
    layer: { msg: vi.fn(), no: vi.fn(), wa: vi.fn(), open: vi.fn() },
    cross: { on: vi.fn(), off: vi.fn(), emit: vi.fn() }
  } as unknown as EovaMe
}

/** `/menu/authData` 的成功响应 */
const authDataOk = {
  data: {
    state: 'ok',
    menu: { id: 42, name: '用户管理' },
    btns: [
      { id: 1, name: '新增', cat: 1 },
      { id: 2, name: '删除', cat: 1 },
      { id: 3, name: '导出', cat: 2 }
    ],
    roles: [
      { id: 10, name: '管理员' },
      { id: 20, name: '普通用户' }
    ],
    auths: [{ bid: 1, rid: 10 }]
  }
}

describe('MenuAuth.vue（旧 _view/menu/auth 的行为等价）', () => {
  let me: EovaMe

  beforeEach(() => {
    post.mockReset()
    resetUzooWarning()
    delete (globalThis as unknown as Record<string, unknown>)['uzoo']
    me = makeMe()
    setEovaMe(me)
  })

  it('`id` 来自 URL 第 0 段（旧 `MenuController#auth()` 的 `getInt(0)`），并写回 uzoo.page.id', () => {
    const w = mount(MenuAuth)
    expect((w.vm as never as { pageId: string }).pageId).toBe('42')
    const uzoo = (globalThis as unknown as Record<string, unknown>)['uzoo'] as {
      page: Record<string, unknown>
    }
    expect(uzoo.page['id']).toBe('42')
  })

  it('初始化：POST /menu/authData 体为 {id}，并把四项赋值（menu/btnCats/roles/auths）', async () => {
    post.mockResolvedValue(authDataOk)
    const w = mount(MenuAuth)
    await flushPromises()
    expect(post.mock.calls[0][0]).toBe('/menu/authData')
    expect(post.mock.calls[0][1]).toEqual({ id: '42' })
    const vm = w.vm as never as {
      menu?: { name?: string }
      btnCats: Record<string, unknown[]>
      roles: unknown[]
      auths: unknown[]
    }
    expect(vm.menu).toEqual({ id: 42, name: '用户管理' })
    expect(Object.keys(vm.btnCats)).toEqual(['1', '2'])
    expect(vm.roles).toHaveLength(2)
    expect(vm.auths).toHaveLength(1)
  })

  it('★ 勾选状态是【命令式】设置的：按 id 规则 CK_{bid}_{rid} 回查并置 checked（不是 v-model）', async () => {
    post.mockResolvedValue(authDataOk)
    // ★ 必须 `attachTo`：组件内部用的是 `document.getElementById`（旧实现如此），
    //   未挂到文档上的 wrapper 查不到元素 —— 这条"必须先挂载"本身就证明它是命令式的。
    const host = document.createElement('div')
    document.body.appendChild(host)
    const w = mount(MenuAuth, { attachTo: host })
    await flushPromises()
    await flushPromises() // 组件内的 `await nextTick()` 还要再冲刷一次
    const box = w.find('#CK_1_10').element as HTMLInputElement
    expect(box.checked).toBe(true)
    // 未授权的那个必须保持未勾选（这条证明是"按 auths 逐条置位"，不是"全选"）
    expect((w.find('#CK_2_10').element as HTMLInputElement).checked).toBe(false)
    expect((w.find('#CK_3_20').element as HTMLInputElement).checked).toBe(false)
    w.unmount()
    host.remove()
  })

  it('结构：按分组逐张表渲染；表头「角色」+ 按钮名；行按角色；末尾有提示文案', async () => {
    post.mockResolvedValue(authDataOk)
    const w = mount(MenuAuth)
    await flushPromises()
    await w.vm.$nextTick()
    const tables = w.findAll('table.eova-table')
    expect(tables).toHaveLength(2)
    expect(tables[0].findAll('th').map((t) => t.text())).toEqual(['角色', '新增', '删除'])
    expect(tables[1].findAll('th').map((t) => t.text())).toEqual(['角色', '导出'])
    expect(tables[0].findAll('tbody tr').map((r) => r.find('td').text())).toEqual([
      '管理员',
      '普通用户'
    ])
    expect(w.find('.eova-notes').text()).toContain('授权后相关角色需要重新登录更新权限')
    // 复选框总数 = 行×列：表1 2角色×2按钮 + 表2 2角色×1按钮 = 6
    expect(w.findAll('input[type="checkbox"]')).toHaveLength(6)
    expect(w.find('h3').text()).toBe('用户管理')
  })

  it('★ /auth/update 的字段名是 is_check（带下划线），载荷 {is_check,bid,rid}；成功提示「授权成功」', async () => {
    post.mockResolvedValue(authDataOk)
    const w = mount(MenuAuth)
    await flushPromises()
    await w.vm.$nextTick()
    post.mockClear()
    post.mockResolvedValue({ data: { state: 'ok' } })
    const box = w.find('#CK_3_20').element as HTMLInputElement
    box.checked = true
    await box.dispatchEvent(new Event('change'))
    await flushPromises()
    expect(post).toHaveBeenCalledTimes(1)
    expect(post.mock.calls[0][0]).toBe('/auth/update')
    expect(post.mock.calls[0][1]).toEqual({ is_check: true, bid: 3, rid: 20 })
    expect(me.layer.msg).toHaveBeenCalledWith('授权成功')
  })

  it('授权失败走 layer.no(ret.msg)（与成功提示不同级）', async () => {
    post.mockResolvedValue(authDataOk)
    const w = mount(MenuAuth)
    await flushPromises()
    await w.vm.$nextTick()
    post.mockResolvedValue({ data: { state: 'fail', msg: '无权操作' } })
    const box = w.find('#CK_2_10').element as HTMLInputElement
    box.checked = false
    await box.dispatchEvent(new Event('change'))
    await flushPromises()
    expect(me.layer.no).toHaveBeenCalledWith('无权操作')
    expect(me.layer.msg).not.toHaveBeenCalledWith('授权成功')
  })

  it('授权请求异常：layer.msg(\'客户端请求异常: \' + message)', async () => {
    post.mockResolvedValue(authDataOk)
    const w = mount(MenuAuth)
    await flushPromises()
    await w.vm.$nextTick()
    post.mockRejectedValue(new Error('boom'))
    const box = w.find('#CK_2_10').element as HTMLInputElement
    await box.dispatchEvent(new Event('change'))
    await flushPromises()
    expect(me.layer.msg).toHaveBeenCalledWith('客户端请求异常: boom')
  })

  it('authData 业务失败走 layer.no(ret.msg)（不抛）', async () => {
    post.mockResolvedValue({ data: { state: 'fail', msg: '菜单不存在' } })
    mount(MenuAuth)
    await flushPromises()
    expect(me.layer.no).toHaveBeenCalledWith('菜单不存在')
  })

  it('authData 请求异常：layer.msg(\'客户端请求异常: \' + message)', async () => {
    post.mockRejectedValue(new Error('net'))
    mount(MenuAuth)
    await flushPromises()
    expect(me.layer.msg).toHaveBeenCalledWith('客户端请求异常: net')
  })

  it('★★ 弹层「确认」不做任何提交，只回传 eova-layer-ok_done', async () => {
    post.mockResolvedValue(authDataOk)
    mount(MenuAuth)
    await flushPromises()
    expect(me.cross.on).toHaveBeenCalledWith('eova-layer-ok', expect.any(Function))
    const handler = (me.cross.on as unknown as { mock: { calls: unknown[][] } }).mock.calls[0][1] as (
      id: unknown
    ) => void
    post.mockClear()
    handler(77)
    await flushPromises()
    expect(me.cross.emit).toHaveBeenCalledWith('eova-layer-ok_done', 77)
    // 关键：确认不触发任何请求
    expect(post).not.toHaveBeenCalled()
  })

  it('menu 未就绪时不报错（旧模板用了可选链 menu?.name）', async () => {
    post.mockResolvedValue({ data: { state: 'fail' } })
    const w = mount(MenuAuth)
    await flushPromises()
    expect(w.find('h3').text()).toBe('')
  })
})

describe('groupByCat（旧 `groupByCat` 的等价）', () => {
  it('★ 分组顺序即界面表格顺序：**整数键按升序枚举**（JS 对象语义，不是插入顺序）', () => {
    const g = groupByCat([
      { id: 1, cat: 3 },
      { id: 2, cat: 1 },
      { id: 3, cat: 3 }
    ])
    // 实测：JS 对象的整数样键按**升序**枚举 ⇒ 先渲染 cat=1 的表，再 cat=3；
    // Vue 的 `v-for` over object 同样走 Object.keys 顺序 ⇒ 这就是界面顺序。
    expect(Object.keys(g)).toEqual(['1', '3'])
    expect(g['3'].map((x) => x.id)).toEqual([1, 3])
    expect(g['1'].map((x) => x.id)).toEqual([2])
  })

  it('非整数样的 cat 保持插入顺序（与整数键的升序规则不同，两种都要能对上）', () => {
    const g = groupByCat([
      { id: 1, cat: 'b' },
      { id: 2, cat: 'a' }
    ])
    expect(Object.keys(g)).toEqual(['b', 'a'])
  })

  it('不排序、不合并（同名 cat 自然合并，不同 cat 不混）', () => {
    const g = groupByCat([
      { id: 2, cat: 2 },
      { id: 1, cat: 2 }
    ])
    expect(g['2'].map((x) => x.id)).toEqual([2, 1])
  })

  it('非数组入参 ⇒ 空对象（不抛错）', () => {
    expect(groupByCat(null)).toEqual({})
    expect(groupByCat(undefined)).toEqual({})
  })
})
