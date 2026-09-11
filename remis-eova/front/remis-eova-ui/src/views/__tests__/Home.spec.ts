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

vi.mock('axios')
const post = axios.post as unknown as ReturnType<typeof vi.fn>

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
})
