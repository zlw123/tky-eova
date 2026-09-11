/**
 * 登录页（阶段 2 首个改造页）的**行为等价判据**。
 *
 * 断言的是**契约**，不是 DOM 长相（工程 README 契约纪律 ③）：
 *  ① 提交去向与载荷字段名（后端按 login_id/login_pwd/captcha 取参）
 *  ② 成功判定 `state === 'ok'` ⇒ 跳首页 `/`
 *  ③ 业务失败 ⇒ 显示 `ret.msg`
 *  ④ 网络异常 ⇒ 固定文案 `客户端请求异常`
 *  ⑤ 验证码按 conf 开关显示，图片指向 `/user/captcha`
 */
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import axios from 'axios'
import Login from '../Login.vue'

vi.mock('axios')

const post = axios.post as unknown as ReturnType<typeof vi.fn>

/** 挂载页面（可注入 conf） */
function mountLogin(conf: Record<string, unknown> = {}) {
  const wrapper = mount(Login)
  Object.assign((wrapper.vm as any).conf, { app_name: 'EOVA', is_captcha: true, copyright: '', ...conf })
  return wrapper
}

describe('Login.vue（旧 _view/index/login.html + login.js 的行为等价）', () => {
  beforeEach(() => {
    post.mockReset()
    // jsdom 下 location.href 赋值会触发"未实现导航"，故替换为可写替身
    Object.defineProperty(window, 'location', { value: { href: '' }, writable: true })
  })

  it('提交契约：POST /user/doLogin，载荷字段名与旧实现一致', async () => {
    post.mockResolvedValue({ data: { state: 'ok' } })
    const w = mountLogin()
    const vm = w.vm as any
    vm.data.login_id = 'admin'
    vm.data.login_pwd = 'pwd'
    vm.data.captcha = 'ab12'
    await vm.onSubmit()
    expect(post).toHaveBeenCalledTimes(1)
    expect(post.mock.calls[0][0]).toBe('/user/doLogin')
    expect(post.mock.calls[0][1]).toEqual({ login_id: 'admin', login_pwd: 'pwd', captcha: 'ab12' })
  })

  it('成功：state === ok ⇒ 跳首页 /', async () => {
    post.mockResolvedValue({ data: { state: 'ok' } })
    const vm = mountLogin().vm as any
    await vm.onSubmit()
    expect(window.location.href).toBe('/')
  })

  it('业务失败：显示后端 msg，且不跳转', async () => {
    post.mockResolvedValue({ data: { state: 'fail', msg: '验证码错误，请重新输入！' } })
    const w = mountLogin()
    const vm = w.vm as any
    await vm.onSubmit()
    expect(vm.data.msg).toBe('验证码错误，请重新输入！')
    expect(window.location.href).toBe('')
    await w.vm.$nextTick()
    expect(w.text()).toContain('验证码错误，请重新输入！')
  })

  it('网络异常：固定文案 客户端请求异常（旧 login.js 的 catch 分支）', async () => {
    post.mockRejectedValue(new Error('boom'))
    const vm = mountLogin().vm as any
    await vm.onSubmit()
    expect(vm.data.msg).toBe('客户端请求异常')
  })

  it('验证码：开关关闭时不渲染，开启时渲染且图片指向 /user/captcha', async () => {
    const off = mountLogin({ is_captcha: false })
    await off.vm.$nextTick()
    expect(off.find('input[placeholder="验证码"]').exists()).toBe(false)

    const on = mountLogin({ is_captcha: true })
    await on.vm.$nextTick()
    const input = on.find('input[placeholder="验证码"]')
    expect(input.exists()).toBe(true)
    const src = on.find('img').attributes('src')!
    expect(src.startsWith('/user/captcha')).toBe(true)
    // 点击刷新：加时间戳参数（旧实现是 this.src='/user/captcha?'+Math.random()）
    await on.find('img').trigger('click')
    await on.vm.$nextTick()
    expect(on.find('img').attributes('src')).not.toBe(src)
  })
})
