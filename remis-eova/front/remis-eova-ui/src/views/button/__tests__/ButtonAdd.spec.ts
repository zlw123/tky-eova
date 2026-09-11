/**
 * 快速添加按钮页的行为等价判据（旧 `_view/button/add/app.js` + `app.html` + `ButtonController`）。
 *
 * 钉的契约：
 *  ① `data` 初值**逐字**（含 `name:'测试'`/`event:'test'`/`ui:'/demo/test/btn.js'`/`auth:'/demo/xxx'` 这些**样例值**）；
 *  ② `rules` **只有 6 项** —— `menu_code`/`role`/`style` **不在**校验里；
 *  ③ `menu_code` 来自**路由第 0 段**、`role` 来自引导值（回退 `'1'` = `EovaConst.ADMIN_RID`）；
 *  ④ 图标字段用的是 **`ev-icon`**（不是 `ev-input`）—— 该组件不在制品具名导出里，但确实全局注册；
 *  ⑤ 提交：载荷是**整个 data**；成功**只**回传 `eova-layer-ok_done`（不像 su 页还有 `_data` 那条）；
 *  ⑥ 6 个样式快捷按钮把样式名写进 `data.style`（"默认"写空串）。
 */
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import ButtonAdd from '../ButtonAdd.vue'
import { setEovaMe, setEovaTools, type EovaMe, type EovaTools, type EovaValidateRule } from '@/compat/eova-runtime'

vi.mock('axios')
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { menuCode: 'menu_abc' } })
}))

const post = axios.post as unknown as ReturnType<typeof vi.fn>

/** 造 `me` 替身 */
function makeMe(): EovaMe {
  return {
    layer: { msg: vi.fn(), no: vi.fn(), wa: vi.fn(), open: vi.fn() },
    cross: { on: vi.fn(), off: vi.fn(), emit: vi.fn() }
  } as unknown as EovaMe
}

/**
 * 造 `EovaTools` 替身（按制品语义：`start` 原地写回 `rule.msg`，`showMsg` 汇总非空 msg 以 `<br>` 连接）
 *
 * @param forceFail true ⇒ 让校验失败（用于钉"不过不发请求"）
 */
function makeTools(forceFail = false): EovaTools {
  return {
    isEmpty: (v: unknown) => v == null || String(v).trim() === '',
    dom: { getViewSize: () => ({ width: 1200, height: 800 }) },
    validate: {
      start: (rules: Record<string, EovaValidateRule>) => {
        Object.values(rules).forEach((r) => {
          r.msg = ''
        })
        if (forceFail) {
          rules.num.msg = '序号不能为空'
          return false
        }
        return true
      },
      showMsg: (rules: Record<string, EovaValidateRule>) => {
        let out = ''
        Object.keys(rules).forEach((k) => {
          if (rules[k].msg) {
            out += rules[k].msg + '<br>'
          }
        })
        return out
      },
      addRules: vi.fn()
    }
  } as unknown as EovaTools
}

/** `ev-*` 组件的替身（真实组件由 legacy 制品在装配期注册） */
const stubs = {
  EvInput: { name: 'EvInput', props: ['modelValue', 'type', 'placeholder', 'style'], template: '<input class="eova-value" />' },
  EvIcon: { name: 'EvIcon', props: ['modelValue', 'type', 'placeholder'], template: '<span class="eova-icon-picker"></span>' },
  EvSelect: { name: 'EvSelect', props: ['modelValue', 'option', 'multiple', 'placeholder'], template: '<div class="eova-select"></div>' },
  EvPopup: { name: 'EvPopup', props: ['placement'], template: '<span class="eova-popup"><slot /><slot name="content" /></span>' }
}

const mountOpts = { global: { components: stubs } }

describe('ButtonAdd.vue（旧 _view/button/add 的行为等价）', () => {
  let me: EovaMe

  beforeEach(() => {
    post.mockReset()
    me = makeMe()
    setEovaMe(me)
    setEovaTools(makeTools())
  })

  it('data 初值逐字对齐旧 app.js（含样例值，不是空占位）', () => {
    const w = mount(ButtonAdd, mountOpts)
    expect((w.vm as never as { data: Record<string, unknown> }).data).toEqual({
      menu_code: 'menu_abc',
      num: 1,
      icon: 'eova-icon-ok',
      name: '测试',
      event: 'test',
      style: '',
      ui: '/demo/test/btn.js',
      auth: '/demo/xxx',
      role: '1'
    })
  })

  it('★ rules 只有 6 项：menu_code / role / style 不在校验里（不得顺手补上）', () => {
    const w = mount(ButtonAdd, mountOpts)
    const rules = (w.vm as never as { rules: Record<string, { label: string; rules: string[] }> })
      .rules
    expect(Object.keys(rules)).toEqual(['num', 'icon', 'name', 'event', 'ui', 'auth'])
    expect(rules.num).toMatchObject({ label: '序号', rules: ['required'] })
    expect(rules.auth).toMatchObject({ label: '权限配置', rules: ['required'] })
    for (const k of ['menu_code', 'role', 'style']) {
      expect(rules[k]).toBeUndefined()
    }
  })

  it('结构：3 个 fieldset（两个 legend 文案 + 一个"授权"）、8 个字段行、图标用 ev-icon 而非 ev-input', () => {
    const w = mount(ButtonAdd, mountOpts)
    const legends = w.findAll('legend').map((l) => l.text())
    expect(legends[0]).toBe('按钮外观')
    expect(legends[1]).toContain('按钮点击事件')
    expect(legends[2]).toBe('授权')
    expect(w.findAll('.eova-form-field')).toHaveLength(8)
    expect(w.findAll('span.eova-icon-picker')).toHaveLength(1)
    // 8 个字段 = 6×ev-input（序号/名称/样式/事件编码/事件逻辑/权限配置）
    //          + 1×ev-icon（图标，**不是** ev-input）
    //          + 1×ev-select（授权角色）
    expect(w.findAll('input.eova-value')).toHaveLength(6)
    expect(w.findAll('.eova-select')).toHaveLength(1)
  })

  it('必填标记：6 个 required（图标/序号/名称/事件编码/事件逻辑/权限配置），样式与授权角色**不是**必填', () => {
    const w = mount(ButtonAdd, mountOpts)
    const req = w.findAll('label.eova-form-label.required').map((l) => l.text())
    expect(req).toEqual(['序号', '图标', '名称', '事件编码', '事件逻辑', '权限配置'])
    const all = w.findAll('label.eova-form-label').map((l) => l.text())
    expect(all).toContain('样式')
    expect(all).toContain('授权角色')
  })

  it('授权角色：ev-select 的 option=eova_role 且 multiple', () => {
    const w = mount(ButtonAdd, mountOpts)
    const sel = w.findComponent(stubs.EvSelect)
    expect(sel.props('option')).toBe('eova_role')
    expect(sel.props('multiple')).toBe(true)
  })

  it('6 个样式快捷按钮：文案与 class 逐条一致，点击把样式名写进 data.style（"默认"写空串）', async () => {
    const w = mount(ButtonAdd, mountOpts)
    const btns = w.findAll('button[type="button"]')
    expect(btns.map((b) => b.text())).toEqual(['默认', '成功', '危险', '警告', '信息', '原始'])
    expect(btns.map((b) => b.classes().join(' '))).toEqual([
      'eova-btn_s30',
      'eova-btn_s30 eova-btn_success',
      'eova-btn_s30 eova-btn_error',
      'eova-btn_s30 eova-btn_warn',
      'eova-btn_s30 eova-btn_info',
      'eova-btn_s30 eova-btn_base'
    ])
    const vm = w.vm as never as { data: Record<string, unknown> }
    await btns[2].trigger('click')
    expect(vm.data.style).toBe('eova-btn_error')
    await btns[0].trigger('click')
    expect(vm.data.style).toBe('')
  })

  it('校验不过：走 layer.wa（不是 msg/no）且【不发请求】', async () => {
    setEovaTools(makeTools(true))
    const w = mount(ButtonAdd, mountOpts)
    await (w.vm as never as { onSubmit: (id?: unknown) => Promise<void> }).onSubmit(5)
    expect(me.layer.wa).toHaveBeenCalledWith('序号不能为空<br>')
    expect(post).not.toHaveBeenCalled()
    expect(me.cross.emit).not.toHaveBeenCalled()
  })

  it('提交：载荷是【整个 data 对象】（含 menu_code 与 role）', async () => {
    post.mockResolvedValue({ data: { state: 'ok' } })
    const w = mount(ButtonAdd, mountOpts)
    const vm = w.vm as never as {
      data: Record<string, unknown>
      onSubmit: (id?: unknown) => Promise<void>
    }
    await vm.onSubmit(1)
    expect(post).toHaveBeenCalledTimes(1)
    expect(post.mock.calls[0][0]).toBe('/button/doAdd')
    expect(post.mock.calls[0][1]).toBe(vm.data)
    expect(Object.keys(post.mock.calls[0][1] as object).sort()).toEqual(
      ['auth', 'event', 'icon', 'menu_code', 'name', 'num', 'role', 'style', 'ui'].sort()
    )
  })

  it('★ 成功：只回传 eova-layer-ok_done（不像 su 页还有 _data 那条）', async () => {
    post.mockResolvedValue({ data: { state: 'ok' } })
    const w = mount(ButtonAdd, mountOpts)
    await (w.vm as never as { onSubmit: (id?: unknown) => Promise<void> }).onSubmit(42)
    const emit = me.cross.emit as unknown as { mock: { calls: unknown[][] } }
    expect(emit.mock.calls).toEqual([['eova-layer-ok_done', 42]])
    expect(me.layer.no).not.toHaveBeenCalled()
  })

  it('业务失败：layer.no(ret.msg)；异常：layer.msg(\'客户端请求异常: \' + message)', async () => {
    post.mockResolvedValue({ data: { state: 'fail', msg: '按钮已存在' } })
    const w1 = mount(ButtonAdd, mountOpts)
    await (w1.vm as never as { onSubmit: (id?: unknown) => Promise<void> }).onSubmit(1)
    expect(me.layer.no).toHaveBeenCalledWith('按钮已存在')

    post.mockRejectedValue(new Error('boom'))
    const w2 = mount(ButtonAdd, mountOpts)
    await (w2.vm as never as { onSubmit: (id?: unknown) => Promise<void> }).onSubmit(1)
    expect(me.layer.msg).toHaveBeenCalledWith('客户端请求异常: boom')
    expect(me.cross.emit).not.toHaveBeenCalled()
  })

  it('onMounted 注册 eova-layer-ok，且回调用事件带回的句柄提交', async () => {
    post.mockResolvedValue({ data: { state: 'ok' } })
    mount(ButtonAdd, mountOpts)
    expect(me.cross.on).toHaveBeenCalledWith('eova-layer-ok', expect.any(Function))
    const handler = (me.cross.on as unknown as { mock: { calls: unknown[][] } }).mock.calls[0][1] as (
      id: unknown
    ) => void
    handler(99)
    await flushPromises()
    expect(me.cross.emit).toHaveBeenCalledWith('eova-layer-ok_done', 99)
  })

  it('★ 第 107 轮修复：挂载前回调扩展钩子 uzoo.vue.mountBefore（旧 app.js:39；漏调是静默失效）', () => {
    const hook = vi.fn()
    ;(globalThis as unknown as Record<string, unknown>)['uzoo'] = {
      page: {},
      vue: { mountBefore: hook },
      app: {}
    }
    try {
      mount(ButtonAdd, mountOpts)
      expect(hook).toHaveBeenCalledTimes(1)
    } finally {
      delete (globalThis as unknown as Record<string, unknown>)['uzoo']
    }
  })

  it('运行时未装配时【响亮失败】', async () => {
    setEovaTools(null)
    const w = mount(ButtonAdd, mountOpts)
    await expect(
      (w.vm as never as { onSubmit: (id?: unknown) => Promise<void> }).onSubmit(1)
    ).rejects.toThrow(/未装配/)
    expect(post).not.toHaveBeenCalled()
  })
})
