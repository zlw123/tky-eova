/**
 * 「页面是否需要引导数据」的判据（第 146 轮）
 *
 * ## 防的是什么
 *
 * 旧栈的页面靠**服务端插值**（`#(object.code)`、`#(menu.*)`、`#(btnList)`、`#(loginUser.*)`…）拿数据；
 * 前后分离后这些值只能来自**页面引导数据**（DES-004）。若某个已迁页面在旧 HTML 里**确实有**这类插值、
 * 而 SPA 侧**从不读引导数据**，症状是：
 *
 * - 页面能打开（不报错），但字段是空的 / 按钮区是空的 / URL 参数少一段；
 * - 而所有既有判据**全绿**（它们只判"页面自身行为"，不判"数据从哪来"）。
 *
 * 反过来，"旧 HTML 里没有插值的页面"不应该被硬塞引导数据（那会让降级告警误报，
 * 也模糊了"这页到底依赖什么"）。
 *
 * ## 判据口径（跨制品：冻结旧 HTML ↔ 已迁 SFC）
 *
 * ① 对**迁移映射表**里的每一对（旧 HTML → SPA 页面），扫描旧 HTML 是否含引导家族插值
 *    （`#(object.`/`#(menu.`/`#(btnList`/`#(loginUser.`）；
 * ② 含插值 ⇒ SPA 页面必须读引导数据（`props.bootstrap` 或 `loadPageBootstrap`）；
 * ③ 不含插值 ⇒ 记录在案（本判据只要求"如果含就必须读"，不强制反面，避免把
 *    `Sse.vue`/`Widget.vue` 这类页面钉死）。
 *
 * ★ 只读**已入库**文件（`src/legacy/**` 旧 HTML + `src/views/**` SFC）。
 */
import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

/** 旧 HTML（冻结资产）→ SPA 页面（迁移映射） */
const PAGE_MAP: ReadonlyArray<{ legacy: string; spa: string; note: string }> = [
  { legacy: 'src/legacy/eova/_view/template/table/index.html', spa: 'src/views/template/TemplateTable.vue', note: '列表模版' },
  { legacy: 'src/legacy/eova/_view/template/tree/index.html', spa: 'src/views/template/TemplateTree.vue', note: '树模版' },
  { legacy: 'src/legacy/eova/_view/template/tree_table/index.html', spa: 'src/views/template/TemplateTreeTable.vue', note: '树表模版' },
  { legacy: 'src/legacy/eova/_view/meta/edit/app.html', spa: 'src/views/meta/MetaEdit.vue', note: '元字段' },
  { legacy: 'src/legacy/eova/_view/meta/field/index.html', spa: 'src/views/meta/MetaField.vue', note: '元字段个性化' },
  { legacy: 'src/legacy/eova/_view/meta/import/app.html', spa: 'src/views/meta/MetaImport.vue', note: '导入元数据' },
  { legacy: 'src/legacy/eova/_view/meta/reorder/app.html', spa: 'src/views/meta/MetaReorder.vue', note: '重新排序' },
  { legacy: 'src/legacy/eova/_view/menu/add/app.html', spa: 'src/views/menu/MenuAdd.vue', note: '创建菜单' },
  { legacy: 'src/legacy/eova/_view/button/add/app.html', spa: 'src/views/button/ButtonAdd.vue', note: '快速添加按钮' },
  { legacy: 'src/legacy/eova/_view/user/su/app.html', spa: 'src/views/admin/Su.vue', note: '虚拟用户切换' }
]

/** 引导数据的插值家族（旧 HTML 里的服务端插值前缀） */
const BOOTSTRAP_PREFIXES: readonly string[] = [
  '#(object.',
  '#(menu.',
  '#(btnList',
  '#(loginUser.'
]

/**
 * 旧 HTML 是否含引导家族插值
 *
 * @param file 旧 HTML 路径
 * @returns 命中的插值前缀
 */
function bootstrapInterpolations(file: string): string[] {
  const src = readFileSync(file, 'utf-8')
  return BOOTSTRAP_PREFIXES.filter((p) => src.includes(p))
}

/**
 * SPA 页面是否读引导数据
 *
 * @param file SFC 路径
 * @returns 是否引用引导数据
 */
function readsBootstrap(file: string): boolean {
  const src = readFileSync(file, 'utf-8')
  return src.includes('props.bootstrap') || src.includes('loadPageBootstrap')
}

describe('页面 ↔ 引导数据（跨制品：冻结旧 HTML ↔ 已迁 SFC）', () => {
  it('① 映射表非空且每个文件都存在（反空断言 + 防"路径写错导致永远绿"）', () => {
    expect(PAGE_MAP.length).toBeGreaterThanOrEqual(10)
    for (const { legacy, spa } of PAGE_MAP) {
      expect(() => readFileSync(legacy, 'utf-8'), `旧 HTML 不存在: ${legacy}`).not.toThrow()
      expect(() => readFileSync(spa, 'utf-8'), `SPA 页面不存在: ${spa}`).not.toThrow()
    }
  })

  it('② 旧 HTML 里有引导插值的页面，SPA 侧必须读引导数据（否则字段静默为空）', () => {
    const missing: string[] = []
    for (const { legacy, spa, note } of PAGE_MAP) {
      const hits = bootstrapInterpolations(legacy)
      if (hits.length > 0 && !readsBootstrap(spa)) {
        missing.push(`${note}：${legacy} 含 ${hits.join('、')}，但 ${spa} 从未引用引导数据`)
      }
    }
    expect(missing, `以下页面拿不到引导数据（字段会静默为空）：\n  ${missing.join('\n  ')}`).toEqual([])
  })

  it('③ 至少有一个页面确实含插值（否则本判据可能是"因为都没插值而通过"）', () => {
    const withInterp = PAGE_MAP.filter(({ legacy }) => bootstrapInterpolations(legacy).length > 0)
    expect(withInterp.length, '映射表里没有任何页面含引导插值 ⇒ 判据形同不存在').toBeGreaterThan(0)
    // 抽查一个已知页面：列表模版的 object/menu/loginUser 都在
    const table = bootstrapInterpolations('src/legacy/eova/_view/template/table/index.html')
    expect(table).toContain('#(object.')
    expect(table).toContain('#(loginUser.')
  })
})
