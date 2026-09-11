/**
 * EovaUI 组件演示页的判据（旧 `_view/widget/index.html` 62 行的等价）。
 *
 * 钉的契约：
 *  ① **跨制品等值**：页面里每个 `<ev-progress>` 的 `:value`/`text`/`color`/`size` 与旧标记**逐字一致**
 *     （直接读冻结旧文件解析，而不是把 7 组属性在测试里再抄一遍 —— 抄一遍会让"两边同时改错"无人发现）；
 *  ② 7 个进度条各自包在 `<div style="width: 50%; margin: 10px">` 里，且 `fieldset > legend` 文案是「进度条」；
 *  ③ `.main` 与 `.eova-notes` 的存在与文案逐字（含末尾的「！」，旧文件原样）；
 *  ④ ★ 页级 `body` 背景：**挂载时设置、卸载时还原**（旧页是独立文档 ⇒ 作用范围就是"本页打开期间"；
 *     不做这一步就会污染其它路由，或干脆丢掉这条样式）；
 *  ⑤ 页面**无数据依赖**：挂载不抛错（不需要 EovaTools/EovaMe/uzoo/引导数据）。
 */
import { readFileSync } from 'node:fs'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Widget from '../Widget.vue'

/** 冻结的旧标记（逐字节复制在 `src/legacy/…`） */
const LEGACY_WIDGET_HTML = 'src/legacy/_view/widget/index.html'

/** `ev-progress` 替身：把 props 原样渲染成属性，便于逐字比对 */
const EvProgress = defineComponent({
  name: 'EvProgress',
  props: ['value', 'text', 'color', 'size'],
  setup(props) {
    return () =>
      h('div', {
        class: 'stub-progress',
        'data-value': String(props.value),
        'data-text': props.text ?? '',
        'data-color': props.color ?? '',
        'data-size': props.size ?? ''
      })
  }
})

const mountOpts = { global: { components: { EvProgress } } }

/** 从旧标记里解析出每个 `<ev-progress>` 的属性（顺序即渲染顺序） */
function legacyProgress(): Record<string, string>[] {
  const src = readFileSync(LEGACY_WIDGET_HTML, 'utf-8')
  const out: Record<string, string>[] = []
  const re = /<ev-progress([^>]*)><\/ev-progress>/g
  let m: RegExpExecArray | null
  while ((m = re.exec(src)) !== null) {
    const attrs: Record<string, string> = {}
    const attrRe = /(:?)([\w-]+)="([^"]*)"/g
    let a: RegExpExecArray | null
    while ((a = attrRe.exec(m[1])) !== null) {
      // `:value="90"` 与 `text="默认进度条"` 统一按"属性名 → 值"收集
      attrs[a[2]] = a[3]
    }
    out.push(attrs)
  }
  return out
}

describe('Widget.vue（旧 _view/widget/index.html 的行为等价）', () => {
  beforeEach(() => {
    document.body.style.backgroundColor = ''
  })

  it('① 7 个 ev-progress 的属性与旧标记逐字一致（跨制品等值）', () => {
    const expected = legacyProgress()
    // 反空断言：解析规则失效时不能"因为找不到而通过"
    expect(expected).toHaveLength(7)
    expect(expected[0]).toEqual({ value: '90', text: '默认进度条' })
    expect(expected[6]).toEqual({ value: '60', text: '粗进度条', color: '#67C23A', size: '22' })

    const w = mount(Widget, mountOpts)
    const actual = w.findAll('.stub-progress').map((p) => {
      const attrs: Record<string, string> = {
        value: p.attributes('data-value') ?? '',
        text: p.attributes('data-text') ?? ''
      }
      const color = p.attributes('data-color')
      const size = p.attributes('data-size')
      if (color) {
        attrs.color = color
      }
      if (size) {
        attrs.size = size
      }
      return attrs
    })
    expect(actual).toEqual(expected)
  })

  it('② 每个进度条都包在 `width: 50%; margin: 10px` 的 div 里；legend 文案「进度条」', () => {
    const w = mount(Widget, mountOpts)
    const wrappers = w.findAll('.main > div[style]')
    // 7 个进度条包装 div + 末尾的 .eova-notes（它也带 class，不在 [style] 选择器里）
    expect(wrappers).toHaveLength(7)
    for (const d of wrappers) {
      const style = d.attributes('style')?.replace(/\s+/g, ' ') ?? ''
      expect(style).toContain('width: 50%')
      expect(style).toContain('margin: 10px')
    }
    expect(w.find('fieldset > legend').text()).toBe('进度条')
  })

  it('③ `.main` 容器与 `.eova-notes` 文案逐字（含末尾的「！」）', () => {
    const w = mount(Widget, mountOpts)
    expect(w.find('.main').exists()).toBe(true)
    expect(w.find('.eova-notes').text()).toBe('💡 逐步补充其他组件Demo, 急用可在会员群里问！')
    // 旧页的 `<style>` 里有这三条声明（本页在 SFC 的 `<style scoped>` 里逐字保留）
    const src = readFileSync('src/views/widget/Widget.vue', 'utf-8')
    expect(src).toContain('border: 1px solid #f1f1f1')
    expect(src).toContain('background-color: #ffffff')
    expect(src).toContain('fieldset {\n  margin: 10px;\n}')
  })

  it('④ ★ body 背景：挂载时设置、卸载时**还原**（旧页的作用范围＝本页打开期间）', () => {
    document.body.style.backgroundColor = 'rgb(1, 2, 3)'
    const w = mount(Widget, mountOpts)
    expect(document.body.style.backgroundColor).toBe('var(--eova-color_bg)')
    w.unmount()
    expect(document.body.style.backgroundColor, '卸载后必须还原，避免污染其它路由').toBe(
      'rgb(1, 2, 3)'
    )
  })

  it('④ 旧页面本就无 body 背景时，卸载后回到空（不留下痕迹）', () => {
    const w = mount(Widget, mountOpts)
    w.unmount()
    expect(document.body.style.backgroundColor).toBe('')
  })

  it('⑤ 无数据依赖：不装任何运行时接缝也能挂载（旧页只有空壳 index.js）', () => {
    ;(globalThis as unknown as Record<string, unknown>)['uzoo'] = undefined
    ;(globalThis as unknown as Record<string, unknown>)['EovaTools'] = undefined
    ;(globalThis as unknown as Record<string, unknown>)['EovaUI'] = undefined
    expect(() => mount(Widget, mountOpts)).not.toThrow()
  })

  it('⑤ 旧 index.js 是空壳（setup 返回 {}）⇒ 本页不需要迁移任何逻辑', () => {
    const legacyJs = readFileSync('src/legacy/_view/widget/index.js', 'utf-8')
    expect(legacyJs).toContain('return {};')
    expect(legacyJs).toContain("app.mount('#app')")
    // 本页脚本里不得出现取数/弹层等行为（只有 body 背景的挂载/卸载处理）
    const src = readFileSync('src/views/widget/Widget.vue', 'utf-8')
    expect(src).not.toContain('axios')
    expect(src).not.toContain('getEovaMe()')
    void vi
  })
})
