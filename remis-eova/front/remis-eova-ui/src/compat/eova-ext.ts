/**
 * `uzoo` 扩展机制接缝（第 107 轮）
 *
 * ## 它是什么
 *
 * 旧页面在 `<script>` 里往全局 `uzoo` 写"页面配置"，再由页面 JS 读它；
 * 同时别的脚本可以往 `uzoo.vue.*` 注册**扩展钩子**，页面在固定时点回调它们。
 *
 * 取证（第 107 轮）：`uzoo` 由**已冻结资产**定义 —— `eova/ui/meta/eova.meta.js`（264 字节）：
 * ```js
 * window.uzoo = { page: {}, vue: {}, app: {} }
 * ```
 * 它由服务端 partial `_block/meta.html:6` 加载（`_page/form.html`/`list.html`/`list1.html` 都 include 它），
 * 而 `_page/meta.html` 里紧接着还定义 `window.urls`。**分离后这两样都没有来源**：
 * `window.urls` 已在 r103 解决；`uzoo` 由本模块 + 运行时装配解决。
 *
 * ## 为什么单独做接缝（而不是让页面直接写 `uzoo`）
 *
 * ① 迁到 Vite/ESM 后 `uzoo` 不再是隐式全局 —— 直接引用会在**编译期**断；
 * ② 它承载的是**扩展点**（`uzoo.vue.*`），而扩展点最怕"**静默不生效**"：
 *    旧实现的守则是 `typeof uzoo.vue.setup === 'function'` 才调用（**存在才调**），
 *    本模块把这条守则固化，并让"钩子存在但抛错"这类问题**响亮暴露**（不吞异常）；
 * ③ 用到的页面很多：实测 `uzoo.vue`/`me.vue.` 出现在 **17 个文件**里，覆盖
 *    `button/add`、`index/login`、`menu/add`、`meta/{field,import,reorder}`、
 *    `template/form/{add,update,detail}`、`template/{table,tree,tree_table}` 等入口页。
 *
 * ## ★ 与 `me.vue`（制品自带）的分工
 *
 * `me.vue`（`ul`）**由 legacy 制品提供**（`eovaui.js` 的 `K.vue`），本工程**零工作**：
 * `created(app)` = `app.use(EovaUI)` + `createAfter`；`app(code, def)` 注册自定义 app；
 * `component(name, url)` 注册异步组件；`mount(app, code, el)` 在有自定义定义时并发加载
 * script/components/template 后再挂载，**没有定义时直接 `app.mount(el)`**（这就是普通页面照常工作的原因）。
 *
 * ⚠️ **待决（不在本模块解决）**：`me.vue.app(code, …)` 这套"自定义 app"机制是**围绕
 * `createApp` + `mount`** 设计的，而 SPA 的路由页不做 `createApp`。
 * 因此"自定义表单(元对象编码)干预新增/修改/查看"（`eova.vue.config.js` 里的 `vue.app('meta_hotel', …)`）
 * **在 SPA 下的等价形态需要显式口径** —— 登记为待决项，不擅自发明。
 */

/** `uzoo.vue` 上已取证的钩子名（页面在固定时点回调） */
export type UzooVueHook = 'mountBefore' | 'setup' | 'onReady' | 'useTemplateRef' | 'onSubmit'

/** 扩展钩子可能注册在 `uzoo.vue` 上的函数 */
type AnyHook = (...args: unknown[]) => unknown

/** `uzoo` 的形状（与冻结资产 `eova.meta.js` 一致） */
export interface Uzoo {
  /** 页面配置（旧页面在 `<script>` 里写，如 `uzoo.page.code = '#(menu.code)'`） */
  page: Record<string, unknown>
  /** 扩展钩子注册面 */
  vue: Record<string, unknown>
  /** 页面 setup 返回对象的挂载点（旧页面 `return uzoo.app = {...}`） */
  app: Record<string, unknown>
}

/** 判定一个值是否是可调用的钩子 */
function isHook(v: unknown): v is AnyHook {
  return typeof v === 'function'
}

/**
 * 取全局 `uzoo`。
 *
 * 与 `me`/`EovaTools` 的接缝不同：`uzoo` **允许缺失**（它的三个成员本就是空对象起步，
 * 且大量页面完全不碰它）⇒ 缺失时返回一个**惰性创建**的空结构并**告警一次**，
 * 而不是抛错。理由：抛错会让"没装扩展机制"这种**合法状态**变成页面崩溃；
 * 但**静默**又会让"钩子没生效"无从诊断 ⇒ 取中道：可用 + 一次性告警。
 *
 * @param target 目标全局对象（默认 `globalThis`）
 * @param warn 告警出口
 * @returns `uzoo` 实例
 */
export function getUzoo(
  target: Record<string, unknown> = globalThis as never,
  warn: (m: string) => void = console.warn
): Uzoo {
  const existing = target['uzoo'] as Uzoo | undefined
  if (existing && typeof existing === 'object') {
    return existing
  }
  if (!warnedMissing) {
    warnedMissing = true
    warn(
      '[eova-ext] 未找到全局 `uzoo`（应由冻结资产 `eova/ui/meta/eova.meta.js` 提供）。' +
        '已惰性创建空结构以保证页面可用 —— 但若本应注册的扩展钩子（`uzoo.vue.*`）没生效，' +
        '原因多半在此。'
    )
  }
  const created: Uzoo = { page: {}, vue: {}, app: {} }
  target['uzoo'] = created
  return created
}

/** 是否已就 "uzoo 缺失" 告警过（避免每次调用都刷屏） */
let warnedMissing = false

/**
 * 重置告警状态（判据用）。
 */
export function resetUzooWarning(): void {
  warnedMissing = false
}

/**
 * 调用 `uzoo.vue` 上的扩展钩子（**存在才调**，与旧实现同一条守则）。
 *
 * - 钩子未注册 ⇒ 什么也不做（旧实现即如此：`uzoo.vue` 起步为空对象）；
 * - 钩子已注册但**抛错** ⇒ **原样上抛**（不吞）：静默吞掉会让"扩展失效"变成无差别现象；
 * - 返回值原样返回，便于 `setup` 这类需要取返回值的钩子。
 *
 * @param name 钩子名
 * @param args 传给钩子的参数
 * @param target 目标全局对象
 * @returns 钩子返回值；未注册时返回 undefined
 */
export function callUzooHook(
  name: UzooVueHook,
  args: unknown[] = [],
  target: Record<string, unknown> = globalThis as never
): unknown {
  const uzoo = getUzoo(target)
  const hook = uzoo.vue[name]
  if (!isHook(hook)) {
    return undefined
  }
  return hook(...args)
}

/**
 * 设置 `uzoo.page` 的一项（旧页面在 `<script>` 里就是这么做：`uzoo.page.code = '#(object.code)'`）。
 *
 * 语义细节（与旧实现一致）：**同名后写覆盖前写**（普通对象赋值）；
 * 且 `undefined` 也会被写入（旧实现没有过滤）。
 *
 * @param key 键
 * @param value 值
 * @param target 目标全局对象
 */
export function setUzooPage(
  key: string,
  value: unknown,
  target: Record<string, unknown> = globalThis as never
): void {
  getUzoo(target).page[key] = value
}

/**
 * 取 `uzoo.page`（页面配置的只读视图）。
 *
 * @param target 目标全局对象
 * @returns 页面配置
 */
export function getUzooPage(target: Record<string, unknown> = globalThis as never): Record<string, unknown> {
  return getUzoo(target).page
}

/**
 * 把页面 setup 暴露的对象挂到 `uzoo.app`（旧页面结尾的 `return uzoo.app = {…}`）。
 *
 * 语义（与旧实现一致）：**整体替换**，不是合并 —— 旧实现就是一次赋值，
 * 而 `uzoo.app` 的初值是冻结资产 `eova.meta.js` 里的 `{}`。
 *
 * ★ 为什么必须保留这个全局点：外部脚本（演示工程的 `eova.vue.config.js` 与自定义按钮脚本）
 * 通过 `uzoo.app.data.value.xxx` 读写页面数据；不挂就等于**扩展机制静默失效**
 * （r107 已因"扩展点漏调"抓到过一次真实回归）。
 *
 * ⚠️ 待决项（不在本函数解决）：旧栈的 `me.vue.mount(app, code)` / `me.vue.app(code, def)`
 * 是围绕 `createApp` + `mount` 的"自定义 app"机制，而 SPA 路由页不做 `createApp`
 * ⇒ 其等价形态需要显式口径（见 `eova-ext.ts` 文件头与 DES-002-R4 的待用户口径清单）。
 *
 * @param app setup 返回的对象
 * @param target 目标全局对象
 */
export function setUzooApp(
  app: Record<string, unknown>,
  target: Record<string, unknown> = globalThis as never
): void {
  getUzoo(target).app = app
}

/**
 * 取 `uzoo.app`（页面 setup 暴露的对象）。
 *
 * @param target 目标全局对象
 * @returns 页面暴露对象
 */
export function getUzooApp(target: Record<string, unknown> = globalThis as never): Record<string, unknown> {
  return getUzoo(target).app
}
