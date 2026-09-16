/**
 * DES-012 P4-(d) 判据：**附件基址必须同源**（不得指向旧栈 9090）。
 *
 * 实测缺口（r332）：制品 `eovaui.js` 内 `me.conf` 的默认值是
 * `{web_file:"http://127.0.0.1:9090"}`（旧栈），而 EvUpload 的预览/下载基址取
 * `prop.file_down || conf.get("web_cdn") || conf.get("web_file")` ⇒ 不干预就会请求旧栈。
 */
import { readFileSync } from 'node:fs'
import { describe, expect, it, vi } from 'vitest'
import { seedSameOriginBase } from '../ui-conf'

describe('ui-conf · 同源基址兜底（P4-(d)）', () => {
  it('★ 行为：把 web_file 覆盖为同源（且不含旧栈 9090）', () => {
    const putAll = vi.fn()
    const ok = seedSameOriginBase({ me: { conf: { putAll } }, origin: 'http://localhost:48090' })
    expect(ok).toBe(true)
    expect(putAll).toHaveBeenCalledTimes(1)
    const payload = putAll.mock.calls[0][0] as string
    expect(payload).toContain('"web_file":"http://localhost:48090"')
    expect(payload).not.toContain('9090')
  })

  it('边界：无 putAll / 非 http(s) 源 ⇒ 不写（不抛错）', () => {
    expect(seedSameOriginBase({ me: {}, origin: 'http://x' })).toBe(false)
    expect(seedSameOriginBase({ me: { conf: { putAll: vi.fn() } }, origin: '' })).toBe(false)
    expect(seedSameOriginBase({ me: { conf: { putAll: vi.fn() } }, origin: 'file:///tmp/x' })).toBe(false)
  })

  it('★ 顺序：main.ts 必须在 loadUiConf 之前调用兜底（真 conf 来源仍可覆盖）', () => {
    const main = readFileSync('src/main.ts', 'utf8')
    const iSeed = main.indexOf('seedSameOriginBase()')
    const iLoad = main.indexOf('await loadUiConf()')
    expect(iSeed).toBeGreaterThan(-1)
    expect(iLoad).toBeGreaterThan(-1)
    expect(iSeed).toBeLessThan(iLoad)
  })

  it('★ 事实记录 + 反向：制品里确有旧栈 9090（漂移源），而我们非冻结源码零处硬编码 127.0.0.1:9xxx', () => {
    const frozen = readFileSync('src/legacy/eova/lib/eova/eovaui.js', 'utf8')
    expect(frozen).toContain('web_file:"http://127.0.0.1:9090"')

    // ★ 必须**先剥注释**再扫描：本文件与 ui-conf.ts 的注释里都**引用**了那个 9090 作为证据，
    //   直接扫原文会把"记录事实的注释"当成违规（本项目在 Java 侧同样有此纪律）。
    const stripComments = (src: string): string =>
      src.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/[^\n]*/g, '$1')
    const ours = ['src/compat/ui-conf.ts', 'src/compat/eova-runtime.ts', 'src/compat/legacy-runtime.ts', 'src/main.ts']
    for (const p of ours) {
      expect(/127\.0\.0\.1:9\d{3}/.test(stripComments(readFileSync(p, 'utf8')))).toBe(false)
    }
  })
})
