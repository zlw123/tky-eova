/**
 * 主框架页（`_view/index`）剩余行为的纯逻辑 —— 与旧 `index.js` + `index.html` 逐条对应。
 *
 * 抽成纯函数的理由与 `tab.ts` 同：`<script setup>` 不能含 ES export，而这些规则属对外行为，
 * 必须**直接判**。DOM/iframe/vendor 前缀探测等真实副作用通过参数注入，便于判据观测。
 *
 * ★ 本模块承载的两类"最容易在改写时丢"的东西：
 *   ① `refresh()` 的 **`about:blank` 兜底刷新算法**（跨域 iframe 只能靠换 src 触发重载，
 *      且 URL 带 `#hash` 时必须先有变化才会重载 —— 所以中间必须是 `about:blank`）；
 *   ② 全屏 vendor 前缀的**精确名称与探测顺序**（`requestFullScreen` 不是标准名
 *      `requestFullscreen`；`msRequestFullscreen` 的 s 是小写）——"顺手规范化"会让旧浏览器行为丢失。
 */

/** 页签（与 `tab.ts` 的 `TabItem` 结构一致，此处只声明本模块用到的字段） */
export interface FrameTab {
  id: number | string
  active?: boolean
  link?: string
  [k: string]: unknown
}

/** 跨域兜底刷新的延迟（旧实现硬编码 300ms） */
export const REFRESH_FALLBACK_DELAY_MS = 300

/**
 * 页签对应 iframe 的 id（旧模板 `:id="'IF' + m.id"` 与 `refresh` 的 `getElementById('IF'+id)` 同一约定）
 *
 * @param id 页签 id
 * @returns iframe 的 DOM id
 */
export function buildIframeId(id: number | string): string {
  return 'IF' + id
}

/**
 * 取当前激活页签（旧 `tabMenus.value.find(m => m.active)`）
 *
 * @param tabs 页签数组
 * @returns 激活页签；无激活页签时返回 undefined（旧实现在此会抛错，见 `activeIframeId`）
 */
export function findActiveTab(tabs: FrameTab[]): FrameTab | undefined {
  return tabs.find((m) => m.active)
}

/**
 * 取当前激活页签的 iframe id。
 *
 * **既有语义**：旧 `refresh()` 里 `getElementById('IF' + menu.id)` 在 `try` **之前**，
 * 故"无激活页签"是**未捕获的 TypeError**（不是静默跳过）。本函数保留该行为：
 * 没有激活页签即抛错，不得改为返回 null（那会静默不刷新，属行为漂移）。
 *
 * @param tabs 页签数组
 * @returns iframe 的 DOM id
 */
export function activeIframeId(tabs: FrameTab[]): string {
  const m = findActiveTab(tabs)
  if (!m) {
    // 与旧实现一致：读 undefined.id 抛 TypeError
    throw new TypeError("Cannot read properties of undefined (reading 'id')")
  }
  return buildIframeId(m.id)
}

/**
 * 内容区 / 页签区的左侧偏移（旧模板 `:style="showMenu ? 'left:200px' : 'left:0'"`）。
 *
 * 这是 `showMenu` 对布局的**唯一**耦合点 —— 折叠菜单时内容区不位移就是漏了这一条。
 *
 * @param showMenu 是否显示左菜单
 * @returns 行内样式串
 */
export function bodyLeftStyle(showMenu: boolean): string {
  return showMenu ? 'left:200px' : 'left:0'
}

/**
 * 侧边伸缩按钮的图标类（旧模板 `'eova-icon-' + (showMenu ? 'shrink-right' : 'spread-left')`）
 *
 * @param showMenu 是否显示左菜单
 * @returns 图标类名
 */
export function foldIconClass(showMenu: boolean): string {
  return 'eova-icon-' + (showMenu ? 'shrink-right' : 'spread-left')
}

/**
 * 菜单项标题（旧模板 `:title="m.id + '|' + m.link"`）—— 调试用，但属可观测契约（自动化脚本会读）
 *
 * @param m 菜单
 * @returns 标题串
 */
export function menuTitle(m: { id: number | string; link?: unknown }): string {
  return m.id + '|' + m.link
}

/**
 * 菜单是否挂在目录 `c` 下（旧模板 `v-if="m.parent_id == c.id && m.type != 'dir'"`）
 *
 * **既有语义**：① `parent_id` 用**宽松相等**（后端可能返回字符串 id）；② `type === 'dir'` 的菜单
 * 本身是目录，不当作叶子挂出来。
 *
 * @param m 菜单
 * @param c 目录
 * @returns 是否属于该目录
 */
export function isMenuUnderCat(
  m: { parent_id?: unknown; type?: unknown },
  c: { id: unknown }
): boolean {
  // eslint-disable-next-line eqeqeq -- 与旧模板的宽松相等保持一致
  return m.parent_id == c.id && m.type != 'dir'
}

/**
 * 探测可用的全屏请求函数（旧 `toggleFullScreen` 的探测顺序，逐字保留）
 *
 * 顺序：`requestFullScreen` → `webkitRequestFullScreen` → `mozRequestFullScreen` → `msRequestFullscreen`。
 * 注意旧实现用的是**非标准的 `requestFullScreen`**（大写 S）；改成标准的 `requestFullscreen`
 * 会让探测结果在部分浏览器上从"命中"变"未命中"，属行为漂移。
 *
 * @param ele 目标元素（旧实现为 `document.documentElement`）
 * @returns 可调用的请求函数；全部缺失时返回 null
 */
export function resolveFullscreenRequest(ele: object): ((...args: unknown[]) => unknown) | null {
  const e = ele as Record<string, unknown>
  const req =
    e['requestFullScreen'] ||
    e['webkitRequestFullScreen'] ||
    e['mozRequestFullScreen'] ||
    e['msRequestFullscreen']
  if (typeof req !== 'function') {
    return null
  }
  return req as (...args: unknown[]) => unknown
}

/** 退出全屏所需的最小 doc 面（只取旧实现用到的那一项） */
export interface ExitFullscreenDoc {
  exitFullscreen?: () => unknown
}

/**
 * 退出全屏（旧实现**只**探测 `document.exitFullscreen`，不探测 vendor 前缀）
 *
 * @param doc 文档对象
 * @returns 是否执行了退出（未探测到 ⇒ false，调用方保持 `isFull` 不变）
 */
export function exitFullscreen(doc: ExitFullscreenDoc): boolean {
  if (typeof doc.exitFullscreen === 'function') {
    doc.exitFullscreen()
    return true
  }
  return false
}

/** `refresh` 兜底刷新所需的最小 iframe 面 */
export interface RefreshableIframe {
  src: string
  contentWindow?: { location?: { reload: (force?: boolean) => unknown } } | null
}

/** 定时器注入面（既有判据可观测性，也便于在测试里推进时间） */
export interface TimerLike {
  setTimeout: (fn: () => void, ms: number) => unknown
  clearTimeout: (id: unknown) => void
}

/** 真实定时器 */
export const realTimers: TimerLike = {
  setTimeout: (fn, ms) => setTimeout(fn, ms),
  clearTimeout: (id) => clearTimeout(id as ReturnType<typeof setTimeout>)
}

/**
 * 刷新激活页签的 iframe（旧 `refresh()` 的**完整算法**，含两层 try/catch）
 *
 * 算法逐条保留：
 *  ① 先试图 `iframe.contentWindow.location.reload(true)` —— 同源时成功；
 *  ② 跨域时上一步抛错 ⇒ 兜底：记下 `tmpUrl = iframe.src`，把 `src` 置为 `about:blank`，
 *     **300ms 后**恢复 `tmpUrl`；
 *  ③ 兜底内部再抛错 ⇒ 只打两行日志（`console.log(e)` + `'页面刷新失败!'`），不上抛。
 *
 * 为什么中间必须是 `about:blank`：URL 带 `#hash` 时，把 `src` 设成**相同**值不会触发重载，
 * 必须先变成别的值（`about:blank`）再变回来。
 *
 * @param iframe 目标 iframe
 * @param timers 定时器（可注入）
 * @returns 走了哪条路径：`'reload'` 同源直刷 / `'fallback'` 兜底换 src / `'failed'` 兜底也失败
 */
export function refreshIframe(iframe: RefreshableIframe, timers: TimerLike = realTimers): 'reload' | 'fallback' | 'failed' {
  try {
    iframe.contentWindow?.location?.reload(true)
    return 'reload'
  } catch (e) {
    try {
      const tmpUrl = iframe.src
      iframe.src = 'about:blank'
      const timeout = timers.setTimeout(() => {
        iframe.src = tmpUrl
        timers.clearTimeout(timeout)
      }, REFRESH_FALLBACK_DELAY_MS)
      return 'fallback'
    } catch (e2) {
      console.log(e2)
      console.log('页面刷新失败!')
      return 'failed'
    }
  }
}

/**
 * 切换全屏（旧 `toggleFullScreen`）
 *
 * **既有语义**：只有 `document.exitFullscreen` 存在时才退出；`isFull` **不会**因用户按 ESC
 * 或浏览器事件回到 false（旧实现没有 `fullscreenchange` 监听）—— 属既有缺陷，原样保留。
 *
 * @param full 当前全屏标记
 * @param doc   文档对象
 * @param ele   目标元素（旧为 `document.documentElement`）
 * @returns 切换后的全屏标记
 */
export function toggleFullscreen(full: boolean, doc: ExitFullscreenDoc, ele: object): boolean {
  if (full) {
    if (exitFullscreen(doc)) {
      return false
    }
    return true
  }
  const req = resolveFullscreenRequest(ele)
  if (req) {
    req.call(ele)
    return true
  }
  return false
}
