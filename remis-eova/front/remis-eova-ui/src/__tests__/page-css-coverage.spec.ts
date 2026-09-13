/**
 * **页面级 CSS 覆盖**判据（第 302 轮）—— 防"迁移时漏加载页面样式"这类**成类缺口**。
 *
 * ## 它防的是什么（本轮实测出来的真缺陷）
 *
 * 旧栈每个页面在 `<head>` 里 `<link rel="stylesheet">` 自己的**页面级样式**。
 * 迁 SPA 时若漏掉，`.eova-home_menu/_head/_tabs/_body` 这类**布局规则**就整类消失，
 * 症状是"元素都在、文本也对，但布局塌成正常流" ——
 *
 * | | `#app` 高度 | `.eova-home` 高度 | `scrollHeight` |
 * |---|---|---|---|
 * | 旧栈 9090 | `0px` | `0px` | **813** |
 * | 新 SPA（漏 `index.css`） | `1881px` | `1881px` | **1347** |
 * | 新 SPA（补上后） | `0px` | `0px` | **813** |
 *
 * ★ **所有既有判据都是绿的**：`Home.spec.ts` 23 条绿、构建绿、S5 真浏览器 6/6 绿 ——
 * 因为它们看的是"元素/文本/事件"，而**没有一个判据看样式是否到位**。
 * 这正是阶段 2 判据第三根支柱（**视觉对照**）存在的理由；本判据是它在**单测层**的可自动化部分。
 *
 * ## 判据口径
 *
 * 扫 `src/legacy/**\/*.html` 里的 `<link rel="stylesheet">`（排除框架级的 `_block/base.html`
 * 已由 SPA 外壳加载的那两份），每个被引用的页面级 CSS 必须满足**二者之一**：
 *  ① 被 `src/**`（非 legacy）import；
 *  ② 在 {@link EXEMPT} 里**显式登记**，且**写明理由**。
 * ⇒ 新增一个页面级 CSS 而没人引用、也没登记 ⇒ 红。
 *
 * ★ 反空断言三处：扫到的 CSS 条数、引用的页面条数、豁免条数都有下限 —— 否则"扫描规则失效"
 *   会让本判据因为"什么都没扫到"而通过（r170 口径）。
 */
import { readFileSync, readdirSync, statSync } from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'

/** 框架级样式：由 SPA 外壳 `index.html` 统一加载（不属于"页面级"） */
const FRAMEWORK_CSS = ['/eova/lib/eova/eovaui.css', '/eova/ui/css/common.css']

/**
 * **显式豁免**：被旧页引用、但**按既定口径不迁**的页面级 CSS（每条必须写理由）。
 *
 * 这份表是"决定"的落点：往里加条目 = 声明"这个样式我们有意不加载"，必须能说出为什么。
 */
const EXEMPT: Readonly<Record<string, string>> = {
  '/_eova/assets/eova.ui.ext.css':
    'demo 工程资产（`meta-eova/eova/demo/src/main/webapp/_eova/`），由 `_eova/include.html` 引入；' +
    '口径④ 已裁定"不迁移 demo 应用" ⇒ 有意不加载'
}

/**
 * 递归收集 legacy 下的 HTML
 *
 * @param dir 目录
 * @returns 文件路径列表
 */
function htmlFiles(dir: string): string[] {
  const out: string[] = []
  for (const entry of readdirSync(dir)) {
    const full = path.join(dir, entry)
    if (statSync(full).isDirectory()) {
      out.push(...htmlFiles(full))
    } else if (entry.endsWith('.html')) {
      out.push(full.replace(/\\/g, '/'))
    }
  }
  return out
}

/**
 * 收集非 legacy 的工程源码（`.vue`/`.ts`）
 *
 * @param dir 目录
 * @returns 文件路径列表
 */
function srcFiles(dir: string): string[] {
  const out: string[] = []
  for (const entry of readdirSync(dir)) {
    const full = path.join(dir, entry)
    if (statSync(full).isDirectory()) {
      if (full.replace(/\\/g, '/').includes('src/legacy')) {
        continue
      }
      out.push(...srcFiles(full))
    } else if (/\.(vue|ts)$/.test(entry)) {
      out.push(full.replace(/\\/g, '/'))
    }
  }
  return out
}

/**
 * 去掉注释（`//…` 与 `/* … *&#47;`）—— ★ 防"被注释掉的 import 仍被当成引用"。
 *
 * 第 302 轮实测教训：本判据首版用**子串匹配**（`text.includes('index.css')`），
 * 于是把 `Home.vue` 里的 import **注释掉**后判据仍然绿（变异 M1 未被捕获 ⇒ 假绿）。
 * 断言必须对准**形态**，且该谓词本身要有用例（见文件末）。
 *
 * @param text 源码文本
 * @returns 去注释后的文本
 */
export function stripComments(text: string): string {
  // ★ 必须处理 **SFC 的三种注释语法**，且要能容忍"注释里的 glob"：
  //   · `<!-- … -->`（Vue 模板注释）
  //   · `//` 行注释
  //   · `/* … */` 块注释
  //
  //   两轮实测教训（都被判据自己绊倒过）：
  //   ① 首版用正则 `/\/\*[\s\S]*?\*\//g` ⇒ 被注释里的 glob（`src/views/**/*.vue`）提前开块；
  //   ② 改为"只认 `//` 与 `/* */`"的状态机后 ⇒ 仍被 **HTML 注释里**的 glob
  //      （`Home.vue:80` 的 `` `/eova/_view/index/**` ``，其 `/*` **没有配对**）卡住，
  //      从那一行起把后面的**真代码**（含 import）整段吞掉 ⇒ 对**真有 import 的文件**返回 false（假红）。
  //   ⇒ 与 R71「`src/views/**/*.vue` 写在块注释里提前闭合」同一族：**glob 与注释语法同形**，
  //     而 SFC 里注释还不止一种。故这里逐行扫、三种形式都认。
  let out = ''
  let inJs = false
  let inHtml = false
  for (const line of text.split('\n')) {
    let lineOut = ''
    let i = 0
    while (i < line.length) {
      if (inHtml) {
        if (line.startsWith('-->', i)) {
          inHtml = false
          i += 3
        } else {
          i += 1
        }
        continue
      }
      if (inJs) {
        if (line.startsWith('*/', i)) {
          inJs = false
          i += 2
        } else {
          i += 1
        }
        continue
      }
      if (line.startsWith('<!--', i)) {
        inHtml = true
        i += 4
        continue
      }
      if (line.startsWith('//', i)) {
        break // 行注释：本行余下丢弃
      }
      if (line.startsWith('/*', i)) {
        inJs = true
        i += 2
        continue
      }
      lineOut += line[i]
      i += 1
    }
    out += lineOut + '\n'
  }
  return out
}

export function hasImport(text: string, base: string): boolean {
  const re = new RegExp(`import\\s+['"][^'"]*${base.replace('.', '\\.')}['"]`)
  return re.test(stripComments(text))
}

/** 旧页 HTML 引用的页面级 CSS → 引用它的页面（去重） */
function pageLevelCss(): Map<string, string[]> {
  const found = new Map<string, Set<string>>()
  for (const html of htmlFiles('src/legacy')) {
    const text = readFileSync(html, 'utf-8')
    const re = /<link[^>]+rel="stylesheet"[^>]+href="([^"]+)"/g
    let m: RegExpExecArray | null
    while ((m = re.exec(text)) !== null) {
      const href = m[1].split('?')[0]
      if (FRAMEWORK_CSS.includes(href)) {
        continue
      }
      if (!found.has(href)) {
        found.set(href, new Set())
      }
      found.get(href)!.add(html)
    }
  }
  return new Map([...found].map(([k, v]) => [k, [...v].sort()]))
}

describe('页面级 CSS 覆盖（漏加载页面样式 ⇒ 布局整类塌掉，而所有行为判据仍绿）', () => {
  const css = pageLevelCss()

  it('① 旧页确实引用了页面级 CSS（反空：扫描规则失效时本判据先红）', () => {
    expect(css.size, `未从 legacy HTML 扫到任何页面级 CSS：${[...css.keys()].join(', ')}`).toBeGreaterThanOrEqual(3)
    // 这三条是本轮实测出来的引用关系，逐条点名（防扫描悄悄漏掉某一页）
    expect([...css.keys()]).toContain('/eova/_view/index/index.css')
    expect([...css.keys()]).toContain('/eova/_view/index/login.css')
    expect(css.get('/eova/_view/index/index.css')).toContain('src/legacy/eova/_view/index/index.html')
  })

  it('② ★ 每个页面级 CSS 必须"被 import"或"显式豁免（带理由）"', () => {
    const projectSrc = srcFiles('src')
    const bodies = projectSrc.map((f) => ({ f, text: readFileSync(f, 'utf-8') })).filter((x) => x.f !== 'src/__tests__')

    const missing: string[] = []
    for (const href of css.keys()) {
      const base = href.split('/').pop()!
      // ★ 用 hasImport（对准 import 形态、忽略注释），**不用子串匹配** —— 见 stripComments 的说明
      const imported = bodies.some((b) => hasImport(b.text, base))
      const exempt = Object.prototype.hasOwnProperty.call(EXEMPT, href)
      if (!imported && !exempt) {
        missing.push(href)
      }
    }
    expect(
      missing,
      `★ 以下页面级 CSS 既没被工程引用、也没登记豁免 ⇒ 该页布局会整类塌掉（实测症状：\`#app\` 被内容撑高、scrollHeight 与旧栈差 500+px）：\n  ${missing.join('\n  ')}`
    ).toEqual([])
  })

  it('③ ★ 回归钉子：`index.css`（首页布局）必须**真的被 `Home.vue` 引用**', () => {
    const home = readFileSync('src/views/Home.vue', 'utf-8')
    expect(
      hasImport(home, 'index.css'),
      '★ Home.vue 必须**真的 import** `index.css` —— 旧 `eova/_view/index/index.html:7` 的页面级样式，' +
        '`.eova-home_*` 的定位/高度规则全在里面（注释掉不算）'
    ).toBe(true)
    // 反面：别把登录页的样式错当首页样式引进来。
    // ★ 只认 **import 形态**，不用子串匹配 —— 首版写成 `not.toContain('login.css')` 时，
    //   被**本文件自己的说明注释**（"与 Login.vue 引 login.css 同款"）绊倒而假红。
    //   （与 `mutate.py` 的"锚点命中注释 ⇒ 空变异"是同一族：断言要对准**形态**，不是子串。）
    expect(hasImport(home, 'login.css'), 'Home.vue 不应 **import** login.css（那是登录页的样式）').toBe(false)
  })

  it('④ 豁免表非空且每条都有理由（"豁免"本身也受监督）', () => {
    const entries = Object.entries(EXEMPT)
    expect(entries.length, '豁免表不应为空 —— 有豁免就该写明').toBeGreaterThanOrEqual(1)
    for (const [href, reason] of entries) {
      expect(reason.length, `豁免 ${href} 的理由太短（要能说清依据）`).toBeGreaterThan(20)
      // 被豁免的必须确实是旧页引用过的（防"登记一个不存在的项"来凑数）
      expect(css.has(href), `豁免表里的 ${href} 并不被任何旧页引用`).toBe(true)
    }
  })
})

/**
 * ★ 谓词自身的用例（第 302 轮 M1 未捕获的修法）。
 *
 * 变异实验证明：只把断言从"子串"改成"import 形态"还不够 —— 还得**给这个谓词写用例**，
 * 否则下次有人把它改回子串匹配，同样没人发现。
 */
describe('hasImport（本判据的判定谓词）', () => {
  it('认真的 import 语句（含双引号与多级路径）', () => {
    expect(hasImport("import '../legacy/eova/_view/index/index.css'", 'index.css')).toBe(true)
    expect(hasImport('import "@/legacy/x/index.css"', 'index.css')).toBe(true)
  })

  it('★ 不认被注释掉的 import（M1 未捕获的根因）', () => {
    expect(hasImport("// import '../legacy/eova/_view/index/index.css'", 'index.css')).toBe(false)
    expect(hasImport("/* import '../legacy/eova/_view/index/index.css' */", 'index.css')).toBe(false)
  })

  // ★ 夹具里的 glob **故意用拼接构造**：源码里直接写字面量会被 `check-comment-hazards.py`
  //   判为"块注释提前闭合"隐患（R71 同族）—— 那道闸是**文本级启发式**，分不清"注释"与
  //   "字符串里的数据"（实测：本夹具首版直接把 glob 写进字符串，闸门报 3 处 hazard）。
  //   拼接后**运行时字符串仍然含完整 glob**（夹具语义不变），而闸门保持严格。
  //   ⇒ **不要"顺手合并"回字面量**：那会让全量门控红。
  const GLOB_VUE = 'src/views/**' + '/*.vue'
  const GLOB_TS = 'src/**' + '/*.ts'

  it('★ 注释里的 glob 不得破坏剥离（glob 与注释语法同形）', () => {
    const src = [
      `<!-- 说明：\`${GLOB_VUE}\` 这类 glob 写在注释里 -->`,
      `/* 另一处 glob：${GLOB_TS} */`,
      "import '../legacy/eova/_view/index/index.css'"
    ].join('\n')
    expect(hasImport(src, 'index.css'), 'glob 之后被整段剥掉了').toBe(true)
  })

  it('★ HTML 注释里的 glob（`/eova/_view/index/**`，`/*` 无配对）不得吞掉后面的真代码', () => {
    const src = [
      '<!-- 说明：`/eova/_view/index/**` 是后端静态路径 -->',
      "import '../legacy/eova/_view/index/index.css'"
    ].join('\n')
    expect(hasImport(src, 'index.css'), 'HTML 注释里的 glob 把 import 吞了').toBe(true)
  })

  it('★ 多行块注释里的 import 也不算', () => {
    const src = "/*\nimport '../x/index.css'\n*/\nconst a = 1"
    expect(hasImport(src, 'index.css')).toBe(false)
  })

  it('不认"只提到文件名"的文本（注释或普通字符串）', () => {
    expect(hasImport('// 见 index.css 里的 .eova-home_body 规则', 'index.css')).toBe(false)
    expect(hasImport("const p = 'index.css'", 'index.css')).toBe(false)
  })

  it('区分不同样式文件（login.css 不等于 index.css）', () => {
    expect(hasImport("import '../x/login.css'", 'index.css')).toBe(false)
    expect(hasImport("import '../x/login.css'", 'login.css')).toBe(true)
  })
})
