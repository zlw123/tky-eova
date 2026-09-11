/**
 * 页面级 URL 表（旧 `window.urls`）—— 逐字固化 + 挂回全局（第 103 轮）
 *
 * ## 为什么需要它（取证见 `docs/DES-003-R1-ui-runtime-config-design.md`）
 *
 * 旧栈里有**两张互不相同**的 URL 表，别再混为一谈：
 *
 * | 表 | 定义位置 | 键空间 | 谁在用 |
 * |---|---|---|---|
 * | `me.urls`（`il`） | **硬编码在制品 `eovaui.js` 内**（23 键） | **扁平**：`meta_table`、`form_data`、`table_query`、`widget_data`、`tree`、`file_upload`… | 组件内部：`K.urls.url("table_query", params)` |
 * | `window.urls` | **页面内联脚本**：`_view/_block/meta.html:9-31` | **两层**：`meta.*`、`form.*` | 模板页直接读：`x.str.template(urls.form.delete, props)` |
 *
 * ⇒ `me.urls` 由制品自带，**本工程零工作**；`window.urls` 无来源，必须由本模块提供。
 *
 * ★ 两张表**不得合并**：键空间与用途都不同，合并即改变契约。
 *
 * ## 消费点（取证）
 *
 * - `eova/_view/template/table/index.js:107,135` → `urls.form.delete` / `urls.form.hide`
 * - `eova/_view/template/tree/index.js:216` → `urls.form.delete`
 * - `eova/_view/template/tree_table/index.js:123,151` → `urls.form.delete` / `urls.form.hide`
 * - `eova/_view/meta/edit/app.js:51` → `urls.form.delete`
 *
 * 读法一律是 `x.str.template(表串, 参数对象)`（`x` 来自 EovaTools 制品），
 * 故本模块**只提供数据**，不重新实现模板替换（那会构成按功能重新设计）。
 *
 * ## 已声明适配
 *
 * 旧栈把它定义为**页面内联全局**（`window.urls`）；本工程是 SPA，改由本模块定义并在启动期
 * 用 `installWindowUrls()` 挂回 `window.urls` —— **保留全局读法**，页面代码无需改写。
 */

/** 页面级 URL 表（两层） */
export interface PageUrls {
  meta: {
    table: string
    form: string
    query: string
    option: string
    setting: string
  }
  form: {
    data: string
    delete: string
    hide: string
    add: string
    update: string
    detail: string
    validate: string
  }
}

/**
 * 表内容 —— **逐字取自** `eova/_view/_block/meta.html:12-29`（含模板串与查询串的写法）。
 *
 * 注意 `form.validate` 是**全路径无占位**的 `/api/meta/validate`（与其它项不同）。
 */
export const PAGE_URLS: PageUrls = {
  meta: {
    table: '/api/meta/table/{{object}}',
    form: '/api/meta/form/{{object}}?mode={{mode}}',
    query: '/api/meta/query/{{object}}',
    option: '/api/meta/option/{{option}}',
    setting: '/api/meta/setting/{{biz}}'
  },
  form: {
    data: '/api/form/data/{{object_code}}?pk={{pk}}',
    delete: '/api/form/delete/{{object_code}}',
    hide: '/api/form/hide/{{object_code}}',
    add: '/api/form/add/{{object_code}}',
    update: '/api/form/update/{{object_code}}',
    detail: '/api/form/detail/{{object_code}}',
    validate: '/api/meta/validate'
  }
}

/**
 * 把页面级 URL 表挂回 `window.urls`（旧栈的读法）。
 *
 * **不覆盖**已存在的同名全局：若环境里已有（例如某个仍走服务端渲染的页面注入过），
 * 以既有的为准并告警 —— 覆盖会让"到底哪份表在生效"变成不可判定。
 *
 * @param target 目标全局对象（默认 `globalThis`）
 * @returns 是否由本次安装写入
 */
export function installWindowUrls(target: Record<string, unknown> = globalThis as never): boolean {
  if (target['urls'] != null) {
    console.warn('[ui-urls] window.urls 已存在，保留既有值（不覆盖）')
    return false
  }
  target['urls'] = PAGE_URLS
  return true
}

/**
 * 取页面级 URL 表中的一项（`group.name`，如 `form.delete`）。
 *
 * 供迁移后的 SFC 用（模板页原先是直接读全局）；**缺键即抛错**，不返回空串 ——
 * 空串会拼出错误 URL 并发到后端，比抛错难查得多。
 *
 * @param group 分组（`meta` / `form`）
 * @param name  项名
 * @returns 模板串（可能含 `{{...}}` 占位）
 */
export function pageUrl(group: keyof PageUrls, name: string): string {
  const g = PAGE_URLS[group] as Record<string, string> | undefined
  const v = g?.[name]
  if (typeof v !== 'string') {
    throw new Error(`[ui-urls] 未知的 URL 项：${String(group)}.${name}（不得静默返回空串）`)
  }
  return v
}
