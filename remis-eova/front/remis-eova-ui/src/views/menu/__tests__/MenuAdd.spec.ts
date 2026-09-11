/**
 * 创建菜单页的行为等价判据（旧 `_view/menu/add/app.js` + `app.html` + `MenuController`）。
 *
 * 钉的契约：
 *  ① `data` 初值逐字；`types` 四项；`rules` **5 项**（含 `len[2~15]`/`eovacode`/`range[1~9999]` 三种规则串）；
 *  ② `watch(() => data.template)` ⇒ 变化即拉 `/menu/props/:val`；`onMounted` 也拉一次
 *  ③ `getTemplateProps`：`o.props` 走 `x.json.toObj`、`config[o.key] = o.value`
 *  ④ ★ **提交前把 config 序列化进 `data.config`**（`x.json.toStr`，无缩进）
 *  ⑤ ★ **`parent.uzoo.app.refTree.value.reload()` 无存在性判断**：
 *     父页有 ⇒ 先刷新父树再回传关层；父页没有 ⇒ **抛错被同一个 catch 接住**
 *     （表现为 `客户端请求异常: …`，且**不回传** `ok_done` ⇒ 弹层不关）—— 既有行为，原样保留
 *  ⑥ `onBeforeMount` 回调扩展钩子；两个 fieldset 的 `v-show` 条件
 */
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import MenuAdd from '../MenuAdd.vue'
import { setEovaMe, setEovaTools, type EovaMe, type EovaTools } from '@/compat/eova-runtime'
import { resetUzooWarning } from '@/compat/eova-ext'

vi.mock('axios')
const post = axios.post as unknown as ReturnType<typeof vi.fn>

/** 造 `me` 替身 */
function makeMe(): EovaMe {
  return {
    layer: { msg: vi.fn(), no: vi.fn(), wa: vi.fn(), open: vi.fn() },
    cross: { on: vi.fn(), off: vi.fn(), emit: vi.fn() }
  } as unknown as EovaMe
}

/** 造 `EovaTools` 替身（本页用到 `json.toStr/toObj`、`log`、`validate`） */
function makeTools(): EovaTools {
  return {
    isEmpty: (v: unknown) => v == null || String(v).trim() === '',
    dom: { getViewSize: () => ({ width: 1200, height: 800 }) },
    json: {
      toStr: (v: unknown) => JSON.stringify(v),
      toObj: (v: string) => {
        try {
          return JSON.parse(v)
        } catch {
          return null
        }
      }
    },
    log: vi.fn(),
    validate: {
      start: () => true,
      showMsg: () => '',
      addRules: vi.fn()
    }
  } as unknown as EovaTools
}

/** `ev-*` 替身 */
const stubs = {
  EvSelect: { name: 'EvSelect', props: ['modelValue', 'items', 'type', 'name'], template: '<div class="eova-select" />' },
  EvInput: { name: 'EvInput', props: ['modelValue', 'name', 'type', 'placeholder', 'style'], template: '<input class="eova-value" />' },
  EvIcon: { name: 'EvIcon', props: ['modelValue', 'type', 'placeholder'], template: '<span class="eova-icon-picker" />' },
  EvFind: { name: 'EvFind', props: ['modelValue', 'option', 'multiple', 'placeholder', 'style'], template: '<div class="eova-find" />' }
}
const mountOpts = { global: { components: stubs } }

/**
 * `/menu/props/:val` 的成功响应 —— ★ 必须是**工厂**：
 * 组件会**原地改写** `ret.data[i].props`（字符串 → 对象），若各处共用同一个常量对象，
 * 前一条用例的改写会污染后一条（实测：`JSON.parse({})` 抛错 ⇒ 拿到 null）。
 */
function propsOk() {
  return {
    data: {
      state: 'ok',
      data: [
        { key: 'objectCode', name: '元对象', title: '请选择', type: 'input', value: '', props: '{"a":1}' },
        // ★ `find` 类型的项**必须带 `props`**：模板里是 `:option="o.props.option"`，
        //   真实数据里 find 项一定带 props（否则渲染期读 undefined.option 会报错）
        {
          key: 'findOne',
          name: '查找',
          title: '查找项',
          type: 'find',
          multiple: true,
          info: '说明',
          value: 'x',
          props: '{"option":"eova_object"}'
        }
      ]
    }
  }
}

describe('MenuAdd.vue（旧 _view/menu/add 的行为等价）', () => {
  let me: EovaMe

  beforeEach(() => {
    post.mockReset()
    resetUzooWarning()
    delete (globalThis as unknown as Record<string, unknown>)['uzoo']
    delete (globalThis as unknown as Record<string, unknown>)['refTreeParent']
    me = makeMe()
    setEovaMe(me)
    setEovaTools(makeTools())
  })

  it('data 初值逐字（含 meta_test_001 / 测试功能1 / num=9 / type=app）', () => {
    const w = mount(MenuAdd, mountOpts)
    const d = (w.vm as never as { data: Record<string, unknown> }).data
    expect(d.parent_id).toBe(0)
    expect(d.num).toBe(9)
    expect(d.type).toBe('app')
    expect(d.icon).toBe('eova-icon-app')
    expect(d.code).toBe('meta_test_001')
    expect(d.name).toBe('测试功能1')
    expect(d.template).toBe('table')
    expect(d.url).toBe('')
    expect(d.objects).toEqual([])
  })

  it('types 四项逐字', () => {
    const w = mount(MenuAdd, mountOpts)
    expect((w.vm as never as { types: unknown[] }).types).toEqual([
      { val: 'app', txt: '应用' },
      { val: 'dir', txt: '目录' },
      { val: 'diy', txt: '链接' },
      { val: 'open', txt: '弹窗' }
    ])
  })

  it('★ rules 5 项，且规则串逐字（len[2~15] / eovacode / range[1~9999]）', () => {
    const w = mount(MenuAdd, mountOpts)
    const rules = (w.vm as never as { rules: Record<string, { label: string; rules: string[] }> })
      .rules
    expect(Object.keys(rules)).toEqual(['type', 'name', 'code', 'icon', 'num'])
    expect(rules.type).toMatchObject({ label: '类型', rules: ['required'] })
    expect(rules.name).toMatchObject({ label: '名称', rules: ['required', 'len[2~15]'] })
    expect(rules.code).toMatchObject({ label: '编码', rules: ['required', 'eovacode'] })
    expect(rules.icon).toMatchObject({ label: '图标', rules: ['required'] })
    expect(rules.num).toMatchObject({ label: '序号', rules: ['required', 'range[1~9999]'] })
  })

  it('结构：3 个 fieldset 的 legend；应用配置 fieldset 绑 type==="app"；自定义配置绑 diy||open', async () => {
    const w = mount(MenuAdd, mountOpts)
    await w.vm.$nextTick()
    const legends = w.findAll('legend').map((l) => l.text())
    expect(legends).toEqual(['菜单信息', '应用配置', '自定义配置'])
    const fieldsets = w.findAll('fieldset')
    expect((fieldsets[1].element as HTMLElement).style.display).toBe('') // app ⇒ 显示
    expect((fieldsets[2].element as HTMLElement).style.display).toBe('none') // 非 diy/open ⇒ 隐藏
    // ★ 逐类型验证 v-show 条件：`open`（弹窗）也必须显示 —— 只判 'diy' 的话
    //   "条件丢掉 open" 这类变异在 type==='app' 下不可区分（等价变异）
    const d = (w.vm as never as { data: { type: string } }).data
    d.type = 'open'
    await w.vm.$nextTick()
    expect((fieldsets[2].element as HTMLElement).style.display).toBe('')
    expect((fieldsets[1].element as HTMLElement).style.display).toBe('none')
    d.type = 'diy'
    await w.vm.$nextTick()
    expect((fieldsets[2].element as HTMLElement).style.display).toBe('')
    d.type = 'dir'
    await w.vm.$nextTick()
    expect((fieldsets[2].element as HTMLElement).style.display).toBe('none')
    // 模版示例图走旧静态路径，且默认 default.png
    expect(w.find('.app-template_img img').attributes('src')).toBe(
      '/eova/_view/menu/add/img/default.png'
    )
  })

  it('onMounted 用【当前 template】拉一次模版配置（旧实现如此）', async () => {
    post.mockResolvedValue(propsOk())
    mount(MenuAdd, mountOpts)
    await flushPromises()
    expect(post.mock.calls[0][0]).toBe('/menu/props/table')
    expect(post.mock.calls[0][1]).toEqual({})
  })

  it('★ getTemplateProps：props 走 x.json.toObj；config 逐项取 value', async () => {
    post.mockResolvedValue(propsOk())
    const w = mount(MenuAdd, mountOpts)
    await flushPromises()
    const vm = w.vm as never as {
      templateProps: Array<{ key: string; props?: unknown }>
      config: Record<string, unknown>
    }
    expect(vm.templateProps).toHaveLength(2)
    expect(vm.templateProps[0].props).toEqual({ a: 1 }) // 已解析成对象
    expect(vm.config).toEqual({ objectCode: '', findOne: 'x' })
    // 第二个是 find 类型 ⇒ 渲染 ev-find，且 option 取自解析后的 props
    await w.vm.$nextTick()
    const finds = w.findAllComponents(stubs.EvFind)
    expect(finds).toHaveLength(1)
    expect(finds[0].props('option')).toBe('eova_object')
    expect(finds[0].props('multiple')).toBe(true)
    // 一个 find + 一个 input：证明"按 o.type 分流"确实生效
    expect(w.findAll('input.eova-value').length).toBeGreaterThan(0)
  })

  it('getTemplateProps 业务失败走 layer.no；异常走 layer.msg', async () => {
    post.mockResolvedValue({ data: { state: 'fail', msg: '无此模版' } })
    mount(MenuAdd, mountOpts)
    await flushPromises()
    expect(me.layer.no).toHaveBeenCalledWith('无此模版')

    post.mockRejectedValue(new Error('boom'))
    mount(MenuAdd, mountOpts)
    await flushPromises()
    expect(me.layer.msg).toHaveBeenCalledWith('客户端请求异常: boom')
  })

  it('模板变化触发重新拉取（watch(() => data.template)）', async () => {
    post.mockResolvedValue(propsOk())
    const w = mount(MenuAdd, mountOpts)
    await flushPromises()
    post.mockClear()
    post.mockResolvedValue(propsOk())
    ;(w.vm as never as { data: { template: string } }).data.template = 'tree'
    await flushPromises()
    expect(post.mock.calls[0][0]).toBe('/menu/props/tree')
  })

  it('★★ 提交前把 config 序列化进 data.config（文本，不是对象）', async () => {
    post.mockResolvedValue(propsOk())
    const w = mount(MenuAdd, mountOpts)
    await flushPromises()
    const vm = w.vm as never as {
      data: Record<string, unknown>
      config: Record<string, unknown>
      onSubmit: (id?: unknown) => Promise<void>
    }
    vm.config = { a: 1 }
    post.mockClear()
    post.mockResolvedValue({ data: { state: 'ok' } })
    await vm.onSubmit(1)
    const body = post.mock.calls[0][1] as { config: unknown }
    expect(typeof body.config).toBe('string')
    expect(body.config).toBe('{"a":1}')
  })

  it('★★ 成功：先刷新【父页】的 refTree，再回传 eova-layer-ok_done', async () => {
    post.mockResolvedValue(propsOk())
    const reload = vi.fn()
    // 模拟"被父页以 iframe 打开"：jsdom 里 parent === window ⇒ 在 window 上挂父页的 uzoo
    ;(globalThis as unknown as Record<string, unknown>)['uzoo'] = {
      page: {},
      vue: {},
      app: { refTree: { value: { reload } } }
    }
    const w = mount(MenuAdd, mountOpts)
    await flushPromises()
    post.mockResolvedValue({ data: { state: 'ok' } })
    await (w.vm as never as { onSubmit: (id?: unknown) => Promise<void> }).onSubmit(42)
    expect(reload).toHaveBeenCalledTimes(1)
    expect(me.cross.emit).toHaveBeenCalledWith('eova-layer-ok_done', 42)
  })

  it('★★ 父页没有 refTree ⇒ 抛错被【同一个 catch】接住：提示客户端请求异常，且不回传 ok_done（弹层不关）', async () => {
    post.mockResolvedValue(propsOk())
    const w = mount(MenuAdd, mountOpts)
    await flushPromises()
    // 清掉上一条用例留下的 uzoo（本页自身没有 refTree）
    delete (globalThis as unknown as Record<string, unknown>)['uzoo']
    post.mockResolvedValue({ data: { state: 'ok' } })
    await (w.vm as never as { onSubmit: (id?: unknown) => Promise<void> }).onSubmit(7)
    expect(me.cross.emit).not.toHaveBeenCalled()
    expect(me.layer.msg).toHaveBeenCalledTimes(1)
    expect(String((me.layer.msg as unknown as { mock: { calls: unknown[][] } }).mock.calls[0][0])).toContain(
      '客户端请求异常:'
    )
    expect(me.layer.no).not.toHaveBeenCalled()
  })

  it('业务失败走 layer.no（不刷父树、不回传）', async () => {
    post.mockResolvedValue(propsOk())
    const reload = vi.fn()
    ;(globalThis as unknown as Record<string, unknown>)['uzoo'] = {
      page: {},
      vue: {},
      app: { refTree: { value: { reload } } }
    }
    const w = mount(MenuAdd, mountOpts)
    await flushPromises()
    post.mockResolvedValue({ data: { state: 'fail', msg: '编码重复' } })
    await (w.vm as never as { onSubmit: (id?: unknown) => Promise<void> }).onSubmit(1)
    expect(me.layer.no).toHaveBeenCalledWith('编码重复')
    expect(reload).not.toHaveBeenCalled()
    expect(me.cross.emit).not.toHaveBeenCalled()
  })

  it('★ onBeforeMount 回调扩展钩子 uzoo.vue.mountBefore（本页 app.html:110 正是定义它的一侧）', () => {
    const hook = vi.fn()
    ;(globalThis as unknown as Record<string, unknown>)['uzoo'] = {
      page: {},
      vue: { mountBefore: hook },
      app: {}
    }
    mount(MenuAdd, mountOpts)
    expect(hook).toHaveBeenCalledTimes(1)
  })

  it('required(field)：只有 field.required 才返回 required', () => {
    const w = mount(MenuAdd, mountOpts)
    const f = (w.vm as never as { required: (x: unknown) => string | undefined }).required
    expect(f({ required: true })).toBe('required')
    expect(f({})).toBeUndefined()
  })

  it('引导数据未就绪时 templates 为空（诚实降级），但页面可用', () => {
    const w = mount(MenuAdd, mountOpts)
    expect((w.vm as never as { templates: unknown[] }).templates).toEqual([])
    expect((w.vm as never as { data: { template: string } }).data.template).toBe('table')
  })
})
