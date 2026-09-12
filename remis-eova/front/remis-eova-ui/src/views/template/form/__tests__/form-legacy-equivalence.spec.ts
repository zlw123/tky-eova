/**
 * 表单三页的**跨制品契约判据**（切片 S6，第 295 轮）。
 *
 * ## 为什么单独一个文件
 *
 * `FormAdd/Update/Detail.spec.ts` 各自钉"本页行为"，但它们的期望值是**写在判据里**的
 * （`mode="create"`、`name="update_from"`…）。这类"判据抄一遍组件里的字面量"有个已知弱点：
 * 旧标记与组件**同时被改错**时，判据照样绿。
 *
 * 本文件改用**跨制品等值**：直接解析冻结的旧模板
 * `src/legacy/eova/_view/template/form/{add,update,detail}/index.html` 的 `<ev-form>` 属性集
 * 与 `#include(... admin_form.html, mode="…")`，把服务端插值（`#(object.code)`/`#(id)`）替换成
 * 与判据同一批取值，再与**实际渲染出来的 props** 逐项比对。
 *
 * 同时钉住三个**接线**（每一条漂移都是静默的）：
 *  ① 路由 path 的动态段名必须与组件读取的名字一致（`:objectCode` ↔ `route.params.objectCode`）
 *     —— 改个名不会让任何构建/类型判据变红，症状只是"页面永远显示缺参降级"；
 *  ② 三条路由各自绑到正确的组件；
 *  ③ 三条路由的**具体 URL** 必须归 SPA（否则 dev 代理会把它送回后端 HTML）。
 */
import { readFileSync } from 'node:fs'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { isSpaOwnedPath } from '@/router/routes'
import { routes } from '@/router/index'
import FormAdd from '../FormAdd.vue'
import FormUpdate from '../FormUpdate.vue'
import FormDetail from '../FormDetail.vue'
import { resetUzooWarning } from '@/compat/eova-ext'
import { setEovaMe, setEovaTools, type EovaMe, type EovaTools } from '@/compat/eova-runtime'

vi.mock('axios')
// ★ 部分 mock：本文件要 import 真实路由表（`@/router/index` 顶层会 `createRouter()`），
//   整体 mock 掉会让它拿不到 `createRouter` ⇒ 只替换 `useRoute`。
vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return { ...actual, useRoute: () => ({ params: routeParams }) }
})

/** 路由参数（本文件里三页共用；每个用例前重置） */
const routeParams: { objectCode?: string } = { objectCode: '' }

/** 判据里统一使用的"具体取值"（替换旧模板里的服务端插值） */
const OBJECT_CODE = 'eova_object_code'
const ID = '12'

/** 三个旧模板（逐字节复制在 `src/legacy/…`） */
const LEGACY: readonly { action: string; html: string; component: unknown }[] = [
  {
    action: 'add',
    html: 'src/legacy/eova/_view/template/form/add/index.html',
    component: FormAdd
  },
  {
    action: 'update',
    html: 'src/legacy/eova/_view/template/form/update/index.html',
    component: FormUpdate
  },
  {
    action: 'detail',
    html: 'src/legacy/eova/_view/template/form/detail/index.html',
    component: FormDetail
  }
]

/** `ev-form` 的替身 */
const EvFormStub = defineComponent({
  name: 'EvForm',
  props: ['mode', 'name', 'object', 'biz', 'pk', 'modelValue'],
  emits: ['update:modelValue', 'ready', 'resize', 'submit'],
  setup(_props, { expose }) {
    expose({
      validate: () => true,
      getData: () => ({}),
      reset: vi.fn(),
      getFieldInstance: vi.fn()
    })
    return () => h('div', { class: 'ev-form' })
  }
})

/** `ev-popup` 的替身（`EovaAdminForm` 是真组件，它要用到） */
const EvPopupStub = defineComponent({
  name: 'EvPopup',
  props: ['trigger', 'placement'],
  template: '<span class="eova-popup"><slot /><slot name="content" /></span>'
})

const mountOpts = { global: { components: { EvForm: EvFormStub, EvPopup: EvPopupStub } } }

/**
 * 把旧模板里的服务端插值换成判据用的具体值
 *
 * @param html 旧模板原文
 * @returns 替换后的文本
 */
function interpolate(html: string): string {
  return html.replace(/#\(object\.code\)/g, OBJECT_CODE).replace(/#\(id\)/g, ID)
}

/**
 * 解析旧模板里 `<ev-form …>` 的属性集（只取**静态字符串**属性，`v-model`/`@ready` 另判）
 *
 * @param html 旧模板原文
 * @returns 属性表
 */
function legacyEvFormAttrs(html: string): Record<string, string> {
  const m = /<ev-form([\s\S]*?)>/.exec(interpolate(html))
  if (!m) {
    throw new Error('旧模板里没有 <ev-form>')
  }
  const out: Record<string, string> = {}
  const re = /([A-Za-z_@][-A-Za-z0-9_:@.]*)\s*=\s*"([^"]*)"/g
  let a: RegExpExecArray | null
  while ((a = re.exec(m[1])) !== null) {
    out[a[1]] = a[2]
  }
  return out
}

/** 解析旧模板里 `#include("…/admin_form.html", mode="…")` 的 mode */
function legacyAdminFormMode(html: string): string {
  const m = /admin_form\.html"\s*,\s*mode="([^"]*)"/.exec(html)
  if (!m) {
    throw new Error('旧模板里没有 admin_form.html 的 mode= 参数')
  }
  return m[1]
}

/** 造 `me` 替身（本文件不提交，只需要能挂载） */
function makeMe(): EovaMe {
  return {
    layer: { msg: vi.fn(), no: vi.fn(), wa: vi.fn(), open: vi.fn() },
    cross: { on: vi.fn(), off: vi.fn(), emit: vi.fn() },
    urls: { url: vi.fn(() => '/api/form/update/{{object_code}}') }
  } as unknown as EovaMe
}

/** 造 `EovaTools` 替身 */
function makeTools(): EovaTools {
  return {
    isEmpty: (v: unknown) => v == null || String(v).trim() === '',
    validate: { start: vi.fn(() => true), showMsg: vi.fn(() => ''), addRules: vi.fn() },
    dom: { getViewSize: () => ({ width: 1200, height: 800 }) },
    log: vi.fn()
  } as unknown as EovaTools
}

describe('表单三页 ↔ 冻结旧模板（跨制品等值）', () => {
  beforeEach(() => {
    routeParams.objectCode = OBJECT_CODE
    window.history.replaceState({}, '', `/app/add/${OBJECT_CODE}?id=${ID}`)
    setEovaMe(makeMe())
    setEovaTools(makeTools())
    resetUzooWarning()
    delete (globalThis as unknown as Record<string, unknown>)['uzoo']
  })

  it('★ `<ev-form>` 的属性集与旧模板逐条等值（含"add 页没有 biz/pk"这类形状差别）', () => {
    for (const { action, html, component } of LEGACY) {
      const legacy = legacyEvFormAttrs(readFileSync(html, 'utf-8'))
      // 反空判据：解析规则失效时不能"因为两边都空而通过"
      expect(legacy['mode'], `${action} 未解析出 mode`).toBeTruthy()
      expect(legacy['object'], `${action} 未解析出 object`).toBeTruthy()

      delete (globalThis as unknown as Record<string, unknown>)['uzoo']
      const w = mount(component as never, mountOpts)
      const form = w.findComponent(EvFormStub)
      expect(form.exists(), `${action} 未渲染 ev-form`).toBe(true)

      // mode / name / object：旧模板里都是静态字符串（已做插值替换）
      expect(form.props('mode'), `${action} 的 mode 与旧模板不一致`).toBe(legacy['mode'])
      expect(form.props('name'), `${action} 的 name 与旧模板不一致`).toBe(legacy['name'])
      expect(form.props('object'), `${action} 的 object 与旧模板不一致`).toBe(legacy['object'])
      // biz / pk：旧模板里**没有**就是没有（add 页两项都没有）
      expect(form.props('biz'), `${action} 的 biz 与旧模板不一致`).toBe(legacy['biz'])
      expect(form.props('pk'), `${action} 的 pk 与旧模板不一致`).toBe(legacy['pk'])
      // v-model + @ready 两项在旧模板里是 Vue 指令，单独钉
      expect(form.props('modelValue'), `${action} 的 v-model 未绑到 data`).toBe(
        (w.vm as never as { data: Record<string, unknown> }).data
      )
    }
  })

  it('★ 三个旧模板的形状差别被完整保留（add 无 biz/pk；name 只有 add 是动态的）', () => {
    const attrs = LEGACY.map((l) => ({
      action: l.action,
      ...legacyEvFormAttrs(readFileSync(l.html, 'utf-8'))
    }))
    // add：没有 biz、没有 pk；name 用 object.code（已插值 ⇒ 等于 OBJECT_CODE）
    expect(attrs[0]).toMatchObject({ mode: 'create', name: OBJECT_CODE, object: OBJECT_CODE })
    expect('biz' in attrs[0]).toBe(false)
    expect('pk' in attrs[0]).toBe(false)
    // update / detail：有 biz 与 pk，name 是**字面量**
    expect(attrs[1]).toMatchObject({
      mode: 'update',
      name: 'update_from',
      object: OBJECT_CODE,
      biz: OBJECT_CODE,
      pk: ID
    })
    expect(attrs[2]).toMatchObject({
      mode: 'read',
      name: 'detail_from',
      object: OBJECT_CODE,
      biz: OBJECT_CODE,
      pk: ID
    })
  })

  it('★ `admin_form.html` 的 `mode=` 实参与页面传给面板的 mode 一致', () => {
    for (const { action, html, component } of LEGACY) {
      const want = legacyAdminFormMode(readFileSync(html, 'utf-8'))
      expect(want, `${action} 未解析出 admin_form 的 mode`).toBeTruthy()
      delete (globalThis as unknown as Record<string, unknown>)['uzoo']
      const w = mount(component as never, mountOpts)
      const panel = w.findComponent({ name: 'EovaAdminForm' })
      expect(panel.exists(), `${action} 未渲染 EovaAdminForm`).toBe(true)
      expect(panel.props('mode'), `${action} 传给面板的 mode 与旧 include 实参不一致`).toBe(want)
    }
  })
})

describe('表单三页的路由接线', () => {
  /** 取路由表里 path → name 的映射 */
  function routeNames(): Record<string, string | undefined> {
    return Object.fromEntries(
      routes.map((r) => [String(r.path), r.name == null ? undefined : String(r.name)])
    )
  }

  it('① 三条路由存在且各自绑到正确组件（不是"都指向同一个组件"）', () => {
    const byPath = new Map(routes.map((r) => [String(r.path), r]))
    const wants: [string, string, unknown][] = [
      ['/app/add/:objectCode', 'form-add', FormAdd],
      ['/app/update/:objectCode', 'form-update', FormUpdate],
      ['/app/detail/:objectCode', 'form-detail', FormDetail]
    ]
    for (const [path, name, component] of wants) {
      const r = byPath.get(path)
      expect(r, `路由表缺 ${path}`).toBeTruthy()
      expect(r!.name, `${path} 的 name 不对`).toBe(name)
      expect(r!.component, `${path} 绑到了别的组件`).toBe(component)
    }
    // 反向：三条路径互不相同（写成同一条会让后一条覆盖前一条而"看起来还在"）
    expect(new Set(wants.map((w) => w[0])).size).toBe(3)
    expect(routeNames()['/app/add/:objectCode']).toBe('form-add')
  })

  it('② ★ 路由的动态段名必须与组件读取的名字一致（改名 = 页面永远走缺参降级）', () => {
    const files: [string, string][] = [
      ['src/views/template/form/FormAdd.vue', 'FormAdd'],
      ['src/views/template/form/FormUpdate.vue', 'FormUpdate'],
      ['src/views/template/form/FormDetail.vue', 'FormDetail']
    ]
    for (const [file] of files) {
      const src = readFileSync(file, 'utf-8')
      const read = /route\.params\.([A-Za-z_$][\w$]*)/.exec(src)
      expect(read, `${file} 未从 route.params 读参数`).toBeTruthy()
      const paramName = read![1]
      const routesWithParam = routes.filter((r) => String(r.path).startsWith('/app/'))
      const matching = routesWithParam.filter((r) =>
        new RegExp(`:${paramName}\\b`).test(String(r.path))
      )
      expect(
        matching.length,
        `${file} 读的是 route.params.${paramName}，但 /app/** 下没有带 :${paramName} 的路由`
      ).toBeGreaterThan(0)
      // 且该名字必须出现在三条表单路由上（三页同构，不能只对一页有效）
      const formRoutes = routesWithParam.filter((r) =>
        ['form-add', 'form-update', 'form-detail'].includes(String(r.name))
      )
      expect(formRoutes).toHaveLength(3)
      for (const r of formRoutes) {
        expect(String(r.path), `${String(r.path)} 的动态段不是 :${paramName}`).toContain(
          `:${paramName}`
        )
      }
    }
  })

  it('③ 三条路由的具体 URL 必须归 SPA（否则 dev 代理会把它送回后端 HTML）', () => {
    for (const [, , url] of [
      ['add', FormAdd, `/app/add/${OBJECT_CODE}?biz=x&id=${ID}`],
      ['update', FormUpdate, `/app/update/${OBJECT_CODE}?id=${ID}`],
      ['detail', FormDetail, `/app/detail/${OBJECT_CODE}?id=${ID}`]
    ] as unknown as [string, unknown, string][]) {
      expect(isSpaOwnedPath(url), `${url} 未被 SPA 所有权规则覆盖`).toBe(true)
    }
    // 反向：非表单的 2 段路径仍归后端（不能"整段让给 SPA"）
    expect(isSpaOwnedPath('/app/errors/404')).toBe(false)
    expect(isSpaOwnedPath('/app/add')).toBe(false)
  })
})
