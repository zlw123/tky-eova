/**
 * 表单动作页（`/app/{add,update,detail}/<object_code>`）的页面契约（第 295 轮，切片 **S6**）
 *
 * ## 它对应旧栈的哪一层
 *
 * 旧栈这三页**不是** `menu.template` 驱动的模版页（第 286 轮实测：`eova_menu.config.template`
 * 全为 NULL，且旧栈 form 页的 URL 是**动作路由** `/app/add|update|detail/<object_code>`）。
 * 它们由 `AppController#add()/update()/detail()` 各自 `renderEnjoy(...)` 渲染，页面参数来自两处：
 *
 * | 参数 | 旧栈来源 | 本模块的等价物 |
 * |---|---|---|
 * | `object.code`（`get(0)`） | URL **第 0 段** | 路由参数 `:objectCode` |
 * | `biz` | `get("biz", "")` | 查询串 `?biz=`（缺省 `''`） |
 * | `id` | `get("id")` | 查询串 `?id=`（缺省 `''`） |
 * | `fixed` | `WidgetManager.getRef(this).toJson()` —— **只读请求参数 `ref`** | `?ref=` 的同口径解析（`parseRefFixed`） |
 * | `object_id`/`object_name`/`object_pk` | `_page/form.html` 的 `#(object.id/name/pk)` —— `sm.meta.getMeta(objectCode)` | ⚠️ **无来源**（见下） |
 * | `loginUser.isAdmin` | `LoginInterceptor` 每请求 `ctrl.set(LoginService.USER, user)` | ⚠️ **无来源**（见下） |
 *
 * ★ `fixed` 为什么可以在前端算（取证，不是"顺手实现"）：`WidgetManager#getRef`
 * （`eova-core/.../widget/WidgetManager.java:750`）**只**读 `c.get("ref")`，
 * 不查库、不看会话 ⇒ 与 `getPara("ref")` 同源，属"前端自己取"的那一类
 * （`page-bootstrap.ts` 文件头第 14 行的口径）。`parseRefFixed` 逐行 port 该 12 行实现。
 *
 * ## ⚠️ 已登记的缺口（**不得当成已完成**）
 *
 * `object.*` 与 `loginUser.*` 在旧栈是**渲染期插值**，分离后属页面引导数据（DES-004）。
 * 但 `POST /api/page/bootstrap` 的**取数口径是菜单编码**（body.menu 或 body.path 末段，
 * S4 判据已冻结）；动作页的末段是**元对象编码**，拿它当菜单编码查会得到**另一个对象**
 * （`/app/add/meta_product` 的末段恰好也是菜单编码 `meta_product` ⇒ 会"看起来正常但可能错"）。
 * ⇒ 本页**不调用**该端点（调用比不调用更危险），改用**可声明降级**：
 * `uzoo.page.object_id/object_name/object_pk` 不写、超管面板不渲染，并由页面**响亮告警**。
 * 这三项的补口（动作页引导数据）属**独立单元**，需先有 DES 口径 —— 见 DES-005 §16.1 非范围②。
 *
 * ## 既有待口径项（与本模块无关，但影响这两页的等价性）
 *
 * `custom-apps.ts` 登记了 `meta_hotel`/`meta_product` 的**自定义表单**（键是**元对象编码**，
 * 由旧 `me.vue.mount(app, uzoo.page.code)` 命中后**替换整页模版**）。该机制在 SPA 下**未实现**
 * ⇒ 对这两个元对象，本页渲染的是**标准表单**，与旧栈**不等价**（既有登记，非本模块引入）。
 */

import { getUzoo } from './eova-ext'
import { pickBootstrapString } from './page-bootstrap'

/** 三个表单动作页（= `AppController` 的 add/update/detail） */
export type FormPageAction = 'add' | 'update' | 'detail'

/**
 * `ev-form` 的 `mode` prop，也是 `uzoo.page.form` 的值。
 *
 * 逐字取自三个旧模板的 `<script>`：`add` ⇒ `create`、`update` ⇒ `update`、`detail` ⇒ `read`。
 */
export type FormPageMode = 'create' | 'update' | 'read'

/** 动作 → `ev-form` 的 mode（也是 `uzoo.page.form`） */
export const FORM_PAGE_MODE: Readonly<Record<FormPageAction, FormPageMode>> = {
  add: 'create',
  update: 'update',
  detail: 'read'
}

/**
 * `_page/form.html` 写在 `uzoo.page` 上的元对象键（旧栈是渲染期插值）。
 *
 * ★ 用途取证：冻结脚本 `_view/template/eova.template.js:31` 读 `uzoo.page.object_id`
 * （`onMetaObject` 拼 `/app/update/eova_object_code?id=<object_id>`）；
 * `:41` 读 `uzoo.page.object_code`（`onMetaField` 拼 `/meta/edit?object=…`）。
 */
export const FORM_PAGE_OBJECT_KEYS: readonly string[] = [
  'object_id',
  'object_name',
  'object_code',
  'object_pk'
]

/**
 * 判定 `ref` 参数是否为空 —— 与冻结制品 `eova-tools.umd.js` 的 `x.isEmpty` **同口径**。
 *
 * 取证（制品实现）：
 * `n == null || n === "undefined" || (typeof n != "boolean" && (typeof n == "number"
 *   ? Number.isNaN(n) : typeof n == "string" || n instanceof String ? n.toString().trim() === "" : …))`
 *
 * 本函数只保留**字符串输入**那一条分支（`ref` 只可能来自 `getPara("ref")`）——
 * 其余分支（数组/Map/数字）在本调用点不可达，保留它们只会制造"看起来覆盖更多"的假象。
 *
 * @param v 待判定值
 * @returns 是否为空
 */
export function isEmptyRefParam(v: unknown): boolean {
  if (v == null) {
    return true
  }
  return String(v).trim() === '' || String(v) === 'undefined'
}

/**
 * 解析 `ref` 请求参数为**固定值表** —— `WidgetManager#getRef` 的逐行等价 port。
 *
 * 旧实现（`WidgetManager.java:750-772`）：
 * ```java
 * EovaRecord r = new EovaRecord();
 * try {
 *     String ref = c.get("ref");
 *     if (x.isEmpty(ref)) { return r; }
 *     String[] fields = ref.split(",");
 *     for (String field : fields) {
 *         String[] strs = field.split(":");
 *         r.set(strs[0], strs[1]);
 *     }
 * } catch (Exception e) { e.printStackTrace(); return r; }
 * return r;
 * ```
 *
 * ★ **三处"看起来像 bug、实为既有语义"的地方，原样保留**：
 * ① 段内没有 `:` 时 `strs[1]` 越界 ⇒ 抛 `ArrayIndexOutOfBoundsException`，被 catch 吞掉，
 *    返回**已经累积的部分**（不是空表）——故 `"a:1,b"` 的结果是 `{a:'1'}` 而不是 `{}`；
 * ② 值为 `undefined` 时 `EovaRecord.set(key, undefined)` 照写（与 `x.isEmpty` 无关）；
 * ③ 空段（`"a:1,,b:2"` 的中间那段）**没有冒号** ⇒ 走与①同一条路径 ⇒ 同样**停在它之前**
 *    （`{a:'1'}`，后面的 `b` 拿不到）。注意 Java 的 `String.split` 会去掉**尾部**空串而 JS 不会
 *    （`"a:1,"` ⇒ Java `['a:1']`、JS `['a:1','']`），但两者最终结果相同（JS 那边在空段处停手）。
 *
 * @param ref `ref` 参数原文（通常来自查询串）
 * @param isEmpty 空值判定（生产走制品 `x.isEmpty`；缺省用同口径的字符串分支）
 * @returns 固定值表（键 → 值；顺序即参数出现顺序）
 */
export function parseRefFixed(
  ref: unknown,
  isEmpty: (v: unknown) => boolean = isEmptyRefParam
): Record<string, unknown> {
  const out: Record<string, unknown> = {}
  if (isEmpty(ref)) {
    return out
  }
  const fields = String(ref).split(',')
  for (const field of fields) {
    const strs = field.split(':')
    // ① 旧实现靠 catch 兜住"没有冒号"的解构越界；这里必须**同样只累积到出错前**并停手。
    if (strs.length < 2) {
      break
    }
    out[strs[0]] = strs[1]
  }
  return out
}

/** URL 参数面（与旧 `getPara` 同源；`object` 在动作页来自**路径段**而非查询串） */
export interface FormPageParams {
  /** 元对象编码（旧 `get(0)` = URL 第 0 段） */
  objectCode: string
  /** `biz`（旧 `get("biz", "")`；缺省空串） */
  biz: string
  /** `id`（旧 `get("id")`；缺省空串 —— `ev-form` 的 `pk`） */
  id: string
  /** `ref` 参数原文（`getRef` 的输入） */
  ref: string
  /** 固定值表（旧 `WidgetManager.getRef(this)` 的等价物） */
  fixed: Record<string, unknown>
}

/**
 * 解析表单动作页的页面参数。
 *
 * 语义逐条对齐 `AppController#add/update/detail`：
 * · `objectCode`：路由参数（= 旧 `get(0)`）；取不到 ⇒ 空串（**由页面显式降级**，不在这里抛）；
 * · `biz`/`id`/`ref`：查询串；缺省 `''`（旧 `get("biz", "")` 与 `get("id")` 的既有形态）。
 *
 * @param opts.objectCodeFromRoute 路由参数 `objectCode`（`string | string[] | undefined`）
 * @param opts.search 查询串（默认取当前地址；判据可注入）
 * @returns 页面参数
 */
export function resolveFormPageParams(opts: {
  objectCodeFromRoute?: unknown
  search?: string
}): FormPageParams {
  const q = opts.search ?? (typeof window === 'undefined' ? '' : window.location.search)
  const sp = new URLSearchParams(q)
  // ★ 只认字符串（与 `TemplateTable.vue`/`ButtonAdd.vue` 对路由参数的既有处置一致）：
  //   vue-router 对**非可重复**参数的 `params.menuCode` 只可能是字符串；数组只出现在可重复参数上，
  //   而本页的 path 模板是 `:objectCode`（非可重复）⇒ 把数组也接受进来等于把"用错了参数名"掩盖过去。
  const rawObjectCode = typeof opts.objectCodeFromRoute === 'string' ? opts.objectCodeFromRoute : ''
  const objectCode = pickBootstrapString(rawObjectCode)
  const ref = sp.get('ref') ?? ''
  return {
    objectCode,
    biz: sp.get('biz') ?? '',
    id: sp.get('id') ?? '',
    ref,
    fixed: parseRefFixed(ref)
  }
}

/**
 * 写 `uzoo.page`（旧 `_page/form.html` 的整对象赋值 + 页内脚本的四条赋值）。
 *
 * 写入顺序即旧栈的执行顺序：
 * ① `_page/form.html`（`<head>`）**整对象赋值** `uzoo.page = {object_id, object_name, object_code, object_pk}`；
 * ② 页内脚本（`</body>` 前）逐条写 `code` / `form` / `biz`，`add` 页另写 `fixed`（**仅当真值**）。
 *
 * ★ 为什么①必须**整体替换**而不是逐键写：SPA 的 `uzoo` 是**跨路由常驻**的全局，
 * 逐键写会把上一页的键留下来；旧栈每次导航都是新文档、`uzoo.page` 恒从 `{}` 起步。
 * （`fixed` 那条的 `if (fixed)` 是**真值判断**：空串不写 —— 原样保留。）
 *
 * @param opts.objectCode 元对象编码（`object_code` / `code`）
 * @param opts.form `uzoo.page.form`（= `ev-form` 的 mode）
 * @param opts.biz `uzoo.page.biz`（旧 `#(biz)`，即查询串里的 `biz`）
 * @param opts.fixed 固定值表（`add` 页在非空时才写 `fixed`）
 * @param opts.objectMeta 服务端插值的 `object.id/name/pk_name`；**当前动作页无来源** ⇒ 传 null
 * @param target 目标全局对象
 * @returns 未取到来源的键（页面据此告警；**空数组表示没有缺口**）
 */
export function writeFormPageUzooPage(
  opts: {
    objectCode: string
    form: FormPageMode
    biz: string
    fixed: Record<string, unknown>
    objectMeta?: { id?: unknown; name?: unknown; pk_name?: unknown } | null
  },
  target: Record<string, unknown> = globalThis as never
): string[] {
  const uzoo = getUzoo(target)

  const meta = opts.objectMeta ?? null
  const missing: string[] = []

  /** 取一个服务端插值键：有值即写并计入"已就位"，无值则记为缺口（**不写 undefined**） */
  const put = (key: string, value: unknown): void => {
    if (value == null || String(value).trim() === '') {
      missing.push(key)
      return
    }
    uzoo.page[key] = value
  }

  // ① `_page/form.html`：整对象赋值（见函数注释"为什么整体替换"）
  uzoo.page = {}
  put('object_id', meta?.id)
  put('object_name', meta?.name)
  // ★ `object_code` 必须写：`me.urls.url('form_add', props)` 的 `{{object_code}}` 靠它替换
  //   （制品 `il.form_add = "/api/form/add/{{object_code}}"`）⇒ 缺它 URL 会变成 `.../undefined`。
  put('object_code', opts.objectCode)
  put('object_pk', meta?.pk_name)

  // ② 页内脚本
  uzoo.page['code'] = opts.objectCode
  uzoo.page['form'] = opts.form
  uzoo.page['biz'] = opts.biz
  // 旧 **add** 页独有的一段：`let fixed = '#(fixed)'; if (fixed) { uzoo.page.fixed = JSON.parse(fixed) }`
  //   · `fixed` 的取值是 `WidgetManager.getRef(this).toJson()` ⇒ **空表也序列化成 `"{}"`**，
  //     而 `'{}'` 是**真值** ⇒ 这一段在 add 页**恒执行**（`uzoo.page.fixed` 至少是 `{}`）。
  //   · update/detail 的 html **没有**这一段 ⇒ 那两页 `props.fixed` 恒为 `undefined`，
  //     故它们的 `data = reactive({...props.fixed})`（detail）实际等价于 `{}`。
  //   ⇒ 这里只在 `create` 写、且**无条件写**（含空表），与旧栈逐字一致。
  if (opts.form === 'create') {
    uzoo.page['fixed'] = opts.fixed
  }

  return missing
}
