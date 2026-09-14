/**
 * **切片 B 行为判据**：`meta_hotel` / `table_meta_hotel` 自定义脚本的**省→市→区联动**
 * （r327；契约来源 = 冻结资产 `src/legacy/hotel/app.js`）。
 *
 * ## 钉的是什么（逐条对齐旧文件；旧行号见 `metaHotelApp.ts` 文件头）
 *
 * 1. `onReady(fields)` 先按 `uzoo.page.form` 分四支弹通知（`create`/`update`/`read`/`query`，文案逐字）；
 * 2. 取 `fields.get('city')` / `('region')` 并**初始禁用**这两个字段；
 * 3. `watch(uzoo.app.data.province)`：非空 ⇒ `me.layer.msg('省选择: X')` + 放开市 +
 *    `setOption('area;2,X')` + **重新禁用区**；
 * 4. `watch(uzoo.app.data.city)`：非空 ⇒ `me.layer.msg('市选择: X')` + 放开区 + `setOption('area;3,X')`；
 * 5. ★ **既有缺陷原样保留**：`fields` 里没有 `city` 时**抛 TypeError**（旧实现没有判空）——
 *    反向用例钉住"不许顺手加判空把旧行为改掉"。
 *
 * ★ 空值判定走**制品的 `x.isEmpty`**（本判据传入 spy ⇒ 断言调用发生，防"本地重写一套判定"）。
 */
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, nextTick, reactive } from 'vue'
import { beforeEach, describe, expect, it, vi, type Mock } from 'vitest'
import { setUzooApp, setUzooPage } from '@/compat/eova-ext'
import { setEovaMe, setEovaTools, type EovaMe, type EovaTools } from '@/compat/eova-runtime'
import { metaHotelAppHooks } from '../metaHotelApp'

/** `me` 替身（只声明本脚本用到的面） */
function makeMe() {
  return {
    layer: { msg: vi.fn(), notify: vi.fn(), no: vi.fn(), wa: vi.fn(), open: vi.fn() },
    cross: { on: vi.fn(), off: vi.fn(), emit: vi.fn() },
    urls: { url: vi.fn(() => '/x') },
    conf: { putAll: vi.fn(), get: vi.fn(), getAll: () => ({}) }
  }
}

/** `EovaTools.x` 替身 —— `isEmpty` 用 spy（判据要断言"走制品判定"而不是本地重写） */
function makeTools(isEmpty: (v: unknown) => boolean) {
  return {
    isEmpty,
    validate: { start: vi.fn(() => true), showMsg: vi.fn(() => ''), addRules: vi.fn() },
    dom: { getViewSize: () => ({ width: 1200, height: 800 }) },
    log: vi.fn()
  }
}

/** 字段替身（旧 `fields.get('city')` 的返回值） */
function makeField() {
  return { setDisabled: vi.fn(), setOption: vi.fn() }
}

let me: ReturnType<typeof makeMe>
let isEmptySpy: Mock<[v: unknown], boolean>

beforeEach(() => {
  me = makeMe()
  isEmptySpy = vi.fn((v: unknown) => v == null || String(v).trim() === '')
  setEovaMe(me as unknown as EovaMe)
  setEovaTools(makeTools(isEmptySpy) as unknown as EovaTools)
  delete (globalThis as unknown as Record<string, unknown>)['uzoo']
})

describe('metaHotelApp（旧 legacy/hotel/app.js）', () => {
  it('① 初始：市/区都禁用；通知文案按 uzoo.page.form 逐字（update）', () => {
    setUzooPage('form', 'update')
    const city = makeField()
    const region = makeField()
    setUzooApp({ data: reactive({ province: '', city: '' }) })

    metaHotelAppHooks.onReady({ get: (n: string) => (n === 'city' ? city : region) })

    expect(city.setDisabled).toHaveBeenCalledWith(true)
    expect(region.setDisabled).toHaveBeenCalledWith(true)
    expect(me.layer.notify).toHaveBeenCalledWith('自定义逻辑', '/hotel/app.js form:update', 'ok')
  })

  it('② ★ 省→市：非空省 ⇒ 提示 + 放开市 + 选项换成 `area;2,<省>` + 区重新禁用', async () => {
    setUzooPage('form', 'update')
    const city = makeField()
    const region = makeField()
    const data = reactive<Record<string, unknown>>({ province: '', city: '' })
    setUzooApp({ data })

    metaHotelAppHooks.onReady({ get: (n: string) => (n === 'city' ? city : region) })
    city.setDisabled.mockClear()
    region.setDisabled.mockClear()

    data.province = '北京'
    await nextTick()

    expect(me.layer.msg).toHaveBeenCalledWith('省选择: 北京')
    expect(city.setDisabled).toHaveBeenCalledWith(false)
    expect(city.setOption).toHaveBeenCalledWith('area;2,北京')
    expect(region.setDisabled).toHaveBeenCalledWith(true)
  })

  it('③ ★ 市→区：非空市 ⇒ 提示 + 放开区 + 选项换成 `area;3,<市>`', async () => {
    setUzooPage('form', 'update')
    const city = makeField()
    const region = makeField()
    const data = reactive<Record<string, unknown>>({ province: '', city: '' })
    setUzooApp({ data })

    metaHotelAppHooks.onReady({ get: (n: string) => (n === 'city' ? city : region) })
    region.setDisabled.mockClear()

    data.city = '安庆'
    await nextTick()

    expect(me.layer.msg).toHaveBeenCalledWith('市选择: 安庆')
    expect(region.setDisabled).toHaveBeenCalledWith(false)
    expect(region.setOption).toHaveBeenCalledWith('area;3,安庆')
  })

  it('④ 空值不触发（走制品 `x.isEmpty`；反向：空串/undefined 都不放开）', async () => {
    setUzooPage('form', 'update')
    const city = makeField()
    const region = makeField()
    const data = reactive<Record<string, unknown>>({ province: undefined, city: '' })
    setUzooApp({ data })

    metaHotelAppHooks.onReady({ get: (n: string) => (n === 'city' ? city : region) })
    city.setDisabled.mockClear()
    city.setOption.mockClear()

    data.province = ''
    await nextTick()
    data.province = undefined
    await nextTick()

    expect(city.setDisabled).not.toHaveBeenCalledWith(false)
    expect(city.setOption).not.toHaveBeenCalled()
    expect(me.layer.msg).not.toHaveBeenCalled()
    // ★ 判定必须走制品（而不是本地重写一套）
    expect(isEmptySpy).toHaveBeenCalled()
  })

  it('⑤ ★ 既有缺陷原样保留：fields 里没有 city ⇒ 抛 TypeError（旧实现无判空）', () => {
    setUzooPage('form', 'update')
    setUzooApp({ data: reactive({}) })
    expect(() => metaHotelAppHooks.onReady({ get: () => undefined })).toThrow(TypeError)
  })

  it('⑥ 四条通知分支逐字（create / update / read / query）', () => {
    const city = makeField()
    const region = makeField()
    setUzooApp({ data: reactive({}) })
    const cases: Array<[string, string]> = [
      ['create', '/hotel/app.js form:create'],
      ['update', '/hotel/app.js form:update'],
      ['read', '/hotel/app.js form:read'],
      ['query', '/hotel/app.js form:query']
    ]
    for (const [form, text] of cases) {
      me.layer.notify.mockClear()
      setUzooPage('form', form)
      metaHotelAppHooks.onReady({ get: (n: string) => (n === 'city' ? city : region) })
      expect(me.layer.notify).toHaveBeenCalledWith('自定义逻辑', text, 'ok')
    }
  })

  it('⑦ 未知 form 值 ⇒ 不弹任何通知（旧实现只有四条分支，没有 else）', () => {
    setUzooPage('form', 'unknown-mode')
    const city = makeField()
    const region = makeField()
    setUzooApp({ data: reactive({}) })
    metaHotelAppHooks.onReady({ get: (n: string) => (n === 'city' ? city : region) })
    expect(me.layer.notify).not.toHaveBeenCalled()
  })

  it('⑧ `setup()` 返回空对象（旧 `return {}`）且注册一次 onMounted 日志', async () => {
    const spy = vi.spyOn(console, 'log').mockImplementation(() => undefined)
    const Comp = defineComponent({
      setup() {
        return metaHotelAppHooks.setup!()
      },
      template: '<div />'
    })
    mount(Comp)
    await flushPromises()
    expect(spy.mock.calls.some((c) => String(c[0]).includes('hotel app setup onMounted'))).toBe(true)
    spy.mockRestore()
  })
})
