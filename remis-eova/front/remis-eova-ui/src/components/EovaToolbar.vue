<!--
  工具栏按钮渲染（第 116 轮）—— 旧 `_block/toolbar.html:3-17` 的 SPA 等价物

  ## 旧实现原文（逐字）

  ```
  #for(btn : btnList)
  #if(btn.is_base)
  <button class="#(btn.style)" @click="#(btn.event)()">
      <i class="#(btn.icon)"></i>
      #(btn.name)
  </button>
  #else
  <button class="#(btn.style)" onclick="handlerButtonEvent('#(btn.event)')">
      <i class="#(btn.icon)"></i>
      #(btn.name)
  </button>
  #end
  #end
  ```

  ## ★ 关键差异：`@click="#(btn.event)()"` 在 SPA 里不能照抄

  旧栈是**服务端插值**：`#(btn.event)` 先把字符串拼进属性，Vue 再**编译**成真正的绑定
  （`@click="onAdd()"`）。SPA 里 event 名是**运行时数据**，无法编译成绑定
  ⇒ 本组件改为**查表派发**：`@click="dispatch(is_base 分支…)"`，
  即 `is_base=1` 时调 `props.handlers[event]`（页面注入的方法表）。

  **语义等价**：两者都"调页面上的那个方法"；差异只在**绑定时机**（编译期 vs 运行期），
  属**已声明适配**。

  ## `is_base=0`：**必须**走全局派发器，不能改成查表

  自定义按钮的实现在**外部 `.js` 脚本**里（`btn.ui` 指向它，由 r115 的加载器注入），
  它把处理函数挂到 `window`（派发器要求），页面**无权也无必要知道**它
  ⇒ 这里必须与旧栈一致：`onclick="handlerButtonEvent('<event>')"`，
  其中 `handlerButtonEvent` 由**冻结资产** `_view/template/eova.template.js` 提供。
-->
<template>
  <span class="eova-toolbar-buttons">
    <template v-for="btn in list" :key="btn.id ?? btn.event">
      <!-- is_base=1：内置按钮 ⇒ 调**页面注入的方法表**（等价于旧栈编译期的 @click="<event>()"） -->
      <button v-if="btn.is_base" :class="btn.style" @click="dispatch(btn)">
        <i :class="btn.icon"></i>
        {{ btn.name }}
      </button>
      <!-- is_base=0：自定义按钮 ⇒ 走**全局派发器**（与旧栈逐字一致） -->
      <button v-else :class="btn.style" :onclick="customOnclick(btn)">
        <i :class="btn.icon"></i>
        {{ btn.name }}
      </button>
    </template>
  </span>
</template>

<script setup lang="ts">
/** 按钮行（字段名沿用旧 `eova_button` 的列名） */
export interface ToolbarButton {
  id?: number | string
  name?: string
  event?: string
  icon?: string
  style?: string
  is_base?: boolean | number
  ui?: unknown
  [k: string]: unknown
}

const props = defineProps<{
  /** 按钮列表（来自引导数据的 `btnList`） */
  list: ToolbarButton[]
  /**
   * 页面注入的**方法表**（`is_base=1` 时按 `btn.event` 查表调用）。
   *
   * 与旧栈的差别：旧栈靠编译期绑定，这里靠运行期查表 —— 已声明适配。
   */
  handlers: Record<string, (() => void) | undefined>
  /** 告警出口（方法缺失时） */
  warn?: (message: string) => void
}>()

/**
 * 调内置按钮对应的页面方法（旧栈 `@click="<event>()"` 的等价）
 *
 * ★ 方法缺失时**不静默**：旧栈会因编译失败**白屏**（见 `handlerButtonEvent` 的注释
 *   「如方法不存在会导致白屏」）；SPA 里既然能判，就**响亮告警**并什么都不做。
 *
 * @param btn 按钮行
 */
function dispatch(btn: ToolbarButton): void {
  const name = String(btn.event ?? '')
  const fn = props.handlers[name]
  if (typeof fn === 'function') {
    fn()
    return
  }
  const warn = props.warn ?? ((m: string) => console.warn(m))
  warn(
    `[toolbar] 内置按钮的事件处理函数缺失：${name}（按钮：${String(btn.name ?? '')}#${String(
      btn.id ?? ''
    )}）⇒ 已忽略点击。旧栈此处会因绑定不存在而白屏，SPA 侧改为响亮告警。`
  )
}

/**
 * 自定义按钮的行内 `onclick` 表达式（旧栈逐字：`handlerButtonEvent('<event>')`）
 *
 * ★ 用**字符串**而非 `@click`：处理函数在外部脚本里挂 `window`，
 *   页面不该去查自己的方法表（旧栈也不查）。`handlerButtonEvent` 由冻结资产提供。
 *
 * @param btn 按钮行
 * @returns onclick 表达式
 */
function customOnclick(btn: ToolbarButton): string {
  // 事件名里的单引号按 JS 字符串字面量转义（旧栈是服务端直接拼接，未做转义 ⇒ 这里保持"能工作"的最小处理）
  const event = String(btn.event ?? '').replace(/'/g, "\\'")
  return `handlerButtonEvent('${event}')`
}

defineExpose({ dispatch, customOnclick })
</script>
