/**
 * `EovaTools` 全局解析的判据（第 303 轮 · 真缺陷回归锁）
 *
 * ★ 这个缺陷为什么能活到第 303 轮（两条判据同时"看不全"）：
 *  ① 组件测试用 `setEovaTools(替身)` **注入**，绕过了真实解析路径 ⇒ 解析分支无覆盖；
 *  ② S5 真浏览器判据只查 `typeof window.EovaTools === 'object'`（**存在性**，不查**形状**）
 *     ⇒ 命名空间在、工具对象不在，判据照样绿。
 *
 * 实测事实（真浏览器 CDP 两端一致，第 303 轮）：
 * ```js
 * window.EovaTools = { EovaTools, ValidateTool, default, eova, x }  // ← UMD 命名空间
 * window.EovaTools.x.validate  // → object  ← ★ 真工具对象在这里
 * ```
 * 旧页脚本取的正是 `const {x} = EovaTools`（对照 `_view/template/form/add/index.js:3`）。
 * 修前：`resolveEovaTools` 只认"整体 `EovaTools`"与 `eova.x` ⇒ **真浏览器里从未解析成功**，
 * 凡走 `getEovaTools()` 的路径（表格 `doResize`、工具栏按钮、提交前校验）**一律抛**
 * —— 修前 `/app/meta_product` 实测每页 **2 个未捕获异常**。
 *
 * 本判据钉形状表（**不是**"跑通就算"）：真形状 / 变体 / 缺失 / 优先级 / 注入优先。
 */
import { afterEach, describe, expect, it } from 'vitest'
import { getEovaTools, resolveEovaTools, setEovaTools } from '../eova-runtime'
import type { EovaTools } from '../eova-runtime'

/** 真工具对象（旧页脚本用的就是这个 `x`） */
function realTools(): EovaTools {
  return { validate: { check: () => true } } as unknown as EovaTools
}

/**
 * 造一个"形状与实测一致"的 UMD 命名空间。
 *
 * 键集合**照抄**真浏览器实测结果，多一个少一个都算形状变了
 * （对象字面量按构造顺序保持插入序 ⇒ 下面的键序断言才成立）。
 */
function realNamespace(tools: EovaTools) {
  return {
    // ★ 实测键序：EovaTools, ValidateTool, default, eova, x
    EovaTools: { tag: 'self-ref' },
    ValidateTool: { tag: 'validate-tool' },
    default: { tag: 'default' },
    eova: { tag: 'eova' },
    x: tools
  }
}

afterEach(() => {
  setEovaTools(null)
})

describe('EovaTools 解析 · 真形状（r303 实测）', () => {
  it('实测形状：工具对象在 `EovaTools.x`，必须解析成功', () => {
    const tools = realTools()
    const g = { EovaTools: realNamespace(tools) }
    expect(resolveEovaTools(g)).toBe(tools)
  })

  it('实测命名空间的键集合 = [EovaTools, ValidateTool, default, eova, x]（形状本身即契约）', () => {
    // 这条不是"复述 fixture"：fixture 若被改成"简化形状"，下面的解析断言就失去意义，
    // ⇒ 先钉住 fixture 与实测一致，解析断言才可信。
    expect(Object.keys(realNamespace(realTools()))).toEqual([
      'EovaTools',
      'ValidateTool',
      'default',
      'eova',
      'x'
    ])
    // 且"整体"上没有 validate —— 这正是"只认整体"会失败的原因
    expect((realNamespace(realTools()) as { validate?: unknown }).validate).toBeUndefined()
  })

  it('变体：`eova.x`（无 `EovaTools`）仍可用', () => {
    const tools = realTools()
    expect(resolveEovaTools({ eova: { x: tools } })).toBe(tools)
  })

  it('变体：直接挂工具对象（整体自带 validate）仍可用', () => {
    const tools = realTools()
    expect(resolveEovaTools({ EovaTools: tools as never })).toBe(tools)
  })

  it('优先级：`EovaTools.x` > `eova.x` > 裸 `EovaTools`（`x` 才是真对象）', () => {
    const fromX = realTools()
    const fromEova = realTools()
    const bare = realTools()
    expect(resolveEovaTools({ EovaTools: realNamespace(fromX), eova: { x: fromEova } })).toBe(fromX)
    expect(resolveEovaTools({ EovaTools: bare as never, eova: { x: fromEova } })).toBe(fromEova)
  })

  it('缺失/半成品一律返回 null（不"猜一个"）：空全局、命名空间无 `x`、`x` 无 validate', () => {
    expect(resolveEovaTools({})).toBeNull()
    expect(resolveEovaTools({ EovaTools: undefined })).toBeNull()
    // 命名空间在但 `x` 缺席 —— 修前的失败现场
    expect(resolveEovaTools({ EovaTools: { EovaTools: {}, default: {}, eova: {} } })).toBeNull()
    // `x` 在但没有 validate ⇒ 是半成品，不得当工具对象用
    expect(resolveEovaTools({ EovaTools: { x: {} } })).toBeNull()
  })
})

describe('getEovaTools · 与真实形状打通', () => {
  it('真形状下不抛，且返回的正是 `EovaTools.x`（不再是"存在但抛"）', () => {
    const tools = realTools()
    const g = globalThis as unknown as { EovaTools?: unknown }
    const saved = g.EovaTools
    g.EovaTools = realNamespace(tools)
    try {
      expect(getEovaTools()).toBe(tools)
    } finally {
      g.EovaTools = saved
    }
  })

  it('注入优先于全局（注入是判据通道，不得被真形状抢走）', () => {
    const injected = realTools()
    const g = globalThis as unknown as { EovaTools?: unknown }
    const saved = g.EovaTools
    g.EovaTools = realNamespace(realTools())
    setEovaTools(injected)
    try {
      expect(getEovaTools()).toBe(injected)
    } finally {
      g.EovaTools = saved
    }
  })

  it('解析不出仍**响亮失败**（静默降级会退化成"不校验直接提交"）', () => {
    const g = globalThis as unknown as { EovaTools?: unknown; eova?: unknown }
    const savedTools = g.EovaTools
    const savedEova = g.eova
    delete g.EovaTools
    delete g.eova
    try {
      expect(() => getEovaTools()).toThrow(/EovaTools 未装配/)
    } finally {
      g.EovaTools = savedTools
      g.eova = savedEova
    }
  })
})
