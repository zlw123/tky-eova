/**
 * 主框架页（第 2 个入口页）的行为等价判据。
 *
 * 钉两条最容易在改写时丢的契约：
 *  ① **cats 过滤规则**（逻辑）：只保留"确实有子菜单"的目录；
 *  ② **握手事件 `EovaMenuNextTick`**（集成契约）：加载完成后必须派发一次，且**在 nextTick 之后**。
 * 另加取数契约与两句失败文案。
 */
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import Home from '../Home.vue'
import { filterCats } from '@/utils/menu'
import { setEovaMe, type EovaMe } from '@/compat/eova-runtime'

vi.mock('axios')
const post = axios.post as unknown as ReturnType<typeof vi.fn>

/**
 * 造一个 `me` 替身（记录调用，供接线判据断言）。
 *
 * @returns 带 mock 的 `me` 实例
 */
function makeMe(): EovaMe {
  const me = {
    layer: { open: vi.fn(), msg: vi.fn(), no: vi.fn() },
    cross: { on: vi.fn(), off: vi.fn() }
  } as unknown as EovaMe
  setEovaMe(me)
  return me
}

const okBody = {
  data: {
    state: 'ok',
    menus: [
      { id: 1, name: '菜单A', parent_id: 10, type: 'menu' },
      { id: 2, name: '目录B本身', parent_id: null, type: 'dir' },
      { id: 3, name: '菜单C', parent_id: 20, type: 'menu' }
    ],
    cats: [
      { id: 10, name: '目录十' },
      { id: 20, name: '目录二十' },
      { id: 30, name: '空目录（无子菜单）' }
    ]
  }
}

describe('Home.vue（旧 _view/index/index.html + index.js 的行为等价）', () => {
  beforeEach(() => {
    post.mockReset()
  })

  it('取数契约：POST /api/home/menu，空体', async () => {
    post.mockResolvedValue(okBody)
    mount(Home)
    await flushPromises()
    expect(post).toHaveBeenCalledTimes(1)
    expect(post.mock.calls[0][0]).toBe('/api/home/menu')
    expect(post.mock.calls[0][1]).toEqual({})
  })

  it('cats 过滤规则（纯函数）：parent_id 为 null/负数的菜单不构成"有子菜单"', () => {
    // 含 id=-1 的目录：旧规则里 parent_id >= 0 才有效 ⇒ 该目录必须被过滤掉
    const cats = [{ id: 1 }, { id: 2 }, { id: 3 }, { id: -1 }]
    // 只有 parent_id=1 与 parent_id=3 是有效归属
    const menus = [
      { parent_id: null },
      { parent_id: -1 },
      { parent_id: 1 },
      { parent_id: 3 }
    ]
    expect(filterCats(menus as any, cats as any).map((c: any) => c.id)).toEqual([1, 3])
    // 关键：parent_id=-1 不构成归属 ⇒ id=-1 的目录不得入选（去掉 >=0 判定后此断言会失败）
    // 全部无归属 ⇒ 目录全被过滤
    expect(filterCats([{ parent_id: null }] as any, cats as any)).toEqual([])
  })

  it('cats 过滤规则：只保留确实有子菜单的目录（旧 index.js 的 pids 规则）', async () => {
    post.mockResolvedValue(okBody)
    const w = mount(Home)
    await flushPromises()
    const ids = (w.vm as any).cats.map((c: any) => c.id)
    expect(ids).toEqual([10, 20])          // 30 无子菜单 ⇒ 被过滤
    expect(ids).not.toContain(30)
    // parent_id 为 null（目录自身）不构成"有子菜单"
    expect(ids).not.toContain(null)
  })

  it('握手事件：加载完成后派发一次 EovaMenuNextTick（子组件初始化依赖）', async () => {
    const seen: string[] = []
    const handler = (e: Event) => seen.push(e.type)
    document.addEventListener('EovaMenuNextTick', handler)
    try {
      post.mockResolvedValue(okBody)
      mount(Home)
      await flushPromises()
      expect(seen).toEqual(['EovaMenuNextTick'])
    } finally {
      document.removeEventListener('EovaMenuNextTick', handler)
    }
  })

  it('失败文案：业务失败与网络异常两句与旧实现一致', async () => {
    post.mockResolvedValue({ data: { state: 'fail' } })
    const w1 = mount(Home)
    await flushPromises()
    expect((w1.vm as any).msg).toBe('加载菜单错误, 请稍候再试')

    post.mockRejectedValue(new Error('boom'))
    const w2 = mount(Home)
    await flushPromises()
    expect((w2.vm as any).msg).toBe('请求异常')
  })

  it('组件级接线：点菜单入页签并激活；重复点不重复入栈', async () => {
    post.mockResolvedValue(okBody)
    const w = mount(Home)
    await flushPromises()
    const vm = w.vm as any
    expect(vm.tabMenus.map((t: any) => t.id)).toEqual([0])
    vm.onMenuClick({ id: 1, name: '菜单A' })
    expect(vm.tabMenus.map((t: any) => t.id)).toEqual([0, 1])
    expect(vm.tabMenus.find((t: any) => t.id == 1).active).toBe(true)
    vm.onMenuClick({ id: 1, name: '菜单Again' })
    expect(vm.tabMenus.length).toBe(2)
  })

  it('组件级接线：type=open 的菜单走新窗口且不入页签', async () => {
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null)
    post.mockResolvedValue(okBody)
    const w = mount(Home)
    await flushPromises()
    const vm = w.vm as any
    vm.onMenuClick({ id: 8, name: '外链', type: 'open', link: 'https://eova.cn' })
    expect(openSpy).toHaveBeenCalledWith('https://eova.cn')
    expect(vm.tabMenus.map((t: any) => t.id)).toEqual([0])
    openSpy.mockRestore()
  })

  it('组件级接线：点页签切换（onTabClick 必须接 toTab，单一激活）', async () => {
    post.mockResolvedValue(okBody)
    const w = mount(Home)
    await flushPromises()
    const vm = w.vm as any
    vm.onMenuClick({ id: 1, name: 'A' })
    vm.onMenuClick({ id: 2, name: 'B' })
    expect(vm.tabMenus.find((t: any) => t.id == 2).active).toBe(true)
    // 回点 A：A 激活、B 失活（这条专门覆盖 onTabClick → toTab 的接线；缺它时 M2 变异会漏网）
    vm.onTabClick(vm.tabMenus.find((t: any) => t.id == 1))
    expect(vm.tabMenus.find((t: any) => t.id == 1).active).toBe(true)
    expect(vm.tabMenus.find((t: any) => t.id == 2).active).toBe(false)
  })

  it('组件级接线：关页签切到最后一个；关全部只留首页', async () => {
    post.mockResolvedValue(okBody)
    const w = mount(Home)
    await flushPromises()
    const vm = w.vm as any
    vm.onMenuClick({ id: 1, name: 'A' })
    vm.onMenuClick({ id: 2, name: 'B' })
    vm.onMenuClick({ id: 3, name: 'C' })
    vm.onTabClose({ id: 1, name: 'A' })
    expect(vm.tabMenus.map((t: any) => t.id)).toEqual([0, 2, 3])
    expect(vm.tabMenus.find((t: any) => t.active).id).toBe(3)
    vm.onCloseAll()
    expect(vm.tabMenus.map((t: any) => t.id)).toEqual([0])
    expect(vm.tabMenus[0].active).toBe(true)
  })

  it('渲染：过滤后的目录与归属菜单可见（m.parent_id == c.id && type != dir）', async () => {
    post.mockResolvedValue(okBody)
    const w = mount(Home)
    await flushPromises()
    await w.vm.$nextTick()
    const text = w.text()
    expect(text).toContain('目录十')
    expect(text).toContain('菜单A')
    expect(text).not.toContain('空目录（无子菜单）')
  })

  it('渲染：目录折叠态用 c.open；菜单项带 :title 与 :id（旧模板契约）', async () => {
    post.mockResolvedValue({
      data: {
        state: 'ok',
        menus: [{ id: 5, name: '菜单E', parent_id: 10, type: 'menu', link: '/eova/meta' }],
        cats: [{ id: 10, name: '目录十', open: false }]
      }
    })
    const w = mount(Home)
    await flushPromises()
    await w.vm.$nextTick()
    // c.open === false ⇒ 菜单项容器 v-show 隐藏（旧模板 v-show="c.open"）
    const items = w.find('.eova-menu_items')
    expect(items.exists()).toBe(true)
    expect((items.element as HTMLElement).style.display).toBe('none')
    const a = w.find('.eova-menu_item a')
    expect(a.attributes('title')).toBe('5|/eova/meta')
    expect(a.attributes('id')).toBe('5')
  })
})

describe('Home.vue · 第 101 轮补齐的主框架行为（内容区 / 折叠 / 刷新 / 全屏 / 用户菜单）', () => {
  beforeEach(() => {
    post.mockReset()
    setEovaMe(null)
  })

  it('内容区：每个页签一个 iframe，id=IF{id}、src=m.link，且首页 iframe 有 src（r97 漏带 link 的回归）', async () => {
    post.mockResolvedValue(okBody)
    const w = mount(Home)
    await flushPromises()
    await w.vm.$nextTick()
    const home = w.find('#IF0')
    expect(home.exists()).toBe(true)
    expect(home.attributes('src')).toBe('/main')
  })

  it('内容区：切回首页时被切走的 iframe 【仍在 DOM 里】（v-show 而非 v-if，切页签保留状态）', async () => {
    post.mockResolvedValue(okBody)
    const w = mount(Home)
    await flushPromises()
    const vm = w.vm as any
    vm.onMenuClick({ id: 1, name: 'A', link: '/eova/a' })
    await w.vm.$nextTick()
    expect(w.find('#IF1').exists()).toBe(true)
    vm.onTabClick(vm.tabMenus.find((t: any) => t.id == 0))
    await w.vm.$nextTick()
    // 关键：IF1 未激活但仍存在；若改成 v-if 会消失，此断言必红
    expect(w.find('#IF1').exists()).toBe(true)
    expect((w.find('#IF1').element.closest('.eova-home_tabbody') as HTMLElement).style.display).toBe('none')
  })

  it('折叠菜单：showMenu 取反，且 .eova-home_tabs 与 .eova-home_body 的 left 同步变化', async () => {
    post.mockResolvedValue(okBody)
    const w = mount(Home)
    await flushPromises()
    const vm = w.vm as any
    expect(vm.showMenu).toBe(true)
    expect((w.find('.eova-home_body').element as HTMLElement).style.left).toBe('200px')
    vm.onFoldMenu()
    await w.vm.$nextTick()
    expect(vm.showMenu).toBe(false)
    expect((w.find('.eova-home_body').element as HTMLElement).style.left).toBe('0px')
    expect((w.find('.eova-home_tabs').element as HTMLElement).style.left).toBe('0px')
    // 菜单面板与 logo 同时隐藏（旧模板两处都绑 showMenu）
    expect((w.find('.eova-home_menu').element as HTMLElement).style.display).toBe('none')
    expect((w.find('.eova-home_logo').element as HTMLElement).style.display).toBe('none')
  })

  it('折叠图标随 showMenu 切换（shrink-right / spread-left）', async () => {
    post.mockResolvedValue(okBody)
    const w = mount(Home)
    await flushPromises()
    expect((w.vm as any).foldIcon).toBe('eova-icon-shrink-right')
    ;(w.vm as any).onFoldMenu()
    await w.vm.$nextTick()
    expect((w.vm as any).foldIcon).toBe('eova-icon-spread-left')
  })

  it('刷新：按【激活页签】的 IF{id} 找 iframe 并触发刷新（同源走 reload）', async () => {
    post.mockResolvedValue(okBody)
    const w = mount(Home)
    await flushPromises()
    const vm = w.vm as any
    vm.onMenuClick({ id: 1, name: 'A', link: '/eova/a' })
    const reload = vi.fn()
    const findSpy = vi
      .spyOn(document, 'getElementById')
      .mockImplementation((id: string) =>
        id === 'IF1'
          ? ({ src: '/eova/a', contentWindow: { location: { reload } } } as unknown as HTMLElement)
          : null
      )
    vm.onRefresh()
    expect(findSpy).toHaveBeenCalledWith('IF1')
    expect(reload).toHaveBeenCalledWith(true)
    findSpy.mockRestore()
  })

  it('全屏：按旧规则进入 / 退出，且无可用 API 时标记不变', async () => {
    post.mockResolvedValue(okBody)
    const w = mount(Home)
    await flushPromises()
    const vm = w.vm as any
    const req = vi.fn()
    const doc = document as unknown as { exitFullscreen?: () => void }
    const savedExit = doc.exitFullscreen
    const savedReq = (document.documentElement as unknown as Record<string, unknown>)[
      'requestFullScreen'
    ]
    try {
      ;(document.documentElement as unknown as Record<string, unknown>)['requestFullScreen'] = req
      vm.onToggleFullScreen()
      expect(req).toHaveBeenCalledTimes(1)
      expect(vm.isFull).toBe(true)
      doc.exitFullscreen = vi.fn()
      vm.onToggleFullScreen()
      expect(vm.isFull).toBe(false)
      // 退出但 exitFullscreen 缺失 ⇒ 标记保持 true（旧实现不回落）
      delete doc.exitFullscreen
      vm.isFull = true
      vm.onToggleFullScreen()
      expect(vm.isFull).toBe(true)
    } finally {
      if (savedExit) {
        doc.exitFullscreen = savedExit
      } else {
        delete doc.exitFullscreen
      }
      ;(document.documentElement as unknown as Record<string, unknown>)['requestFullScreen'] =
        savedReq
    }
  })

  it('用户菜单：虚拟用户还原成功 ⇒ 文案 `虚拟用户还原`、currentUser 清空、并重新加载菜单', async () => {
    const me = makeMe()
    post.mockResolvedValue(okBody)
    const w = mount(Home)
    await flushPromises()
    const vm = w.vm as any
    vm.currentUser = '曹雪芹'
    post.mockClear()
    post.mockResolvedValue({ data: { state: 'ok' } })
    await vm.onResetUser()
    expect(post.mock.calls[0][0]).toBe('/eova/admin/reLogin')
    expect(me.layer.msg).toHaveBeenCalledWith('虚拟用户还原')
    expect(vm.currentUser).toBeUndefined()
    await flushPromises()
    expect(post.mock.calls.some((c: unknown[]) => c[0] === '/api/home/menu')).toBe(true)
  })

  it('用户菜单：虚拟用户还原【业务失败】走 me.layer.no（不是 msg），文案取后端 msg', async () => {
    const me = makeMe()
    post.mockResolvedValue(okBody)
    const w = mount(Home)
    await flushPromises()
    post.mockResolvedValue({ data: { state: 'fail', msg: '没有虚拟用户' } })
    await (w.vm as any).onResetUser()
    expect(me.layer.no).toHaveBeenCalledWith('没有虚拟用户')
    expect(me.layer.msg).not.toHaveBeenCalled()
  })

  it('用户菜单：虚拟用户还原【请求异常】文案为 `客户端请求异常: ` + message', async () => {
    const me = makeMe()
    post.mockResolvedValue(okBody)
    const w = mount(Home)
    await flushPromises()
    post.mockRejectedValue(new Error('boom'))
    await (w.vm as any).onResetUser()
    expect(me.layer.msg).toHaveBeenCalledWith('客户端请求异常: boom')
  })

  it('用户菜单：修改密码与系统消息的 me.layer.open 参数逐字一致（含系统消息的 `修改成功` 旧缺陷文案）', async () => {
    const me = makeMe()
    post.mockResolvedValue(okBody)
    const w = mount(Home)
    await flushPromises()
    const vm = w.vm as any
    vm.onUpdatePwd()
    expect(me.layer.open).toHaveBeenCalledWith('修改密码', '/user/password', 400, 300, expect.any(Function))
    vm.onOpenEovaMsg()
    expect(me.layer.open).toHaveBeenCalledWith(
      '系统消息',
      '/app/eova_msg',
      0.9,
      0.95,
      expect.any(Function)
    )
    // 触发两个回调：文案都是 `修改成功`（系统消息那条是旧的复制粘贴缺陷，不得"顺手改对"）
    const calls = (me.layer.open as unknown as { mock: { calls: unknown[][] } }).mock.calls
    ;(calls[0][4] as () => void)()
    ;(calls[1][4] as () => void)()
    expect(me.layer.msg).toHaveBeenNthCalledWith(1, '修改成功')
    expect(me.layer.msg).toHaveBeenNthCalledWith(2, '修改成功')
  })

  it('用户菜单：切换虚拟用户先注册 eova-layer-ok_done_data，再开弹层；回调里提示用 currentUser', async () => {
    const me = makeMe()
    post.mockResolvedValue(okBody)
    const w = mount(Home)
    await flushPromises()
    const vm = w.vm as any
    vm.onSwitchUser()
    expect(me.cross.on).toHaveBeenCalledWith('eova-layer-ok_done_data', expect.any(Function))
    expect(me.layer.open).toHaveBeenCalledWith(
      '超级用户切换',
      '/eova/admin/su',
      1200,
      0.9,
      expect.any(Function)
    )
    // 监听回调把回传值写进 currentUser
    const listener = (me.cross.on as unknown as { mock: { calls: unknown[][] } }).mock.calls[0][1] as (
      d: unknown
    ) => void
    listener('林黛玉')
    expect(vm.currentUser).toBe('林黛玉')
    // 弹层回调：提示文案 = '切换为:' + currentUser，并重新加载菜单
    const done = (me.layer.open as unknown as { mock: { calls: unknown[][] } }).mock.calls[0][4] as () => void
    done()
    expect(me.layer.msg).toHaveBeenCalledWith('切换为:林黛玉')
  })

  it('运行时未装配时必须【响亮失败】：调用 me 相关动作抛错，不静默吞掉', async () => {
    post.mockResolvedValue(okBody)
    const w = mount(Home)
    await flushPromises()
    const vm = w.vm as any
    expect(() => vm.onUpdatePwd()).toThrow(/未装配/)
  })
})
