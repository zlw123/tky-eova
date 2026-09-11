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
  { legacy: 'src/legacy/eova/_view/template/tree_table/index.html', spa: 'src/views/template/TemplateTreeTable.vue' }
]

/** 共享区块：旧 include 名 → SPA 组件标签 */
const BLOCKS: ReadonlyArray<{ legacyName: string; tag: string }> = [
  { legacyName: '_block/toolbar.html', tag: '<EovaToolbar' },
  { legacyName: '_block/admin.html', tag: '<EovaAdminPanel' }
]

/**
 * `_page/*.html` 的等价物（第 149 轮补）—— 这些 partial **不产出 DOM**，
 * 它们提供的是**运行时接缝**（`_page/meta.html` 定义 `window.uzoo` + `window.urls`；
 * `_page/{list,list1,form}.html` 再 include 它 + `_block/base.html` 做制品装配）。
 * ⇒ SPA 侧的等价物是：`uzoo` 接缝（`compat/eova-ext`）+ `window.urls`（`compat/ui-urls`）
 *   + 入口装配（`main.ts`），**不是**某个组件标签。
 */
const PAGE_PARTIALS: ReadonlyArray<{ legacy: string; spa: string; note: string }> = [
  { legacy: 'src/legacy/eova/_view/meta/edit/app.html', spa: 'src/views/meta/MetaEdit.vue', note: '元字段（列表页 partial）' },
  { legacy: 'src/legacy/eova/_view/meta/field/index.html', spa: 'src/views/meta/MetaField.vue', note: '元字段个性化' },
  { legacy: 'src/legacy/eova/_view/meta/import/app.html', spa: 'src/views/meta/MetaImport.vue', note: '导入元数据（表单页 partial）' },
  { legacy: 'src/legacy/eova/_view/meta/reorder/app.html', spa: 'src/views/meta/MetaReorder.vue', note: '重新排序' },
  { legacy: 'src/legacy/eova/_view/menu/add/app.html', spa: 'src/views/menu/MenuAdd.vue', note: '创建菜单' },
  { legacy: 'src/legacy/eova/_view/button/add/app.html', spa: 'src/views/button/ButtonAdd.vue', note: '快速添加按钮' },
  { legacy: 'src/legacy/eova/_view/user/su/app.html', spa: 'src/views/admin/Su.vue', note: '虚拟用户切换（list1 partial）' }
]

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
      const src = readFileSync(spa, 'utf-8')
      const legacySrc = readFileSync(legacy, 'utf-8')
      for (const { legacyName, tag } of BLOCKS) {
        if (legacySrc.includes(legacyName) && !src.includes(tag)) {
          missing.push(`${spa} 缺 ${tag}（旧 HTML include 了 ${legacyName}）`)
        }
      }
    }
    expect(missing, `以下共享区块漏挂：\n  ${missing.join('\n  ')}`).toEqual([])
  })

  it('③ 共用的两个组件确实存在且被导出为组件文件（防"标签写了但组件不在"）', () => {
    expect(() => readFileSync('src/components/EovaToolbar.vue', 'utf-8')).not.toThrow()
    expect(() => readFileSync('src/components/EovaAdminPanel.vue', 'utf-8')).not.toThrow()
  })

  it('④ 管理页的 `_page/*.html` partial：其运行时接缝在 SPA 侧成立（uzoo + window.urls）', () => {
    // partial 本身不产出 DOM ⇒ 判"接缝"而不是"标签"：
    // 这些页面都靠 `uzoo.page` / `uzoo.vue.*`（页面配置与扩展钩子）与 `window.urls`（表单/数据 URL 表）。
    const uzooSrc = readFileSync('src/compat/eova-ext.ts', 'utf-8')
    expect(uzooSrc).toContain('export function getUzoo')
    expect(uzooSrc).toContain('export function setUzooPage')
    expect(uzooSrc).toContain('export function callUzooHook')

    const urlsSrc = readFileSync('src/compat/ui-urls.ts', 'utf-8')
    expect(urlsSrc).toContain('export function installWindowUrls')
    expect(urlsSrc).toContain('/api/form/delete/{{object_code}}')

    // 每个管理页至少引用其中一侧（否则它拿不到旧 partial 提供的接缝）
    const missing: string[] = []
    for (const { legacy, spa, note } of PAGE_PARTIALS) {
      const partial = readFileSync(legacy, 'utf-8')
      // 反空断言：这些页面确实 include 了 partial
      expect(partial, `${legacy} 未 include _page/*.html`).toContain('#include("/eova/_view/_page/')
      const src = readFileSync(spa, 'utf-8')
      const usesUzoo = src.includes('@/compat/eova-ext')
      const usesUrls = src.includes('@/compat/ui-urls')
      const usesBootstrap = src.includes('loadPageBootstrap')
      if (!usesUzoo && !usesUrls && !usesBootstrap) {
        missing.push(`${note}：${spa} 未引用 uzoo / window.urls / 引导数据任一侧`)
      }
    }
    expect(missing, `以下页面缺少旧 partial 提供的接缝：\n  ${missing.join('\n  ')}`).toEqual([])
  })
})
