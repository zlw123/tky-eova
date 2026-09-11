/**
 * `main.ts` 启动装配**顺序**的判据（第 144 轮）
 *
 * ## 防的是什么
 *
 * 旧栈的运行时装配顺序写在 `index.html` 的多个 `<script>` 里（服务的：先注入全局 Vue/axios，
 * 再按序加载 `EovaTools`/`LayuiVue`/`EovaUI`）。迁到 SPA 后这段顺序落在 `src/main.ts`，
 * 而它的注释早就写着"**顺序不可换**"—— 但**此前没有任何判据**：
 * 谁把 `createApp` 挪到 `loadLegacyRuntime()` 之前（或忘了 `app.use(getEovaUI())`），
 * 症状是 `<ev-*>` 组件**渲染为空且不报错**（R66/R68 已记的同一类静默失效）。
 *
 * 本判据按**出现顺序**核对调用链（顺序错 ⇒ 红），并带反空断言。
 *
 * ★ 只读**已入库源码**（`src/main.ts`），不读 `docs/**`。
 */
import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

/**
 * 取某段代码在文件里的首个出现位置
 *
 * @param src 源码
 * @param needle 片段
 * @returns 下标；未找到返回 -1
 */
function at(src: string, needle: string): number {
  return src.indexOf(needle)
}

describe('main.ts 启动装配顺序', () => {
  const src = readFileSync('src/main.ts', 'utf-8')

  it('① 关键调用都在场（反空断言：抽取/改名会让本判据先红）', () => {
    for (const needle of [
      'loadLegacyRuntime(',
      'installWindowUrls(',
      'loadUiConf(',
      'setDefaultBootstrapFetcher(',
      'createApp(App)',
      'app.use(getEovaUI()',
      'app.use(router)',
      "app.mount('#app')"
    ]) {
      expect(at(src, needle), `main.ts 里找不到 ${needle}`).toBeGreaterThanOrEqual(0)
    }
  })

  it('② 装配顺序：运行时 → 全局 URL 表 → me.conf → 引导来源 → createApp → use(EovaUI) → use(router) → mount', () => {
    const order = [
      'loadLegacyRuntime(',
      'installWindowUrls(',
      'loadUiConf(',
      'setDefaultBootstrapFetcher(',
      'createApp(App)',
      'app.use(getEovaUI()',
      'app.use(router)',
      "app.mount('#app')"
    ]
    const positions = order.map((n) => at(src, n))
    for (let i = 1; i < positions.length; i++) {
      expect(
        positions[i],
        `顺序错：${order[i]} 必须出现在 ${order[i - 1]} 之后（旧栈 index.html 的脚本顺序，改了会让 <ev-*> 渲染为空且不报错）`
      ).toBeGreaterThan(positions[i - 1])
    }
  })

  it('③ 运行时装配在 `createApp` 之前是**硬约束**：EovaUI 未装配时 `getEovaUI()` 会响亮抛出', () => {
    // 说明性断言：装配态检查确实存在（否则"响亮失败"只是注释里的承诺）
    const runtime = readFileSync('src/compat/eova-runtime.ts', 'utf-8')
    expect(runtime).toContain('EovaUI 未装配')
    expect(runtime).toContain('EovaTools 未装配')
  })
})
