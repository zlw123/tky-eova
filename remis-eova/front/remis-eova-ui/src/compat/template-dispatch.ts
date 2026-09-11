/**
 * 模版页分派（第 118 轮）
 *
 * ## 它解决什么
 *
 * 旧栈里 `/app/<menu.code>` 由 `AppController#index()` **按菜单的 `template` 列**渲染不同页面：
 *
 * ```java
 * String templdate = menu.getTemplate();
 * renderEnjoy(String.format("/eova/_view/template/%s/index.html", templdate));
 * ```
 *
 * ⇒ SPA **不能"一个页面一条固定路由"**（那样就把 DB 里的选择权搬进了前端代码），
 * 必须由 `/app/:menuCode` 先取到 `menu.template`，再分派到对应组件。
 *
 * ## 取值来源与"取不到"怎么办
 *
 * `menu.template` 是**服务端插值**（渲染期 `set("menu", menu)`）⇒ 前后分离后它属于
 * **页面引导数据**（`DES-004`）：`bootstrap.menu.template`。
 * 端点未就绪时**没有**任何前端可推的等价来源（URL 上没有这个信息、`me.conf` 里也没有）
 * ⇒ 本模块返回 `source='missing'`，由调用方**响亮报错**，**不猜默认值**
 * （猜成 `table` 会让 `tree`/`tree_table` 菜单渲染出错页，而且看起来"正常"）。
 *
 * ## 迁移面
 *
 * | template | 旧实现 | 本轮状态 |
 * |---|---|---|
 * | `table` | `_view/template/table/index.html` + `index.js`（318 行） | **已迁移**（`TemplateTable.vue`） |
 * | `tree` | `_view/template/tree/index.html`(66) + `index.js`(292) | **已迁移**（`TemplateTree.vue`，第 119 轮） |
 * | `tree_table` | `_view/template/tree_table/index.html`(92) + `index.js`(211) | 未迁移（登记） |
 *
 * 未迁移的模版**不做静默降级**（不"先按 table 渲染"）：由宿主明确告知"该模版尚未迁移"，
 * 因为按错的模版渲染出来的页面**看起来是能用的**，这比报错危险得多。
 */

import type { PageBootstrap } from './page-bootstrap'

/** 已迁移的模版名（有对应 SPA 组件；组件表见 `views/template/registry.ts`） */
export const MIGRATED_TEMPLATES: readonly string[] = ['table', 'tree']

/**
 * 旧栈存在、但尚未迁移的模版名（登记在案，不得静默降级成已迁移模版）
 */
export const UNMIGRATED_TEMPLATES: readonly string[] = ['tree_table']

/** 模版名的取值来源（判据必须能分辨"取到了"与"没取到"） */
export type TemplateSource = 'bootstrap' | 'missing'

/** 分派结果 */
export interface ResolvedTemplate {
  /** 模版名（`missing` 时为空串） */
  template: string
  /** 来源 */
  source: TemplateSource
}

/**
 * 从引导数据解析模版名（`AppController#index()` 的 `menu.getTemplate()` 的 SPA 等价）。
 *
 * 语义：只认引导数据的 `menu.template`；空/缺 ⇒ `source='missing'`（**不猜**，见文件头）。
 *
 * @param bs 页面引导数据
 * @returns 模版名与其来源
 */
export function resolveTemplate(bs: PageBootstrap): ResolvedTemplate {
  const raw = bs.menu?.template
  const template = raw == null ? '' : String(raw).trim()
  if (template === '') {
    return { template: '', source: 'missing' }
  }
  return { template, source: 'bootstrap' }
}

/**
 * 该模版是否已迁移到 SPA。
 *
 * @param template 模版名
 * @returns 是否有对应组件
 */
export function isMigratedTemplate(template: string): boolean {
  return MIGRATED_TEMPLATES.includes(template)
}

/**
 * 取菜单配置对象（旧 `uzoo.page.menu_conf` = `_page/list.html` 里的 `#(menu.conf)`）。
 *
 * ★ 形态说明（取证）：`Menu.getConf()`（旧 `cn/eova/model/Menu.java:79-81`）返回 `config` 列的**字符串**，
 * 而 `_page/list.html` 用 `#(menu.conf)` **不带引号**打印 —— 即旧栈里它是**对象字面量文本**
 * （`menu_conf: {"layer_width":900}`）。故这里两种形态都接受：已是对象 ⇒ 直接用；
 * 是 JSON 串 ⇒ 解析；解析失败 ⇒ 返回空对象并**告警**（不把脏数据当配置）。
 *
 * @param bs 页面引导数据
 * @param warn 告警出口
 * @returns 菜单配置对象（无则空对象）
 */
export function menuConfOf(
  bs: PageBootstrap,
  warn: (message: string) => void = console.warn
): Record<string, unknown> {
  const raw = bs.menu?.['conf'] ?? bs.menu?.['config']
  if (raw == null || raw === '') {
    return {}
  }
  if (typeof raw === 'object' && !Array.isArray(raw)) {
    return raw as Record<string, unknown>
  }
  if (typeof raw === 'string') {
    try {
      const parsed = JSON.parse(raw) as unknown
      if (parsed != null && typeof parsed === 'object' && !Array.isArray(parsed)) {
        return parsed as Record<string, unknown>
      }
      warn(`[template] menu.conf 解析结果不是对象，已按空配置处理：${raw}`)
    } catch (e) {
      warn(`[template] menu.conf 不是合法 JSON，已按空配置处理：${(e as Error).message}`)
    }
  }
  return {}
}

/**
 * 弹层宽高（旧各模版页的 `const LW = conf.layer_width || 720, LH = conf.layer_height || <默认>`）。
 *
 * ★ 默认高**逐模版不同**（取证：`template/table/index.js:13` 是 `660`，
 * `template/tree/index.js:15` 与 `template/tree_table/index.js:16` 是 `720`）⇒ 默认值必须由调用方传入，
 * 不能在本函数里写死一个"通用默认"。
 *
 * `||` 语义原样保留（`0`、`''`、`null`、`NaN` 都落回默认值）。
 *
 * @param conf 菜单配置
 * @param defaultWidth 默认宽（旧实现是 720）
 * @param defaultHeight 默认高（按模版不同）
 * @returns `{ width, height }`（值原样透传，可能为字符串 —— 旧 `me.layer.open` 自己换算）
 */
export function layerSizeOf(
  conf: Record<string, unknown>,
  defaultWidth: number,
  defaultHeight: number
): { width: number | string; height: number | string } {
  const w = conf['layer_width'] as number | string | undefined
  const h = conf['layer_height'] as number | string | undefined
  return { width: w || defaultWidth, height: h || defaultHeight }
}
