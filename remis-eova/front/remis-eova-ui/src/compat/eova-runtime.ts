/**
 * EovaUI / EovaTools 运行时接缝（`me`）
 *
 * 旧页面的所有对话框、消息提示与跨层通信都走全局 `me`：
 *   · `me.layer.open(title, url, width, height, cb)` —— 打开 iframe 弹层（超管切换/改密/系统消息）
 *   · `me.layer.msg(...)` / `me.layer.no(...)` —— 普通/失败提示
 *   · `me.cross.on(event, cb)` —— 跨层回传（如 `eova-layer-ok_done_data`）
 *
 * `me` 由**已冻结的 legacy 资产**提供：
 *   `src/legacy/eova/lib/eova/lib/eova-tools.umd.js` + `src/legacy/eova/lib/eova/eovaui.js`
 *   （旧 `_view/index/index.html` 第 49~53 行的加载顺序；`me` 是全局变量，`EovaUI.me` 是同一对象）
 *
 * ★ 为什么做成接缝而不是直接 `const {me} = EovaUI`
 *   ① legacy 资产是**冻结**的（`src/legacy/**` 只准引用、不准改），装配时点由本工程决定；
 *   ② 迁到 Vite/ESM 后 `me` 不再是隐式全局 —— 直接引用会在**编译期**就断，
 *      而"少一个全局"这类问题在组件测试里极难定位，故显式化；
 *   ③ 纪律：**未装配时必须响亮失败，禁止静默降级**（静默会让"提示没弹出来"被当成用户没点）。
 */

/** 弹层回调 */
export type LayerCallback = (data?: unknown) => void

/** `me.layer` 面（只声明本工程实际用到的部分，多声明会掩盖真实的缺失） */
export interface EovaLayer {
  /** 打开 iframe 弹层：标题 / 内部路径 / 宽 / 高 / 关闭后回调 */
  open: (
    title: string,
    url: string,
    width: number,
    height: number,
    done?: LayerCallback
  ) => unknown
  /** 普通提示 */
  msg: (message: string, done?: LayerCallback) => unknown
  /** 失败提示（旧实现用 `me.layer.no`，不是 `msg`） */
  no: (message: string, done?: LayerCallback) => unknown
  /** 加载中提示（返回句柄，供 `close`） */
  loading?: (message?: string) => unknown
  /** 关闭指定句柄 */
  close?: (handle: unknown) => unknown
}

/** `me.cross` 面（跨层通信） */
export interface EovaCross {
  on: (event: string, cb: LayerCallback) => unknown
  off: (event: string, cb?: LayerCallback) => unknown
}

/** `me` 的最小可用面 */
export interface EovaMe {
  layer: EovaLayer
  cross: EovaCross
  [k: string]: unknown
}

/** 测试与装配用的注入点 */
let injected: EovaMe | null = null

/**
 * 注入 `me`（装配期或判据使用）。
 *
 * @param me `me` 实例；传 null 清除注入（恢复从全局解析）
 */
export function setEovaMe(me: EovaMe | null): void {
  injected = me
}

/**
 * 取 `me`。
 *
 * 解析顺序：注入实例 → `globalThis.me` → `globalThis.EovaUI.me`。
 * 三者都没有时**抛错**（不返回空实现）—— 静默降级会让"弹层没打开/提示没出现"变成无差别现象。
 *
 * @returns `me` 实例
 */
export function getEovaMe(): EovaMe {
  if (injected) {
    return injected
  }
  const g = globalThis as unknown as { me?: EovaMe; EovaUI?: { me?: EovaMe } }
  const me = g.me ?? g.EovaUI?.me
  if (!me) {
    throw new Error(
      'EovaUI 运行时未装配：未找到全局 `me`（应由 legacy 资产 eova-tools.umd.js + eovaui.js 提供）。' +
        '此为响亮失败，不得以空实现代替 —— 否则弹层/提示会静默失效。'
    )
  }
  return me
}
