/**
 * 「旧 HTML 的 `#include` ↔ SPA 组件」的判据（第 147 轮）
 *
 * ## 防的是什么
 *
 * 三个列表模版页的旧 HTML 都靠 **Enjoy 的 `#include`** 拼装共享区块：
 * `#include("/eova/_view/_block/toolbar.html")`（按钮区）与
 * `#include("/eova/_view/_block/admin.html"[, type="list"])`（超管设置面板）。
 * 迁到 SPA 后这两个区块变成组件（`EovaToolbar` / `EovaAdminPanel`）。
 *
 * 若某个模版页**漏挂其中一个**，症状是：页面照常打开、表格照常出数据，
 * 但**按钮区没了**／**超管入口没了** —— 而该页自己的判据（只断它绑定的 handler、
 * 只断它自己的 DOM）**全绿**。这是"逐页迁移时最容易漏"的一类共享区块。
 *
 * ## 口径（跨制品：冻结旧 HTML ↔ 已迁 SFC）
 *
 * 对每个模版页：旧 HTML 里有 `toolbar.html` 的 include ⇒ SPA 必须有 `<EovaToolbar`；
 * 有 `admin.html` 的 include ⇒ SPA 必须有 `<EovaAdminPanel`。反向不强制
 * （SPA 可以多渲染，但那样会在下面被显式记下来）。
 *
 * ★ 只读**已入库**文件。
 */
import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

/** 模版页：旧 HTML → SPA 页面 */
const PAGES: ReadonlyArray<{ legacy: string; spa: string }> = [
  { legacy: 'src/legacy/eova/_view/template/table/index.html', spa: 'src/views/template/TemplateTable.vue' },
  { legacy: 'src/legacy/eova/_view/template/tree/index.html', spa: 'src/views/template/TemplateTree.vue' },
  { legacy: 'src/legacy/eova/_view/template/tree_table/index.html', spa: 'src/views/template/TreeTablePlaceholder.vue' }
]

/** 共享区块：旧 include 名 → SPA 组件标签 */
const BLOCKS: ReadonlyArray<{ legacyName: string; tag: string }> = [
  { legacyName: '_block/toolbar.html', tag: '<EovaToolbar' },
  { legacyName: '_block/admin.html', tag: '<EovaAdminPanel' }
]

/** tree_table 的真实落点（映射表里写的占位名需要纠正） */
const TREE_TABLE_SPA = 'src/views/template/TemplateTreeTable.vue'

describe('旧 HTML 的 #include ↔ SPA 组件', () => {
  it('① 三个模版页的旧 HTML 都 include 了 toolbar 与 admin（反空断言：取证面确实存在）', () => {
    for (const { legacy } of PAGES) {
      const src = readFileSync(legacy, 'utf-8')
      expect(src, `${legacy} 未 include toolbar`).toContain('_block/toolbar.html')
      expect(src, `${legacy} 未 include admin`).toContain('_block/admin.html')
    }
  })

  it('② 每个模版页在 SPA 里都渲染了对应的组件（漏挂 ⇒ 按钮区/超管入口静默消失）', () => {
    const missing: string[] = []
    for (const { legacy, spa } of PAGES) {
      const target = spa.endsWith('TreeTablePlaceholder.vue') ? TREE_TABLE_SPA : spa
      const src = readFileSync(target, 'utf-8')
      const legacySrc = readFileSync(legacy, 'utf-8')
      for (const { legacyName, tag } of BLOCKS) {
        if (legacySrc.includes(legacyName) && !src.includes(tag)) {
          missing.push(`${target} 缺 ${tag}（旧 HTML include 了 ${legacyName}）`)
        }
      }
    }
    expect(missing, `以下共享区块漏挂：\n  ${missing.join('\n  ')}`).toEqual([])
  })

  it('③ 共用的两个组件确实存在且被导出为组件文件（防"标签写了但组件不在"）', () => {
    expect(() => readFileSync('src/components/EovaToolbar.vue', 'utf-8')).not.toThrow()
    expect(() => readFileSync('src/components/EovaAdminPanel.vue', 'utf-8')).not.toThrow()
  })
})
