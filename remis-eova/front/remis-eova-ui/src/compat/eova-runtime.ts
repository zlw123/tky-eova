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
  /**
   * 打开 iframe 弹层（宽高：===1 ⇒ 视口；<1 ⇒ 视口 × 比例；否则像素）
   *
   * ★ 第 118 轮把宽高类型从 `number` 放宽到 `number | string`：旧各模版页把它们**原样透传**
   * （`const LW = conf.layer_width || 720`，`conf` 来自菜单配置 JSON），
   * 而 `me.layer.open` 内部是 `< 1` / `=== 1` 的比较（字符串会被 JS 强制转数值）。
   * 写死 `number` 会逼调用方加类型断言 —— 那会把"类型不准"藏进类型系统里。
   */
  open: (
    title: string,
    url: string,
    width?: number | string,
    height?: number | string,
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

/** `x.dom` 面（EovaTools.DomTool） */
export interface EovaDom {
  /**
   * 取视口尺寸（制品实现：`document.documentElement.clientWidth/clientHeight`）
   *
   * @param target 目标窗口（默认 `window`）
   */
  getViewSize: (target?: Window) => { width: number; height: number }
  [k: string]: unknown
}

/** `x.json` 面（EovaTools.JsonTool） */
export interface EovaJson {
  /** `JSON.parse` 的包装：解析失败时 `console.error` 并返回 **null**（不是抛错） */
  toObj: (text: string) => unknown
  /** `JSON.stringify(v, null, space)`（默认 `space = 0`） */
  toStr: (value: unknown, space?: number) => string | undefined
}

/** `x.str` 面（EovaTools.StrTool） */
export interface EovaStr {
  /**
   * 模板替换：把 `{{key}}` 换成参数值（制品实现见 `eova-tools.umd.js` 的 `StrTool.template`）。
   *
   * ★ 缺键时的语义（取证）：`t[key]` 为 null/undefined 时**保留原样 `{{key}}`**，不替换成空串。
   */
  template: (template: string, params: Record<string, unknown>) => string
  [k: string]: unknown
}

/** `x.axios` 面（EovaTools.AxiosTool） */
export interface EovaAxios {
  /**
   * 下载（导出用）：`download(url, data, fileName, type)`。
   *
   * 取证（第 118 轮，从冻结制品 `eova-tools.umd.js` 抽取）：
   * `class As { download = async (n, t, e, i) => { … en.post(n, t, { responseType: 'blob',
   * headers: { 'Content-Type': … } }) … a.download = e; a.click() … } }`
   * —— 即 4 个形参 `(url, data, fileName, type)`，POST 拿 blob 后造 `<a download>` 点名下载。
   * 调用点：旧 `template/table/index.js:182`（`onExport`）。
   */
  download: (
    url: string,
    data: unknown,
    fileName: string,
    /** 导出类型（'xlsx'/'csv'）—— 旧栈可缺省：内置按钮走方法表时是无参调用 */
    type?: string
  ) => Promise<unknown>
}

/** `EovaTools` 面（只声明本工程实际用到的部分） */
export interface EovaTools {
  validate: EovaValidate
  isEmpty: (value: unknown) => boolean
  dom: EovaDom
  json: EovaJson
  str: EovaStr
  /** 下载工具（导出）—— 见 `EovaAxios.download` 的取证 */
  axios: EovaAxios
  /** 打日志（制品实现即 `console.log`） */
  log: (message: unknown) => void
  [k: string]: unknown
}

/** `me` 的最小可用面 */
/**
 * `me.urls` 面（制品的 URL 工具）
 *
 * 取证（第 119 轮）：URL 表**硬编码在冻结制品 `eovaui.js` 里**（扁平键，如
 * `form_update:"/api/form/update/{{object_code}}"`、`form_detail:…`），
 * 组件与页面通过 `me.urls.url(key, params)` 取用（旧 `template/tree/index.js:139`
 * 即 `me.urls.url('form_update', props)`）。**本工程不重建这张表**（r103 已定：
 * `me.urls` 由制品自带，本工程零工作）—— 只声明类型，调用仍走制品实现。
 */
export interface EovaUrls {
  /** 按键取 URL（`{{key}}` 占位由制品替换；参数缺省时行为与制品一致） */
  url: (key: string, params?: Record<string, unknown>) => string
  [k: string]: unknown
}

export interface EovaMe {
  layer: EovaLayer
  cross: EovaCross
  /** 制品自带的 URL 表工具（见 `EovaUrls`） */
  urls: EovaUrls
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
 * 从"全局形状"解析出 `EovaTools` 工具对象（**纯函数**，判据直接钉形状表）。
 *
 * ★ 为什么必须单独抽出来（第 303 轮实测出来的真缺陷）：
 * 制品是 **UMD 命名空间**，实测两端形状完全一致：
 * ```js
 * window.EovaTools = { EovaTools, ValidateTool, default, eova, x }   // ← 命名空间
 * window.EovaTools.x.validate  // → object  ← ★ 真正的工具对象在这里
 * ```
 * 旧页脚本取的正是 `const {x} = EovaTools`（如 `_view/template/form/add/index.js:3`）。
 * 而本接缝原先只认 `globalThis.EovaTools`（整体）与 `globalThis.eova?.x`
 * ⇒ **在真浏览器里从未解析成功过**（命名空间上没有 `validate`）。
 *
 * 为什么所有判据都没发现：① 组件测试用 `setEovaTools(替身)` **绕过了**真实解析；
 * ② S5 真浏览器判据只查 `typeof EovaTools === 'object'`（**存在性**，不查**形状**）。
 * ⇒ 只有会调用 `getEovaTools()` 的页面（`TemplateTable` 族）才暴露：`doResize` 抛错导致
 * 表格高度算不出、工具栏按钮（新增/删除/导出）点击即抛。**修前真浏览器实测每页 2 个未捕获异常。**
 *
 * 优先级（`x` 是命名空间里那个真对象，故它优先于整体）：
 * ① `globalThis.EovaTools.x`（实测形状）→ ② `globalThis.eova.x` → ③ `globalThis.EovaTools`
 * （**仅当它自身带 `validate`**，兼容"直接挂工具对象"的其它装配方式）。
 *
 * @param g 全局形状（判据可注入）
 * @returns 工具对象；解析不出返回 null
 */
export function resolveEovaTools(g: {
  // 真命名空间还有 `EovaTools/ValidateTool/default/eova` 等成员 ⇒ 用 Record 放行额外键
  EovaTools?: Record<string, unknown> & { x?: unknown; validate?: unknown }
  eova?: { x?: unknown }
}): EovaTools | null {
  const candidates: unknown[] = [g.EovaTools?.x, g.eova?.x, g.EovaTools]
  for (const c of candidates) {
    if (c != null && typeof (c as EovaTools).validate !== 'undefined') {
      return c as EovaTools
    }
  }
  return null
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
  const g = globalThis as unknown as {
    EovaTools?: EovaTools & { x?: EovaTools }
    eova?: { x?: EovaTools }
  }
  const tools = resolveEovaTools(g)
  if (!tools) {
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
