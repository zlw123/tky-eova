/**
 * 嵌入态入口参数消费的判据（阶段 3，第 297 轮；设计见 `docs/DES-006-R1-…`）。
 *
 * 钉的契约（每条都对应一种"凭据泄漏"或"两侧行为分叉"）：
 *  ① 契约 1：能从 URL 读出 `_sourceSystemCode`/`_accessToken`/`_tenantId`/`_requestId`；
 *  ② 契约 2：消费后 `replaceState` 清掉 `_accessToken`/`_tenantId`，**保留 `_sourceSystemCode`**；
 *  ③ ★ **只删该删的**：其它 `_` 开头的内部参数与全部业务参数原样保留（顺序不变）——
 *     `_requestId` 尤其不能丢，它是**关闭回传**的唯一关联键；
 *  ④ ★ **反向**：没有 `_accessToken` 时，`replaceState` **一次都不许调**（连 `_tenantId` 也不删）——
 *     这是平台 `iframeAuth.ts:104-117` 的既有分支语义，不是"漏删"；
 *  ⑤ ★ **凭据不得进日志**：`info` 载荷序列化后**不得出现 token 原值**（平台同纪律：只打布尔）；
 *  ⑥ 出错只告警不抛（平台 `catch` 后 `return false`）；
 *  ⑦ **跨制品等值**（可选）：平台源码可读时核对"删哪两个参数"与"保留 `_sourceSystemCode`"的注释；
 *     读不到 ⇒ `skipped`（= `not executed`，**不得算通过**）。
 */
import { existsSync, readFileSync } from 'node:fs'
import { describe, expect, it, vi } from 'vitest'
import {
  KEPT_INTERNAL_PARAM_NAMES,
  SENSITIVE_PARAM_NAMES,
  SKIP_REASON_NO_ACCESS_TOKEN,
  consumeEmbedEntry,
  readEmbedEntry,
  stripSensitiveParams
} from '../embed-entry'

/** 平台源码（只读；不存在则跨制品判据 `skipped`） */
const PLATFORM_AUTH =
  process.env['YUDAO_PLATFORM_AUTH'] ??
  '/Users/zhouliwei/git_206/platform/fornt/yudao-ui/src/utils/iframeAuth.ts'
const HAS_PLATFORM_SOURCE = existsSync(PLATFORM_AUTH)

/** 判据用的假 token（★ 断言"它不得出现在日志里"） */
const TOKEN = 'tok-DEADBEEF-0123456789'

/**
 * 造一个可控窗口面（`href` 与 `search` 保持一致）
 *
 * @param search 查询串（含 `?`）
 * @returns `{ win, replaceState }`
 */
function makeWin(search: string): {
  win: Record<string, unknown>
  replaceState: ReturnType<typeof vi.fn>
} {
  const replaceState = vi.fn()
  const win = {
    location: {
      search,
      href: `http://localhost:9091/app/meta_menu${search}`,
      pathname: '/app/meta_menu'
    },
    history: { replaceState }
  }
  return { win, replaceState }
}

describe('embed-entry · 读取（契约 1）', () => {
  it('读出四个键；缺则 null', () => {
    const search =
      `?_accessToken=${TOKEN}&_tenantId=7&_sourceSystemCode=REMIS&_requestId=r-1`
    expect(readEmbedEntry(search)).toEqual({
      accessToken: TOKEN,
      tenantId: '7',
      sourceSystemCode: 'REMIS',
      requestId: 'r-1'
    })
    expect(readEmbedEntry('?biz=x')).toEqual({
      accessToken: null,
      tenantId: null,
      sourceSystemCode: null,
      requestId: null
    })
  })
})

describe('embed-entry · 清理哪些参数（契约 2/③）', () => {
  it('★ 只删 `_accessToken`/`_tenantId`，其余（含 `_sourceSystemCode`/`_requestId`/业务参数）保持原序', () => {
    const search = `?biz=meta_menu&_accessToken=${TOKEN}&id=7&_tenantId=9&_sourceSystemCode=REMIS&_requestId=r-1`
    expect(stripSensitiveParams(search)).toBe('?biz=meta_menu&id=7&_sourceSystemCode=REMIS&_requestId=r-1')
    // 常量与断言双向一致（防"改了常量、断言还用字面量"的自证）
    expect(SENSITIVE_PARAM_NAMES).toEqual(['_accessToken', '_tenantId'])
    expect(KEPT_INTERNAL_PARAM_NAMES).toContain('_sourceSystemCode')
    expect(KEPT_INTERNAL_PARAM_NAMES).toContain('_requestId')
  })

  it('全删空 ⇒ 返回空串（不是孤零零的 `?`）', () => {
    expect(stripSensitiveParams(`?_accessToken=${TOKEN}&_tenantId=1`)).toBe('')
  })
})

describe('embed-entry · 消费（契约 2 正向 + ④ 反向）', () => {
  it('★ 有 token ⇒ replaceState 一次，且新 URL 里**没有**敏感参数、**有**保留参数', () => {
    const search = `?biz=meta_menu&_accessToken=${TOKEN}&_tenantId=9&_sourceSystemCode=REMIS&_requestId=r-1`
    const { win, replaceState } = makeWin(search)
    const result = consumeEmbedEntry({ win, info: vi.fn(), warn: vi.fn() })

    expect(result.stripped).toBe(true)
    expect(result.skippedReason).toBeNull()
    expect(replaceState).toHaveBeenCalledTimes(1)
    const nextUrl = String(replaceState.mock.calls[0][2])
    expect(nextUrl).not.toContain('_accessToken')
    expect(nextUrl).not.toContain(TOKEN)
    expect(nextUrl).not.toContain('_tenantId')
    // ★ 契约 2 的"保留"侧：这两个必须还在（嵌入态判定 + 关闭回传都靠它们）
    expect(nextUrl).toContain('_sourceSystemCode=REMIS')
    expect(nextUrl).toContain('_requestId=r-1')
    expect(nextUrl).toContain('biz=meta_menu')
  })

  it('★ 反向：没有 `_accessToken` ⇒ **一次都不 replaceState**，即使 URL 上有 `_tenantId`', () => {
    const search = '?_tenantId=9&_sourceSystemCode=REMIS'
    const { win, replaceState } = makeWin(search)
    const result = consumeEmbedEntry({ win, info: vi.fn(), warn: vi.fn() })

    expect(replaceState).not.toHaveBeenCalled()
    expect(result.stripped).toBe(false)
    // 平台 `iframeAuth.ts:128` 的 reason 原文
    expect(result.skippedReason).toBe('missing_access_token')
    expect(SKIP_REASON_NO_ACCESS_TOKEN).toBe('missing_access_token')
  })

  it('★ ⑤ 凭据不得进日志：两个分支的 info 载荷序列化后都不含 token 原值', () => {
    const seen: unknown[] = []
    const info = vi.fn((_m: string, detail?: unknown) => seen.push(detail))

    const withToken = makeWin(`?_accessToken=${TOKEN}&_sourceSystemCode=REMIS`)
    consumeEmbedEntry({ win: withToken.win, info, warn: vi.fn() })

    const noToken = makeWin('?_sourceSystemCode=REMIS')
    consumeEmbedEntry({ win: noToken.win, info, warn: vi.fn() })

    // 反空：必须真的打了日志（否则"没有 token"是因为什么都没记）
    expect(info).toHaveBeenCalledTimes(4) // start/success + start/skip
    for (const detail of seen) {
      expect(JSON.stringify(detail), '日志载荷里出现了 token 原值').not.toContain(TOKEN)
    }
    // 但"有没有 token"这件事必须可诊断
    expect(JSON.stringify(seen[0])).toContain('"hasAccessToken":true')
    expect(JSON.stringify(seen[2])).toContain('"hasAccessToken":false')
  })
})

describe('embed-entry · 出错路径（⑥）', () => {
  it('replaceState 抛 ⇒ 只告警、不抛、`stripped=false` 且 reason=strip_failed', () => {
    const search = `?_accessToken=${TOKEN}`
    const { win } = makeWin(search)
    ;(win['history'] as { replaceState: unknown }).replaceState = () => {
      throw new Error('SecurityError: replaceState denied')
    }
    const warn = vi.fn()

    let result: ReturnType<typeof consumeEmbedEntry> | null = null
    expect(() => {
      result = consumeEmbedEntry({ win, warn, info: vi.fn() })
    }).not.toThrow()
    expect(result!.stripped).toBe(false)
    expect(result!.skippedReason).toBe('strip_failed')
    expect(warn).toHaveBeenCalledWith(expect.stringContaining('[embed-entry][restore][error]'))
    // 参数仍要交回调用方（读到了就是读到了，不因清理失败而丢）
    expect(result!.params.accessToken).toBe(TOKEN)
  })
})

/**
 * ★ 跨制品等值（⑦）：平台源码不在本机时本 describe **跳过**（= `not executed`，三态第二态）。
 */
describe.skipIf(!HAS_PLATFORM_SOURCE)('embed-entry · 跨制品等值（平台源码）', () => {
  it('★ "删哪两个参数"与平台源码逐字一致', () => {
    const src = readFileSync(PLATFORM_AUTH, 'utf-8')
    const deleted = [...src.matchAll(/searchParams\.delete\('([^']+)'\)/g)].map((m) => m[1])
    // 反空：抽不到就说明抽取规则失效 ⇒ 不能"因为找不到而通过"
    expect(deleted.length, '未从平台源码抽到 searchParams.delete（规则可能已失效）').toBeGreaterThan(0)
    expect(deleted).toEqual([...SENSITIVE_PARAM_NAMES])
  })

  it('★ 平台源码里确实写着"保留 `_sourceSystemCode`"（本模块 KEPT 常量的依据）', () => {
    const src = readFileSync(PLATFORM_AUTH, 'utf-8')
    expect(src).toMatch(/保留\s*_sourceSystemCode/)
    expect(src).toContain('replaceState')
  })

  it('★ 平台也是在"有 accessToken"分支里才清理（本模块 ④ 的依据）', () => {
    const src = readFileSync(PLATFORM_AUTH, 'utf-8')
    // `if (accessToken) {` 出现在第一个 delete 之前
    const gateAt = src.search(/if\s*\(\s*accessToken\s*\)\s*\{/)
    const firstDeleteAt = src.indexOf("searchParams.delete('_accessToken')")
    expect(gateAt, '未找到 `if (accessToken)` 门控').toBeGreaterThanOrEqual(0)
    expect(firstDeleteAt).toBeGreaterThan(gateAt)
    // 且 skip 分支带 reason（文案同源）
    expect(src).toContain("reason: 'missing_access_token'")
  })
})
