/**
 * 平台 iframe **嵌入态入口参数消费**（阶段 3，第 297 轮）
 *
 * ## 它对应平台契约的哪两条
 *
 * `DES-002-R4 §100 §四`（remis-eova-ui **必须实现**的宿主契约，逐条带 file:line）：
 *
 * | # | 契约 | 本模块 |
 * |---|---|---|
 * | 1 | 入口（router/permission 之前）读取 `_sourceSystemCode`、`_accessToken`、`_tenantId` | {@link readEmbedEntry} |
 * | 2 | 消费后 `history.replaceState` **清除** `_accessToken`/`_tenantId`，**保留** `_sourceSystemCode` | {@link stripSensitiveParams} / {@link consumeEmbedEntry} |
 *
 * 依 r100 的**口径裁定**（写死在计划里）："**按 DES-007 的目标态设计，但落地必须先兼容已实现的
 * `_accessToken` 契约（第 1~2 条），二者同时存在**。" ⇒ 本模块做的就是那两条**已实现**的契约
 * （DES-007 的 `embedTicket`/`REMIS_EMBED_AUTH_*` 目标态属独立单元，见文件末）。
 *
 * ## 溯源
 *
 * - ported from: `platform/fornt/yudao-ui/src/utils/iframeAuth.ts`（193 行，`restoreAuthFromUrl()`）
 * - source revision: 本地只读工作副本；`sha256 = e78ba899f50db2c0fdfb4132c4531a4b0f6c826673ae717f8961ed219b3c7c59`
 * - 关键行：`:110-111` 两个 `searchParams.delete`；`:113` "保留 `_sourceSystemCode`" 的注释；`:117` `replaceState`
 * - 本侧设计：`docs/DES-006-R1-platform-host-contract-consumer.md`
 *
 * ## 逐条保留的既有语义（**不得"顺手改对"**）
 *
 * ① **清理整段都在 `if (accessToken)` 分支里**（平台 `:104-117`）⇒ **没有 `_accessToken` 时，
 *    连 `_tenantId` 也不删、URL 一个字符都不动**（本模块返回 `skippedReason='no-access-token'`）。
 *    ★ 这看起来像"漏删"，但它是平台的**现有可观测行为**；单方面"改对"会让两侧在
 *    "同一 URL 消费后是否变" 这件事上分叉 —— 故**原样保留**并登记（见文件末"已登记的观察"）。
 * ② **只删这两个参数**，其余一律不动（含其它 `_` 开头的内部参数与全部业务参数）；
 * ③ `_sourceSystemCode` **必须保留**（平台注释原文："保留 `_sourceSystemCode`，因为它用于嵌入模式判断"）；
 *    `_requestId` 同理必须保留 —— 它是**关闭回传**的唯一关联键（见 `iframe-bridge.ts`）；
 * ④ 出错（URL 不可解析 / `replaceState` 抛）⇒ **只告警不抛**（平台 `catch` 后 `return false`）。
 *
 * ## 已声明适配（非静默改写）
 *
 * ① **不落盘、不入会话缓存**：平台把 token 放进**会话级**缓存（`sessionCache.set('ACCESS_TOKEN', …)`，
 *    注释"嵌入场景使用会话级 token，关闭页面后要求重新登录"）——因为平台**自己要拿它发请求**。
 *    本工程目前**没有任何用得上该 token 的消费点**（EovaMeta 的会话是 `eovasid` Cookie，
 *    见文件末"仍未闭"），把一份用不到的 bearer 凭据留在浏览器存储里是**净负债**
 *    ⇒ 本模块只**读出来交给调用方**（{@link consumeEmbedEntry} 的返回值），**不缓存**。
 * ② **日志只记布尔/非敏感字段**：与平台同一条纪律（平台只打 `hasAccessToken: Boolean(accessToken)`），
 *    本模块的 `info` 载荷**不含 token 原值**，并由判据钉住"序列化结果里不得出现 token"。
 * ③ 日志前缀改用本工程自己的标签（`[embed-entry]`）——平台前缀是它自己的模块名，抄过来会让
 *    排障时误以为是平台侧日志；**格式与字段名保持同类**（`hasAccessToken`/`sourceSystemCode`/`reason`）。
 *
 * ## ⚠️ 仍未闭（**不得当成已完成**）
 *
 * 1. **token → 会话**（阶段 3 首要待办）：`_accessToken` 是**平台**的 bearer token，
 *    本工程后端**没有**校验它的能力（会话是 `eovasid` HttpOnly Cookie）⇒ 读到 ≠ 能用。
 *    两条候选路径：① 平台 DES-007 的 `embedTicket`（平台侧 `LC-012` **未做**）；
 *    ② 复用 EovaMeta **既有**开放 API 鉴权口（`ApiRouterHandler` 的 `app_key`+`sign`+`timestamp`，
 *    `APP_CONFIG` 由 `addAppConfig` 注入，且 DEV 环境接受 `sign.startsWith("dev")`）做服务端换票。
 *    两者都需要平台侧配合 ⇒ 属**独立单元**（先立 DES）。
 * 2. DES-007 目标态（`REMIS_EMBED_AUTH_READY`/`AUTH_FAILED`、精确 origin）—— 同上。
 * 3. 读到的 `accessToken`/`tenantId` 目前**没有消费点**（`consumeEmbedEntry` 的调用方是 `main.ts`，
 *    只做"读取 + 清理"）。
 */

/** 消费后**必须从 URL 清除**的敏感参数（逐字取自平台 `iframeAuth.ts:114-115`） */
export const SENSITIVE_PARAM_NAMES: readonly string[] = ['_accessToken', '_tenantId']

/**
 * 必须**保留**的内部参数（不得被清理逻辑波及）。
 *
 * · `_sourceSystemCode`：承担**嵌入态判定**（平台 `iframeAuth.ts:113` 注释原文）；
 * · `_requestId`：**关闭回传**的唯一关联键（`iframe-bridge.ts` 的 `getRequestId` 读它）。
 *
 * ★ 单列成常量是为了让判据能断言"清理只删该删的"，而不是靠"看起来没删"。
 */
export const KEPT_INTERNAL_PARAM_NAMES: readonly string[] = ['_sourceSystemCode', '_requestId']

/** 平台 `restoreAuthFromUrl()` 跳过分支的 reason 原文（`iframeAuth.ts:128`） */
export const SKIP_REASON_NO_ACCESS_TOKEN = 'missing_access_token'

/** 从 URL 读到的嵌入态入口参数 */
export interface EmbedEntryParams {
  /** 平台签发的访问令牌（**敏感**：不得落盘、不得进日志） */
  accessToken: string | null
  /** 租户 ID */
  tenantId: string | null
  /** 调用来源系统编码（**保留在 URL 上**，用于嵌入态判定） */
  sourceSystemCode: string | null
  /** 关闭回传用的请求 ID（**保留在 URL 上**） */
  requestId: string | null
}

/** 消费结果 */
export interface EmbedEntryResult {
  /** 读到的参数（无论是否清理都返回） */
  params: EmbedEntryParams
  /** 是否真的执行了 URL 清理 */
  stripped: boolean
  /** 未清理的原因（清理过则为 null） */
  skippedReason: string | null
}

/** 注入点（判据可替换窗口面与出口；生产用真实 `window`） */
export interface EmbedEntryDeps {
  /** 窗口面 */
  win?: {
    location?: { search?: string; href?: string; pathname?: string }
    history?: { replaceState: (data: unknown, title: string, url: string) => void }
  }
  /** 查询串（默认取 `win.location.search`；判据可注入） */
  search?: string
  /** 完整地址（默认取 `win.location.href`；判据可注入） */
  href?: string
  /** 告警出口（默认 `console.warn`） */
  warn?: (message: string) => void
  /** 诊断出口（默认 `console.info`） */
  info?: (message: string, detail?: unknown) => void
}

/**
 * 读出嵌入态入口参数。
 *
 * 四个键都是"有则返回、无则 null"（`URLSearchParams.get` 语义，原样保留）。
 *
 * @param search 查询串（默认取当前地址；判据可注入）
 * @returns 入口参数
 */
export function readEmbedEntry(search?: string): EmbedEntryParams {
  const raw = search ?? (typeof window === 'undefined' ? '' : window.location.search)
  const sp = new URLSearchParams(raw)
  return {
    accessToken: sp.get('_accessToken'),
    tenantId: sp.get('_tenantId'),
    sourceSystemCode: sp.get('_sourceSystemCode'),
    requestId: sp.get('_requestId')
  }
}

/**
 * 计算"清掉敏感参数后"的查询串（纯函数，判据直接断言）。
 *
 * 语义：**只删** {@link SENSITIVE_PARAM_NAMES} 里的键；其余键（含其它 `_` 开头者与业务参数）
 * **原样保留且保持相对顺序**。返回带前导 `?` 的串；空则返回 `''`。
 *
 * @param search 原查询串（可带或不带前导 `?`）
 * @returns 清理后的查询串
 */
export function stripSensitiveParams(search: string): string {
  const sp = new URLSearchParams(search)
  for (const name of SENSITIVE_PARAM_NAMES) {
    sp.delete(name)
  }
  const s = sp.toString()
  return s === '' ? '' : `?${s}`
}

/**
 * 消费嵌入态入口参数：**读取 + 清理 URL**（契约 1/2）。
 *
 * 与平台 `restoreAuthFromUrl()` 的**分支语义逐条一致**：
 * · 有 `_accessToken` ⇒ 清理并 `replaceState`（返回 `stripped: true`）；
 * · 没有 `_accessToken` ⇒ **一个字符都不动**，返回 `skippedReason='missing_access_token'`（见文件头 ①）；
 * · 任何环节出错（URL 不可解析 / `replaceState` 抛）⇒ 只告警，返回 `stripped: false`，**不抛**。
 *
 * ★ 调用时点：`main.ts` 的 **`bootstrap()` 第一步**（契约 1 要求"入口、router/permission 之前"）。
 *
 * @param deps 注入点
 * @returns 消费结果
 */
export function consumeEmbedEntry(deps: EmbedEntryDeps = {}): EmbedEntryResult {
  const win = deps.win ?? (globalThis.window as never as EmbedEntryDeps['win'])
  const warn = deps.warn ?? ((m: string) => console.warn(m))
  const info = deps.info ?? ((m: string, detail?: unknown) => console.info(m, detail))
  const search = deps.search ?? win?.location?.search ?? ''

  const params = readEmbedEntry(search)

  // 平台 `iframeAuth.ts:64-70`：入口日志只记布尔与非敏感值（★ 绝不打 token 原值）
  info('[embed-entry][restore][start]', {
    pathname: win?.location?.pathname ?? '',
    hasAccessToken: Boolean(params.accessToken),
    hasTenantId: Boolean(params.tenantId),
    sourceSystemCode: params.sourceSystemCode
  })

  if (params.accessToken) {
    try {
      const href = deps.href ?? win?.location?.href ?? ''
      const url = new URL(href)
      // ★ 只删这两个（平台 `:114-115`）；`_sourceSystemCode`/`_requestId` 必须留下（文件头 ③）
      for (const name of SENSITIVE_PARAM_NAMES) {
        url.searchParams.delete(name)
      }
      const history = win?.history
      if (!history) {
        throw new Error('窗口面没有 history.replaceState（注入的替身不完整？）')
      }
      history.replaceState({}, '', url.toString())
      info('[embed-entry][restore][success]', {
        sourceSystemCode: params.sourceSystemCode,
        tenantId: params.tenantId
      })
      return { params, stripped: true, skippedReason: null }
    } catch (e) {
      // 平台 `:131-133`：catch 后只告警并 return false（不抛）
      warn(`[embed-entry][restore][error] URL 清理失败（已放弃清理，不阻塞启动）：${String(e)}`)
      return { params, stripped: false, skippedReason: 'strip_failed' }
    }
  }

  // 平台 `:127-130`：没有 token 走 skip 分支（URL **不动**，连 `_tenantId` 也不删 —— 文件头 ①）
  info('[embed-entry][restore][skip]', { reason: SKIP_REASON_NO_ACCESS_TOKEN })
  return { params, stripped: false, skippedReason: SKIP_REASON_NO_ACCESS_TOKEN }
}
