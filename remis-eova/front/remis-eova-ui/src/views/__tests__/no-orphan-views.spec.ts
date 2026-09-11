/**
 * "视图文件没有孤儿"判据（第 155 轮）
 *
 * ## 防的是什么
 *
 * 逐页迁移累加至今，`src/views/**` 下有 19 个 SFC。**没有孤儿检查**时，一次重构
 * （把某页改成组件、或换名重写）很容易留下**没人引用的旧文件**：它不进打包产物、不影响判据，
 * 但会让后来者按目录名以为"这一页还在这里"，于是改错文件（或被它的内容误导）。
 *
 * ## 口径（两个合法的引用入口）
 *
 * 1. **路由表** `src/router/index.ts` —— 常规页面；
 * 2. **模版组件表** `src/views/template/registry.ts` —— 三个模版页由宿主按 `menu.template`
 *    动态选（不写进路由），这是**有意为之**，不是孤儿。
 *
 * 其余未被任一处引用的 `every .vue under src/views` 即判为孤儿（逐条列出）。
 *
 * ★ 只读**已入库源码**。
 */
import { readFileSync, readdirSync } from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * 递归收集 `src/views` 下的所有 SFC（排除测试目录）
 *
 * @returns 相对工程根的路径（排序）
 */
function viewFiles(): string[] {
  const root = process.cwd()
  const out: string[] = []
  const walk = (dir: string): void => {
    for (const entry of readdirSync(path.join(root, dir), { withFileTypes: true })) {
      const rel = path.posix.join(dir, entry.name)
      if (entry.isDirectory()) {
        if (rel !== 'src/views/__tests__') {
          walk(rel)
        }
        continue
      }
      if (entry.name.endsWith('.vue') && !rel.includes('__tests__')) {
        out.push(rel)
      }
    }
  }
  walk('src/views')
  return out.sort()
}

/**
 * 收集被 `@/views/...` 形式引用的文件（两个入口一起扫）
 *
 * @param files 要扫的引用方
 * @returns 被引用的 `src/views/**` 路径集合
 */
function referencedViews(files: readonly string[]): Set<string> {
  const out = new Set<string>()
  for (const f of files) {
    const src = readFileSync(f, 'utf-8')
    // alias form: '@/views/x/Y.vue'
    const re = /['"]@\/views\/([A-Za-z0-9_\-/]+\.vue)['"]/g
    let m: RegExpExecArray | null
    while ((m = re.exec(src)) !== null) {
      out.add(`src/views/${m[1]}`)
    }
    // relative form (e.g. './TemplateTable.vue' inside src/views/template/registry.ts) -> resolve to src/views path
    const dir = path.posix.dirname(f)
    const relRe = /['"]\.\/([A-Za-z0-9_\-/]+\.vue)['"]/g
    while ((m = relRe.exec(src)) !== null) {
      out.add(path.posix.join(dir, m[1]))
    }
  }
  return out
}

describe('视图文件没有孤儿', () => {
  it('① 每个 src/views 下的 .vue 都被路由表或模版组件表引用（逐条列出孤儿）', () => {
    const views = viewFiles()
    // 反空断言：扫描规则失效时不能"因为找不到而通过"
    expect(views.length).toBeGreaterThanOrEqual(19)

    const referenced = referencedViews(['src/router/index.ts', 'src/views/template/registry.ts'])
    expect(referenced.size, '未从两个入口扫到任何引用').toBeGreaterThanOrEqual(16)

    const orphans = views.filter((v) => !referenced.has(v))
    expect(orphans, `以下视图文件没有任何引用入口（孤儿）：\n  ${orphans.join('\n  ')}`).toEqual([])
  })

  it('② 三个模版页确实由 `registry.ts` 引用（它们**不在**路由表里，是有意为之）', () => {
    const registryRefs = referencedViews(['src/views/template/registry.ts'])
    for (const f of [
      'src/views/template/TemplateTable.vue',
      'src/views/template/TemplateTree.vue',
      'src/views/template/TemplateTreeTable.vue'
    ]) {
      expect(registryRefs, `${f} 应由 registry.ts 引用（宿主按 menu.template 动态选）`).toContain(f)
    }
    // 且它们确实没写进路由表（动态分派，不是"一页一路由"）
    const routerSrc = readFileSync('src/router/index.ts', 'utf-8')
    expect(routerSrc).not.toContain('@/views/template/TemplateTable.vue')
  })
})
