/**
 * SSE 演示页的判据（旧 `_view/sse/index.html` 73 行内联脚本的等价）。
 *
 * 钉的契约：
 *  ① 结构：**三个平级根节点**（`h2` + 两个按钮 + `#messages`），**不额外包一层 `#app`**
 *     （旧页是独立 HTML，`<body>` 里就是这三个元素）；旧注释掉的「开始接收」按钮**不渲染**；
 *  ② `onMounted`（旧 `window.onload`）**自动开始接收**：`new EventSource('/sse')`；
 *  ③ 三个监听：`msg`/`addUser` ⇒ `addMessage(event.data)`；★ `error` ⇒ **`close()`**（旧注释：不关闭浏览器会重连）；
 *  ④ `startSSE` 结尾顺序：`console.log('sse init...')` 然后 `addMessage("SSE连接成功")`；
 *  ⑤ `stopSSE`：有连接 ⇒ `close()` + `addMessage("手动关闭连接")`；**无连接 ⇒ 什么都不做**；
 *  ⑥ `pushMsg`：用 **XMLHttpRequest**（不是 axios）`GET /test/msg`，`onload` 打响应、`onerror` 打 `new Error('网络错误')`，
 *     并且**在 send 之后**（不等回调）就 `addMessage("服务端模拟推送5条消息，1秒1条。")`；
 *  ⑦ `addMessage`：`innerHTML += \`<p>\${时间}: \${text}</p>\`` 且把 `scrollTop` 拉到底
 *     —— ★ **内容按 HTML 解析**（不转义），含 `<b>` 的消息应渲染成元素而不是字面量；
 *  ⑧ 本页**不用引导数据、不用 `uzoo`、不用 EovaUI 组件**（旧文件里没有任何插值/组件）。
 *
 * ★ jsdom 没有 `EventSource`：本判据用替身（并据此把"真实推送链路"标为 `not executed`）。
 */
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi, type MockInstance } from 'vitest'
import Sse from '../Sse.vue'

/** `EventSource` 替身：记录 url 与监听器，可手工触发事件 */
class FakeEventSource {
  static instances: FakeEventSource[] = []
  url: string
  closed = false
  listeners = new Map<string, ((e: unknown) => void)[]>()

  constructor(url: string) {
    this.url = url
    FakeEventSource.instances.push(this)
  }

  addEventListener(type: string, fn: (e: unknown) => void): void {
    const list = this.listeners.get(type) ?? []
    list.push(fn)
    this.listeners.set(type, list)
  }

  close(): void {
    this.closed = true
  }

  /** 手工触发某个事件 */
  emit(type: string, event: unknown): void {
    for (const fn of this.listeners.get(type) ?? []) {
      fn(event)
    }
  }
}

/** `XMLHttpRequest` 替身：记录调用，不真的发请求 */
class FakeXhr {
  static instances: FakeXhr[] = []
  method = ''
  url = ''
  sent = false
  responseText = 'OK'
  onload: (() => void) | null = null
  onerror: (() => void) | null = null

  constructor() {
    FakeXhr.instances.push(this)
  }

  open(method: string, url: string): void {
    this.method = method
    this.url = url
  }

  send(): void {
    this.sent = true
  }
}

/** 取当前挂载实例的 SSE 连接 */
function source(): FakeEventSource {
  const s = FakeEventSource.instances.at(-1)
  expect(s, '未创建 EventSource').toBeTruthy()
  return s!
}

describe('Sse.vue（旧 _view/sse/index.html 的行为等价）', () => {
  let log: MockInstance

  beforeEach(() => {
    FakeEventSource.instances = []
    FakeXhr.instances = []
    vi.stubGlobal('EventSource', FakeEventSource)
    vi.stubGlobal('XMLHttpRequest', FakeXhr)
    log = vi.spyOn(console, 'log').mockImplementation(() => {})
  })

  it('① 结构：三个平级根节点（h2 + 2 按钮 + #messages），无额外包裹层，注释按钮不渲染', () => {
    const w = mount(Sse)
    // 多根节点组件的 `w.html()` 是各根节点拼接（若多包一层，这里就会以 `<div` 开头 —— 这正是判别点）
    const html = w.html()
    expect(html.trim().startsWith('<h2>')).toBe(true)
    expect(html).not.toContain('id="app"')
    // 顺序：h2 → 两个按钮 → 消息容器
    expect(html.indexOf('<h2')).toBeLessThan(html.indexOf('<button'))
    expect(html.indexOf('模拟服务端推送5条消息')).toBeLessThan(html.indexOf('id="messages"'))
    expect(w.find('h2').text()).toBe('服务端推送演示 (SSE)')
    expect(w.findAll('button').map((b) => b.text())).toEqual(['停止接收', '模拟服务端推送5条消息'])
    // ★ jsdom 会把颜色归一成 rgb（`#ccc` ⇒ `rgb(204, 204, 204)`，与 calc/颜色的既有归一同类）
    const style = w.find('#messages').attributes('style')?.replace(/\s+/g, ' ') ?? ''
    expect(style).toContain('margin-top: 20px')
    expect(style).toContain('border: 1px solid rgb(204, 204, 204)')
    expect(style).toContain('padding: 10px')
  })

  it('② onMounted（旧 window.onload）自动开始接收：连接 /sse 并先写一条「SSE连接成功」', () => {
    const w = mount(Sse)
    expect(source().url).toBe('/sse')
    expect(w.find('#messages').html()).toContain('SSE连接成功')
    // 旧的 console.log('sse init...') 顺序在 addMessage 之前
    expect(log.mock.calls.map((c) => String(c[0]))).toContain('sse init...')
  })

  it('③ msg / addUser 事件都调 addMessage(event.data)；★ error 事件把连接 close 掉', () => {
    const w = mount(Sse)
    const s = source()
    s.emit('msg', { data: '第一条' })
    s.emit('addUser', { data: '有人上线' })
    const html = w.find('#messages').html()
    expect(html).toContain('第一条')
    expect(html).toContain('有人上线')
    expect(log.mock.calls.map((c) => String(c[0]))).toContain('收到消息:')

    expect(s.closed).toBe(false)
    s.emit('error', { type: 'error' })
    expect(s.closed, 'error 事件后必须 close（否则浏览器会重连）').toBe(true)
    expect(log.mock.calls.map((c) => String(c[0]))).toContain('关闭:')
  })

  it('④ startSSE 可再次调用（旧实现是全局函数，按钮/控制台都能调）', () => {
    const w = mount(Sse)
    ;(w.vm as never as { startSSE: () => void }).startSSE()
    expect(FakeEventSource.instances).toHaveLength(2)
    expect(FakeEventSource.instances[1].url).toBe('/sse')
  })

  it('⑤ stopSSE：有连接 ⇒ close + 「手动关闭连接」；无连接 ⇒ 什么都不做（不报错）', async () => {
    const w = mount(Sse)
    const s = source()
    const vm = w.vm as never as { stopSSE: () => void }
    vm.stopSSE()
    expect(s.closed).toBe(true)
    expect(w.find('#messages').html()).toContain('手动关闭连接')

    void w
  })

  it('⑤b ★ 无连接时 stopSSE **什么都不做**（旧实现如此：`if (eventSource) {...}`）', () => {
    // 造"从未连上"的状态：EventSource 构造抛错 ⇒ `eventSource` 保持 null。
    // 旧栈是 `window.onload` 里抛错（浏览器只记错误、页面继续），故这里**接住 mount 抛出的错**；
    // 随后用**真实点击**（DOM 已在文档里）验证"无连接 ⇒ 不新增任何消息"，
    // 这样不依赖 vm（mount 抛错时拿不到 wrapper）。
    class ThrowingEventSource {
      constructor() {
        throw new Error('EventSource 不可用')
      }
    }
    vi.stubGlobal('EventSource', ThrowingEventSource)
    const host = document.createElement('div')
    document.body.appendChild(host)
    let threw = false
    try {
      mount(Sse, { attachTo: host })
    } catch (e) {
      threw = true
      expect(String(e)).toContain('EventSource 不可用')
    }
    // 反空断言：确实处于"没有连接"的状态（构造真的抛了、且没写下「SSE连接成功」）
    expect(threw).toBe(true)
    const messages = host.querySelector('#messages') as HTMLElement
    expect(messages.innerHTML).not.toContain('SSE连接成功')

    const stopBtn = host.querySelectorAll('button')[0] as HTMLButtonElement
    stopBtn.click()
    // ★ 空判据修正点：两种实现在"无连接"下都会追加内容**是错觉** —— 只有无条件追加的写法才会新增
    expect(messages.innerHTML, '无连接时不应新增任何消息').toBe('')
  })

  it('⑥ pushMsg：XHR **GET /test/msg**，且不等回调就先写提示；onload/onerror 各打一条日志', async () => {
    const w = mount(Sse)
    ;(w.vm as never as { pushMsg: () => void }).pushMsg()
    expect(FakeXhr.instances).toHaveLength(1)
    const xhr = FakeXhr.instances[0]
    expect([xhr.method, xhr.url, xhr.sent]).toEqual(['GET', '/test/msg', true])
    expect(w.find('#messages').html()).toContain('服务端模拟推送5条消息，1秒1条。')

    xhr.responseText = 'ok-body'
    xhr.onload?.()
    expect(log.mock.calls.map((c) => String(c[0]))).toContain('ok-body')
    const err = new Error('网络错误')
    xhr.onerror?.()
    expect(log.mock.calls.map((c) => String(c[0]))).toContain(String(err))
  })

  it('⑦ addMessage 用 innerHTML（内容按 HTML 解析，**不转义**）+ 滚到底部', () => {
    const w = mount(Sse)
    const el = w.find('#messages').element as HTMLDivElement
    ;(w.vm as never as { addMessage: (t: string) => void }).addMessage('<b>加粗</b>')
    // ★ 若被"顺手"改成文本插值，这里会变成 &lt;b&gt;
    expect(el.querySelector('b')?.textContent).toBe('加粗')
    expect(el.innerHTML).not.toContain('&lt;b&gt;')
    // 每条消息一个 <p>，格式是「时间: 内容」
    const ps = el.querySelectorAll('p')
    expect(ps.length).toBeGreaterThanOrEqual(2)
    expect(ps[ps.length - 1].textContent).toContain(': 加粗')

    // ★ 滚动断言必须先把 scrollHeight 造成非 0：jsdom 里它恒为 0，于是 `scrollTop === scrollHeight`
    //   是**空判据**（变异 M8"不再滚到底部"实测未被捕获）。这里用 defineProperty 造一个真值。
    Object.defineProperty(el, 'scrollHeight', { value: 500, configurable: true })
    el.scrollTop = 0
    ;(w.vm as never as { addMessage: (t: string) => void }).addMessage('再一条')
    expect(el.scrollTop, '未把 scrollTop 拉到底部').toBe(500)
  })

  it('⑧ 本页不依赖引导数据/uzoo/EovaUI 组件（旧文件里没有任何插值）', () => {
    // 挂载不需要任何全局接缝（未设 EovaTools/EovaMe/uzoo 也不抛错）
    ;(globalThis as unknown as Record<string, unknown>)['uzoo'] = undefined
    expect(() => mount(Sse)).not.toThrow()
  })
})
