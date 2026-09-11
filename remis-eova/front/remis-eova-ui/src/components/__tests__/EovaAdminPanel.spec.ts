/**
 * 超管功能面板的判据（旧 `_view/_block/admin.html` 的等价）。
 *
 * 钉的契约：
 *  ① `isAdmin` 门控（旧 `#if(loginUser.isAdmin)`）：false/缺省 ⇒ 整块不渲染；
 *  ② ★ **8 条 li 与旧标记逐字一致**（`onclick` 字符串 + 文案），且旧文件里那条被注释掉的
 *     "查看按钮" **不得**被渲染出来；
 *  ③ ★ **按钮的 `title` 位置不统一**这一既有怪癖原样保留（第 1 个在 `<button>` 上，
 *     第 2/3 个在 `<i>` 上），第 4 个关闭按钮的 `onclick` 是**原始 DOM 表达式**；
 *  ④ 3 个 `ev-popup` 的 `trigger`/`placement` 与旧标记一致；
 *  ⑤ ★★ **`onclick` 里的函数由冻结脚本提供**：用 `node:vm` 在同一全局里执行
 *     `src/legacy/eova/_view/template/eova.template.js`，断言本组件用到的每个函数名
 *     都存在、类型是函数、形参个数够用 —— 缺函数时旧栈是"点了没反应/控制台报错"，
 *     而"渲染成功"的判据完全看不出来。
 *
 * ★ 判据 ②③④ 是**跨制品等值**（直接读冻结的旧 `admin.html` 解析，而不是把文案再抄一遍到测试里）：
 *   抄一遍的话，旧标记和组件同时被改错也不会有人发现。
 */
import { readFileSync } from 'node:fs'
import { createContext, runInContext } from 'node:vm'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import EovaAdminPanel from '../EovaAdminPanel.vue'

/** 冻结的旧标记（逐字节复制在 `src/legacy/…`） */
const LEGACY_ADMIN_HTML = 'src/legacy/eova/_view/_block/admin.html'
/** 冻结的旧运行时脚本（顶层 const 提供全局处理函数） */
const FROZEN_TEMPLATE_JS = 'src/legacy/eova/_view/template/eova.template.js'

/** `ev-*` 组件的替身（真实组件由 legacy 制品在装配期注册） */
const stubs = {
  EvPopup: {
    name: 'EvPopup',
    props: ['trigger', 'placement'],
    template: '<span class="eova-popup"><slot /><slot name="content" /></span>'
  }
}

const mountOpts = { global: { components: stubs } }

/** 读旧标记，并**去掉 HTML 注释**（旧文件里有一条注释掉的 li，不能算自己的行） */
function legacyMarkup(): string {
  return readFileSync(LEGACY_ADMIN_HTML, 'utf-8').replace(/<!--[\s\S]*?-->/g, '')
}

/** 旧标记里的 li 列表（`onclick` + 文案） */
function legacyLis(): { onclick: string; text: string }[] {
  const out: { onclick: string; text: string }[] = []
  const re = /<li onclick="([^"]*)">([^<]*)<\/li>/g
  let m: RegExpExecArray | null
  while ((m = re.exec(legacyMarkup())) !== null) {
    out.push({ onclick: m[1], text: m[2] })
  }
  return out
}

/** 旧标记里的按钮列表（`class` / `title` / `onclick` / 内层 `<i>` 的 class 与 title） */
function legacyButtons(): Record<string, string | null>[] {
  const out: Record<string, string | null>[] = []
  const re = /<button([^>]*)>\s*<i([^>]*)><\/i>\s*<\/button>/g
  let m: RegExpExecArray | null
  while ((m = re.exec(legacyMarkup())) !== null) {
    const btn = attrsOf(m[1])
    const icon = attrsOf(m[2])
    out.push({
      class: btn['class'] ?? null,
      title: btn['title'] ?? null,
      onclick: btn['onclick'] ?? null,
      iconClass: icon['class'] ?? null,
      iconTitle: icon['title'] ?? null
    })
  }
  return out
}

/** 旧标记里的 `ev-popup` 属性列表 */
function legacyPopups(): Record<string, string>[] {
  const out: Record<string, string>[] = []
  const re = /<ev-popup([^>]*)>/g
  let m: RegExpExecArray | null
  while ((m = re.exec(legacyMarkup())) !== null) {
    out.push(attrsOf(m[1]))
  }
  return out
}

/** 解析属性串 `a="b" c="d"` */
function attrsOf(raw: string): Record<string, string> {
  const out: Record<string, string> = {}
  const re = /([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*"([^"]*)"/g
  let m: RegExpExecArray | null
  while ((m = re.exec(raw)) !== null) {
    out[m[1]] = m[2]
  }
  return out
}

/**
 * 本组件源码里的 `onclick="名字(实参…)"`（排除原始 DOM 表达式那一条）。
 *
 * 只扫 `<template>` 段：文件头的说明里也引用了 `onclick="onMetaField()"` 这种字面量，
 * 连注释一起扫会把它们当成"组件真的用了"，让判据数出 9 条（**已实测踩到**）。
 */
function componentOnclickCalls(): { name: string; argCount: number, raw: string }[] {
  const src = readFileSync('src/components/EovaAdminPanel.vue', 'utf-8')
  const tpl = src.slice(src.indexOf('<template>'), src.lastIndexOf('</template>'))
  const out: { name: string; argCount: number; raw: string }[] = []
  const re = /onclick="([A-Za-z_$][\w$]*)\(([^"]*)\)"/g
  let m: RegExpExecArray | null
  while ((m = re.exec(tpl)) !== null) {
    const args = m[2].trim()
    out.push({ name: m[1], argCount: args === '' ? 0 : args.split(',').length, raw: m[0] })
  }
  return out
}

/**
 * 在 `node:vm` 里执行冻结脚本（与浏览器里 `<script src>` 同为经典脚本 ⇒ 顶层 const 落全局词法环境）。
 *
 * @returns 该 vm 上下文（可继续 `runInContext` 取全局词法环境里的名字）
 */
function frozenScriptContext(): ReturnType<typeof createContext> {
  const ctx = createContext({
    console: { log: () => {} },
    // 冻结脚本第 4/8/10 行引用的三个全局（与装配期一致的最小替身）
    EovaTools: { x: { time: { now: () => 0 }, str: { template: (t: string) => t } } },
    EovaUI: { me: { ver: 'test', layer: {}, cross: {} } },
    parent: undefined,
    axios: { post: () => Promise.resolve({ data: { state: 'ok' } }) }
  })
  runInContext(readFileSync(FROZEN_TEMPLATE_JS, 'utf-8'), ctx)
  return ctx
}

describe('EovaAdminPanel（旧 _block/admin.html 的等价）', () => {
  it('① isAdmin 门控：true 才渲染（旧 `#if(loginUser.isAdmin)`）', () => {
    expect(mount(EovaAdminPanel, { props: { isAdmin: false }, ...mountOpts }).find('.eova-admins').exists()).toBe(false)
    expect(mount(EovaAdminPanel, mountOpts).find('.eova-admins').exists()).toBe(false)
    const w = mount(EovaAdminPanel, { props: { isAdmin: true }, ...mountOpts })
    expect(w.find('.eova-admins > .eova-tools_box').exists()).toBe(true)
  })

  it('② 8 条 li 的 onclick 与文案与旧标记逐字一致（含 emoji），且注释掉的那条不渲染', () => {
    const expected = legacyLis()
    // 反空判据 + 数量：旧文件里被注释掉的"查看按钮"不该出现在这里
    expect(expected).toHaveLength(8)
    expect(expected.map((l) => l.text)).not.toContain('查看按钮')

    const w = mount(EovaAdminPanel, { props: { isAdmin: true }, ...mountOpts })
    const actual = w.findAll('.eova-select_items li').map((li) => ({
      onclick: li.attributes('onclick') ?? '',
      text: li.text()
    }))
    expect(actual).toEqual(expected)
  })

  it('③ 四个按钮的 class/title/onclick 与旧标记逐字一致（含 title 位置不统一这一怪癖）', () => {
    const expected = legacyButtons()
    expect(expected).toHaveLength(4)
    // 怪癖本身也钉住：第 1 个 title 在 button 上，第 2/3 个在 i 上
    expect(expected.map((b) => b.title)).toEqual(['元数据', null, null, '暂时关闭'])
    expect(expected.map((b) => b.iconTitle)).toEqual([null, '模版', '按钮', null])

    const w = mount(EovaAdminPanel, { props: { isAdmin: true }, ...mountOpts })
    const actual = w.findAll('.eova-tools_box button').map((b) => {
      const icon = b.find('i')
      return {
        class: b.attributes('class') ?? null,
        title: b.attributes('title') ?? null,
        onclick: b.attributes('onclick') ?? null,
        iconClass: icon.attributes('class') ?? null,
        iconTitle: icon.attributes('title') ?? null
      }
    })
    expect(actual).toEqual(expected)
  })

  it('④ 三个 ev-popup 的 trigger/placement 与旧标记一致', () => {
    const expected = legacyPopups()
    expect(expected).toHaveLength(3)
    const w = mount(EovaAdminPanel, { props: { isAdmin: true }, ...mountOpts })
    const pops = w.findAllComponents(stubs.EvPopup)
    expect(pops).toHaveLength(expected.length)
    expect(
      pops.map((p) => ({ trigger: p.props('trigger'), placement: p.props('placement') }))
    ).toEqual(expected.map((e) => ({ trigger: e['trigger'], placement: e['placement'] })))
  })

  it('⑤ ★ 每个 onclick 里的函数都由冻结脚本提供，且形参个数够用', () => {
    const calls = componentOnclickCalls()
    // 反空判据：抽取规则失效时本判据不能"因为找不到而通过"（组件里必须有 8 条）
    expect(calls).toHaveLength(8)

    const ctx = frozenScriptContext()
    for (const c of calls) {
      const arity = runInContext(`${c.name}.length`, ctx) as number
      expect(arity, `冻结脚本未提供 ${c.name}()（onclick 会点了没反应）`).toBeGreaterThanOrEqual(
        c.argCount
      )
      expect(runInContext(`typeof ${c.name}`, ctx), `${c.name} 不是函数`).toBe('function')
    }
  })

  it('⑤ 关闭按钮的 onclick 是原始 DOM 表达式（不是冻结脚本里的函数）', () => {
    const w = mount(EovaAdminPanel, { props: { isAdmin: true }, ...mountOpts })
    const close = w.findAll('.eova-tools_box button')[3]
    expect(close.attributes('onclick')).toBe(
      "document.querySelectorAll('.eova-admins')[0].remove()"
    )
  })
})
