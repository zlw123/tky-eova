/**
 * Tab 行为的判据（逐条对齐旧 `_view/index/index.js` 的 4 个函数与 5 条既有语义）。
 */
import { describe, expect, it } from 'vitest'
import { closeAllTab, closeTab, initTabs, openTab, toTab, type TabItem } from '../tab'

describe('tab.ts（旧 index.js 的 Tab 行为等价）', () => {
  it('初始页签：首页固定第一项且激活', () => {
    expect(initTabs()).toEqual([{ id: 0, name: '首页', active: true }])
  })

  it('openTab：已存在则只切换、不重复入栈；不存在则入栈并激活', () => {
    const tabs: TabItem[] = initTabs()
    expect(openTab(tabs, { id: 1, name: 'A' })).toBeNull()
    expect(tabs.map((t) => t.id)).toEqual([0, 1])
    expect(tabs.find((t) => t.id == 1)!.active).toBe(true)

    openTab(tabs, { id: 1, name: 'A 改了名' })
    expect(tabs.length).toBe(2)
    expect(tabs.find((t) => t.id == 1)!.name).toBe('A')
  })

  it('openTab：type=open 不入页签，返回链接（旧实现新窗口打开）', () => {
    const tabs: TabItem[] = initTabs()
    const link = openTab(tabs, { id: 9, name: '外链', type: 'open', link: 'https://eova.cn' })
    expect(link).toBe('https://eova.cn')
    expect(tabs.map((t) => t.id)).toEqual([0])
  })

  it('比较用宽松相等：字符串 id 与数字 id 视为同一页签', () => {
    const tabs: TabItem[] = initTabs()
    openTab(tabs, { id: 2, name: 'B' })
    openTab(tabs, { id: '2', name: 'B' })
    expect(tabs.length).toBe(2)
  })

  it('closeTab：移除后切到【最后一个】页签（不是相邻）', () => {
    const tabs: TabItem[] = initTabs()
    openTab(tabs, { id: 1, name: 'A' })
    openTab(tabs, { id: 2, name: 'B' })
    openTab(tabs, { id: 3, name: 'C' })
    closeTab(tabs, { id: 1, name: 'A' })
    expect(tabs.map((t) => t.id)).toEqual([0, 2, 3])
    expect(tabs.find((t) => t.active)!.id).toBe(3)
  })

  it('closeAllTab：只保留首页并激活（splice(1)）', () => {
    const tabs: TabItem[] = initTabs()
    openTab(tabs, { id: 1, name: 'A' })
    openTab(tabs, { id: 2, name: 'B' })
    closeAllTab(tabs)
    expect(tabs).toEqual([{ id: 0, name: '首页', active: true }])
  })

  it('toTab：单一激活（其余全部置 false）', () => {
    const tabs: TabItem[] = [{ id: 0, name: '首页', active: true }, { id: 1, name: 'A' }]
    toTab(tabs, tabs[1])
    expect(tabs.map((t) => t.active)).toEqual([false, true])
  })
})
