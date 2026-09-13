/**
 * legacy 运行时装配的判据（第 102 轮）
 *
 * 钉四件事（都是"错了会静默坏掉"的类型）：
 *  ① **顺序**：三个 UMD 制品必须按 `eova-tools → layui → eovaui` 加载
 *     （后一个在前一个执行时就要读全局）；
 *  ② **全局先于加载**：`Vue`/`axios` 必须在**第一个**脚本加载前就已注入，
 *     否则制品执行时报 `requires` 缺失，拿到的是半成品；
 *  ③ **幂等**：已装配不得重复插入 script（重复加载会重新执行 UMD，覆盖 `EovaUI` 实例）；
 *  ④ **响亮失败**：加载出错、或加载后必需全局仍缺失，一律抛错，不静默继续。
 */
import { describe, expect, it } from 'vitest'
import {
  LEGACY_RUNTIME_SCRIPTS,
  MODULE_SCRIPT_PREFIX,
  SCRIPT_EXPECTED_GLOBAL,
  isLegacyRuntimeLoaded,
  loadLegacyRuntime
} from '../legacy-runtime'

/** 造一个"会真的挂全局"的假加载器 */
function fakeLoader(target: Record<string, unknown>) {
  const loads: string[] = []
  const globalAtLoad: Array<Record<string, unknown>> = []
  return {
    loads,
    globalAtLoad,
    loadScript: async (url: string) => {
      loads.push(url)
      // 记录"加载这一刻"的全局快照 —— 用于证明 Vue/axios 是先注入的
      globalAtLoad.push({ Vue: target['Vue'], axios: target['axios'] })
      const name = SCRIPT_EXPECTED_GLOBAL[url]
      if (name) {
        target[name] = { providedBy: url }
        // `EovaUI` 落地时同时给出 `me.render`（真实制品如此）；渲染器注册表由
        // 扩展资产 `/_eova/assets/eova.ui.ext.js` 填充 —— 见下方"渲染器闸门"用例。
        if (name === 'EovaUI') {
          const registry = new Map<string, unknown>()
          registry.set('eova-table-cell', { providedBy: '/_eova/assets/eova.ui.ext.js' })
          target['EovaUI'] = {
            providedBy: url,
            me: { render: { get: (key: string) => registry.get(key) ?? null } }
          }
        }
      }
    }
  }
}

const HOST_GLOBALS = { Vue: { tag: 'app-vue' }, axios: { tag: 'app-axios' } }

describe('legacy-runtime · 单元格渲染器闸门（r304）', () => {
  it('渲染器缺失时**响亮抛错**，不静默装配（否则列表页"有数据但格子全空"）', async () => {
    const target: Record<string, unknown> = {}
    const f = fakeLoader(target)
    // 让 EovaUI 落地但**不带**渲染器（模拟 `/_eova/assets/eova.ui.ext.js` 未执行/被注释掉）
    await expect(loadLegacyRuntime({
      loadScript: async (url: string) => {
        f.loads.push(url)
        const name = SCRIPT_EXPECTED_GLOBAL[url]
        if (name === 'EovaUI') {
          target['EovaUI'] = { me: { render: { get: () => null } } }
        } else if (name) {
          target[name] = { providedBy: url }
        }
      },
      globals: HOST_GLOBALS,
      target
    })).rejects.toThrow(/单元格渲染器/)
    expect(f.loads).toContain('/_eova/assets/eova.ui.ext.js')
  })

  it('渲染器已在位时装配通过（正例，防"闸门过严"）', async () => {
    const target: Record<string, unknown> = {}
    const f = fakeLoader(target)
    await loadLegacyRuntime({ loadScript: f.loadScript, globals: HOST_GLOBALS, target })
    expect(isLegacyRuntimeLoaded(target)).toBe(true)
  })

  it('`_eova/**` 必须按 **ES 模块**加载（经典脚本会因顶层 `const me` 冲突而整份失效）', () => {
    // 判据口径：清单里每个工程级扩展资产都在 MODULE_SCRIPT_PREFIX 下 —— 装载器据此设 type="module"
    expect(LEGACY_RUNTIME_SCRIPTS.filter((u) => u.includes('eova.ui.ext.js'))).toEqual([
      '/_eova/assets/eova.ui.ext.js'
    ])
    expect('/_eova/assets/eova.ui.ext.js'.startsWith(MODULE_SCRIPT_PREFIX)).toBe(true)
    expect('/eova/_view/template/eova.template.js'.startsWith(MODULE_SCRIPT_PREFIX)).toBe(false)
  })
})

describe('legacy-runtime · 装配顺序', () => {
  it('按 eova-tools → layui → eovaui → 页面脚本 的顺序加载（清单本身即契约；r249 起含 2 个页面脚本）', async () => {
    const target: Record<string, unknown> = {}
    const f = fakeLoader(target)
    await loadLegacyRuntime({ loadScript: f.loadScript, globals: HOST_GLOBALS, target })
    expect(f.loads).toEqual([...LEGACY_RUNTIME_SCRIPTS])
    expect(f.loads).toEqual([
      '/eova/lib/eova/lib/eova-tools.umd.js',
      '/eova/lib/eova/lib/layui.umd.js',
      '/eova/lib/eova/eovaui.js',
      // ★ r249（真浏览器实测）：页面脚本必须在 vendor 之后 —— 它们执行时读 EovaTools/EovaUI，
      //   排在 index.html 的静态 <script> 里会抢先执行并报 ReferenceError。
      '/eova/ui/meta/eova.meta.js',
      '/eova/_view/template/eova.template.js',
      // ★ r304：工程级扩展资产（注册表格单元格渲染器 `eova-table-cell`）。
      //   缺了它的实测症状：列表页有数据、表头与分页都对，但**每个数据格都是空的**。
      // ★ r311：主题脚本按旧 `_eova/include.html` 的相对顺序排在扩展资产**之前**
      //   （它给 `document.body` 加 `eova-theme_default` 并加载主题 CSS）。
      '/_eova/theme/eova.theme.js',
      '/_eova/assets/eova.ui.ext.js'
    ])
  })

  it('宿主 Vue/axios 在【第一个脚本加载前】就已注入（否则制品读到的是半成品）', async () => {
    const target: Record<string, unknown> = {}
    const f = fakeLoader(target)
    await loadLegacyRuntime({ loadScript: f.loadScript, globals: HOST_GLOBALS, target })
    expect(f.globalAtLoad).toHaveLength(LEGACY_RUNTIME_SCRIPTS.length)
    for (const snap of f.globalAtLoad) {
      expect(snap.Vue).toBe(HOST_GLOBALS.Vue)
      expect(snap.axios).toBe(HOST_GLOBALS.axios)
    }
  })

  it('加载 legacy 的 vue.global.js / axios.min.js 会造出第二个 Vue 实例 ⇒ 不在清单内', async () => {
    const target: Record<string, unknown> = {}
    const f = fakeLoader(target)
    await loadLegacyRuntime({ loadScript: f.loadScript, globals: HOST_GLOBALS, target })
    expect(f.loads.some((u) => u.includes('vue.global.js'))).toBe(false)
    expect(f.loads.some((u) => u.includes('/vue/axios.min.js'))).toBe(false)
    // 全局 Vue 仍是宿主那一个（没有被 legacy 制品覆盖）
    expect(target['Vue']).toBe(HOST_GLOBALS.Vue)
  })

  it('URL 一律走旧原路径（`/eova/**` 或 `/_eova/**`，不加新前缀）', () => {
    // ★ r304 纠正：原判据断言"都在 /eova/ 下"，依据是"旧栈 /_eova/** 是 404"这条**错记**。
    //   复测旧栈：`/_eova/assets/eova.ui.ext.js` 200、`/_eova/theme/eova.theme.js` 200
    //   （只有模板片段 `/_eova/include.html` 是 404，它从不按 URL 取）。
    //   ⇒ 合法前缀是**两个**，且都是旧栈真实存在的原路径（不是我们新造的）。
    for (const url of LEGACY_RUNTIME_SCRIPTS) {
      expect(
        url.startsWith('/eova/') || url.startsWith('/_eova/'),
        `${url} 必须走旧栈原路径`
      ).toBe(true)
    }
    // vendor 三件走 /eova/lib/**；页面脚本走各自旧原路径（eova.meta.js / _view/template/eova.template.js）
    expect(
      LEGACY_RUNTIME_SCRIPTS.filter((u) => u.startsWith('/eova/lib/'))
    ).toHaveLength(3)
  })
})

describe('legacy-runtime · 幂等与失败语义', () => {
  it('已装配则不再加载任何脚本', async () => {
    const target: Record<string, unknown> = { EovaTools: {}, LayuiVue: {}, EovaUI: {}, uzoo: {} }
    const f = fakeLoader(target)
    await loadLegacyRuntime({ loadScript: f.loadScript, globals: HOST_GLOBALS, target })
    expect(f.loads).toEqual([])
    expect(isLegacyRuntimeLoaded(target)).toBe(true)
  })

  it('已装配时**连宿主全局都不再要求**（提前返回；这条区分"提前返回"与"逐个跳过"两道闸）', async () => {
    const target: Record<string, unknown> = { EovaTools: {}, LayuiVue: {}, EovaUI: {}, uzoo: {} }
    const f = fakeLoader(target)
    // 故意不提供宿主全局：若幂等的提前返回被删掉，这里会以「宿主全局缺失」抛错
    await expect(
      loadLegacyRuntime({ loadScript: f.loadScript, globals: { Vue: null, axios: null }, target })
    ).resolves.toBeUndefined()
    expect(f.loads).toEqual([])
  })

  it('未装配完时缺宿主全局仍抛错（提前返回不得掩盖真问题）', async () => {
    const target: Record<string, unknown> = { EovaTools: {} }
    await expect(
      loadLegacyRuntime({ globals: { Vue: null, axios: null }, target, loadScript: async () => {} })
    ).rejects.toThrow(/宿主全局缺失/)
  })

  it('只装配了一半时会【只补缺的】，已就位的制品不重复加载', async () => {
    const target: Record<string, unknown> = { EovaTools: { kept: true } }
    const f = fakeLoader(target)
    await loadLegacyRuntime({ loadScript: f.loadScript, globals: HOST_GLOBALS, target })
    expect(f.loads).toEqual([
      '/eova/lib/eova/lib/layui.umd.js',
      '/eova/lib/eova/eovaui.js',
      '/eova/ui/meta/eova.meta.js',
      '/eova/_view/template/eova.template.js',
      // ★ r311：主题脚本（旧 `_eova/include.html` 的顺序：主题在扩展资产之前）
      '/_eova/theme/eova.theme.js',
      '/_eova/assets/eova.ui.ext.js'
    ])
    expect((target['EovaTools'] as { kept?: boolean }).kept).toBe(true)
  })

  it('脚本加载失败 ⇒ 抛错（不静默继续）', async () => {
    const target: Record<string, unknown> = {}
    await expect(
      loadLegacyRuntime({
        globals: HOST_GLOBALS,
        target,
        loadScript: async (url) => {
          throw new Error(`boom ${url}`)
        }
      })
    ).rejects.toThrow(/boom/)
  })

  it('脚本"加载成功"但应提供的全局仍缺失 ⇒ **该脚本这一步**就抛错并点名 URL（不静默继续）', async () => {
    const target: Record<string, unknown> = {}
    // 断言必须点名 URL —— 否则与末尾那道"装配后仍缺全局"的总检查混为一谈，失去区分力
    await expect(
      loadLegacyRuntime({
        globals: HOST_GLOBALS,
        target,
        // 加载了，但不挂全局 —— 模拟制品执行失败（例如 requires 的全局不对）
        loadScript: async () => {}
      })
    ).rejects.toThrow(/eova-tools\.umd\.js 加载完成但全局 `EovaTools` 仍不存在/)
  })

  it('宿主全局缺失 ⇒ 抛错（先于任何脚本加载）', async () => {
    const target: Record<string, unknown> = {}
    let loaded = false
    await expect(
      loadLegacyRuntime({
        globals: { Vue: null, axios: {} },
        target,
        loadScript: async () => {
          loaded = true
        }
      })
    ).rejects.toThrow(/宿主全局缺失/)
    expect(loaded).toBe(false)
  })
})
