/**
 * 平台 iframe **宿主契约消费端**的判据（阶段 3，第 296 轮；设计见 `docs/DES-006-R1-…`）。
 *
 * 钉的契约（每条都对应一种"编译绿、单测绿、集成当天才发现"的断链）：
 *  ① **关闭消息逐字**：`{type:'YUDAO_PAGE_CLOSE', payload:{requestId, result}}` + 目标 origin 常量
 *     —— 平台侧 `IframeBridge.vue` 是按 `type` 分发、按 `payload.requestId` 查 pending 的，
 *     任一处改字都会**静默不回传**；
 *  ② **失败分支两向**：不在平台环境 / 没有 `_requestId` 各**只告警不发消息**；
 *     ★ 同时断言"正常态**必须发**"—— 否则"永远不发"也能让前两条通过（空转断言）；
 *  ③ `result` **原样透传**（含 `undefined`/`null`：键必须在），r125 查实平台无固定 schema；
 *  ④ `getCallParams()` 排除**所有** `_` 开头的键（含 `_requestId`/`_sourceSystemCode`）；
 *  ⑤ `isEmbedMode()` 的真值表（契约 3 的三条件与）—— 四组输入逐组断言，含"未配本地系统编码 ⇒ false
 *     且**一次性告警**"；
 *  ⑥ **快照 vs 实时**：`isInPlatform` 与平台一样**构造时求值一次**；而 `getRequestId()` 等
 *     与平台一样**每次实时读** —— 两种语义各自钉住，改错任一边都会红；
 *  ⑦ **跨制品等值**（可选）：平台源码可读时，逐条核对协议字面量与门控分支；
 *     **读不到 ⇒ `skipped`（= `not executed`），不得算通过**。
 */
import { existsSync, readFileSync } from 'node:fs'
import { describe, expect, it, vi } from 'vitest'
import {
  INTERNAL_PARAM_PREFIX,
  PAGE_CLOSE_MESSAGE_TYPE,
  POST_MESSAGE_TARGET_ORIGIN,
  URL_PARAM_REQUEST_ID,
  URL_PARAM_SOURCE_SYSTEM_CODE,
  getCallParams,
  getRequestId,
  getSourceSystemCode,
  isInPlatform,
  resetIframeBridgeWarning,
  setLocalSystemCode,
  useIframeBridge
} from '../iframe-bridge'

/** 平台源码（只读；不存在则跨制品判据 `skipped`） */
const PLATFORM_BRIDGE =
  process.env['YUDAO_PLATFORM_BRIDGE'] ??
  '/Users/zhouliwei/git_206/platform/fornt/yudao-ui/src/utils/iframeBridge.ts'
const HAS_PLATFORM_SOURCE = existsSync(PLATFORM_BRIDGE)

/**
 * 造一个可控窗口面
 *
 * ★ `window.parent === window` 是"非 iframe"的**同一性**判断 ⇒ 非嵌入态必须让 `parent`
 * 指向**同一个对象**（第一版写成另一个对象，于是被误判成"在平台环境"，判据当场抓到）。
 *
 * @param inIframe 是否有**不同于自身**的父窗口
 * @param search 查询串
 * @returns `{ win, postMessage }`
 */
function makeWin(
  inIframe: boolean,
  search = ''
): { win: Record<string, unknown>; postMessage: ReturnType<typeof vi.fn> } {
  const postMessage = vi.fn()
  const win: Record<string, unknown> = { location: { search } }
  win['parent'] = inIframe ? { postMessage } : win
  return { win, postMessage }
}

describe('iframe-bridge · 关闭消息形态（①③）', () => {
  it('★ 逐字：type=YUDAO_PAGE_CLOSE + payload.{requestId,result}，且目标 origin 是平台现状的 \'*\'', () => {
    resetIframeBridgeWarning()
    const { win, postMessage } = makeWin(true, `?${URL_PARAM_REQUEST_ID}=r-1`)
    const warn = vi.fn()
    const bridge = useIframeBridge({ win, warn })

    bridge.closePage({ ok: true })

    expect(postMessage).toHaveBeenCalledTimes(1)
    expect(postMessage).toHaveBeenCalledWith(
      { type: 'YUDAO_PAGE_CLOSE', payload: { requestId: 'r-1', result: { ok: true } } },
      '*'
    )
    // 常量与字面量双向一致（防"常量改了、断言还用字面量"这种自证）
    expect(PAGE_CLOSE_MESSAGE_TYPE).toBe('YUDAO_PAGE_CLOSE')
    expect(POST_MESSAGE_TARGET_ORIGIN).toBe('*')
    expect(URL_PARAM_REQUEST_ID).toBe('_requestId')
    expect(warn).not.toHaveBeenCalled()
  })

  it('★ ③ result 原样透传：undefined/null/0/\'\'/字符串/对象都【带键】进 payload', () => {
    const cases: unknown[] = [undefined, null, 0, '', 'text', { a: [1, 2] }, false]
    for (const result of cases) {
      resetIframeBridgeWarning()
      const { win, postMessage } = makeWin(true, `?${URL_PARAM_REQUEST_ID}=r`)
      useIframeBridge({ win }).closePage(result)
      const payload = (postMessage.mock.calls[0][0] as { payload: Record<string, unknown> }).payload
      expect('result' in payload, `result=${String(result)} 时键丢失`).toBe(true)
      expect(payload['result']).toEqual(result)
    }
  })
})

describe('iframe-bridge · 失败分支两向（②）', () => {
  it('不在平台环境 ⇒ 只告警（逐字文案）、**不发消息**', () => {
    resetIframeBridgeWarning()
    const { win, postMessage } = makeWin(false, `?${URL_PARAM_REQUEST_ID}=r-1`)
    const warn = vi.fn()
    const bridge = useIframeBridge({ win, warn })

    bridge.closePage(1)

    expect(warn).toHaveBeenCalledWith('[iframeBridge] 当前不在平台环境中，closePage 无效')
    expect(postMessage).not.toHaveBeenCalled()
  })

  it('在平台环境但没有 `_requestId` ⇒ 只告警（逐字文案）、**不发消息**', () => {
    resetIframeBridgeWarning()
    const { win, postMessage } = makeWin(true, '?biz=x')
    const warn = vi.fn()
    const bridge = useIframeBridge({ win, warn })

    bridge.closePage(1)

    expect(warn).toHaveBeenCalledWith('[iframeBridge] 未找到 requestId，无法返回结果')
    expect(postMessage).not.toHaveBeenCalled()
  })

  it('★ 反向：正常态**必须发**（否则前两条会被"永远不发"满足 —— 空转断言）', () => {
    resetIframeBridgeWarning()
    const { win, postMessage } = makeWin(true, `?${URL_PARAM_REQUEST_ID}=ok`)
    const warn = vi.fn()
    useIframeBridge({ win, warn }).closePage()
    expect(postMessage).toHaveBeenCalledTimes(1)
    expect(warn).not.toHaveBeenCalled()
  })
})

describe('iframe-bridge · 调用参数（④）', () => {
  it('★ 排除【所有】`_` 开头的键（含 _requestId/_sourceSystemCode/_accessToken 家族）', () => {
    const search =
      '?biz=meta_menu&id=7&_requestId=r-1&_sourceSystemCode=REMIS&_accessToken=t&_tenantId=1&_x=z'
    expect(getCallParams({ search })).toEqual({ biz: 'meta_menu', id: '7' })
    expect(INTERNAL_PARAM_PREFIX).toBe('_')
  })

  it('同名键后值覆盖前值；空值成空串（不是 undefined）', () => {
    expect(getCallParams({ search: '?a=1&a=2&b=' })).toEqual({ a: '2', b: '' })
  })

  it('getRequestId / getSourceSystemCode：有则返回，无则 null', () => {
    expect(getRequestId({ search: '?_requestId=r1' })).toBe('r1')
    expect(getRequestId({ search: '?x=1' })).toBeNull()
    expect(getSourceSystemCode({ search: '?_sourceSystemCode=REMIS' })).toBe('REMIS')
    expect(getSourceSystemCode({ search: '?x=1' })).toBeNull()
  })
})

describe('iframe-bridge · 嵌入态判定真值表（⑤，契约 3）', () => {
  const cases: [string, string | null, string, boolean][] = [
    ['无 _sourceSystemCode（普通打开）', null, 'EOVA', false],
    ['有 source 但未配本地编码（★ r100 登记的现状）', 'REMIS', '', false],
    ['source 与本地编码相同（自己人）', 'EOVA', 'EOVA', false],
    ['source 与本地编码不同（真嵌入）', 'REMIS', 'EOVA', true]
  ]

  for (const [name, source, local, expected] of cases) {
    it(`${name} ⇒ ${expected}`, () => {
      resetIframeBridgeWarning()
      setLocalSystemCode(local)
      try {
        const search = source == null ? '' : `?${URL_PARAM_SOURCE_SYSTEM_CODE}=${source}`
        const bridge = useIframeBridge({ search, warn: vi.fn() })
        expect(bridge.isEmbedMode()).toBe(expected)
      } finally {
        setLocalSystemCode(null)
      }
    })
  }

  it('★ "有 source 但未配本地编码" 必须**一次性告警**点名原因（返回值仍为 false）', () => {
    resetIframeBridgeWarning()
    setLocalSystemCode(null)
    try {
      const warn = vi.fn()
      const bridge = useIframeBridge({ search: '?_sourceSystemCode=REMIS', warn })
      expect(bridge.isEmbedMode()).toBe(false)
      expect(bridge.isEmbedMode()).toBe(false)
      // 只告警一次（刷屏会让日志失去诊断价值），且文案点名了 `setLocalSystemCode`
      expect(warn).toHaveBeenCalledTimes(1)
      expect(String(warn.mock.calls[0][0])).toContain('setLocalSystemCode')
    } finally {
      setLocalSystemCode(null)
    }
  })
})

describe('iframe-bridge · 快照 vs 实时（⑥）', () => {
  it('★ isInPlatform **构造时求值一次**（与平台同语义）：构造后父窗口变化不影响 closePage', () => {
    resetIframeBridgeWarning()
    const postMessage = vi.fn()
    const win: Record<string, unknown> = { location: { search: '?_requestId=r' } }
    // 初始"不在 iframe 里"：`parent` 指向**自身**（同一性判断）
    let parent: unknown = win
    Object.defineProperty(win, 'parent', { get: () => parent, configurable: true })

    const bridge = useIframeBridge({ win, warn: vi.fn() })
    expect(bridge.isInPlatform).toBe(false)

    // 构造之后才"进入 iframe"
    parent = { postMessage }
    const bridge2 = useIframeBridge({ win, warn: vi.fn() })
    // bridge（快照 false）仍不得发消息
    bridge.closePage(1)
    expect(postMessage).not.toHaveBeenCalled()
    // 而重新构造的 bridge2 看到的是新的父窗口
    expect(bridge2.isInPlatform).toBe(true)
    bridge2.closePage(1)
    expect(postMessage).toHaveBeenCalledTimes(1)
  })

  it('★ getRequestId 等**每次实时读**（与平台同语义）：定位串变化立即可见', () => {
    const loc = { search: '?_requestId=a' }
    const postMessage = vi.fn()
    const win = { parent: { postMessage }, location: loc }
    const bridge = useIframeBridge({ win })
    expect(bridge.getRequestId()).toBe('a')
    loc.search = '?_requestId=b'
    expect(bridge.getRequestId()).toBe('b')
    bridge.closePage(1)
    expect((postMessage.mock.calls[0][0] as { payload: { requestId: string } }).payload.requestId).toBe(
      'b'
    )
  })
})

describe('iframe-bridge · isInPlatform 的边界', () => {
  it('父窗口取不到（访问抛）⇒ false（旧实现 catch 后 false，不抛）', () => {
    const win = {
      get parent(): unknown {
        throw new Error('cross-origin')
      }
    }
    expect(isInPlatform(win)).toBe(false)
  })

  it('parent 为 null/undefined ⇒ false', () => {
    expect(isInPlatform({ parent: null })).toBe(false)
    expect(isInPlatform({})).toBe(false)
  })
})

/**
 * ★ 跨制品等值（⑦）：直接从平台源码里**抽**协议字面量，再与本模块的常量/文案比。
 *
 * 平台源码不在本机时本 describe **跳过**（= `not executed`，三态里的第二态，**不得算通过**）。
 */
describe.skipIf(!HAS_PLATFORM_SOURCE)('iframe-bridge · 跨制品等值（平台源码）', () => {
  /** 读平台源码（判据内每次都重读，避免"读一次缓存导致变更不可见"） */
  function platformSrc(): string {
    return readFileSync(PLATFORM_BRIDGE, 'utf-8')
  }

  it('协议字面量与平台源码逐条一致（含两个 URL 参数名与 `_` 前缀）', () => {
    const src = platformSrc()
    const hits = {
      closeType: /type:\s*'([A-Z_]+)'\s*,\s*\n\s*payload:\s*\{\s*requestId,\s*result\s*\}/.exec(src),
      requestId: /urlParams\.get\('(_requestId)'\)/.exec(src),
      sourceSystemCode: /urlParams\.get\('(_sourceSystemCode)'\)/.exec(src),
      callParamsSkip: /key\.startsWith\('(_)'\)/.exec(src)
    }
    // 反空：抽不到就说明抽取规则失效 ⇒ 判据不能"因为找不到而通过"
    for (const [k, m] of Object.entries(hits)) {
      expect(m, `未从平台源码抽到 ${k}（抽取规则可能已失效）`).toBeTruthy()
    }
    expect(hits.closeType![1]).toBe(PAGE_CLOSE_MESSAGE_TYPE)
    expect(hits.requestId![1]).toBe(URL_PARAM_REQUEST_ID)
    expect(hits.sourceSystemCode![1]).toBe(URL_PARAM_SOURCE_SYSTEM_CODE)
    expect(hits.callParamsSkip![1]).toBe(INTERNAL_PARAM_PREFIX)
  })

  it('★ 两条失败分支的文案与平台源码逐字一致（平台侧靠文案排查，改了就对不上日志）', () => {
    const src = platformSrc()
    expect(src).toContain('当前不在平台环境中，closePage 无效')
    expect(src).toContain('未找到 requestId，无法返回结果')
  })

  it('★ 目标 origin 与平台现状一致（`\'*\'`）；platform 侧若已升级为精确 origin ⇒ 本判据要跟着改', () => {
    const src = platformSrc()
    const m = /\},\s*'(\*|https?:[^']*)'\s*\)/.exec(src)
    expect(m, '未从平台源码抽到 postMessage 的目标 origin').toBeTruthy()
    expect(m![1], '★ 平台侧 origin 口径已变，DES-006 §3 D4 需同步复核').toBe(
      POST_MESSAGE_TARGET_ORIGIN
    )
  })
})
