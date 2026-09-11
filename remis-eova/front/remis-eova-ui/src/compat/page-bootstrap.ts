/**
 * 页面引导数据接缝（第 105 轮，设计见 `docs/DES-004-R1-page-bootstrap-design.md`）
 *
 * ## 它解决什么
 *
 * 旧页面里的 `#(object.code)`、`#(menu.name)`、`#(btn.ui)`、`#(loginUser.isAdmin)` 都是**服务端插值**：
 * 后端在渲染前 `setAttr("object"|"menu"|"btnList"|"loginUser", …)`，模板里直接写。
 * 前后分离后这些插值没有来源 —— 且它们**不是配置**（与 `DES-003` 的 `me.conf` 是两类东西）：
 * 由 URL 参数（`object`/`menu`/`biz`/`mode`/`id`）**查库**得到，**含权限面**（`btnList` 按角色查、
 * `loginUser` 是会话身份），因此**不可缓存**、必须按请求返回。
 *
 * 本模块是该契约的**消费端**：
 *  - 字段名**沿用旧 `setAttr` 的名字**（`object`/`menu`/`btnList`/`loginUser`/`isQuery`），少一层映射；
 *  - `biz`/`mode`/`id`/`object`/`menu` 这类**来自 URL 的参数由前端自己取**（与旧 `getPara` 同源，不需要端点）；
 *  - 服务端才有的部分（`object.*` 元对象描述、`menu.*`、`btnList`、`loginUser`）走可注入的 `fetcher`；
 *  - 端点尚未上线时**响亮告警并降级为"仅 URL 参数"**（页面据此走可声明的回退），**不静默假装成功**。
 *
 * ## 纪律
 *
 * - 引导数据 **不得** 混进 `me.conf`（配置），也 **不得** 被缓存：里面有按角色的按钮集合。
 * - `btnList[].ui` **保持字符串**（要么是 HTML 片段、要么是 `.js` 路径，由页面逐项判定）——
 *   这是既有契约（纪律①：按钮 HTML 由后端生成、前端 `v-html` 渲染），不得改成结构化对象。
 * - 缺关键字段时**抛错**（由页面显式声明回退），不得拿空串去拼 URL。
 */

/** 元对象描述（字段名沿用旧 `MetaObject` 的对外字段） */
export interface BootstrapObject {
  code: string
  name?: string
  pk_name?: string
  table?: string
  data_source?: string
  [k: string]: unknown
}

/** 菜单（字段名沿用旧 `Menu`） */
export interface BootstrapMenu {
  code: string
  name?: string
  [k: string]: unknown
}

/** 功能按钮：`ui` 是**字符串**（HTML 片段或 `.js` 路径），见文件头纪律 */
export interface BootstrapButton {
  ui?: string
  [k: string]: unknown
}

/** 登录身份（权限面，随会话变化） */
export interface BootstrapLoginUser {
  isAdmin?: boolean
  id?: number | string
  name?: string
  [k: string]: unknown
}

/** 来自 URL 的参数（前端自行取，与旧 `getPara` 同源） */
export interface UrlParams {
  object?: string
  menu?: string
  biz?: string
  mode?: string
  id?: string
  [k: string]: string | undefined
}

/** 引导数据整体 */
export interface PageBootstrap {
  /** 是否拿到了服务端引导数据（false ⇒ 仅 URL 参数可用，页面须走可声明的回退） */
  fromServer: boolean
  /** URL 参数（永远可用） */
  url: UrlParams
  object?: BootstrapObject
  menu?: BootstrapMenu
  btnList?: BootstrapButton[]
  loginUser?: BootstrapLoginUser
  isQuery?: boolean
  /**
   * **页面自有引导值**（DES-004 §2 的"页面自有"家族：`role`、`parent_id`、`where`、`rid`、
   * `login_id`/`login_pwd` 等）。这些值在旧栈里由各页面自己的控制器 `set(...)`，
   * 形态各异，故用一张**开放字典**承载，不逐个建字段。
   */
  pageParams?: Record<string, unknown>
  /** 契约版本（端点上线后用于兼容判断） */
  version?: number
}

/** URL 中会被当作引导参数读取的键（顺序无关） */
export const BOOTSTRAP_URL_KEYS: readonly string[] = ['object', 'menu', 'biz', 'mode', 'id']

/**
 * 从查询串取引导参数（与旧渲染期 `getPara` 同源）。
 *
 * @param search 查询串（默认取当前地址；判据可注入）
 * @returns 参数对象（只含存在的键）
 */
export function readUrlParams(search?: string): UrlParams {
  const q = search ?? (typeof window === 'undefined' ? '' : window.location.search)
  const sp = new URLSearchParams(q)
  const out: UrlParams = {}
  for (const k of BOOTSTRAP_URL_KEYS) {
    const v = sp.get(k)
    if (v !== null) {
      out[k] = v
    }
  }
  return out
}

/** 装配选项 */
export interface LoadBootstrapOptions {
  /**
   * 服务端引导数据来源（返回 JSON 字符串）。未提供 ⇒ 只返回 URL 参数并告警。
   *
   * 端点形态见 DES-004 §3.1（`POST /api/page/bootstrap`）。
   */
  fetcher?: (ctx: { url: UrlParams }) => Promise<string | null>
  /** 路径（端点用于判定该页需要哪些引导数据；默认取当前 pathname） */
  path?: string
  /** URL 参数来源（判据可注入） */
  search?: string
  /** 告警出口 */
  warn?: (message: string) => void
}

/**
 * 装配页面引导数据。
 *
 * 行为：① 先取 URL 参数（永远可用）；② 若有 `fetcher` 则拉服务端部分并合并；
 * ③ 端点未提供/失败/返回空 ⇒ **告警**且 `fromServer=false`（页面据此走显式回退）；
 * ④ 服务端载荷字段非法 ⇒ 告警并忽略（不把脏数据当引导数据）。
 *
 * @param options 注入点
 * @returns 引导数据
 */
export async function loadPageBootstrap(options: LoadBootstrapOptions = {}): Promise<PageBootstrap> {
  const warn = options.warn ?? ((m: string) => console.warn(m))
  const url = readUrlParams(options.search)
  const base: PageBootstrap = { fromServer: false, url }

  if (!options.fetcher) {
    warn(
      '[page-bootstrap] 未提供服务端引导数据来源 ⇒ 仅 URL 参数可用。' +
        '受影响的是含 `#(object.*)`/`#(menu.*)`/`#(btn.ui)`/`#(loginUser.*)` 的页面' +
        '（元对象与菜单描述、按角色的按钮集合、会话身份）。端点形态见 DES-004 §3.1。'
    )
    return base
  }

  let json: string | null
  try {
    json = await options.fetcher({ url })
  } catch (e) {
    warn(`[page-bootstrap] 拉取引导数据失败（不阻塞页面，走降级）：${(e as Error).message}`)
    return base
  }
  if (!json) {
    warn('[page-bootstrap] 引导数据来源返回空 ⇒ 走降级（仅 URL 参数）')
    return base
  }

  let parsed: unknown
  try {
    parsed = JSON.parse(json)
  } catch (e) {
    warn(`[page-bootstrap] 引导数据不是合法 JSON，已忽略：${(e as Error).message}`)
    return base
  }
  if (parsed == null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    warn('[page-bootstrap] 引导数据不是对象，已忽略')
    return base
  }
  const p = parsed as Record<string, unknown>
  // 旧栈用 state:'ok' 判定成功；缺失时按"结构可用"处理（端点可省略），但显式记录
  if ('state' in p && p['state'] !== 'ok') {
    warn(`[page-bootstrap] 服务端返回 state=${String(p['state'])}，按失败处理`)
    return base
  }

  return {
    fromServer: true,
    url,
    object: p['object'] as BootstrapObject | undefined,
    menu: p['menu'] as BootstrapMenu | undefined,
    btnList: p['btnList'] as BootstrapButton[] | undefined,
    loginUser: p['loginUser'] as BootstrapLoginUser | undefined,
    isQuery: p['isQuery'] as boolean | undefined,
    pageParams: p['pageParams'] as Record<string, unknown> | undefined,
    version: p['version'] as number | undefined
  }
}

/**
 * 取元对象编码（`object.code`），缺则**抛错**。
 *
 * 语义：`objectCode` 这类值在旧栈里是**页面存在的前提**（渲染期就插值了），
 * 分离后若缺失，页面拿空串去拼 `/api/meta/table/` 只会得到 404 —— 比抛错难查得多。
 *
 * @param bs 引导数据
 * @param fallback 可声明的回退值（页面明确知道旧默认时用，例如 `su.object.code` 的文档默认）
 * @returns 元对象编码
 */
export function requireObjectCode(bs: PageBootstrap, fallback?: string): string {
  const code = bs.object?.code ?? bs.url.object ?? fallback
  if (code == null || String(code).trim() === '') {
    throw new Error(
      '[page-bootstrap] 缺少 object.code（引导数据与 URL 参数都没有，且页面未声明回退值）。' +
        '拿空串去拼 URL 会得到 404 而难以定位，故选抛错。'
    )
  }
  return String(code)
}

/**
 * 取按角色的按钮集合（`btnList`）。
 *
 * 返回 `null` 表示"服务端引导数据未就绪"（与"就绪但为空数组"**不是一回事**：
 * 后者是"该角色确实没有按钮"，前者是"还不知道"）—— 页面据此决定是隐藏还是等待。
 *
 * @param bs 引导数据
 * @returns 按钮数组；未就绪返回 null
 */
export function buttonListOf(bs: PageBootstrap): BootstrapButton[] | null {
  if (!bs.fromServer) {
    return null
  }
  return bs.btnList ?? []
}

/**
 * 取页面自有引导值（`pageParams[key]`），缺则用**显式回退**；两者都缺则抛错。
 *
 * 与 `requireObjectCode` 同一纪律：缺关键值时不返回空串 —— 旧栈里这些值是**渲染期就插好**的，
 * 分离后拿空串去提交，会变成"后端收到空值却看不出是前端没取到"。
 *
 * @param bs 引导数据
 * @param key 键（旧 `set(...)` 的名字，如 `role`）
 * @param fallback 可声明的回退值（页面明确知道旧默认/旧常量时用）
 * @returns 该引导值（字符串形态）
 */
export function bootstrapParam(bs: PageBootstrap, key: string, fallback?: string): string {
  const raw = bs.pageParams?.[key]
  const v = raw == null || String(raw).trim() === '' ? fallback : String(raw)
  if (v == null || v.trim() === '') {
    throw new Error(
      `[page-bootstrap] 缺少页面引导值 ${key}（引导数据没有，且页面未声明回退值）。` +
        '旧栈里该值是渲染期插值，缺它提交上去只会得到"空值"而看不出原因。'
    )
  }
  return v
}
