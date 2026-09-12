/**
 * 动作页**引导数据接线**的判据（第 299 轮，`docs/DES-004-R2-action-page-bootstrap.md`）。
 *
 * 三张表单页在 S6 时只能"可声明降级"（`uzoo.page.object_id/name/pk` 与 `loginUser.isAdmin`
 * 没有来源）。第 299 轮把来源补上了：端点新增**对象驱动**分支，页面按 `object` 取引导数据。
 *
 * 本文件钉三件事（三页各跑一遍）：
 *  ① ★ **请求体必须显式带 `object`**：动作页的 URL 末段是**元对象编码**，而端点的 `path` 末段口径是
 *     **菜单编码** ⇒ 只带 `path` 会被**误解析成菜单载荷**（后端实测：
 *     `{"path":"/app/add/meta_product"}` ⇒ `menu:{code:'meta_product'}` + btnList）
 *     ⇒ 漏带 `object` 是**静默出错**，必须由判据钉住；
 *  ② ★ **引导数据就绪时**：`uzoo.page.object_id/object_name/object_pk` 被写入（旧栈由渲染期插值给），
 *     且 `loginUser.isAdmin=true` ⇒ 超管面板渲染；
 *  ③ ★ **未就绪时保持可声明降级**（不猜 true、不静默）：面板不渲染 + 点名 `fromServer` 告警。
 *     —— ②③ 两向都要有：只测一侧时，"永远不写"或"永远渲染面板"都会假通过。
 */
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import FormAdd from '../FormAdd.vue'
import FormUpdate from '../FormUpdate.vue'
import FormDetail from '../FormDetail.vue'
import { resetUzooWarning } from '@/compat/eova-ext'
import { setEovaMe, setEovaTools, type EovaMe, type EovaTools } from '@/compat/eova-runtime'
import { setDefaultBootstrapFetcher } from '@/compat/page-bootstrap-fetcher'

vi.mock('axios')

/** 路由参数（三页共用；用例前重置） */
const routeParams: { objectCode?: string } = { objectCode: 'meta_product' }

vi.mock('vue-router', () => ({ useRoute: () => ({ params: routeParams }) }))

const post = axios.post as unknown as ReturnType<typeof vi.fn>

/** 三页的参数化表（组件 / 动作名 / 模式） */
const PAGES = [
  { name: 'FormAdd', component: FormAdd, action: 'add' },
  { name: 'FormUpdate', component: FormUpdate, action: 'update' },
  { name: 'FormDetail', component: FormDetail, action: 'detail' }
] as const

/** 真库 `eova_object` 的 `meta_product` 行（id/name/pk_name 都是**外部事实**，不是编的） */
const OBJECT_META = { id: 1223, name: 'Meta产品', code: 'meta_product', pk_name: 'id' }

/** 引导端点返回的对象驱动载荷（形状由后端判据 `PageBootstrapObjectHttpTest` 钉住） */
const BOOTSTRAP_OK = JSON.stringify({
  state: 'ok',
  object: OBJECT_META,
  loginUser: { isAdmin: true, id: 1, name: '曹雪芹' }
})

/** `me` 替身 */
function makeMe(): EovaMe {
  return {
    layer: { msg: vi.fn(), no: vi.fn(), wa: vi.fn(), open: vi.fn() },
    cross: { on: vi.fn(), off: vi.fn(), emit: vi.fn() },
    urls: { url: vi.fn(() => '/api/form/add/meta_product') }
  } as unknown as EovaMe
}

/** `EovaTools` 替身 */
function makeTools(): EovaTools {
  return {
    isEmpty: (v: unknown) => v == null || String(v).trim() === '',
    validate: { start: vi.fn(() => true), showMsg: vi.fn(() => ''), addRules: vi.fn() },
    dom: { getViewSize: () => ({ width: 1200, height: 800 }) },
    log: vi.fn()
  } as unknown as EovaTools
}

/** `ev-form` / `ev-popup` 替身（真实组件由 legacy 制品在装配期注册） */
const stubs = {
  EvForm: {
    name: 'EvForm',
    props: ['mode', 'name', 'object', 'biz', 'pk', 'modelValue'],
    emits: ['ready'],
    template: '<div class="ev-form"></div>'
  },
  EvPopup: {
    name: 'EvPopup',
    props: ['trigger', 'placement'],
    template: '<span class="eova-popup"><slot /><slot name="content" /></span>'
  }
}

const mountOpts = { global: { components: stubs } }

/**
 * 让引导端点返回给定载荷（其余请求返回 `{state:'ok'}`）
 *
 * @param bootstrapBody 引导端点响应体（字符串或对象）
 */
function stubBootstrap(bootstrapBody: unknown): void {
  post.mockImplementation((url: string) =>
    Promise.resolve(
      String(url).includes('/api/page/bootstrap')
        ? { status: 200, data: bootstrapBody }
        : { data: { state: 'ok' } }
    )
  )
}

/** 取引导端点的请求体 */
function bootstrapBody(): Record<string, unknown> {
  const call = post.mock.calls.find((c) => String(c[0]).includes('/api/page/bootstrap'))
  expect(call, '本页必须调用引导端点（否则 object.*/isAdmin 无来源）').toBeTruthy()
  return call![1] as Record<string, unknown>
}

/** 等引导数据落定 */
async function settle(): Promise<void> {
  await flushPromises()
  await flushPromises()
}

describe('动作页引导数据接线（DES-004-R2）', () => {
  beforeEach(() => {
    post.mockReset()
    routeParams.objectCode = 'meta_product'
    window.history.replaceState({}, '', '/app/add/meta_product')
    setEovaMe(makeMe())
    setEovaTools(makeTools())
    setDefaultBootstrapFetcher(null)
    resetUzooWarning()
    delete (globalThis as unknown as Record<string, unknown>)['uzoo']
  })

  for (const { name, component } of PAGES) {
    it(`① ★ ${name}：引导请求体**必须显式带 object**（只带 path 会被误解析成菜单载荷）`, async () => {
      stubBootstrap(BOOTSTRAP_OK)
      mount(component as never, mountOpts)
      await settle()

      const body = bootstrapBody()
      expect(body['object'], `${name} 必须显式带 object（= 路由参数，即旧栈 get(0)）`).toBe(
        'meta_product'
      )
      // 反向：元对象编码**不得**被塞进 menu（那会命中菜单驱动分支，拿到 btnList/menu 载荷）
      expect(body['menu']).toBeUndefined()
    })

    it(`② ★ ${name}：引导数据就绪 ⇒ 补写 object_id/name/pk，且 isAdmin=true 时超管面板渲染`, async () => {
      stubBootstrap(BOOTSTRAP_OK)
      const w = mount(component as never, mountOpts)
      await settle()

      const page = (globalThis as unknown as Record<string, unknown>)['uzoo'] as {
        page: Record<string, unknown>
      }
      // ★ 旧栈由 `_page/form.html` 的 `#(object.id/name/pk)` 渲染期插值 —— 现在由引导数据补上
      expect(page.page['object_id']).toBe(1223)
      expect(page.page['object_name']).toBe('Meta产品')
      expect(page.page['object_pk']).toBe('id')
      expect(page.page['object_code']).toBe('meta_product')
      // isAdmin=true ⇒ 面板出现（它是 `#if(loginUser.isAdmin)` 的等价物）
      expect(w.find('.eova-admins').exists(), `${name} 超管会话下应渲染超管面板`).toBe(true)
    })

    it(`③ ★ ${name}（反向）：引导数据未就绪 ⇒ 保持可声明降级（不猜 true、不静默）`, async () => {
      const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
      try {
        // 端点返回 state!=ok ⇒ `loadPageBootstrap` 按失败处理（fromServer=false）
        stubBootstrap({ state: 'no', msg: '未就绪' })
        const w = mount(component as never, mountOpts)
        await settle()

        expect(w.find('.eova-admins').exists(), '未就绪时不得渲染超管面板').toBe(false)
        const page = (globalThis as unknown as Record<string, unknown>)['uzoo'] as {
          page: Record<string, unknown>
        }
        expect('object_id' in page.page, '未就绪时不得编造 object_id').toBe(false)
        const texts = warn.mock.calls.map((c) => String(c[0]))
        expect(
          texts.some((t) => t.includes('object_id/object_name/object_pk') && t.includes('fromServer=')),
          '未就绪必须响亮告警并点名 fromServer'
        ).toBe(true)
      } finally {
        warn.mockRestore()
      }
    })
  }
})
