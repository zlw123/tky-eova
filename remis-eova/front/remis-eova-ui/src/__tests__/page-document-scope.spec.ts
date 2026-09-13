/**
 * **页面级样式的文档作用域**判据（第 303 轮）—— 防"页面级 CSS 跨页面泄漏"这类**成类缺口**。
 *
 * ## 它防的是什么（本轮实测出来的第二个真回归）
 *
 * 旧栈**每个页面是一份独立文档**：页面级 CSS 里对 `html`/`body` 的规则只作用于本文档。
 * 迁 SPA 后所有页面共用**同一个文档** ⇒ 这类规则**泄漏到全站**。实测两条：
 *
 * | 泄漏源 | 旧栈该规则的作用面 | SPA 里的后果 | 实测证据 |
 * |---|---|---|---|
 * | `_view/index/login.css` 的 `body{display:flex;justify-content:center;align-items:center;height:100vh}` | 仅登录文档（把登录框居中） | **全站** `body` 变 flex 居中 ⇒ 列表页 `#app`（高 0、内容全绝对定位）被居中到 `top=407`，整页内容下移 | 列表页 `#app top` **407 → 0**、`scrollHeight` **1205 → 813**（旧栈 813） |
 * | `views/role/RoleAuth.vue` 的 `body input{height:22px;width:22px}` | 仅角色页文档 | 全站 `input` 被压成 22px | 未见可见影响：各页面的 `.eova-form input` 等**具体度更高**的规则压过它（登记备查，不擅改） |
 *
 * ★ 上一轮（r302）同类教训是"**漏加载**页面 CSS"；这一轮的病恰好相反 —— "**多泄漏**页面 CSS"。
 *   两者都**不改变任何行为/契约判据的结果**，故只能靠视觉对照与真浏览器布局锚点发现。
 *
 * ## 判据口径
 *
 * 扫**全部**被引入的页面级样式（① `src/legacy/**\/*.css`；② `src/**\/*.vue` 里**非 scoped**
 * 的 `<style>` 块），取"选择器以 `html`/`body` 起头 **且** 声明里含布局属性"的规则，
 * 逐条与 {@link INVENTORY} 对齐（**双向**：清单里少的、扫描多的，都要红）：
 *  - 每条必须写明 `verdict`（是"两端共有的全局规则"还是"页面级泄漏"，泄漏的必须写清处理方式）；
 *  - 唯一的真泄漏（`login.css` 的 `body`）必须被 SPA 底座样式
 *    `src/compat/page-document-scope.css` **按属性逐项覆盖**，且该文件必须被 `main.ts` 引入
 *    （**不引 = 静默失效**，规则写着也不生效）。
 *
 * ★ 判据分工：本判据管**清单**（新泄漏出现即红）；**效果**由真浏览器判据管
 *   （`s5-browser-acceptance.mjs` ⑧⑨ 实测两路由 computed 与旧栈同值）—— 静态断言无法替代实测。
 */
import { readFileSync, readdirSync, statSync } from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'

/** 会被"页面级样式"污染的选择器：以 `html`/`body` 起头的元素选择器 */
const DOC_ROOT_SELECTOR = /^(html|body)([\s>+~:]|$)/i

/** 布局属性前缀（只有这些才可能跨页面改变版式；纯颜色/字体不登记） */
const LAYOUT_PROPS = [
  'display',
  'position',
  'width',
  'height',
  'min-height',
  'max-height',
  'margin',
  'padding',
  'overflow',
  'float',
  'top',
  'right',
  'bottom',
  'left',
  'flex',
  'align',
  'justify',
  'gap',
  'inset'
]

/** 一条"文档级规则" */
interface DocRule {
  /** 来源：文件路径或 `文件 <style>` */
  file: string
  /** 选择器（已折叠空白） */
  selector: string
  /** 命中的布局属性（升序） */
  props: string[]
}

/**
 * 一条已登记的文档级规则
 */
interface InventoryEntry extends DocRule {
  /** 定性 + 处理方式（必填，防"登记了但没说清"） */
  verdict: string
}

/**
 * **显式清单**：当前全部"`html`/`body` 起头 + 布局属性"的规则。
 *
 * 往里加条目 = 声明"这条会跨页面生效，我们已知情并给出了口径"。
 */
const INVENTORY: readonly InventoryEntry[] = [
  {
    file: 'src/legacy/eova/_view/index/login.css',
    selector: 'body',
    props: ['display', 'height', 'justify-content', 'margin', 'align-items'].sort(),
    verdict:
      '★ 真泄漏（r303 实测）：登录页文档的"登录框居中"规则，SPA 里泄漏到全站 ' +
      '⇒ 列表页 #app 被居中到 top=407 / scrollHeight 1205（旧栈 0/813）。' +
      '处理：冻结资产不改，由 `src/compat/page-document-scope.css` 的 ' +
      '`body:not(:has(.eova-login))` 逐属性覆盖回旧栈值'
  },
  {
    file: 'src/legacy/eova/lib/eova/eovaui.css',
    selector:
      'blockquote,body,button,dd,div,dl,dt,form,h1,h2,h3,h4,h5,h6,input,li,ol,p,pre,td,textarea,th,ul',
    props: ['margin', 'padding'].sort(),
    verdict: '两端共有的 vendor 归零 reset（eovaui.css 在两栈的每个页面都加载）⇒ 不是泄漏，无需覆盖'
  },
  {
    file: 'src/legacy/eova/lib/eova/eovaui.css',
    selector: 'html #layuicss-laydate',
    props: ['display', 'position', 'width'].sort(),
    verdict: 'layui 自注入样式容器的隐藏规则（`display:none`）⇒ 两端同款，无副作用'
  },
  {
    file: 'src/legacy/eova/ui/css/index.css',
    selector: 'html #layuicss-layuiAdmin',
    props: ['display', 'position', 'width'].sort(),
    verdict: 'layuiAdmin 自注入样式容器的隐藏规则（`display:none`）⇒ 两端同款，无副作用'
  },
  {
    file: 'src/views/role/RoleAuth.vue <style>',
    selector: 'body input',
    props: ['height', 'width'].sort(),
    verdict:
      '页面级泄漏（第二例，低影响）：登记备查 —— 未见可见影响，因各页面自身的输入框规则' +
      '（如 `.eova-form input`、`.eova-login_input input`）具体度更高而压过它；' +
      '口径是"旧页 `<style>` 逐字保留"⇒ **不擅改选择器**；若后续实测出影响再单独定'
  }
]

/** 底座适配样式（SPA 自有，允许改） */
const SCOPE_CSS = 'src/compat/page-document-scope.css'

/**
 * `import` **形态**正则：行首（可有缩进）的 `import` 语句里那个说明符。
 *
 * ★ 为什么不用子串匹配：`toContain('xxx.css')` 会被**注释里抄写的同一串**命中 ——
 * 本项目已实测两次假绿（r302 的 `login.css`、r303 变异 M2 的底座样式）。
 * 行首锚定（`(?:^|\n)[ \t]*import`）天然排除被 `//` 或**块注释**包起来的 import。
 * ⚠️ 本条注释自身也踩过一次坑：**别在文档注释里字面写出块注释的终止符** ——
 *   那会让注释提前结束、把后面的 `*` 暴露成代码（本项目已因「注释里写字面量」翻车三次）。
 */
const IMPORT_FORM = /(?:^|\n)[ \t]*import\s[^\n]*?['"]([^'"]+)['"]/g

/**
 * 取一段代码里**真正生效**的 import 说明符
 *
 * @param text 代码文本
 * @returns 说明符列表（去重、保序）
 */
function importedSpecifiers(text: string): string[] {
  return [...text.matchAll(IMPORT_FORM)].map((m) => m[1])
}

/**
 * 剥掉 HTML 注释（`<!-- -->`）与 CSS 注释（`/* *\/`）。
 *
 * ★ 为什么要专门写并单独钉判据：**扫描器自己会被注释里的字面量骗过**（本项目已踩两次）：
 *   · r302：`Home.vue` 注释里写了 glob（`src/views/**\/*.vue`），被当成真引用 ⇒ 假绿；
 *   · r303：`RoleAuth.vue` 注释里写了 `<style>` 字面量，把 `<style>` 开标签粘到选择器上
 *     （`-->\n<style>\nbody input {` 不以 `body` 起头）⇒ **真泄漏被漏扫**（假绿）。
 *
 * @param text 原始文本
 * @returns 去注释后的文本
 */
export function stripComments(text: string): string {
  return text.replace(/<!--[\s\S]*?-->/g, '').replace(/\/\*[\s\S]*?\*\//g, '')
}

/**
 * 从一段 CSS 里取出"文档级布局规则"
 *
 * @param file 来源标识
 * @param css  CSS 文本（**必须先剥注释**）
 * @returns 命中的规则
 */
function docRules(file: string, css: string): DocRule[] {
  const out: DocRule[] = []
  for (const m of css.matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
    const selector = m[1].replace(/\s+/g, ' ').trim()
    const risky = selector
      .split(',')
      .map((s) => s.trim())
      .filter((s) => DOC_ROOT_SELECTOR.test(s))
    if (risky.length === 0) continue
    const props = m[2]
      .split(';')
      .filter((d) => d.includes(':'))
      .map((d) => d.split(':')[0].trim().toLowerCase())
      .filter((p) => LAYOUT_PROPS.some((k) => p === k || p.startsWith(k + '-')))
    if (props.length === 0) continue
    out.push({ file, selector, props: [...new Set(props)].sort() })
  }
  return out
}

/**
 * 递归收集某后缀的文件
 *
 * @param dir      目录
 * @param suffixes 后缀（小写，含点）
 * @returns 路径列表（正斜杠，相对仓库前端根）
 */
function filesUnder(dir: string, suffixes: string[]): string[] {
  const out: string[] = []
  for (const entry of readdirSync(dir)) {
    const full = path.join(dir, entry)
    if (statSync(full).isDirectory()) out.push(...filesUnder(full, suffixes))
    else if (suffixes.some((s) => entry.toLowerCase().endsWith(s))) out.push(full)
  }
  return out
}

/** 扫全部页面级样式（legacy CSS + vue 非 scoped `<style>`） */
function scanAll(): DocRule[] {
  const rules: DocRule[] = []
  for (const f of filesUnder('src/legacy', ['.css'])) {
    rules.push(...docRules(f, stripComments(readFileSync(f, 'utf-8'))))
  }
  for (const f of filesUnder('src', ['.vue'])) {
    const text = stripComments(readFileSync(f, 'utf-8'))
    for (const m of text.matchAll(/<style([^>]*)>([\s\S]*?)<\/style>/g)) {
      if (m[1].includes('scoped')) continue
      rules.push(...docRules(`${f} <style>`, m[2]))
    }
  }
  return rules
}

/** 取底座样式的规则 */
function scopeRules(): DocRule[] {
  return docRules(SCOPE_CSS, stripComments(readFileSync(SCOPE_CSS, 'utf-8')))
}

describe('页面级样式的文档作用域 · 剥注释（先钉扫描器自身）', () => {
  it('HTML 注释里的 `<style>` 字面量不得影响扫描（r303 漏扫的真因，正反对照）', () => {
    const src = [
      '<!-- 说明：页面级 <style> 逐字（选择器是 `body input`） -->',
      '<style>',
      'body input { height: 22px; width: 22px; }',
      '</style>'
    ].join('\n')
    const BLOCK = /<style([^>]*)>([\s\S]*?)<\/style>/g

    // 反面：**不剥注释**时，注释里的 `<style>` 把开标签粘到选择器上（`-->\n<style>\nbody input`）
    //       ⇒ 真泄漏被漏扫 —— 这就是 r303 第一版扫描器"扫出 0 条"的原因
    const naive = [...src.matchAll(BLOCK)]
    expect(docRules('x <style>', naive[0][2])).toEqual([])

    // 正面：剥掉注释后，选择器干净地是 `body input`，泄漏被扫到
    const clean = [...stripComments(src).matchAll(BLOCK)]
    expect(clean.length).toBe(1)
    expect(docRules('x <style>', clean[0][2])).toEqual([
      { file: 'x <style>', selector: 'body input', props: ['height', 'width'] }
    ])
  })

  it('真规则照旧被扫到（剥注释不得把真规则一起吃掉）', () => {
    expect(docRules('x', stripComments('body input { height: 22px; width: 22px; }'))).toEqual([
      { file: 'x', selector: 'body input', props: ['height', 'width'] }
    ])
  })

  it('只登记"文档级 + 布局属性"：`body{color}` 这类不登记（否则清单会被噪声淹掉）', () => {
    expect(docRules('x', stripComments('body { color: red; font-family: Arial; }'))).toEqual([])
    expect(docRules('x', stripComments('.eova-x { display: flex; }'))).toEqual([])
  })
})

describe('页面级样式的文档作用域 · 清单', () => {
  it('扫描结果与显式清单**双向**一致（新泄漏出现即红）', () => {
    const found = scanAll()
    const key = (r: DocRule): string => `${r.file} :: ${r.selector} :: ${r.props.join(',')}`
    const foundKeys = found.map(key).sort()
    const listedKeys = INVENTORY.map(key).sort()
    expect(foundKeys).toEqual(listedKeys)
    // 反空断言：扫到的条数必须有下限，否则"扫描规则失效"会让本判据空过
    expect(found.length).toBeGreaterThanOrEqual(5)
    // 覆盖范围反空断言：非 scoped 的 `<style>` 块确实被扫到了（RoleAuth 那条即证据）
    expect(found.some((r) => r.file.endsWith('RoleAuth.vue <style>'))).toBe(true)
  })

  it('每条清单项都写明了定性/处理（防"登记了但没说清"）', () => {
    for (const e of INVENTORY) {
      expect(e.verdict.length, `${e.file} :: ${e.selector} 缺少 verdict`).toBeGreaterThan(20)
    }
  })
})

describe('页面级样式的文档作用域 · 真泄漏必须被底座覆盖', () => {
  it('`login.css` 的 body 规则仍在（冻结资产不得被"顺手改掉"）', () => {
    const text = stripComments(readFileSync('src/legacy/eova/_view/index/login.css', 'utf-8'))
    const rules = docRules('login.css', text)
    expect(rules.length).toBe(1)
    expect(rules[0].selector).toBe('body')
    // 属性集合变了 ⇒ 覆盖规则必须跟着补（下面那条判据会随之变红，这是有意的联动）
    expect(rules[0].props).toEqual(
      ['display', 'justify-content', 'align-items', 'height', 'margin'].sort()
    )
  })

  it('底座样式按**属性逐项**覆盖该泄漏（少覆盖一项 = 那项继续泄漏）', () => {
    const leak = docRules('login.css', stripComments(readFileSync('src/legacy/eova/_view/index/login.css', 'utf-8')))[0]
    const rules = scopeRules()
    expect(rules.length).toBeGreaterThanOrEqual(1)
    const override = rules[0]
    // 必须是"登录页在场时保留、不在场时复位"——`:has(.eova-login)` 即旧栈"本文档是登录页"的等价物
    expect(override.selector).toContain(':not(:has(.eova-login))')
    const missing = leak.props.filter((p) => !override.props.includes(p))
    expect(missing, `底座未覆盖的泄漏属性：${missing.join(',')}`).toEqual([])
  })

  it('底座样式必须被 `main.ts` 以 **import 形态** 引入（不引 = 规则写着也不生效）', () => {
    const main = readFileSync('src/main.ts', 'utf-8')
    expect(importedSpecifiers(main)).toContain('./compat/page-document-scope.css')
  })

  it('注释掉的 import **不算**引入（r302 与 r303 两次假绿的同一个坑，实测钉住）', () => {
    // r302：`not.toContain('login.css')` 被子串匹配绊倒（注释里也写了这串）；
    // r303 本判据第一版又用 `toContain("'./compat/page-document-scope.css'")` ——
    // 变异 M2（把该 import 注释掉）**未被捕获**，正是本用例的由来。
    expect(importedSpecifiers("// import './compat/page-document-scope.css'")).toEqual([])
    expect(importedSpecifiers("/* import './compat/page-document-scope.css' */")).toEqual([])
    expect(importedSpecifiers("import './compat/page-document-scope.css'")).toEqual([
      './compat/page-document-scope.css'
    ])
  })
})
