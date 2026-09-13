/**
 * **主题落地判据（r311）**：`eova-theme_<name>` 类 + 主题 CSS 必须真的落地，且**主题名与冻结资产同源**。
 *
 * 为什么需要（实测取证，不是推演）：
 * 旧栈 `_eova/theme/eova.theme.js` 是 `<head>` 里的**经典同步脚本**，全部逻辑在
 * `document.addEventListener('DOMContentLoaded', …)` 里 —— 解析期注册 ⇒ 一定赶得上。
 * SPA 的资产由**异步装配器**动态插入 ⇒ 实测 `readyState` 已是 `complete` 时脚本才执行，
 * **监听永不触发**：`body.className === ""`、主题 CSS 一个字节都没请求（旧栈相反）。
 * 故宿主在装配后确定性落地（`applyLegacyTheme`），本判据钉住它的行为与"不自己发明主题名"。
 */
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it, vi } from 'vitest'
import {
  LEGACY_RUNTIME_SCRIPTS,
  LEGACY_THEME_NAME,
  applyLegacyTheme,
  parseThemeName
} from '../legacy-runtime'

/** 冻结主题资产路径（工程内唯一事实源） */
const THEME_ASSET = resolve(__dirname, '../../legacy/_eova/theme/eova.theme.js')

/**
 * 造一个最小的假文档
 *
 * @param readyState `document.readyState` 的值
 * @returns 假文档 + 记录用的挂载点
 */
function fakeDoc(readyState: 'loading' | 'complete') {
  const listeners: Array<() => void> = []
  const links: Array<{ rel: string; href: string; getAttribute: (n: string) => string | null }> = []
  const body = {
    classList: {
      _s: new Set<string>(),
      add(c: string) {
        this._s.add(c)
      },
      contains(c: string) {
        return this._s.has(c)
      }
    }
  }
  const head = {
    appendChild(el: unknown) {
      links.push(el as never)
    }
  }
  const doc = {
    readyState,
    body,
    head,
    createElement: () => ({ rel: '', href: '', getAttribute: () => null }),
    querySelectorAll: () => links,
    addEventListener: (_e: string, cb: () => void) => listeners.push(cb)
  }
  return { doc, body, links, fire: () => listeners.forEach((cb) => cb()) }
}

describe('主题落地（eova.theme.js 的 SPA 时序适配）', () => {
  it('★ 防漂移：主题名必须与**冻结资产原文**一致（资产改了而常量没跟 ⇒ 本判据红）', () => {
    const text = readFileSync(THEME_ASSET, 'utf-8')
    const parsed = parseThemeName(text)
    expect(parsed, `无法从冻结资产解析 EOVA_THEME：${THEME_ASSET}`).not.toBeNull()
    expect(parsed).toBe(LEGACY_THEME_NAME)
    // 资产自身的行为（供后人对照：这两件事就是宿主在补的）
    expect(text).toContain('DOMContentLoaded')
    expect(text).toContain('eova-theme_')
  })

  it('★ 主题资产必须在装配清单里（缺它 = 主题类与主题 CSS 都不会出现）', () => {
    expect(LEGACY_RUNTIME_SCRIPTS).toContain('/_eova/theme/eova.theme.js')
    // 与旧 `_eova/include.html` 的相对顺序一致：主题脚本在扩展资产**之前**
    const iTheme = LEGACY_RUNTIME_SCRIPTS.indexOf('/_eova/theme/eova.theme.js')
    const iExt = LEGACY_RUNTIME_SCRIPTS.indexOf('/_eova/assets/eova.ui.ext.js')
    expect(iTheme).toBeGreaterThanOrEqual(0)
    expect(iExt).toBeGreaterThan(iTheme)
  })

  it('文档已就绪（readyState=complete）⇒ 立即加类 + 加载主题 CSS', () => {
    const { doc, body } = fakeDoc('complete')
    const loadCss = vi.fn()
    applyLegacyTheme({ doc: doc as never, loadCss })
    expect(body.classList.contains(`eova-theme_${LEGACY_THEME_NAME}`)).toBe(true)
    expect(loadCss).toHaveBeenCalledWith(`/_eova/theme/eova.theme.${LEGACY_THEME_NAME}.css`)
  })

  it('文档仍在解析（readyState=loading）⇒ 交给 DOMContentLoaded，不抢先插入', () => {
    const { doc, body, fire } = fakeDoc('loading')
    const loadCss = vi.fn()
    applyLegacyTheme({ doc: doc as never, loadCss })
    expect(loadCss).not.toHaveBeenCalled()
    expect(body.classList.contains(`eova-theme_${LEGACY_THEME_NAME}`)).toBe(false)
    fire()
    expect(body.classList.contains(`eova-theme_${LEGACY_THEME_NAME}`)).toBe(true)
    expect(loadCss).toHaveBeenCalledTimes(1)
  })

  it('幂等：重复调用不再插第二份 CSS（装配可能因热更新重入）', () => {
    const { doc, body, links } = fakeDoc('complete')
    const loadCss = vi.fn((href: string) => {
      links.push({ rel: 'stylesheet', href, getAttribute: (n: string) => (n === 'href' ? href : null) })
    })
    applyLegacyTheme({ doc: doc as never, loadCss })
    applyLegacyTheme({ doc: doc as never, loadCss })
    expect(loadCss).toHaveBeenCalledTimes(1)
    expect(body.classList.contains(`eova-theme_${LEGACY_THEME_NAME}`)).toBe(true)
  })

  it('没有制品的 loadCSS 时退回插入 <link>（不静默不落地）', () => {
    const { doc, links } = fakeDoc('complete')
    applyLegacyTheme({ doc: doc as never, loadCss: undefined as never })
    // 假文档的 createElement 返回可读 href 的对象；这里只断言"确实插了一份"
    expect(links.length).toBe(1)
  })
})
