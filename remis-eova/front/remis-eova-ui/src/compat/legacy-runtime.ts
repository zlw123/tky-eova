/**
 * legacy 运行时装配（第 102 轮）
 *
 * ## 为什么需要它
 *
 * 旧页面的 `app.html` 用的是 EovaUI 的组件（`<ev-input>`、`<ev-table>`…）与全局 `me`，
 * 这些都不是"可以顺手重写"的东西：`eovaui.js` 是 24 个组件 + `me` 的**已冻结制品**
 * （214,497 字节，逐字节复核在案）。按迁移纪律「禁止按功能重新设计」，
 * 正确做法是**让制品继续当运行时**，只把页面级 `app.js` 迁成 SFC/路由。
 *
 * 前提是装配阶段把制品要的全局准备好 —— 这三个 UMD 包在无 CJS/AMD 时**从 globalThis 取依赖**
 * （包体首行原文，见 `docs/.local/baseline/evidence/legacy-runtime-contract.json`）：
 *
 * | 制品 | 提供 | 依赖全局 |
 * |---|---|---|
 * | `eova/lib/eova/lib/eova-tools.umd.js` | `EovaTools` | `axios` |
 * | `eova/lib/eova/lib/layui.umd.js` | `LayuiVue` | `Vue` |
 * | `eova/lib/eova/eovaui.js` | `EovaUI` | `EovaTools`、`Vue`、`LayuiVue`、`axios` |
 *
 * ## ★ 关键取舍：Vue 由谁提供
 *
 * 旧 `index.html` 加载了 legacy 自带的 `vue.global.js`（3.5.18）。迁移后**不能**照抄这一步：
 * 本工程的 SFC 用的是打包进来的 Vue，若再让 legacy 的 `vue.global.js` 覆盖全局 `Vue`，
 * 就会出现**两个 Vue 实例** —— EovaUI 组件（全局 Vue）与本工程组件（打包 Vue）无法互相
 * `resolveComponent`/`provide`/`inject`，表现为组件渲染为空且难定位。
 *
 * 故本轮口径：**由本工程自带的 `vue` / `axios`（ESM）注入为全局**，再按序加载三个 legacy 制品；
 * legacy 的 `vue.global.js` 与 `axios.min.js` **不加载**。这是**已声明适配**：
 * 组件运行时版本由本工程决定（legacy 为 3.5.18），须在真实浏览器实跑中核（当前 `not executed`）。
 * 保真度最高的替代方案（整 App 换成 legacy 3.5.18，vite alias `vue` → 重导出 `globalThis.Vue`）
 * 已登记为备选，本轮不采用 —— 它会改动全工程模块解析（含 vitest / vue-tsc / LSP）。
 *
 * ## 纪律
 *
 * - **顺序不可换**：后一个制品在前一个执行时就要读全局。
 * - **幂等**：已装配则直接返回，不重复插入 script。
 * - **响亮失败**：加载出错、或加载后必需全局仍缺失，一律抛错 —— 不得"继续跑下去"。
 *   静默降级在这里的后果是"组件渲染为空/弹层不出现"，极难从现象反推根因。
 */

import axios from 'axios'
import * as Vue from 'vue'
import type { EovaMe, EovaTools } from './eova-runtime'

/** 需要按序加载的 legacy 制品（URL 用**旧原路径** `/eova/lib/**`，见工程 README 契约纪律 ②） */
export const LEGACY_RUNTIME_SCRIPTS: readonly string[] = [
  '/eova/lib/eova/lib/eova-tools.umd.js',
  '/eova/lib/eova/lib/layui.umd.js',
  '/eova/lib/eova/eovaui.js',
  // ★ r249 修正（真浏览器实测）：这两个页面脚本**必须在 vendor 之后执行**。
  //   `eova.template.js` 第 4/8 行是 `const x = EovaTools.x` / `const me = EovaUI.me` ——
  //   它在 index.html 里以静态 `<script>` 引入时，会早于本装配器加载 vendor ⇒ 实测报
  //   `ReferenceError: EovaTools is not defined`（症状：首页"请求异常"，而构建/单测/闸门全绿）。
  //   旧栈顺序本来就是"vendor 在前、页面脚本在后"（服务端 partial），这里按同一顺序装配。
  '/eova/ui/meta/eova.meta.js',
  '/eova/_view/template/eova.template.js',
  // ★★ r304 追加（用户实测反馈）：`_eova/assets/eova.ui.ext.js` 是**工程级扩展资产**，
  //   它注册制品里"单元格渲染器"注册表的两个条目 —— `eova-table-cell`（表格单元格）与
  //   `eova-table-island`（表底部统计）。制品 `EvTableCell` 的渲染是
  //   `let s = EovaUI.me.render.get(name); if (s) { ... } ` ⇒ **查不到就什么都不渲染**，
  //   而表头/行号/分页都不走注册表 ⇒ 症状是"接口有返回、分页也对，但每一个数据格都是空的"
  //   （真浏览器实测：新栈每页 `EovaUI.me.render.get('eova-table-cell')` = 无 ⇒
  //     `meta_product` 210/210 个数据格全空；旧栈同页 177/210 有内容）。
  //   旧栈是页面 `<head>` 里的经典 `<script src="/_eova/assets/eova.ui.ext.js">`（实测 200），
  //   排在 `eova.template.js` 之后 ⇒ 这里按同一相对顺序追加。
  //   依赖：脚本顶部即读 `Vue`/`EovaUI.me`/`EovaTools`，故**必须在 vendor 与页面脚本之后**。
  '/_eova/assets/eova.ui.ext.js'
]

/**
 * **以 ES 模块方式加载**的资产前缀（`_eova/**` = 工程级扩展资源）。
 *
 * ★★ 为什么必须模块化（第 304 轮实测，不是风格偏好）：
 * 旧栈是 **MPA** —— 主框架与每个页面在**不同 realm**（页面在 iframe 里），于是
 * `eova.template.js` 的顶层 `const me = EovaUI.me` 与扩展资产的 `const {me} = EovaUI`
 * 各自只声明一次、互不冲突。
 * SPA 把所有页面并进**同一个 realm**，而**经典脚本的顶层词法声明是跨脚本共享的**
 * ⇒ 第二个声明直接抛 `SyntaxError: Identifier 'me' has already been declared`，
 * 扩展资产**整份不执行**（实测：脚本 200 已下载，但渲染器注册表仍是空的）。
 * ES 模块自带模块作用域 ⇒ 顶层 `const` 隔离，且仍可读全局 `Vue`/`EovaUI`/`EovaTools`。
 */
export const MODULE_SCRIPT_PREFIX = '/_eova/'

/**
 * 表格单元格渲染器的**注册键**（制品 `EvTableCell` 用它查渲染器；查不到就什么都不渲染）。
 *
 * 见 {@link assertCellRendererRegistered}。
 */
export const CELL_RENDERER_KEY = 'eova-table-cell'

/** 每个制品加载后必须出现的全局名（用于"响亮失败"校验） */
export const SCRIPT_EXPECTED_GLOBAL: Readonly<Record<string, string>> = {
  '/eova/lib/eova/lib/eova-tools.umd.js': 'EovaTools',
  '/eova/lib/eova/lib/layui.umd.js': 'LayuiVue',
  '/eova/lib/eova/eovaui.js': 'EovaUI',
  // 冻结资产 `eova.meta.js` 定义 `window.uzoo`（第 107 轮取证）⇒ 同样逐步校验，缺了就响亮抛
  '/eova/ui/meta/eova.meta.js': 'uzoo'
}

/** 装配所需的宿主全局（本工程自带，注入给 legacy 制品用） */
export interface RuntimeGlobals {
  Vue: unknown
  axios: unknown
}

/** 装配依赖注入点（判据用：不碰真实 DOM/网络即可验证顺序与失败语义） */
export interface LoadOptions {
  /**
   * 加载单个脚本（默认走 DOM `<script>`）
   *
   * @param url 脚本 URL
   */
  loadScript?: (url: string) => Promise<void>
  /** 宿主全局（默认取本工程的 vue / axios） */
  globals?: RuntimeGlobals
  /** 取全局对象（默认 `globalThis`） */
  target?: Record<string, unknown>
}

/**
 * 默认的脚本加载：插入 `<script src>`，`onload`/`onerror` 决定 resolve / reject。
 *
 * 用 `<script>` 而不是 `import()`：这些是 UMD 制品，**必须在全局作用域执行**才能把
 * `EovaTools`/`LayuiVue`/`EovaUI` 挂到 `globalThis` 上（ESM 导入不会）。
 *
 * @param url 脚本 URL
 * @returns 加载完成的 Promise
 */
function defaultLoadScript(url: string): Promise<void> {
  return new Promise<void>((resolve, reject) => {
    if (typeof document === 'undefined') {
      reject(new Error(`[legacy-runtime] 无 document，无法加载 ${url}`))
      return
    }
    const el = document.createElement('script')
    el.src = url
    el.async = false
    // ★ `_eova/**`（工程级扩展资产）必须以模块加载：经典脚本的顶层词法声明跨脚本共享，
    //   而它和页面脚本都声明了 `const me` ⇒ 经典方式下会整份 SyntaxError 而**静默失效**
    //   （脚本 200、注册表却空）。详见 MODULE_SCRIPT_PREFIX 的说明。
    if (url.startsWith(MODULE_SCRIPT_PREFIX)) {
      el.type = 'module'
    }
    el.onload = () => resolve()
    el.onerror = () => reject(new Error(`[legacy-runtime] 脚本加载失败：${url}`))
    document.head.appendChild(el)
  })
}

/**
 * 校验**表格单元格渲染器**已注册（装配后的"响亮失败"闸门）。
 *
 * <p><b>为什么这必须是硬校验</b>：制品 `EvTableCell` 的渲染逻辑是
 * {@code const s = EovaUI.me.render.get(name); if (s) {...}} —— 查不到就渲染成空。
 * 而表头/行号/分页**不走**注册表，于是缺渲染器时的症状是：
 * <b>接口有返回、分页正确、表头齐全，但每一个数据单元格都是空的</b>
 * （第 304 轮用户实测反馈；实测 `meta_product` 210/210 个数据格全空）。
 * 这种"看起来一切正常"的静默失效，必须在装配期就炸掉，而不是等用户来看见空表格。</p>
 *
 * @param target 全局对象（判据可注入）
 * @throws Error 渲染器未注册时
 */
export function assertCellRendererRegistered(
  target: Record<string, unknown> = globalThis as never
): void {
  const ui = target['EovaUI'] as { me?: { render?: { get?: (key: string) => unknown } } } | undefined
  const renderer = ui?.me?.render?.get?.(CELL_RENDERER_KEY)
  if (!renderer) {
    throw new Error(
      `[legacy-runtime] 表格单元格渲染器 \`${CELL_RENDERER_KEY}\` 未注册 —— ` +
        `清单里的 \`/_eova/assets/eova.ui.ext.js\` 未生效（它必须以 **ES 模块**方式加载：` +
        `经典脚本会因顶层 \`const me\` 与页面脚本冲突而整份不执行）。` +
        '症状是"列表页有数据但单元格全空"，故此处响亮抛出。'
    )
  }
}

/**
 * 判定 legacy 运行时是否已装配（用于幂等）。
 *
 * @param target 全局对象
 * @returns 是否三个制品都已在位
 */
export function isLegacyRuntimeLoaded(target: Record<string, unknown> = globalThis as never): boolean {
  return LEGACY_RUNTIME_SCRIPTS.every((url) => {
    const name = SCRIPT_EXPECTED_GLOBAL[url]
    return name ? target[name] != null : true
  })
}

/**
 * 装配 legacy 运行时（幂等）。
 *
 * 步骤：① 注入宿主全局 `Vue`/`axios`；② 按 `LEGACY_RUNTIME_SCRIPTS` 顺序逐个加载；
 * ③ 每步之后校验该制品应提供的全局确实出现，否则抛错。
 *
 * @param options 注入点（判据用）
 * @returns 装配完成（或已装配）
 */
export async function loadLegacyRuntime(options: LoadOptions = {}): Promise<void> {
  const target = (options.target ?? (globalThis as unknown as Record<string, unknown>)) as Record<
    string,
    unknown
  >
  if (isLegacyRuntimeLoaded(target)) {
    return
  }
  const globals = options.globals ?? { Vue, axios }
  if (!globals.Vue || !globals.axios) {
    throw new Error('[legacy-runtime] 宿主全局缺失：需要 `Vue` 与 `axios` 才能装配 legacy 制品')
  }
  const load = options.loadScript ?? defaultLoadScript

  // ① 注入全局：legacy 制品在执行时会立刻读它们
  target['Vue'] = globals.Vue
  target['axios'] = globals.axios

  // ②③ 按序加载 + 逐步校验
  for (const url of LEGACY_RUNTIME_SCRIPTS) {
    const name = SCRIPT_EXPECTED_GLOBAL[url]
    if (name && target[name] != null) {
      // 该制品此前已装配（例如局部热更新后重入）⇒ 跳过，但仍保持顺序语义
      continue
    }
    await load(url)
    if (name && target[name] == null) {
      throw new Error(
        `[legacy-runtime] ${url} 加载完成但全局 \`${name}\` 仍不存在 —— 装配失败（不静默继续）`
      )
    }
  }

  if (!isLegacyRuntimeLoaded(target)) {
    throw new Error('[legacy-runtime] 装配后仍缺少必需全局（EovaTools/LayuiVue/EovaUI）')
  }

  // ④ 渲染器闸门：缺了它列表页会"有数据但格子全空"（静默失效，必须响亮抛）
  assertCellRendererRegistered(target)
}

/**
 * 从全局取已装配的 `me`（`EovaUI.me`）。
 *
 * 与 `eova-runtime.ts` 的 `getEovaMe()` 是**同一份数据的两条读法**：此处只做装配结果的自检，
 * 页面代码请用 `getEovaMe()`（它支持注入替身，便于判据）。
 *
 * @returns `me`；未装配时 null
 */
export function peekEovaMe(): EovaMe | null {
  const g = globalThis as unknown as { me?: EovaMe; EovaUI?: { me?: EovaMe } }
  return g.me ?? g.EovaUI?.me ?? null
}

/**
 * 从全局取已装配的 `EovaTools`。
 *
 * @returns `EovaTools`；未装配时 null
 */
export function peekEovaTools(): EovaTools | null {
  const g = globalThis as unknown as { EovaTools?: EovaTools }
  return g.EovaTools ?? null
}
