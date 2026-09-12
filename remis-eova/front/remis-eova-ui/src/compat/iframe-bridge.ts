/**
 * 平台 iframe **宿主契约消费端**（阶段 3 落点，第 296 轮）
 *
 * ## 它是什么
 *
 * remis-eova-ui 在阶段 3 以**形态 A（iframe 嵌入）** 被 yudao 平台打开（r100 裁定，见
 * `DES-002-R4 §100`），因此必须实现平台规定的**子应用侧**消息契约。本模块是那部分契约的消费端 ——
 * **不引平台库**（r100 三条可检验理由在案：库的启动链要求平台网关/token、全局样式会污染视觉对照判据、
 * `isEmbedMode()` 需要 `systemCode`）、**不改平台源码**。
 *
 * ## 溯源
 *
 * - ported from: `platform/fornt/yudao-ui/src/utils/iframeBridge.ts`（356 行，平台 `useIframeBridge()`）
 * - source revision: 本地只读工作副本；`sha256 = e36c8628e4b9ed100b6234d2fcc3aa5d5d14c8ebb7fc0b39d74e616743886b1f`
 * - 契约清单：`docs/DES-002-R4-techstack-migration-master-plan.md` §100 §四（8 条，逐条带 file:line）
 * - 回传结构查实：`docs/.local/baseline/evidence/phase3-integration-closures.json`（r125）
 * - 本侧设计：`docs/DES-006-R1-platform-host-contract-consumer.md`
 *
 * ## 只 port 子应用需要的那一面（**显式登记，不是遗漏**）
 *
 * | 平台成员 | 本模块 | 理由 |
 * |---|---|---|
 * | `closePage` / `getRequestId` / `getSourceSystemCode` / `getCallParams` | **已 port** | 子应用侧契约（r100 §四 1/3/6） |
 * | `isEmbedMode` | **已 port**（语义按契约 3，本地系统编码走注入） | 同上 |
 * | `isInPlatform`（内部 `checkIsInPlatform`） | **已 port**，但**只作传输前置条件**，见下 | 与平台实现同口径 |
 * | `openCrossAppPage` / `showMessage` / `showConfirm` / `showAlert` / `setupResultListener` / `pendingCallbacks` | **不 port** | 它们是**调用方**能力（打开别的应用、请求平台弹窗），要求 remis-eova-ui 自己充当宿主。本应用是**被调用方**；按"禁止按功能重新设计、不合并无关单元"，不引入用不到的监听器与 pending 表 |
 *
 * ## ★ 两个"环境判断"必须分开（本模块最容易做错的一点）
 *
 * 平台源码里 `closePage` 的门控是 **`isInPlatform = window.parent !== window`**（`iframeBridge.ts:97-103,272`）；
 * 而冻结契约第 3 条（`DES-002-R4 §100 §四(3)` + `DES-007 §5.2`）明确：**嵌入态判定不得用
 * `window.parent !== window`**（"等同把 UI 环境判断当成安全凭据"），要用 `_sourceSystemCode 且 ≠ 本系统编码`。
 *
 * 两者**用途不同**，故本模块把它们**分开实现**，各自写清口径：
 * - {@link isInPlatform} = 传输前置条件（"有没有可 `postMessage` 的父窗口"）——**不承担身份/安全判断**；
 * - {@link isEmbedMode} = **身份判定**（契约 3 的语义）；
 * - `closePage` 的门控用**前者 + `requestId` 存在**，与平台实现逐条对齐。
 *
 * ★ 为什么 `closePage` **不**改用 `isEmbedMode` 门控：remis-eova-ui 目前**没有** `systemCode` 概念
 * （r100 §三 已登记）⇒ `isEmbedMode()` **恒假** ⇒ 回传**永不触发**，功能直接死掉。
 * 这是"照抄设计口号会让功能失效"的一处，取舍写在这里并由判据钉住。
 *
 * ## 已声明适配（非静默改写）
 *
 * ① **告警出口可注入**（判据用；生产仍走 `console.warn`）；
 * ② **`isEmbedMode` 的"本地系统编码"走注入**（`setLocalSystemCode`）：平台从自己的 `@/utils/auth`
 *    读 `getSystemCode()`，本工程无该概念 ⇒ 默认空串 ⇒ 恒假（与 r100 登记一致）。注入口存在是为了
 *    日后真配了编码时**不必改结构**；
 * ③ 当 URL 上**有** `_sourceSystemCode` 而本地系统编码**未配置**时，`isEmbedMode()` 会**一次性告警**
 *    点名原因（返回值仍与平台一致为 `false`）—— 静默返回 false 会让"嵌入态没生效"无从诊断；
 * ④ `closePage` 的 `result` **原样透传、不做任何加工**（r125 查实：平台 `any`、无固定 schema）。
 *
 * ## ⚠️ 仍未实现（**不得当成已完成**）
 *
 * - **调用点**：哪个业务流程触发 `closePage` —— **不发明**。平台契约里它服务于
 *   `openCrossAppPage` 打开的弹窗流程（宿主按 `requestId` 匹配 pending 回调）；菜单 iframe 形态下
 *   URL 上**没有** `_requestId`，此时回传无接收方。待平台侧一条具体业务流程（r136 落地顺序第 4 步）。
 * - **`_accessToken`/`_tenantId` 消费 + `replaceState` 清除**（契约 1/2）：改的是登录/会话语义，
 *   属**独立单元**（见 DES-006 §1 非范围 1）。
 * - **DES-007 目标态**（精确 `postMessage` origin、`REMIS_EMBED_AUTH_READY`/`AUTH_FAILED`）：
 *   平台侧 `LC-012` **未做**；只按设计做 = 上线即断（风险 **R61**）。故本模块 `postMessage` 的目标
 *   origin **照抄平台现状 `'*'`**（`iframeBridge.ts:284`），**不单方面改成精确 origin** ——
 *   接收端未按目标态改造时，改了就是**回传静默丢失**。
 */

/** 关闭页面的消息类型（平台 `iframeBridge.ts:282`） */
export const PAGE_CLOSE_MESSAGE_TYPE = 'YUDAO_PAGE_CLOSE'

/** 调用方请求 ID 的 URL 参数名（平台 `iframeBridge.ts:206`） */
export const URL_PARAM_REQUEST_ID = '_requestId'

/** 调用来源系统编码的 URL 参数名（平台 `iframeBridge.ts:194`） */
export const URL_PARAM_SOURCE_SYSTEM_CODE = '_sourceSystemCode'

/** 内部参数前缀（`getCallParams` 排除所有以它开头的键；平台 `iframeBridge.ts:222`） */
export const INTERNAL_PARAM_PREFIX = '_'

/**
 * `postMessage` 的目标 origin（平台现状是 `'*'`，见文件头"仍未实现"第 3 条）。
 *
 * ★ 单独抽成常量而不是散在调用点：它是一条**待升级为精确 origin** 的契约项，
 * 判据要能断言"当前就是 `'*'`"，避免有人单方面改掉而无人察觉。
 */
export const POST_MESSAGE_TARGET_ORIGIN = '*'

/** 本模块用到的最小窗口面（判据可注入替身；生产用真实 `window`） */
export interface WindowLike {
  /** 父窗口（非 iframe 时等于自身） */
  parent?: unknown
  /** 位置面（只用到 `search`） */
  location?: { search?: string }
  /** 向父窗口发消息（宿主实现在父窗口上） */
  postMessage?: (message: unknown, targetOrigin: string) => void
}

/** 装配注入点 */
export interface IframeBridgeDeps {
  /** 窗口面（默认 `globalThis.window`） */
  win?: WindowLike
  /** 查询串（默认取 `win.location.search`；判据可注入） */
  search?: string
  /** 告警出口（默认 `console.warn`） */
  warn?: (message: string) => void
}

/** 子应用侧契约面（与平台 `IframeBridgeReturn` 的对应子集同名同形） */
export interface IframeBridge {
  /** 是否在平台环境（**传输前置条件**，不是身份判定 —— 见文件头） */
  isInPlatform: boolean
  /** 是否为嵌入调用态（契约 3 语义：`_sourceSystemCode` 与本系统编码都存在且不等） */
  isEmbedMode: () => boolean
  /** 关闭当前页面并回传结果（平台 `closePage`） */
  closePage: (result?: unknown) => void
  /** 取调用方传入的业务参数（排除 `_` 开头的内部参数） */
  getCallParams: () => Record<string, string>
  /** 取调用来源的系统编码 */
  getSourceSystemCode: () => string | null
  /** 取当前请求 ID */
  getRequestId: () => string | null
}

// ---- 本地系统编码（平台从 `@/utils/auth` 读；本工程无该概念 ⇒ 默认空串，见文件头适配 ②）----

/** 本工程的系统编码（未配置 ⇒ 空串 ⇒ `isEmbedMode()` 恒假） */
let localSystemCode = ''

/** 是否已就"`_sourceSystemCode` 存在但本地系统编码未配置"告警过（一次性，避免刷屏） */
let warnedMissingLocalSystemCode = false

/**
 * 设置本工程的系统编码（供日后接入平台系统编码时使用）。
 *
 * @param code 系统编码；传 null/空串表示清除
 */
export function setLocalSystemCode(code: string | null): void {
  localSystemCode = code == null ? '' : String(code)
}

/** 重置告警状态（判据用） */
export function resetIframeBridgeWarning(): void {
  warnedMissingLocalSystemCode = false
}

/**
 * 判定是否在平台环境 —— **只作传输前置条件**（"有没有可 postMessage 的父窗口"）。
 *
 * 与平台 `checkIsInPlatform()`（`iframeBridge.ts:97-103`）同口径：`window.parent !== window`，
 * 取不到父窗口（跨域受限等）时**返回 false**（旧实现就是 catch 后 false，不抛）。
 *
 * ★ **不得**用它承担身份/安全判断（契约 3 / DES-007 §5.2）—— 那用 {@link isEmbedMode}。
 *
 * @param win 窗口面（默认取 `globalThis.window`）
 * @returns 是否有可通信的父窗口
 */
export function isInPlatform(win: WindowLike | undefined = globalThis.window as never): boolean {
  try {
    const parent = win?.parent
    return parent != null && parent !== win
  } catch {
    // 旧实现：访问 `window.parent` 抛（跨域等）⇒ false
    return false
  }
}

/**
 * 解析查询串（默认取窗口 `location.search`）。
 *
 * @param deps 注入点
 * @returns `URLSearchParams`
 */
function searchOf(deps: IframeBridgeDeps): URLSearchParams {
  const raw =
    deps.search ??
    (deps.win?.location?.search as string | undefined) ??
    (typeof window === 'undefined' ? '' : window.location.search)
  return new URLSearchParams(raw)
}

/**
 * 取调用方请求 ID（平台 `getRequestId`，`iframeBridge.ts:204-211`）。
 *
 * @param deps 注入点
 * @returns `_requestId`；没有返回 null
 */
export function getRequestId(deps: IframeBridgeDeps = {}): string | null {
  try {
    return searchOf(deps).get(URL_PARAM_REQUEST_ID)
  } catch {
    return null
  }
}

/**
 * 取调用来源的系统编码（平台 `getSourceSystemCode`，`iframeBridge.ts:192-199`）。
 *
 * @param deps 注入点
 * @returns `_sourceSystemCode`；没有返回 null
 */
export function getSourceSystemCode(deps: IframeBridgeDeps = {}): string | null {
  try {
    return searchOf(deps).get(URL_PARAM_SOURCE_SYSTEM_CODE)
  } catch {
    return null
  }
}

/**
 * 取业务参数 —— 排除**所有**以 `_` 开头的内部参数（平台 `getCallParams`，`iframeBridge.ts:216-230`）。
 *
 * 语义细节（原样保留）：`URLSearchParams.forEach` 对同名键**逐个**回调 ⇒ 后值覆盖前值；
 * 值一律是**字符串**（空值成 `''`，不是 undefined）。
 *
 * @param deps 注入点
 * @returns 业务参数
 */
export function getCallParams(deps: IframeBridgeDeps = {}): Record<string, string> {
  const params: Record<string, string> = {}
  try {
    searchOf(deps).forEach((value, key) => {
      if (!key.startsWith(INTERNAL_PARAM_PREFIX)) {
        params[key] = value
      }
    })
  } catch {
    // 旧实现：ignore
  }
  return params
}

/**
 * 构造子应用侧契约面（等价平台 `useIframeBridge()` 的对应子集）。
 *
 * ★ 语义细节：`isInPlatform` 与平台一样在**构造时求值一次**（后续 `closePage` 闭包捕获它）——
 * 判据钉住这一条，避免有人改成每次调用都重算而与平台语义分叉。
 *
 * @param deps 注入点
 * @returns 契约面
 */
export function useIframeBridge(deps: IframeBridgeDeps = {}): IframeBridge {
  const warn = deps.warn ?? ((m: string) => console.warn(m))
  const win = deps.win ?? (globalThis.window as never as WindowLike)
  const isInPlatformValue = isInPlatform(win)

  /**
   * 是否处于嵌入调用态（契约 3 语义）。
   *
   * `!!(_sourceSystemCode && localSystemCode && 两者不等)` —— 三条缺一即 false（与平台
   * `iframeBridge.ts:178-185` 同一真值表），只是 `localSystemCode` 的来源不同（见文件头适配 ②）。
   */
  function isEmbedMode(): boolean {
    try {
      const source = getSourceSystemCode({ win, search: deps.search })
      if (!source) {
        return false
      }
      if (!localSystemCode) {
        // 一次性告警（返回值仍与平台一致 = false）—— 见文件头适配 ③
        if (!warnedMissingLocalSystemCode) {
          warnedMissingLocalSystemCode = true
          warn(
            '[iframeBridge] URL 上有 `_sourceSystemCode`，但本工程未配置系统编码' +
              '（`setLocalSystemCode`）⇒ `isEmbedMode()` 恒为 false。这不是"没被嵌入"，' +
              '而是"无从判定"；配好系统编码后即按契约 3 生效。'
          )
        }
        return false
      }
      return source !== localSystemCode
    } catch {
      return false
    }
  }

  /**
   * 关闭当前页面并返回结果（平台 `closePage`，`iframeBridge.ts:272-289`）。
   *
   * 两条失败分支都**只告警、不发消息**（与平台逐字一致，文案也一致）：
   * ① 不在平台环境；② 没有 `requestId`。
   *
   * @param result 回传结果（**任意值、原样透传**；r125 查实平台无固定 schema）
   */
  function closePage(result?: unknown): void {
    if (!isInPlatformValue) {
      warn('[iframeBridge] 当前不在平台环境中，closePage 无效')
      return
    }

    const requestId = getRequestId({ win, search: deps.search })

    if (requestId) {
      const parent = win.parent as WindowLike
      parent.postMessage?.(
        { type: PAGE_CLOSE_MESSAGE_TYPE, payload: { requestId, result } },
        POST_MESSAGE_TARGET_ORIGIN
      )
    } else {
      warn('[iframeBridge] 未找到 requestId，无法返回结果')
    }
  }

  return {
    isInPlatform: isInPlatformValue,
    isEmbedMode,
    closePage,
    getCallParams: () => getCallParams({ win, search: deps.search }),
    getSourceSystemCode: () => getSourceSystemCode({ win, search: deps.search }),
    getRequestId: () => getRequestId({ win, search: deps.search })
  }
}
