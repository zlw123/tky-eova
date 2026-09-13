/**
 * **登录页可见面判据（r311）**：主题类 / 页脚 / 验证码开关 / 应用名 —— 与旧栈逐项对照。
 *
 * 为什么单独一组：这三处差异**所有既有判据都是绿的**——
 * 组件判据钉的是"提交契约与载荷字段名"，视觉对照判据（S5/S6/列表/动作）都**不含登录页**。
 * 实测（CDP 探针，两端同页对照）：
 *
 * | 事实 | 旧栈 | 修复前新栈 | 修复后新栈 |
 * |---|---|---|---|
 * | `body.className` | `eova-theme_default` | `""`（主题 JS 的 DOMContentLoaded 监听永不触发） | `eova-theme_default` |
 * | 主题 CSS `_eova/theme/eova.theme.default.css` | 已加载 | **一个字节都没请求** | 已加载 |
 * | `.eova-footer` | 40px、`© 2015-2026 EOVA.CN` | **整块不存在** | 同旧栈 |
 * | 验证码 | 不显示（`isCaptcha=false`） | **恒显示**（硬编码默认 true） | 不显示 |
 * | `<title>` / h2 | `EOVA低代码开发平台` | `EovaMeta` / 账号密码登录 | 同旧栈 |
 */
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import Login from '../Login.vue'

vi.mock('axios')

/** 引导端点返回的文本（每个用例自行设定；null = 拿不到配置） */
const fetchText = vi.fn()
vi.mock('@/compat/page-bootstrap-fetcher', () => ({
  createBootstrapFetcher: () => () => fetchText()
}))

const post = axios.post as unknown as ReturnType<typeof vi.fn>

/** 旧模板 `#(app_name??'EOVA低代码开发平台')` 的兜底值 */
const DEFAULT_APP_NAME = 'EOVA低代码开发平台'

/**
 * 挂载登录页
 *
 * @param payload 引导端点返回的配置（null = 拿不到）
 * @returns 已 flush 的 wrapper
 */
async function mountLogin(payload: unknown = null) {
  fetchText.mockResolvedValue(payload === null ? null : JSON.stringify(payload))
  const wrapper = mount(Login)
  await flushPromises()
  return wrapper
}

describe('Login.vue 可见面（r311：主题类/页脚/验证码/应用名 与旧栈等价）', () => {
  beforeEach(() => {
    post.mockReset()
    fetchText.mockReset()
    document.body.className = ''
    document.title = 'EovaMeta'
  })

  it('页脚：`.eova-footer` 必须在场，文案取自配置的 copyright（旧 login.html:43）', async () => {
    const w = await mountLogin({ state: 'ok', copyright: '© 2015-2026 EOVA.CN', isCaptcha: false })
    const f = w.find('.eova-footer')
    expect(f.exists()).toBe(true)
    expect(f.text()).toBe('© 2015-2026 EOVA.CN')
  })

  it('页脚在拿不到配置时也在场（旧模板渲染空串，不是不渲染）', async () => {
    const w = await mountLogin(null)
    expect(w.find('.eova-footer').exists()).toBe(true)
    expect(w.find('.eova-footer').text()).toBe('')
  })

  it('验证码：`isCaptcha=false` ⇒ 不显示（旧模板 `v-if="conf.is_captcha === \'true\'"`）', async () => {
    const w = await mountLogin({ state: 'ok', isCaptcha: false })
    expect(w.find('.eova-login_cap').exists()).toBe(false)
  })

  it('验证码：`isCaptcha=true` ⇒ 显示，且图片指向 `/user/captcha`（带时间戳防缓存）', async () => {
    const w = await mountLogin({ state: 'ok', isCaptcha: true })
    expect(w.find('.eova-login_cap').exists()).toBe(true)
    expect(w.find('.eova-login_cap img').attributes('src')).toMatch(/^\/user\/captcha\?_=/)
  })

  it('★ 默认值：拿不到配置时**不显示验证码**（旧模板 `#(isCaptcha??false)`；修复前硬编码 true ⇒ 恒显示）', async () => {
    const w = await mountLogin(null)
    expect(w.find('.eova-login_cap').exists()).toBe(false)
  })

  it('应用名：配置为 null ⇒ 走旧模板兜底 `EOVA低代码开发平台`；h2 与 `<title>` 同源', async () => {
    const w = await mountLogin({ state: 'ok', app_name: null })
    expect(w.find('.eova-login h2').text()).toBe(DEFAULT_APP_NAME)
    expect(document.title).toBe(DEFAULT_APP_NAME)
  })

  it('应用名：配置了 `app.name` ⇒ h2 与 `<title>` 都用它', async () => {
    const w = await mountLogin({ state: 'ok', app_name: '拿哥的平台' })
    expect(w.find('.eova-login h2').text()).toBe('拿哥的平台')
    expect(document.title).toBe('拿哥的平台')
  })

  it('开发免输：配置里的 `dev.login_id`/`dev.login_pwd` 回填输入框（旧 login.html:51-52 同口径）', async () => {
    const w = await mountLogin({ state: 'ok', login_id: 'eova', login_pwd: '000000' })
    const inputs = w.findAll('input')
    expect((inputs[0].element as HTMLInputElement).value).toBe('eova')
    expect((inputs[1].element as HTMLInputElement).value).toBe('000000')
  })

  it('反空断言：配置非 ok 时不套用（避免把失败响应当配置）', async () => {
    const w = await mountLogin({ state: 'fail', copyright: '不该出现', isCaptcha: true })
    expect(w.find('.eova-footer').text()).toBe('')
    expect(w.find('.eova-login_cap').exists()).toBe(false)
  })
})
