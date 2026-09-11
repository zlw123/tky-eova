/**
 * EovaUI / EovaTools 运行时接缝（`me` / `x` / `EovaUI`）
 *
 * 旧页面的对话框、消息提示、跨层通信与表单校验都走全局对象：
 *   · `me.layer.open(title, url, width, height, done, confirm, opts)` —— 打开 iframe 弹层
 *   · `me.layer.msg/ok/no/wa/notify/confirm/input/loading/close` —— 各级提示（**`wa` 与 `msg` 不是一回事**）
 *   · `me.cross.on/off/emit(event, payload)` —— 跨层回传
 *   · `x.validate.start(rules, data)` / `x.validate.showMsg(rules)` —— 表单校验（`start` 会**原地写回** `rule.msg`）
 *   · `EovaUI.install` —— 注册 24 个 `<ev-*>` 组件
 *
 * 这些全局由**已冻结的 legacy 制品**提供（第 102 轮取证：`legacy-runtime-contract.json`）：
 *   `eova-tools.umd.js`（→ `EovaTools`，依赖全局 `axios`）、
 *   `layui.umd.js`（→ `LayuiVue`，依赖全局 `Vue`）、
 *   `eovaui.js`（→ `EovaUI`，依赖 `EovaTools`/`Vue`/`LayuiVue`/`axios`）
 * 装配顺序与全局依赖见 `./legacy-runtime.ts`。
 *
 * ★ 为什么做成接缝而不是直接 `const {me} = EovaUI`
 *   ① legacy 制品是**冻结**的（`src/legacy/**` 只准引用、不准改），装配时点由本工程决定；
 *   ② 迁到 Vite/ESM 后这些名字不再是隐式全局 —— 直接引用会在**编译期**就断，
 *      而"少一个全局"这类问题在组件测试里极难定位，故显式化；
 *   ③ 纪律：**未装配时必须响亮失败，禁止静默降级**（静默会让"提示没弹出来"被当成用户没点，
 *      让"校验没拦住"变成"直接提交"）。
 */

/** 弹层回调 */
export type LayerCallback = (data?: unknown) => void

/** `me.layer` 面（第 102 轮按 `eovaui.js` 实际导出补齐；只声明本工程会用到的部分） */
export interface EovaLayer {
  /** 打开 iframe 弹层（宽高：===1 ⇒ 视口；<1 ⇒ 视口 × 比例；否则像素） */
  open: (
    title: string,
    url: string,
    width?: number,
    height?: number,
    done?: LayerCallback,
    confirm?: LayerCallback,
    opts?: Record<string, unknown>
  ) => unknown
  /** 普通提示（无图标） */
  msg: (message: string, done?: LayerCallback) => unknown
  /** 成功提示（icon=1） */
  ok?: (message: string, done?: LayerCallback) => unknown
  /** 失败提示（icon=2，旧实现用 `me.layer.no`，不是 `msg`） */
  no: (message: string, done?: LayerCallback) => unknown
  /** 警告提示（icon=3）—— ★ 与 `msg` 不同级；旧页面的"校验未过"走这条 */
  wa?: (message: string, done?: LayerCallback) => unknown
  /** 通知（分 info/warning 等级） */
  notify?: (title: string, content: string, level?: 'ok' | 'no' | 'wa' | 'info') => unknown
  /** 确认框（旧实现固定标题「请确认」） */
  confirm?: (message: string, cb: () => void) => unknown
  /** 输入框 */
  input?: (placeholder: string, formType: string, cb: (value: string) => void) => unknown
  /** 图标为 16 的消息（**不是**加载遮罩） */
  loading?: (message?: string) => unknown
  /** 关闭指定句柄 */
  close?: (handle: unknown) => unknown
  /** 图片浏览 */
  photos?: (imgList: unknown) => unknown
}

/** `me.cross` 面（跨层通信） */
export interface EovaCross {
  on: (event: string, cb: LayerCallback) => unknown
  off: (event: string, cb?: LayerCallback) => unknown
  /** 触发事件：子页用 `emit('eova-layer-ok_done', id)` 通知宿主关层并执行 done 回调 */
  emit: (event: string, payload?: unknown) => unknown
}

/** 校验规则项（旧 `x.validate.start` 会**原地写回** `msg`，故必须是可变对象） */
export interface EovaValidateRule {
  label: string
  rules: string[]
  msg?: string
  [k: string]: unknown
}

/** `x.validate` 面（EovaTools.ValidateTool） */
export interface EovaValidate {
  /** 逐字段校验并把错误文案写回 `rule.msg`；全部通过返回 true */
  start: (rules: Record<string, EovaValidateRule>, data: Record<string, unknown>) => boolean
  /** 汇总所有非空 `msg`（以 `<br>` 连接，按 rules 的插入顺序） */
  showMsg: (rules: Record<string, EovaValidateRule>) => string
  /** 注册自定义规则 `diy[name]` */
  addRules: (name: string, fn: (value: unknown) => unknown) => void
}

/** `EovaTools` 面（只声明本工程实际用到的部分） */
export interface EovaTools {
  validate: EovaValidate
  isEmpty: (value: unknown) => boolean
  [k: string]: unknown
}

/** `me` 的最小可用面 */
export interface EovaMe {
  layer: EovaLayer
  cross: EovaCross
  [k: string]: unknown
}

/** 测试与装配用的注入点（`me`） */
let injected: EovaMe | null = null

/** 测试与装配用的注入点（`EovaTools`） */
let injectedTools: EovaTools | null = null

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
      'EovaUI 运行时未装配：未找到全局 `me`（应由 legacy 制品 eova-tools.umd.js + eovaui.js 提供）。' +
        '此为响亮失败，不得以空实现代替 —— 否则弹层/提示会静默失效。'
    )
  }
  return me
}

/**
 * 取 `EovaTools`（`{x}`）。
 *
 * 解析顺序：注入实例 → `globalThis.EovaTools` → `globalThis.eova.x`。
 * 缺失即**抛错**：校验是"发不发请求"的闸门，静默降级会退化成"不校验直接提交"。
 *
 * @returns `EovaTools` 实例
 */
export function getEovaTools(): EovaTools {
  if (injectedTools) {
    return injectedTools
  }
  const g = globalThis as unknown as { EovaTools?: EovaTools; eova?: { x?: EovaTools } }
  const tools = g.EovaTools ?? g.eova?.x
  if (!tools || !tools.validate) {
    throw new Error(
      'EovaTools 未装配：未找到全局 `EovaTools`（应由 legacy 制品 eova-tools.umd.js 提供）。' +
        '此为响亮失败 —— 否则表单会跳过校验直接提交。'
    )
  }
  return tools
}

/**
 * 注入 `EovaTools`（判据使用）。
 *
 * @param tools `EovaTools` 实例；传 null 清除注入（恢复从全局解析）
 */
export function setEovaTools(tools: EovaTools | null): void {
  injectedTools = tools
}

/**
 * 取 `EovaUI`（组件库插件对象，含 `install` 与 `me`）。
 *
 * @returns `EovaUI` 实例
 */
export function getEovaUI(): { install: (app: unknown, opts?: unknown) => void; me?: EovaMe } {
  const g = globalThis as unknown as {
    EovaUI?: { install?: (app: unknown, opts?: unknown) => void; me?: EovaMe }
  }
  const ui = g.EovaUI
  if (!ui || typeof ui.install !== 'function') {
    throw new Error(
      'EovaUI 未装配：未找到全局 `EovaUI`（应由 legacy 制品 eovaui.js 提供，且必须先注入 Vue/axios/LayuiVue/EovaTools）。' +
        '此为响亮失败 —— 否则 <ev-*> 组件会渲染为空。'
    )
  }
  return ui as { install: (app: unknown, opts?: unknown) => void; me?: EovaMe }
}
