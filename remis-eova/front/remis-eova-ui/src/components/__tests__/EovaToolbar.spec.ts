/**
 * 工具栏按钮渲染的判据（旧 `_block/toolbar.html:3-17` 的等价）。
 *
 * 钉的契约：
 *  ① `is_base` 真 ⇒ 走**页面方法表**（旧栈编译期 `@click="<event>()"` 的运行期等价）；
 *  ② `is_base` 假 ⇒ 走**全局派发器** `handlerButtonEvent('<event>')`（字符串 onclick，**不是** `@click`）；
 *  ③ 方法缺失时**响亮告警**且不抛（旧栈此处会白屏，SPA 改为可诊断）；
 *  ④ `class`/`icon`/`name` 逐字；顺序与 `btnList` 一致；
 *  ⑤ 事件名里的单引号按 JS 字面量转义（旧栈直接拼接、未转义）。
 */
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import EovaToolbar, { type ToolbarButton } from '../EovaToolbar.vue'

/** 造一批按钮（含内置与自定义） */
function buttons(): ToolbarButton[] {
  return [
    { id: 1, name: '查询', event: 'onQuery', icon: 'eova-icon-search', style: '', is_base: 1 },
    { id: 2, name: '新增', event: 'onAdd', icon: 'eova-icon-add-1', is_base: 1 },
    {
      id: 3,
      name: '导出脚本',
      event: 'exports',
      icon: 'eova-icon-next',
      style: 'eova-btn_warn',
      is_base: 0
    }
  ]
}

describe('EovaToolbar（旧 _block/toolbar.html 的等价）', () => {
  it('结构：按 btnList 顺序渲染，class/icon/name 逐字', () => {
    const w = mount(EovaToolbar, { props: { list: buttons(), handlers: {} } })
    const btns = w.findAll('button')
    expect(btns).toHaveLength(3)
    expect(btns.map((b) => b.text())).toEqual(['查询', '新增', '导出脚本'])
    expect(btns[2].classes()).toContain('eova-btn_warn')
    expect(btns[1].find('i').classes()).toContain('eova-icon-add-1')
  })

  it('★ is_base=1 ⇒ 点击调**页面注入的方法表**（按 event 名查表）', async () => {
    const onQuery = vi.fn()
    const onAdd = vi.fn()
    const w = mount(EovaToolbar, { props: { list: buttons(), handlers: { onQuery, onAdd } } })
    await w.findAll('button')[0].trigger('click')
    expect(onQuery).toHaveBeenCalledTimes(1)
    expect(onAdd).not.toHaveBeenCalled()
    await w.findAll('button')[1].trigger('click')
    expect(onAdd).toHaveBeenCalledTimes(1)
  })

  it('★ is_base=0 ⇒ 渲染**字符串 onclick**（handlerButtonEvent），且不查页面方法表', () => {
    const exportsFn = vi.fn()
    const w = mount(EovaToolbar, {
      props: { list: buttons(), handlers: { exports: exportsFn } }
    })
    const custom = w.findAll('button')[2]
    // 契约就是"这个属性串"（旧 `_block/toolbar.html` 逐字）：改写成 `@click="dispatch(btn)"` 即为行为变更
    expect(custom.attributes('onclick')).toBe("handlerButtonEvent('exports')")
    // ★ `not executed`：**行内 onclick 在 jsdom 里根本不会被执行**（已用"纯 jsdom、不经 Vue"复核：
    //   `setAttribute('onclick', …)` 之后 `el.click()` 与手动调用均不触发），
    //   故"点击后真的调到全局派发器"**无法在本环境判定** ⇒ 明确标注 `not executed`，属浏览器实跑项。
    //   这里只断言"不查页面方法表"这一可判定部分。
    expect(exportsFn).not.toHaveBeenCalled()
  })

  it('★ 内置按钮不渲染行内 onclick（它绑的是 @click）', () => {
    const w = mount(EovaToolbar, { props: { list: buttons(), handlers: {} } })
    expect(w.findAll('button')[0].attributes('onclick')).toBeUndefined()
  })

  it('★★ 方法缺失 ⇒ **响亮告警**且不抛（旧栈此处白屏）', async () => {
    const warn = vi.fn()
    const w = mount(EovaToolbar, { props: { list: buttons(), handlers: {}, warn } })
    await w.findAll('button')[0].trigger('click')
    expect(warn).toHaveBeenCalledTimes(1)
    const msg = warn.mock.calls[0][0] as string
    expect(msg).toContain('onQuery')
    expect(msg).toContain('查询#1')
    expect(msg).toContain('白屏')
  })

  it('★ 事件名含单引号时按 JS 字面量转义（旧栈直接拼接、未转义）', () => {
    const w = mount(EovaToolbar, {
      props: { list: [{ id: 9, name: '怪事件', event: "it's", is_base: 0 }], handlers: {} }
    })
    expect(w.find('button').attributes('onclick')).toBe("handlerButtonEvent('it\\'s')")
  })

  it('空列表 ⇒ 渲染空容器（不报错）', () => {
    const w = mount(EovaToolbar, { props: { list: [], handlers: {} } })
    expect(w.findAll('button')).toHaveLength(0)
  })

  it('is_base 为数字 1 时同样走内置分支（后端列是 tinyint，宽松真值）', async () => {
    const fn = vi.fn()
    const w = mount(EovaToolbar, {
      props: { list: [{ id: 5, name: 'X', event: 'go', is_base: 1 }], handlers: { go: fn } }
    })
    await w.find('button').trigger('click')
    expect(fn).toHaveBeenCalledTimes(1)
  })
})
