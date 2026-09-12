/**
 * 超管功能面板（**表单页变体**）的判据（旧 `_view/_block/admin_form.html` 的等价）。
 *
 * 钉的契约：
 *  ① `isAdmin` 门控（旧 `#if(loginUser.isAdmin)`）：false/缺省 ⇒ 整块不渲染；
 *  ② ★ **4 条 li 的 `onclick` 与文案逐字一致**（含 emoji），其中**三条带 `mode` 实参**
 *     （旧栈由 `#include(..., mode="…")` 插值）—— 判据按**每个 mode 各跑一遍**，
 *     因为"mode 传错"在这页是**静默**的（点了会去配置另一个模式）；
 *  ③ ★ **`title` 只在第 2 条 li 上**、气泡触发按钮的 `title="开发设置"`、刷新/关闭两个按钮的
 *     `onclick` 是**原始 DOM 表达式** —— 都是旧标记里的既有形状，逐字保留；
 *  ④ `ev-popup` 的 `trigger`/`placement` 与旧标记一致；
 *  ⑤ ★★ **`onclick` 里的函数由冻结脚本提供**：用 `node:vm` 在同一全局里执行
 *     `src/legacy/eova/_view/template/eova.template.js`，断言每个函数名都存在、是函数、形参个数够用
 *     —— 缺函数时旧栈是"点了没反应/控制台报错"，而"渲染成功"的判据完全看不出来。
 *
 * ★ 判据 ②③④ 是**跨制品等值**：直接解析冻结的旧 `admin_form.html`（把 `#(mode)` 换成被测 mode），
 *   而不是把文案再抄一遍到测试里。抄一遍的话，旧标记和组件同时被改错也不会有人发现。
 *
 * ★ 环境声明：`onclick` 是**行内属性文本**，jsdom **不执行**行内处理器 ⇒
 *   "点击 → 处理器真的被调用" 这一环在本文件里**不可执行**，标为 `not executed`；
 *   本文件钉的是"属性文本逐字一致"+"函数确实由冻结脚本提供"（缺一不可，合起来才等价）。
 */
import { readFileSync } from 'node:fs'
import { createContext, runInContext } from 'node:vm'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import EovaAdminForm from '../EovaAdminForm.vue'

/** 冻结的旧标记（逐字节复制在 `src/legacy/…`） */
const LEGACY_ADMIN_FORM_HTML = 'src/legacy/eova/_view/_block/admin_form.html'
/** 冻结的旧运行时脚本（顶层 const 提供全局处理函数） */
const FROZEN_TEMPLATE_JS = 'src/legacy/eova/_view/template/eova.template.js'

/** 组件源码（判据 ⑤ 扫它的 `<template>` 段） */
const COMPONENT_SRC = 'src/components/EovaAdminForm.vue'

/** 三个旧模板实际传进来的 mode（取证：三个 `index.html` 的 `#include(..., mode="…")`） */
const MODES: readonly string[] = ['create', 'update', 'read']

/** `ev-*` 组件的替身（真实组件由 legacy 制品在装配期注册） */
const stubs = {
  EvPopup: {
    name: 'EvPopup',
    props: ['trigger', 'placement'],
    template: '<span class="eova-popup"><slot /><slot name="content" /></span>'
  }
}

/**
 * 挂载被测组件
 *
 * @param mode 表单模式
 * @param isAdmin 是否超管
 */
function mountPanel(mode: string, isAdmin = true) {
  return mount(EovaAdminForm, {
    props: { isAdmin, mode },
    global: { components: stubs }
  })
}

/** 读旧标记，并把 `#(mode)` 换成给定 mode（模拟 `#include(..., mode="…")` 的插值） */
function legacyMarkup(mode: string): string {
  return readFileSync(LEGACY_ADMIN_FORM_HTML, 'utf-8')
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/#\(mode\)/g, mode)
}

/** 旧标记里的 li 列表（`onclick` / `title` / 文案） */
function legacyLis(mode: string): { onclick: string; title: string | null; text: string }[] {
  const out: { onclick: string; title: string | null; text: string }[] = []
  const re = /<li onclick="([^"]*)"([^>]*)>([^<]*)<\/li>/g
  let m: RegExpExecArray | null
  while ((m = re.exec(legacyMarkup(mode))) !== null) {
    out.push({ onclick: m[1], title: attrsOf(m[2])['title'] ?? null, text: m[3] })
  }
  return out
}

/** 旧标记里的按钮列表（class / title / onclick / 内层 `<i>` 的 class） */
function legacyButtons(mode: string): Record<string, string | null>[] {
  const out: Record<string, string | null>[] = []
  const re = /<button([^>]*)>\s*<i([^>]*)><\/i>\s*<\/button>/g
  let m: RegExpExecArray | null
  while ((m = re.exec(legacyMarkup(mode))) !== null) {
    const btn = attrsOf(m[1])
    const icon = attrsOf(m[2])
    out.push({
      class: btn['class'] ?? null,
      title: btn['title'] ?? null,
      onclick: btn['onclick'] ?? null,
      iconClass: icon['class'] ?? null
    })
  }
  return out
}

/** 旧标记里的 `ev-popup` 属性列表 */
function legacyPopups(mode: string): Record<string, string>[] {
  const out: Record<string, string>[] = []
  const re = /<ev-popup([^>]*)>/g
  let m: RegExpExecArray | null
  while ((m = re.exec(legacyMarkup(mode))) !== null) {
    out.push(attrsOf(m[1]))
  }
  return out
}

/**
 * 解析属性串 `a="b" c="d"`
 *
 * @param raw 属性串
 * @returns 属性表
 */
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
 * 本组件源码里 `:onclick="…"` 绑定的**函数名与实参个数**（从 computed 的定义里读）。
 *
 * 本组件的 `onclick` 是**动态绑定**（含 `mode`），不能像 `EovaAdminPanel` 那样直接扫字面量；
 * 故按"渲染出来的属性文本"来解析（见用例 ⑤）——这里只提供名字集合的独立性检查。
 *
 * @returns 形如 `onMetaField()` 的调用串（渲染值）
 */
function renderedCalls(mode: string): { name: string; argCount: number; raw: string }[] {
  const w = mountPanel(mode)
  return w
    .findAll('.eova-select_items li')
    .map((li) => li.attributes('onclick') ?? '')
    .map((raw) => {
      const m = /^([A-Za-z_$][\w$]*)\((.*)\)$/.exec(raw)
      if (!m) {
        throw new Error(`onclick 不是"名字(实参)"形态：${raw}`)
      }
      const args = m[2].trim()
      return { name: m[1], argCount: args === '' ? 0 : args.split(',').length, raw }
    })
}

/**
 * 在 `node:vm` 里执行冻结脚本（与浏览器里 `<script src>` 同为经典脚本 ⇒ 顶层 const 落全局词法环境）
 *
 * @returns 该 vm 上下文（可继续 `runInContext` 取全局词法环境里的名字）
 */
function frozenScriptContext(): ReturnType<typeof createContext> {
  const ctx = createContext({
    console: { log: () => {} },
    EovaTools: { x: { time: { now: () => 0 }, str: { template: (t: string) => t } } },
    EovaUI: { me: { ver: 'test', layer: {}, cross: {} } },
    parent: undefined,
    axios: { post: () => Promise.resolve({ data: { state: 'ok' } }) }
  })
  runInContext(readFileSync(FROZEN_TEMPLATE_JS, 'utf-8'), ctx)
  return ctx
}

describe('EovaAdminForm（旧 _block/admin_form.html 的等价）', () => {
  it('① isAdmin 门控：true 才渲染（旧 `#if(loginUser.isAdmin)`）', () => {
    expect(mountPanel('create', false).find('.eova-admins').exists()).toBe(false)
    expect(
      mount(EovaAdminForm, { props: { mode: 'create' }, global: { components: stubs } })
        .find('.eova-admins')
        .exists()
    ).toBe(false)
    expect(mountPanel('create', true).find('.eova-admins > .eova-tools_box').exists()).toBe(true)
  })

  it('② 4 条 li 的 onclick/文案/（唯一那条）title 与旧标记逐字一致 —— 三个 mode 各跑一遍', () => {
    for (const mode of MODES) {
      const expected = legacyLis(mode)
      // 反空判据：解析规则失效时不能"因为找不到而通过"
      expect(expected, `mode=${mode} 未从旧标记解析出 li`).toHaveLength(4)
      // ★ title 只有第 2 条有（旧标记如此）
      expect(expected.map((l) => l.title)).toEqual([null, mode, null, null])

      const w = mountPanel(mode)
      const actual = w.findAll('.eova-select_items li').map((li) => ({
        onclick: li.attributes('onclick') ?? '',
        title: li.attributes('title') ?? null,
        text: li.text()
      }))
      expect(actual, `mode=${mode} 的 li 与旧标记不一致`).toEqual(expected)
      // 逐条点名三条带 mode 实参的（防止"全部相等"是因为两边都少了实参这种巧合）
      expect(actual.map((a) => a.onclick)).toEqual([
        'onMetaField()',
        `onMetaFieldDiy('${mode}')`,
        `onReorderForm('${mode}')`,
        `onLayerSize('${mode}')`
      ])
    }
  })

  it('★ 反向：mode 传错必须可观测（三条带实参的 onclick 文本随之变化）', () => {
    const create = mountPanel('create')
      .findAll('.eova-select_items li')
      .map((li) => li.attributes('onclick'))
    const read = mountPanel('read')
      .findAll('.eova-select_items li')
      .map((li) => li.attributes('onclick'))
    // 若组件把 mode 写死（例如永远 'create'），下面这条会红 —— 这是本页最危险的一类回归
    expect(create).not.toEqual(read)
    expect(read[1]).toBe("onMetaFieldDiy('read')")
  })

  it('③ 3 个按钮的 class/title/onclick 与旧标记逐字一致（含 title 只在前两个上）', () => {
    const expected = legacyButtons('create')
    expect(expected).toHaveLength(3)
    expect(expected.map((b) => b.title)).toEqual(['开发设置', '刷新', '关闭'])
    // 后两个是**原始 DOM 表达式**（不是冻结脚本里的函数）
    expect(expected[1].onclick).toBe('location.reload()')
    expect(expected[2].onclick).toBe("document.querySelectorAll('.eova-admins')[0].remove()")

    const w = mountPanel('create')
    const actual = w.findAll('.eova-tools_box button').map((b) => ({
      class: b.attributes('class') ?? null,
      title: b.attributes('title') ?? null,
      onclick: b.attributes('onclick') ?? null,
      iconClass: b.find('i').attributes('class') ?? null
    }))
    expect(actual).toEqual(expected)
  })

  it('④ ev-popup 的 trigger/placement 与旧标记一致（1 个）', () => {
    const expected = legacyPopups('create')
    expect(expected).toHaveLength(1)
    const pops = mountPanel('create').findAllComponents(stubs.EvPopup)
    expect(pops).toHaveLength(1)
    expect(pops.map((p) => ({ trigger: p.props('trigger'), placement: p.props('placement') }))).toEqual(
      expected.map((e) => ({ trigger: e['trigger'], placement: e['placement'] }))
    )
  })

  it('⑤ ★ 每个 onclick 里的函数都由冻结脚本提供，且形参个数够用', () => {
    const calls = renderedCalls('create')
    // 反空判据：抽取规则失效时本判据不能"因为找不到而通过"
    expect(calls).toHaveLength(4)
    expect(calls.map((c) => c.name)).toEqual([
      'onMetaField',
      'onMetaFieldDiy',
      'onReorderForm',
      'onLayerSize'
    ])

    const ctx = frozenScriptContext()
    for (const c of calls) {
      const arity = runInContext(`${c.name}.length`, ctx) as number
      expect(arity, `冻结脚本未提供 ${c.name}()（onclick 会点了没反应）`).toBeGreaterThanOrEqual(
        c.argCount
      )
      expect(runInContext(`typeof ${c.name}`, ctx), `${c.name} 不是函数`).toBe('function')
    }
  })

  it('⑤ 组件源码里不再有"字面量 onclick"（本组件全部是动态绑定 ⇒ 名字集合以渲染值为准）', () => {
    const src = readFileSync(COMPONENT_SRC, 'utf-8')
    const tpl = src.slice(src.indexOf('<template>'), src.lastIndexOf('</template>'))
    const literal = tpl.match(/onclick="[A-Za-z_$][\w$]*\(/g) ?? []
    expect(literal, `模板里出现了字面量 onclick 调用：${literal.join(' | ')}`).toEqual([])
    // 但两个**原始 DOM 表达式**必须原样保留（它们是旧标记的一部分，不是冻结脚本里的函数）
    expect(tpl).toContain('onclick="location.reload()"')
    expect(tpl).toContain("onclick=\"document.querySelectorAll('.eova-admins')[0].remove()\"")
  })
})
