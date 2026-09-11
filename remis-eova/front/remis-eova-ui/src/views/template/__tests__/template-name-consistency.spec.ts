/**
 * 模版页 ↔ 模版名 ↔ 组件表 的**三方一致**判据（第 143 轮）
 *
 * ## 防的是什么
 *
 * 每个模版页都会在 setup 期写 `uzoo.page.template`（旧 `index.html` 的内联脚本，如
 * `uzoo.page.template = 'table'`），而宿主 `AppTemplateHost` 是拿**引导数据里的 `menu.template`**
 * 去 `registry.ts` 查组件的。三处名字必须对得上：
 *
 * ```
 * 引导数据 menu.template ──► TEMPLATE_COMPONENTS[key] ──► 组件（它自己又写 uzoo.page.template）
 * ```
 *
 * 若有人给页面写错/复制粘贴了别的模版名（例如把 `'tree_table'` 抄成 `'tree'`），
 * 页面自身的行为判据**照样全绿**（各自只断自己的值），而外部扩展脚本读
 * `uzoo.page.template` 时会拿到错值 ⇒ 静默错页。本判据把三者钉在一起。
 *
 * ★ 只读**已入库源码**（`src/views/template/*.vue` + `registry.ts`），不读 `docs/**`。
 */
import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { MIGRATED_TEMPLATES } from '@/compat/template-dispatch'
import { TEMPLATE_COMPONENTS } from '../registry'

/** 模版页文件 → 它应当登记的模版名（对照 `registry.ts` 的键） */
const PAGE_FILES: Readonly<Record<string, string>> = {
  'src/views/template/TemplateTable.vue': 'table',
  'src/views/template/TemplateTree.vue': 'tree',
  'src/views/template/TemplateTreeTable.vue': 'tree_table'
}

/**
 * 抽页面源码里 `setUzooPage('template', 'x')` 的值
 *
 * @param file 页面源码路径
 * @returns 模版名；未找到返回 null
 */
function templateNameOf(file: string): string | null {
  const src = readFileSync(file, 'utf-8')
  return /setUzooPage\('template',\s*'([^']+)'\)/.exec(src)?.[1] ?? null
}

describe('模版页 ↔ 模版名 ↔ 组件表 三方一致', () => {
  it('① 每个模版页写的 `uzoo.page.template` 与组件表的键一致（反空断言 + 逐字）', () => {
    const files = Object.keys(PAGE_FILES)
    // 反空断言：抽取规则失效时不能"因为找不到而通过"
    expect(files.length).toBeGreaterThanOrEqual(3)

    for (const [file, expected] of Object.entries(PAGE_FILES)) {
      const actual = templateNameOf(file)
      expect(actual, `${file} 里未找到 setUzooPage('template', …)`).not.toBeNull()
      expect(actual, `${file} 写的模版名与登记的键不符`).toBe(expected)
    }
  })

  it('② 组件表的键集合与模版页集合**双向一致**（新增页面/改名都会红）', () => {
    const fromPages = Object.values(PAGE_FILES).sort()
    expect(Object.keys(TEMPLATE_COMPONENTS).sort()).toEqual(fromPages)
  })

  it('③ 这些模版名都在"已迁移清单"里（宿主才会判 ready）', () => {
    for (const name of Object.values(PAGE_FILES)) {
      expect(MIGRATED_TEMPLATES, `${name} 不在 MIGRATED_TEMPLATES 里`).toContain(name)
    }
    expect([...MIGRATED_TEMPLATES].sort()).toEqual(Object.values(PAGE_FILES).sort())
  })

  it('④ 三个模版页都调用了 `loadButtonScripts`（旧 `index.html` 的 `.js` 按钮注入）', () => {
    for (const file of Object.keys(PAGE_FILES)) {
      const src = readFileSync(file, 'utf-8')
      expect(src, `${file} 未调用 loadButtonScripts`).toContain('loadButtonScripts(')
    }
  })
})
