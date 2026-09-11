/**
 * 按钮脚本加载器（第 115 轮）—— 旧栈 `template/{table,tree,tree_table}/index.html` 里
 * `<script src="#(btn.ui)">` 注入的 SPA 等价物。
 *
 * ## 取证（`docs/.local/baseline/evidence/button-mechanism-contract.json`）
 *
 * 纪律①「按钮 HTML 由后端生成、前端 `v-html` 渲染」的**完整链路是三层**：
 *
 * | 层 | 位置 | 事实 |
 * |---|---|---|
 * | ① 渲染 | `_block/toolbar.html:3-17` | 按 `btn.is_base` 二分支：**`is_base=1`** ⇒ `@click="<event>()"`（Vue 绑定到**页面 setup 的方法**）；**`is_base=0`** ⇒ `onclick="handlerButtonEvent('<event>')"` |
 * | ② 派发 | `_view/template/eova.template.js:15-24`（**冻结资产**） | `handlerButtonEvent(event)` 检查 **`window.hasOwnProperty(event)`**，命中则 `window[event]()`，否则弹提示「function x() 无法调用…」（注释原文：**如方法不存在会导致白屏**） |
 * | ③ 注入 | `template/{table,tree,tree_table}/index.html` | `#if(btn.ui && btn.ui.contains(".js"))` ⇒ `<script src="#(btn.ui)">`；注释原文「**Vue模版内无法引入script**」⇒ 必须在模板之外注入 |
 *
 * ## `btn.ui` 的真实语义（**订正**第 105 轮的表述）
 *
 * 它**不是**"HTML 片段或 `.js` 路径"，而是：
 * - **`.js` 路径** ⇒ 唯一被读取的分支（本模块负责加载）；
 * - **内置标记**（`'query'`、`'#'`、NULL，以及 `Button.FUN_*_UI` 写入的
 *   `/eova/widget/form/btn/{add,update,delete,detail}.html`、
 *   `/eova/template/{single,singletree}/btn/{import,add,update}.html`）⇒ **不被读取**，
 *   只用于 SQL 筛选（`ui = 'query'` / `ui <> 'query'`）；**那些 html 文件在旧树里根本不存在**。
 *
 * ⇒ 本模块**只处理 `.js`**，其余一律**原样跳过**（不猜测、不加载）。
 *
 * ## 为什么必须在页面挂载后动态加载（而不是 import）
 *
 * 待加载的脚本是**经典脚本**（classic script），它们的处理函数是**全局**的
 * （派发器要求挂在 `window` 上）；用 ESM `import()` 不会产生全局 ⇒ 派发器找不到函数。
 * 故这里与旧栈一致：**插 `<script src>`**。
 *
 * ## 纪律
 *
 * - **幂等**：同一 URL 不重复插入（旧栈每次渲染都会插，本模块去重并说明原因）。
 * - **失败不中断**：旧栈 404 只是控制台报错、页面继续 ⇒ 本模块同样继续，
 *   但**显式告警并点名 URL 与按钮**（可诊断性增强，属已声明适配）。
 *   ★ 已取证的既有数据缺陷：`eova_button.ui` 里有 2 条指向**不存在**的脚本
 *     （`/eova/_view/meta/override/btn.js`、`/eova/_view/meta/syncnew/btn.js`）⇒ 必然 404。
 * - **顺序**：按 `btnList` 顺序加载（旧栈的 `<script src>` 也是按序遍历发出的）。
 */

/** 按钮的最小面（本模块只关心 `ui` 与用于告警的标识） */
export interface ButtonWithUi {
  id?: number | string
  name?: string
  ui?: unknown
  [k: string]: unknown
}

/** 加载依赖注入点（判据用） */
export interface LoadButtonScriptsOptions {
  /**
   * 加载单个脚本（默认插 `<script src>`）
   *
   * @param url 脚本 URL
   */
  loadScript?: (url: string) => Promise<void>
  /** 告警出口 */
  warn?: (message: string) => void
}

/** 已加载过的脚本 URL（模块级去重） */
const loaded = new Set<string>()

/** 重置去重表（判据用） */
export function resetLoadedButtonScripts(): void {
  loaded.clear()
}

/**
 * 默认加载方式：插入**经典** `<script src>`（不加 `type="module"`，与旧栈一致）。
 *
 * @param url 脚本 URL
 * @returns 加载完成/失败的 Promise（失败即 reject，由调用方决定告警）
 */
function defaultLoadScript(url: string): Promise<void> {
  return new Promise<void>((resolve, reject) => {
    if (typeof document === 'undefined') {
      reject(new Error(`[button-scripts] 无 document，无法加载 ${url}`))
      return
    }
    const el = document.createElement('script')
    el.src = url
    // ★ 不设 type="module"：脚本需以经典脚本执行，其声明才会成为全局（派发器要求）
    el.async = false
    el.onload = () => resolve()
    el.onerror = () => reject(new Error(`[button-scripts] 脚本加载失败：${url}`))
    document.head.appendChild(el)
  })
}

/**
 * 从 `btnList` 中筛出需要加载的脚本 URL（**唯一判据**：`ui` **包含 `.js`** ——
 * 与旧栈 `btn.ui.contains(".js")` 逐字同义；
 * ★ **不是** "以 `.js` 结尾"：带查询串的写法（`…/btn.js?v=1.0`）在旧栈**会被加载**）。
 *
 * @param btnList 按钮列表
 * @returns 去重后的脚本 URL（保持首次出现顺序）
 */
export function collectButtonScriptUrls(btnList: ButtonWithUi[] | null | undefined): string[] {
  if (!Array.isArray(btnList)) {
    return []
  }
  const out: string[] = []
  for (const b of btnList) {
    const ui = b?.ui
    if (typeof ui === 'string' && ui.includes('.js')) {
      if (!out.includes(ui)) {
        out.push(ui)
      }
    }
  }
  return out
}

/**
 * 按 `btnList` 加载自定义按钮脚本（旧栈 `<script src>` 注入的等价物）。
 *
 * @param btnList 按钮列表（来自引导数据的 `btnList`）
 * @param options 注入点
 * @returns 实际发起加载的 URL 列表
 */
export async function loadButtonScripts(
  btnList: ButtonWithUi[] | null | undefined,
  options: LoadButtonScriptsOptions = {}
): Promise<string[]> {
  const warn = options.warn ?? ((m: string) => console.warn(m))
  const load = options.loadScript ?? defaultLoadScript
  const urls = collectButtonScriptUrls(btnList)
  const fired: string[] = []
  for (const url of urls) {
    if (loaded.has(url)) {
      continue
    }
    loaded.add(url)
    fired.push(url)
    try {
      await load(url)
    } catch (e) {
      // 旧栈 404 只报错不中断 ⇒ 这里同样继续，但把 URL 与按钮点名
      const owners = (btnList ?? [])
        .filter((b) => b?.ui === url)
        .map((b) => `${String(b.name ?? '')}#${String(b.id ?? '')}`)
        .join('、')
      warn(
        `[button-scripts] 按钮脚本加载失败：${url}（按钮：${owners || '未知'}）。` +
          `旧栈此处同样是 404（既有数据缺陷），页面继续。${(e as Error).message}`
      )
    }
  }
  return fired
}
