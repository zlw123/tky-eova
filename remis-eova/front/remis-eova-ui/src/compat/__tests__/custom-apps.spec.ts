/**
 * 自定义 app / 自定义组件**登记的完整性判据**（第 126 轮）
 *
 * 本轮的登记是"我们知道自己**没实现**什么"这件事的可执行版本。
 * 判据不信任手抄的常量，而是**解析冻结配置**（`src/legacy/_eova/assets/eova.vue.config.js`）
 * 与**扫描冻结资产**，与 `custom-apps.ts` 双向核对：
 *
 *  ① 冻结配置里 `vue.component(name, url)` 的集合 ≡ `CUSTOM_COMPONENTS`；
 *  ② 冻结配置里 `vue.app(code, def)` 的集合 ≡ `CUSTOM_APPS`（含每个 def 的 template/script/components）；
 *  ③ 每个被引用的资产**在 `src/legacy` 里确实存在**（否则"登记"本身就失真）；
 *  ④ 自定义模版的内容与登记一致（`hotel/app.vue` 就是那一行 `sword-coming`；
 *     `product/app.vue` 的**活代码**只有 `<br>{{ data }}`，其余是注释）；
 *  ⑤ 键格式：列表/树表用 `` `${template}_${code}` ``、表单用**元对象编码**；
 *     且 `table_meta_hotel` 这种"列表页定制"在种子数据里**确有对应菜单**（`meta_hotel` + `template=table`）。
 *
 * ★ 这几条一起把"缺口"钉成**有限的、可枚举的**：将来若有人实现了加载、或配置新增了一条定制，
 *   判据都会响。
 */
import { existsSync, readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import {
  CUSTOM_APPS,
  CUSTOM_COMPONENTS,
  findCustomApp,
  formCustomAppKey,
  listCustomAppKey
} from '../custom-apps'

/** 冻结的自定义配置（每页经 `_eova/include.html` 加载） */
const CONFIG_JS = 'src/legacy/_eova/assets/eova.vue.config.js'

/** 解析冻结配置里的 `vue.component(name, url)` */
function parseComponents(): Record<string, string> {
  const src = readFileSync(CONFIG_JS, 'utf-8')
  const out: Record<string, string> = {}
  const re = /^\s*vue\.component\('([^']+)'\s*,\s*'([^']+)'\)/gm
  let m: RegExpExecArray | null
  while ((m = re.exec(src)) !== null) {
    out[m[1]] = m[2]
  }
  return out
}

/** 解析冻结配置里的 `vue.app(code, { … })`（含被注释掉的行要排除） */
function parseApps(): Record<
  string,
  { template: string; script: string; components?: string[] }
> {
  const src = readFileSync(CONFIG_JS, 'utf-8')
  const out: Record<string, { template: string; script: string; components?: string[] }> = {}
  // 逐行去掉 `//` 行注释后再匹配（该文件里注释掉了一条 tree_table 扩展）
  const lines = src
    .split('\n')
    .map((l) => l.replace(/^\s*\/\/.*$/, ''))
    .join('\n')
  const re = /vue\.app\('([^']+)'\s*,\s*\{([\s\S]*?)\}\s*\)/g
  let m: RegExpExecArray | null
  while ((m = re.exec(lines)) !== null) {
    const body = m[2]
    const template = /template:\s*'([^']+)'/.exec(body)?.[1] ?? ''
    const script = /script:\s*'([^']+)'/.exec(body)?.[1] ?? ''
    const comps = /components:\s*\[([^\]]*)\]/.exec(body)?.[1] ?? ''
    // ★ 没写 `components` 时**不要**补一个空数组：登记的语义是"该定义依赖哪些组件"，
    //   `[]` 与"没有这一项"在深比较里不同（首轮实测因此红了一条）
    const def: { template: string; script: string; components?: string[] } = { template, script }
    const list = comps
      .split(',')
      .map((s) => s.trim().replace(/^'|'$/g, ''))
      .filter((s) => s !== '')
    if (list.length > 0) {
      def.components = list
    }
    out[m[1]] = def
  }
  return out
}

describe('自定义 app / 组件的登记完整性（第 126 轮）', () => {
  it('① `vue.component` 集合与冻结配置逐条一致（名字与资产路径）', () => {
    const fromConfig = parseComponents()
    // 反空断言：解析规则失效时不能"因为找不到而通过"
    expect(Object.keys(fromConfig).length).toBeGreaterThanOrEqual(6)
    expect(fromConfig['SwordComing']).toBe('/_component/SwordComing.js')
    expect(CUSTOM_COMPONENTS).toEqual(fromConfig)
  })

  it('② `vue.app` 集合与冻结配置逐条一致（含 template/script/components）', () => {
    const fromConfig = parseApps()
    expect(Object.keys(fromConfig).length).toBeGreaterThanOrEqual(3)
    expect(fromConfig['table_meta_hotel']).toEqual({
      template: '/hotel/app.vue',
      script: '/hotel/app.js',
      components: ['SwordComing']
    })
    expect(CUSTOM_APPS).toEqual(fromConfig)
  })

  it('③ 被引用的资产在 `src/legacy` 里都存在（登记不许指向不存在的文件）', () => {
    for (const [name, url] of Object.entries(CUSTOM_COMPONENTS)) {
      expect(existsSync(`src/legacy${url}`), `自定义组件 ${name} 的资产缺失：${url}`).toBe(true)
    }
    for (const [code, def] of Object.entries(CUSTOM_APPS)) {
      expect(existsSync(`src/legacy${def.template}`), `${code} 的模版缺失：${def.template}`).toBe(
        true
      )
      expect(existsSync(`src/legacy${def.script}`), `${code} 的脚本缺失：${def.script}`).toBe(true)
    }
  })

  it('④ 自定义模版的内容与登记一致（含"活代码只有一行"这一事实）', () => {
    // hotel/app.vue：整页模版就是一行 sword-coming（绑定页面已有的 currentRow/showLinking）
    const hotel = readFileSync('src/legacy/hotel/app.vue', 'utf-8').trim()
    expect(hotel).toBe(
      '<sword-coming ref="link" v-model="currentRow" v-model:show="showLinking"></sword-coming>'
    )
    // product/app.vue：活代码只有 <br>{{ data }}（其余是注释）
    const product = readFileSync('src/legacy/product/app.vue', 'utf-8')
    const live = product
      .split('\n')
      .filter((l) => !/^\s*(<!--|-->)/.test(l))
      .join('\n')
      .trim()
    expect(live).toBe('<br>{{ data }}')
  })

  it('④ 自定义脚本注册的是 `uzoo.vue` 钩子（`setup`/`onReady`），不是自己重做一套', () => {
    const script = readFileSync('src/legacy/hotel/app.js', 'utf-8')
    expect(script).toContain('uzoo.vue.setup =')
    expect(script).toContain('uzoo.vue.onReady =')
    // 与已迁页面的接线一致：TemplateTable 会 callUzooHook('setup')/('onReady')
    const table = readFileSync('src/views/template/TemplateTable.vue', 'utf-8')
    expect(table).toContain("callUzooHook('setup')")
    expect(table).toContain("callUzooHook('onReady'")
  })

  it('⑤ 键格式：列表/树表 `${template}_${code}`、表单是元对象编码', () => {
    expect(listCustomAppKey('table', 'meta_hotel')).toBe('table_meta_hotel')
    expect(listCustomAppKey('tree_table', 'sys_org_user')).toBe('tree_table_sys_org_user')
    expect(formCustomAppKey('meta_product')).toBe('meta_product')
    expect(findCustomApp('table_meta_hotel')?.script).toBe('/hotel/app.js')
    expect(findCustomApp('不存在的键')).toBeUndefined()
  })

  it('⑤ `table_meta_hotel` 对应的菜单在种子数据里确实存在（template=table + 码 meta_hotel）', () => {
    // 前端工程根 ⇒ 仓库根是 `../../..`
    const sql = readFileSync('../../../meta-eova/eova/demo/sql/eova_meta.sql', 'utf-8')
    const rows = sql.split('\n').filter((l) => l.startsWith('INSERT INTO `eova_menu` VALUES'))
    const hit = rows.filter((l) => l.includes("'meta_hotel'"))
    expect(hit.length, '种子数据里应有 meta_hotel 菜单').toBeGreaterThan(0)
    expect(hit.some((l) => l.startsWith("INSERT INTO `eova_menu` VALUES (") && l.includes("'table'"))).toBe(
      true
    )
  })
})
