/**
 * 修改密码页的行为等价判据（旧 `_view/user/password/app.js`）。
 *
 * 钉的契约：
 *  ① 载荷就是三个字段（**不含**弹层句柄 id），提交地址 `/user/doPassword`；
 *  ② 校验不过 ⇒ 走 `me.layer.wa`（不是 `msg`/`no`），且**不发请求**；`showMsg` 为空则不弹提示；
 *  ③ 成功 ⇒ `me.cross.emit('eova-layer-ok_done', id)`（宿主据此关层）；失败 ⇒ `me.layer.no(ret.msg)`；
 *  ④ 异常 ⇒ `me.layer.msg('客户端请求异常: ' + message)`；
 *  ⑤ `onMounted` 注册 `eova-layer-ok`，其回调以事件带回的句柄提交。
 *
 * 说明：`x.validate`/`me` 都是 legacy 制品提供的运行时（第 102 轮取证），
 * 判据用**替身**注入以观测"本页有没有按契约调用"。替身按制品语义写：
 * `start` 原地写回 `rule.msg`，`showMsg` 以 `<br>` 连接非空 msg。
 * ⇒ 本判据证的是**接线**；`x.validate` 自身的算法等价由制品逐字节复核 + 浏览器实跑兜底（当前 not executed）。
 */
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import Password from '../Password.vue'
import {
  setEovaMe,
  setEovaTools,
  type EovaMe,
  type EovaTools,
  type EovaValidateRule
} from '@/compat/eova-runtime'

vi.mock('axios')
const post = axios.post as unknown as ReturnType<typeof vi.fn>

/** 造 `me` 替身 */
function makeMe(): EovaMe {
  return {
    layer: {
      msg: vi.fn(),
      no: vi.fn(),
      wa: vi.fn(),
      open: vi.fn()
    },
    cross: { on: vi.fn(), off: vi.fn(), emit: vi.fn() }
  } as unknown as EovaMe
}

/**
 * 造 `EovaTools` 替身。
 *
 * `start` 按制品语义：逐字段对 required 判空（空串即空），命中就写回 `msg` 并 break；
 * `showMsg` 按制品语义：以 `<br>` 连接所有非空 msg。
 *
 * @param msgFor 指定字段的"应报文案"（用于精确控制结果）
 */
function makeTools(msgFor: (field: string, value: unknown) => string | null): EovaTools {
  return {
    isEmpty: (v: unknown) => v == null || String(v).trim() === '',
    validate: {
      start: (rules: Record<string, EovaValidateRule>, data: Record<string, unknown>) => {
        Object.entries(rules).forEach(([field, rule]) => {
          rule.msg = ''
          for (const raw of rule.rules) {
            const r = raw.trim()
            if (r === 'required') {
              if (v_isEmpty(data[field])) {
                rule.msg = `${rule.label}不能为空`
                break
              }
            }
          }
          const override = msgFor(field, data[field])
          if (override !== null) {
            rule.msg = override
          }
        })
        return Object.values(rules).every((r) => r.msg === '')
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

/** 与制品 `isEmpty` 等价的判空（仅本判据内使用） */
function v_isEmpty(v: unknown): boolean {
  return v == null || String(v).trim() === ''
}

/** 注册一个 `EvInput` 替身组件（真实组件由 legacy 制品在装配期注册） */
const EvInputStub = {
  name: 'EvInput',
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<input class="eova-value" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
}

const mountOpts = { global: { components: { EvInput: EvInputStub } } }

describe('Password.vue（旧 _view/user/password/app.js 的行为等价）', () => {
  let me: EovaMe

  beforeEach(() => {
    post.mockReset()
    me = makeMe()
    setEovaMe(me)
    setEovaTools(makeTools(() => null))
  })

  it('结构与字段：三个 .eova-form-field + required 标签 + ev-input，字段名与顺序为 oldPwd/newPwd/confirm', () => {
    const w = mount(Password, mountOpts)
    const labels = w.findAll('label.eova-form-label.required').map((l) => l.text())
    expect(labels).toEqual(['旧密码', '新密码', '确认密码'])
    expect(w.findAll('.eova-form-field')).toHaveLength(3)
    expect(w.findAll('input.eova-value')).toHaveLength(3)
    expect(Object.keys((w.vm as never as { data: object }).data)).toEqual([
      'oldPwd',
      'newPwd',
      'confirm'
    ])
  })

  it('rules：三项都只有 required（旧实现没有"两次一致"校验，不得顺手补）', () => {
    const w = mount(Password, mountOpts)
    const rules = (w.vm as never as { rules: Record<string, { label: string; rules: string[] }> })
      .rules
    expect(Object.keys(rules)).toEqual(['oldPwd', 'newPwd', 'confirm'])
    expect(rules.oldPwd).toMatchObject({ label: '旧密码', rules: ['required'] })
    expect(rules.newPwd).toMatchObject({ label: '新密码', rules: ['required'] })
    expect(rules.confirm).toMatchObject({ label: '确认密码', rules: ['required'] })
    // 关键：没有 match[newPwd]/match[confirm] 之类的补充规则
    for (const r of Object.values(rules)) {
      expect(r.rules).toEqual(['required'])
    }
  })

  it('校验不过：走 me.layer.wa（不是 msg/no），且【不发请求】；文案是【所有】失败字段以 <br> 连接', async () => {
    const w = mount(Password, mountOpts)
    const vm = w.vm as never as { onSubmit: (id?: unknown) => Promise<void> }
    await vm.onSubmit(7)
    // showMsg 汇总所有非空 msg（不是只报第一条）——三字段全空 ⇒ 三条都在
    expect((me.layer.wa as ReturnType<typeof vi.fn>)).toHaveBeenCalledWith(
      '旧密码不能为空<br>新密码不能为空<br>确认密码不能为空<br>'
    )
    expect(me.layer.msg).not.toHaveBeenCalled()
    expect(me.layer.no).not.toHaveBeenCalled()
    expect(post).not.toHaveBeenCalled()
    // 校验不过时也不得回传宿主关闭弹层
    expect(me.cross.emit).not.toHaveBeenCalled()
  })

  it('校验不过：只填旧密码时文案只剩后两条（证明是"逐字段汇总"而不是"固定三条"）', async () => {
    const w = mount(Password, mountOpts)
    const vm = w.vm as never as {
      data: Record<string, string>
      onSubmit: (id?: unknown) => Promise<void>
    }
    vm.data.oldPwd = 'a'
    await vm.onSubmit(7)
    expect((me.layer.wa as ReturnType<typeof vi.fn>)).toHaveBeenCalledWith(
      '新密码不能为空<br>确认密码不能为空<br>'
    )
  })

  it('校验不过但 showMsg 为空：不弹任何提示（旧实现 `if (txt !== \'\')`）', async () => {
    // 让 start 返回 false 但不写 msg（模拟"有错却无文案"这一分支）
    setEovaTools({
      isEmpty: () => false,
      validate: {
        start: () => false,
        showMsg: () => '',
        addRules: vi.fn()
      }
    } as unknown as EovaTools)
    const w = mount(Password, mountOpts)
    await (w.vm as never as { onSubmit: (id?: unknown) => Promise<void> }).onSubmit(1)
    expect(me.layer.wa).not.toHaveBeenCalled()
    expect(me.layer.msg).not.toHaveBeenCalled()
    expect(post).not.toHaveBeenCalled()
  })

  it('校验通过：POST /user/doPassword，载荷是三个字段且【不含 id】', async () => {
    post.mockResolvedValue({ data: { state: 'ok' } })
    const w = mount(Password, mountOpts)
    const vm = w.vm as never as {
      data: Record<string, string>
      onSubmit: (id?: unknown) => Promise<void>
    }
    vm.data.oldPwd = 'a'
    vm.data.newPwd = 'b'
    vm.data.confirm = 'b'
    await vm.onSubmit(7)
    expect(post).toHaveBeenCalledTimes(1)
    expect(post.mock.calls[0][0]).toBe('/user/doPassword')
    expect(post.mock.calls[0][1]).toEqual({ oldPwd: 'a', newPwd: 'b', confirm: 'b' })
    expect(post.mock.calls[0][1]).not.toHaveProperty('id')
  })

  it('成功：me.cross.emit(\'eova-layer-ok_done\', id) —— 宿主据此关层并执行 done', async () => {
    post.mockResolvedValue({ data: { state: 'ok' } })
    const w = mount(Password, mountOpts)
    const vm = w.vm as never as {
      data: Record<string, string>
      onSubmit: (id?: unknown) => Promise<void>
    }
    vm.data.oldPwd = 'a'
    vm.data.newPwd = 'b'
    vm.data.confirm = 'b'
    await vm.onSubmit(42)
    expect(me.cross.emit).toHaveBeenCalledWith('eova-layer-ok_done', 42)
    expect(me.layer.no).not.toHaveBeenCalled()
  })

  it('业务失败：me.layer.no(ret.msg)（不是 msg），且不回传宿主', async () => {
    post.mockResolvedValue({ data: { state: 'fail', msg: '旧密码错误' } })
    const w = mount(Password, mountOpts)
    const vm = w.vm as never as {
      data: Record<string, string>
      onSubmit: (id?: unknown) => Promise<void>
    }
    vm.data.oldPwd = 'a'
    vm.data.newPwd = 'b'
    vm.data.confirm = 'b'
    await vm.onSubmit(1)
    expect(me.layer.no).toHaveBeenCalledWith('旧密码错误')
    expect(me.cross.emit).not.toHaveBeenCalled()
  })

  it('请求异常：me.layer.msg(\'客户端请求异常: \' + error.message)', async () => {
    post.mockRejectedValue(new Error('boom'))
    const w = mount(Password, mountOpts)
    const vm = w.vm as never as {
      data: Record<string, string>
      onSubmit: (id?: unknown) => Promise<void>
    }
    vm.data.oldPwd = 'a'
    vm.data.newPwd = 'b'
    vm.data.confirm = 'b'
    await vm.onSubmit(1)
    expect(me.layer.msg).toHaveBeenCalledWith('客户端请求异常: boom')
    expect(me.cross.emit).not.toHaveBeenCalled()
  })

  it('onMounted 注册 eova-layer-ok，且回调用事件带回的句柄提交', async () => {
    post.mockResolvedValue({ data: { state: 'ok' } })
    const w = mount(Password, mountOpts)
    expect(me.cross.on).toHaveBeenCalledWith('eova-layer-ok', expect.any(Function))
    const handler = (me.cross.on as unknown as { mock: { calls: unknown[][] } }).mock.calls[0][1] as (
      id: unknown
    ) => void
    const vm = w.vm as never as { data: Record<string, string> }
    vm.data.oldPwd = 'a'
    vm.data.newPwd = 'b'
    vm.data.confirm = 'b'
    handler(99)
    await flushPromises()
    expect(me.cross.emit).toHaveBeenCalledWith('eova-layer-ok_done', 99)
  })

  it('运行时未装配时【响亮失败】（不得静默跳过校验或跳过提交）', async () => {
    setEovaTools(null)
    const w = mount(Password, mountOpts)
    await expect(
      (w.vm as never as { onSubmit: (id?: unknown) => Promise<void> }).onSubmit(1)
    ).rejects.toThrow(/未装配/)
    expect(post).not.toHaveBeenCalled()
  })
})
