/**
 * Tab（页签）行为的纯逻辑 —— 与旧 `_view/index/index.js` 逐条对应。
 *
 * 旧实现形态：4 个闭包函数（`openTab`/`closeTab`/`toTab`/`closeAllTab`）操作 `tabMenus` 数组。
 * 抽成纯函数便于**直接判**（`<script setup>` 不能含 ES export，且这些规则属对外行为）。
 *
 * 既有语义（逐条保留，不得"顺手修正"）：
 *  ① 首页页签 `{id:0, name:'首页', active:true}` 是初始项；
 *  ② `openTab` 对 `type === 'open'` 的菜单**不入页签**，而是新窗口打开 `m.link` 后直接返回；
 *  ③ 查找/比较用**宽松相等**（旧实现 `o.id == m.id`，字符串与数字 id 都能命中）；
 *  ④ `closeTab` 关闭后切到**最后一个**页签（不是相邻的那个）；
 *  ⑤ `closeAllTab` 用 `splice(1)` 只保留**首页**（索引 0），再切到它。
 *
 * 已声明适配：旧 `closeTab` 第一行是 `event.stopPropagation()`（隐式引用全局 `event`），
 * 属 DOM 层职责 —— 本模块不含它，由组件在点击处调用（见 Home.vue）。
 */

/** 一个页签（字段名与旧实现一致） */
export interface TabItem {
  id: number | string
  name: string
  active?: boolean
  type?: string
  link?: string
  [k: string]: unknown
}

/**
 * 初始页签（旧实现：首页固定在第一项且激活）
 *
 * ★ `link: '/main'` **不可省**：旧实现初始项就是
 * `{id:0, name:'首页', active:true, link:'/main'}`，而内容区按 `:src="m.link"` 渲染 iframe
 * —— 少了它首页 iframe 没有 src（第 97 轮抽出本模块时漏带，第 101 轮实施内容区时暴露并补回）。
 *
 * @returns 初始页签数组
 */
export function initTabs(): TabItem[] {
  return [{ id: 0, name: '首页', active: true, link: '/main' }]
}

/**
 * 切换页签（旧 `toTab`：单一激活）
 *
 * @param tabs 页签数组（原地修改，与旧实现一致）
 * @param m    目标页签
 */
export function toTab(tabs: TabItem[], m: TabItem): void {
  tabs.forEach((o) => {
    o.active = o.id == m.id
  })
}

/**
 * 打开菜单（旧 `openTab`）
 *
 * @param tabs 页签数组（原地修改）
 * @param m    菜单
 * @returns 若需新窗口打开，返回其链接；否则返回 null
 */
export function openTab(tabs: TabItem[], m: TabItem): string | null {
  if (m.type === 'open') {
    return m.link ?? null
  }
  const old = tabs.find((o) => o.id == m.id)
  if (old) {
    toTab(tabs, old)
  } else {
    tabs.push(m)
    toTab(tabs, m)
  }
  return null
}

/**
 * 关闭页签（旧 `closeTab`：移除后切到**最后一个**）
 *
 * @param tabs 页签数组（原地修改）
 * @param m    要关闭的页签
 */
export function closeTab(tabs: TabItem[], m: TabItem): void {
  const left = tabs.filter((o) => o.id != m.id)
  tabs.length = 0
  tabs.push(...left)
  if (tabs.length > 0) {
    toTab(tabs, tabs[tabs.length - 1])
  }
}

/**
 * 关闭全部页签（旧 `closeAllTab`：只保留首页并激活）
 *
 * @param tabs 页签数组（原地修改）
 */
export function closeAllTab(tabs: TabItem[]): void {
  tabs.splice(1)
  toTab(tabs, tabs[0])
}
