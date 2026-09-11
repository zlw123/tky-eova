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
      }
    }
  }
}

const HOST_GLOBALS = { Vue: { tag: 'app-vue' }, axios: { tag: 'app-axios' } }

describe('legacy-runtime · 装配顺序', () => {
  it('三个制品按 eova-tools → layui → eovaui 的顺序加载（清单本身即契约）', async () => {
    const target: Record<string, unknown> = {}
    const f = fakeLoader(target)
    await loadLegacyRuntime({ loadScript: f.loadScript, globals: HOST_GLOBALS, target })
    expect(f.loads).toEqual([...LEGACY_RUNTIME_SCRIPTS])
    expect(f.loads).toEqual([
      '/eova/lib/eova/lib/eova-tools.umd.js',
      '/eova/lib/eova/lib/layui.umd.js',
      '/eova/lib/eova/eovaui.js'
    ])
  })

  it('宿主 Vue/axios 在【第一个脚本加载前】就已注入（否则制品读到的是半成品）', async () => {
    const target: Record<string, unknown> = {}
    const f = fakeLoader(target)
    await loadLegacyRuntime({ loadScript: f.loadScript, globals: HOST_GLOBALS, target })
    expect(f.globalAtLoad).toHaveLength(3)
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

  it('URL 走旧原路径 /eova/lib/**（契约纪律 ②：旧 URL 不加前缀）', () => {
    for (const url of LEGACY_RUNTIME_SCRIPTS) {
      expect(url.startsWith('/eova/lib/')).toBe(true)
    }
  })
})

describe('legacy-runtime · 幂等与失败语义', () => {
  it('已装配则不再加载任何脚本', async () => {
    const target: Record<string, unknown> = { EovaTools: {}, LayuiVue: {}, EovaUI: {} }
    const f = fakeLoader(target)
    await loadLegacyRuntime({ loadScript: f.loadScript, globals: HOST_GLOBALS, target })
    expect(f.loads).toEqual([])
    expect(isLegacyRuntimeLoaded(target)).toBe(true)
  })

  it('已装配时**连宿主全局都不再要求**（提前返回；这条区分"提前返回"与"逐个跳过"两道闸）', async () => {
    const target: Record<string, unknown> = { EovaTools: {}, LayuiVue: {}, EovaUI: {} }
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
      '/eova/lib/eova/eovaui.js'
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
