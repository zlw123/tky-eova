<!--
  SSE 演示页（第 122 轮）—— 旧 `_view/sse/index.html`(73 行) 的等价物

  ## 它的真实 URL 与三条关联链路（取证）

  旧文件挂在 `eova/_view/sse/index.html`，但**渲染它的不是它自己**（文件里也没有任何 `#(...)` 插值）：

  | 链路 | 旧实现 | 说明 |
  |---|---|---|
  | **页面** | `demo/.../ctrl/TestController.java:22-25` `sse()` → `render("/eova/sse/index.html")` | ⇒ 页面 URL 是 **`/test/sse`**（`/test` 是 demo 的 `AppRoutes:17` 注册的前缀），**不是 `/sse`** |
  | **推送通道** | `SSEController#index()`（`EovaWebRoutes:49` `add("/sse", …)`）→ `SseKit.startAsync(UID(), this)` | 页面里 `new EventSource('/sse')` 连的就是它 |
  | **触发推送** | `TestController#msg()`（`/test/msg`）→ 起线程每秒 `biz.msg.send(...)` 共 5 条 | 页面里 `pushMsg()` 用 **XHR GET** 打它 |

  ⇒ 本页是**三个 endpoint 的纯前端消费端**，没有任何服务端插值 ⇒ 不需要引导数据、不需要 `uzoo`、不用 EovaUI 组件。

  ## 逐条对齐（旧实现全文只有一段内联脚本）

  · `window.onload` ⇒ `onMounted`：**自动开始接收**（页面一打开就连）
  · `new EventSource('/sse')` + 三个事件监听：`msg` / `addUser` 都 `addMessage(event.data)`；
    ★ `error` ⇒ **`eventSource.close()`**（旧注释原话："这里不关闭时，浏览器可能会重新连接SSE"）
  · `startSSE()` 结尾两条：`console.log('sse init...')` 然后 `addMessage("SSE连接成功")`（★ 顺序即旧顺序）
  · `stopSSE()`：有连接则 `close()` + `addMessage("手动关闭连接")`（无连接则**什么都不做**）
  · `pushMsg()`：`new XMLHttpRequest()` + `open('GET', '/test/msg')`（★ 旧实现用的是 XHR，**不是** axios）；
    `onload` ⇒ `console.log(xhr.responseText)`；`onerror` ⇒ `console.log(new Error('网络错误'))`；
    最后 `addMessage("服务端模拟推送5条消息，1秒1条。")`（★ **在 send 之后**，不等回调）
  · `addMessage(text)`：`messages.innerHTML += \`<p>${new Date().toLocaleTimeString()}: ${text}</p>\``
    并 `messages.scrollTop = messages.scrollHeight`

  ## ★ 保持 `innerHTML +=`（不改成 `v-for` + 文本插值）

  `innerHTML +=` 会把消息内容**当 HTML 解析**（消息来自服务端 `biz.msg.send(...)`）。
  改成 Vue 的文本插值会**顺手把 HTML 转义掉** —— 那是行为改变（含 `<b>` 的消息在旧栈是加粗的）。
  故本页保留同一个 DOM 操作，并用判据钉住"HTML 不被转义"。

  ## 已声明适配（非静默改写）

  ① 旧页是**独立 HTML**（`<body>` 里三个平级元素），本组件用**多根节点**（`h2` + 两个 `button` + `div`）
     保持同一 DOM 形状（**不额外包一层 `#app`**）；
  ② 两个按钮旧用行内 `onclick="stopSSE()"` / `onclick="pushMsg()"`（全局函数）⇒ SPA 用 `@click` 绑定组件方法
     —— 本页**不依赖冻结脚本**（对比 `EovaAdminPanel`：那里的 `onclick` 名字由 `eova.template.js` 提供，
     故必须保持字符串形态），此处等价且更不易漂移；
  ③ 旧的"开始接收"按钮是**注释**（不在 DOM 里，因为 onload 已自动开始）⇒ 原样不渲染。
-->
<template>
  <h2>服务端推送演示 (SSE)</h2>
  <!-- 旧的「开始接收」按钮是注释掉的（onload 已自动开始）⇒ 不渲染 -->
  <button @click="stopSSE">停止接收</button>
  <button @click="pushMsg">模拟服务端推送5条消息</button>
  <div
    ref="messagesEl"
    id="messages"
    style="margin-top: 20px; border: 1px solid #ccc; padding: 10px"
  ></div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'

/** 消息容器（旧实现按 id 取 `document.getElementById('messages')`；这里用模板 ref） */
const messagesEl = ref<HTMLDivElement | null>(null)

/** SSE 连接句柄（旧 `let eventSource;`） */
let eventSource: EventSource | null = null

/**
 * 追加一条消息（旧 `addMessage`）
 *
 * ★ 逐字保留 `innerHTML +=`：消息内容按 **HTML** 解析（不是文本），否则含标签的消息会变成转义后的字面量。
 *
 * @param text 消息内容（服务端推来的 `event.data`，或本地固定文案）
 */
function addMessage(text: string): void {
  const messages = messagesEl.value
  if (!messages) {
    return
  }
  messages.innerHTML += `<p>${new Date().toLocaleTimeString()}: ${text}</p>`
  // 自动滚动到底部
  messages.scrollTop = messages.scrollHeight
}

/**
 * 开始接收（旧 `startSSE`：页面加载时自动调用）
 */
function startSSE(): void {
  // 初始化 EventSource
  eventSource = new EventSource('/sse')

  eventSource.addEventListener('msg', function (event) {
    console.log('收到消息:', event)
    addMessage((event as MessageEvent).data as string)
  })
  eventSource.addEventListener('addUser', function (event) {
    console.log('收到addUser消息:', event)
    addMessage((event as MessageEvent).data as string)
  })
  eventSource.addEventListener('error', function (event) {
    console.log('关闭:', event)
    // 这里不关闭时，浏览器可能会重新连接SSE
    eventSource?.close()
  })
  console.log('sse init...')
  addMessage('SSE连接成功')
}

/**
 * 停止接收（旧 `stopSSE`：无连接时**什么都不做**）
 */
function stopSSE(): void {
  if (eventSource) {
    eventSource.close()
    addMessage('手动关闭连接')
  }
}

/**
 * 触发服务端模拟推送（旧 `pushMsg`：XHR GET `/test/msg`，5 条 1 秒 1 条）
 */
function pushMsg(): void {
  const xhr = new XMLHttpRequest()
  xhr.open('GET', '/test/msg')
  xhr.onload = function () {
    console.log(xhr.responseText)
  }
  xhr.onerror = function () {
    console.log(new Error('网络错误'))
  }
  xhr.send()
  addMessage('服务端模拟推送5条消息，1秒1条。')
}

// 旧实现是 `window.onload = function () { startSSE() }`
onMounted(() => {
  startSSE()
})

defineExpose({ messagesEl, startSSE, stopSSE, pushMsg, addMessage })
</script>
