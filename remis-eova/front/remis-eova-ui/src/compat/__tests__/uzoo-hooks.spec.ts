/**
 * `uzoo.vue` 扩展钩子**调用面**的判据（第 145 轮）
 *
 * ## 防的是什么
 *
 * `eova-ext.ts` 的 `UzooVueHook` 声明了 5 个钩子名（`mountBefore`/`setup`/`onReady`/
 * `useTemplateRef`/`onSubmit`），但**旧栈真正有活调用点的只有 3 个**。两个方向都会出错：
 *
 * · **漏调**：旧页面在某时点回调了钩子，SPA 没调 ⇒ 外部扩展**静默失效**
 *   （r107 就抓到过两处真实回归，并把这条纪律写进了文件头）；
 * · **凭空多调**：给旧栈没有调用点的钩子也加上调用 ⇒ 扩展脚本会在**不该触发**的时点被触发，
 *   属行为改变（不是"更完整"）。
 *
 * 本判据把两个方向一起钉住：从**源码**抽出实际调用面，与"旧栈有活调用点"的名单逐字比对。
 *
 * ## 名单取证（本轮 `grep` 复核）
 *
 * ```
 * $ grep -rn "uzoo.vue.useTemplateRef\|uzoo.vue.onSubmit" meta-eova/eova --include=*.js --include=*.html
 * view/.../template/form/add/index.js:58,60   ← **被注释掉**（死代码）
 * （`uzoo.vue.onSubmit` 全树 0 命中）
 * ```
 * ⇒ 活调用点只有 `mountBefore`/`setup`/`onReady`；另两个仍留在类型里是**扩展面**（外部脚本可注册），
 *   但页面不应主动回调。
 *
 * ★ 只读**已入库源码**，不读 `docs/**`。
 */
import { readFileSync, readdirSync } from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'

/** 旧栈**有活调用点**的钩子（本轮 grep 取证；顺序无关） */
const LIVE_HOOKS: readonly string[] = ['mountBefore', 'setup', 'onReady']

/**
 * 扫描 `src/**`（排除测试）里所有 `callUzooHook('x')` 的钩子名
 *
 * @returns 去重后的钩子名
 */
function calledHooks(): string[] {
  const root = process.cwd()
  const out = new Set<string>()
  const walk = (dir: string): void => {
    for (const entry of readdirSync(path.join(root, dir), { withFileTypes: true })) {
      const rel = path.posix.join(dir, entry.name)
      if (entry.isDirectory()) {
        if (rel !== 'src/legacy') {
          walk(rel)
        }
        continue
      }
      if (!/\.(vue|ts)$/.test(entry.name) || rel.includes('__tests__')) {
        continue
      }
      const txt = readFileSync(path.join(root, rel), 'utf-8')
      const re = /callUzooHook\(\s*'([A-Za-z_$][\w$]*)'/g
      let m: RegExpExecArray | null
      while ((m = re.exec(txt)) !== null) {
        out.add(m[1])
      }
    }
  }
  walk('src')
  return [...out].sort()
}

describe('uzoo.vue 扩展钩子的调用面', () => {
  it('① 旧栈有活调用点的 3 个钩子在 SPA 里都被调（反空断言 + 逐字）', () => {
    const called = calledHooks()
    // 反空断言：抽取规则失效时不能"因为找不到而通过"
    expect(called.length, '未从 src 抽到任何 callUzooHook 调用').toBeGreaterThanOrEqual(3)
    expect(called).toEqual([...LIVE_HOOKS].sort())
  })

  it('② 旧栈**没有**活调用点的钩子不得被页面主动回调（不得凭空多调）', () => {
    const called = calledHooks()
    for (const name of ['useTemplateRef', 'onSubmit']) {
      expect(
        called,
        `${name} 在旧栈只有被注释掉的/不存在的调用点 ⇒ SPA 不得主动回调（否则是行为改变）`
      ).not.toContain(name)
    }
  })

  it('③ 类型声明的钩子名单与"活调用点 + 扩展面"一致（改名/删名会让本判据先红）', () => {
    const src = readFileSync('src/compat/eova-ext.ts', 'utf-8')
    const union = /export type UzooVueHook = ([^\n]+)/.exec(src)?.[1] ?? ''
    for (const name of [...LIVE_HOOKS, 'useTemplateRef', 'onSubmit']) {
      expect(union, `UzooVueHook 里缺少 ${name}`).toContain(`'${name}'`)
    }
  })
})
