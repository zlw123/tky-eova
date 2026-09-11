/**
 * 主框架页剩余行为的判据（逐条对齐旧 `_view/index/index.js` + `index.html`，第 101 轮）。
 *
 * 本文件钉的是"**改写时最容易顺手规范化掉**"的四类东西：
 *  ① `refresh()` 的 `about:blank` 兜底刷新算法（含 300ms 与"带 hash 也必须变值"）；
 *  ② 全屏 vendor 前缀的**精确名称**（旧用非标准的 `requestFullScreen`）与探测顺序；
 *  ③ `showMenu` 对布局的唯一耦合点（`left:200px` / `left:0`）；
 *  ④ `refresh` 在**无激活页签**时不静默跳过（旧实现是未捕获 TypeError）。
 */
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  REFRESH_FALLBACK_DELAY_MS,
  activeIframeId,
  bodyLeftStyle,
  buildIframeId,
  exitFullscreen,
  findActiveTab,
  foldIconClass,
  isMenuUnderCat,
  menuTitle,
  refreshIframe,
  resolveFullscreenRequest,
  toggleFullscreen,
  type FrameTab,
  type RefreshableIframe,
  type TimerLike
} from '../frame'

/** 记录调用的假定时器（含延时常量 —— 延时是契约的一部分，必须被观测到） */
function fakeTimers(): TimerLike & {
  run: () => void
  pending: number
  cleared: unknown[]
  delays: number[]
} {
  const jobs: Array<() => void> = []
  const cleared: unknown[] = []
  const delays: number[] = []
  return {
    pending: 0,
    cleared,
    delays,
    setTimeout(fn: () => void, ms: number) {
      jobs.push(fn)
      delays.push(ms)
      this.pending = jobs.length
      return jobs.length
    },
    clearTimeout(id: unknown) {
      cleared.push(id)
    },
    run() {
      const all = jobs.splice(0)
      this.pending = jobs.length
      all.forEach((fn) => fn())
    }
  }
}

describe('frame.ts · iframe 标识与激活页签', () => {
  it('buildIframeId：旧模板 `IF` + id（数字与字符串 id 都要能用）', () => {
    expect(buildIframeId(0)).toBe('IF0')
    expect(buildIframeId(12)).toBe('IF12')
    expect(buildIframeId('abc')).toBe('IFabc')
  })

  it('activeIframeId：取【激活】页签而不是第一个', () => {
    const tabs: FrameTab[] = [
      { id: 0, active: false, link: '/main' },
      { id: 7, active: true, link: '/eova/meta' }
    ]
    expect(findActiveTab(tabs)!.id).toBe(7)
    expect(activeIframeId(tabs)).toBe('IF7')
  })

  it('activeIframeId：无激活页签时【抛错】，不得静默返回 null（旧实现是未捕获 TypeError）', () => {
    expect(() => activeIframeId([{ id: 0, active: false }])).toThrow(TypeError)
    expect(() => activeIframeId([])).toThrow(TypeError)
  })
})

describe('frame.ts · 布局耦合点（showMenu 的全部影响面）', () => {
  it('bodyLeftStyle：显示菜单 left:200px，折叠后 left:0', () => {
    expect(bodyLeftStyle(true)).toBe('left:200px')
    expect(bodyLeftStyle(false)).toBe('left:0')
  })

  it('foldIconClass：图标随 showMenu 在 shrink-right / spread-left 间切换', () => {
    expect(foldIconClass(true)).toBe('eova-icon-shrink-right')
    expect(foldIconClass(false)).toBe('eova-icon-spread-left')
  })
})

describe('frame.ts · 菜单项判定与标题', () => {
  it('menuTitle：旧模板 `m.id + "|" + m.link`（数字 id 也拼成字符串）', () => {
    expect(menuTitle({ id: 1, link: '/main' })).toBe('1|/main')
    expect(menuTitle({ id: 'x', link: undefined })).toBe('x|undefined')
  })

  it('isMenuUnderCat：parent_id 用【宽松相等】（后端可能给字符串 id）', () => {
    expect(isMenuUnderCat({ parent_id: '10', type: 'menu' }, { id: 10 })).toBe(true)
    expect(isMenuUnderCat({ parent_id: 10, type: 'menu' }, { id: 10 })).toBe(true)
    expect(isMenuUnderCat({ parent_id: 20, type: 'menu' }, { id: 10 })).toBe(false)
  })

  it('isMenuUnderCat：type=dir 的菜单本身是目录，不挂成叶子', () => {
    expect(isMenuUnderCat({ parent_id: 10, type: 'dir' }, { id: 10 })).toBe(false)
    expect(isMenuUnderCat({ parent_id: 10, type: 'open' }, { id: 10 })).toBe(true)
  })

  it('isMenuUnderCat：parent_id 为 null 不归属任何目录', () => {
    expect(isMenuUnderCat({ parent_id: null, type: 'menu' }, { id: 10 })).toBe(false)
  })
})

describe('frame.ts · 全屏（探测顺序与非标准名逐字保留）', () => {
  it('resolveFullscreenRequest：优先【非标准】的 requestFullScreen（大写 S）', () => {
    const nonStandard = vi.fn()
    const webkit = vi.fn()
    const ele = { requestFullScreen: nonStandard, webkitRequestFullScreen: webkit }
    const req = resolveFullscreenRequest(ele)
    expect(req).toBe(nonStandard)
    req!.call(ele)
    expect(nonStandard).toHaveBeenCalledTimes(1)
    expect(webkit).not.toHaveBeenCalled()
  })

  it('resolveFullscreenRequest：只提供【标准】名 requestFullscreen 时探测不到（不得顺手"修正"名字）', () => {
    expect(resolveFullscreenRequest({ requestFullscreen: vi.fn() })).toBeNull()
  })

  it('resolveFullscreenRequest：兜到 msRequestFullscreen（小写 s）', () => {
    const ms = vi.fn()
    expect(resolveFullscreenRequest({ msRequestFullscreen: ms })).toBe(ms)
  })

  it('exitFullscreen：只认 document.exitFullscreen，不探测 vendor 前缀', () => {
    const exit = vi.fn()
    expect(exitFullscreen({ exitFullscreen: exit })).toBe(true)
    expect(exit).toHaveBeenCalledTimes(1)
    expect(exitFullscreen({ webkitExitFullscreen: vi.fn() } as never)).toBe(false)
  })

  it('toggleFullscreen：进入时以 documentElement 为 this 调用请求函数', () => {
    const req = vi.fn()
    const ele = { requestFullScreen: req }
    expect(toggleFullscreen(false, {}, ele)).toBe(true)
    expect(req.mock.instances[0]).toBe(ele)
  })

  it('toggleFullscreen：无可用请求函数时标记【不变】（旧实现：什么都不做）', () => {
    expect(toggleFullscreen(false, {}, {})).toBe(false)
  })

  it('toggleFullscreen：已是全屏但无 exitFullscreen 时标记【保持 true】（旧实现不回落）', () => {
    expect(toggleFullscreen(true, {}, {})).toBe(true)
    const exit = vi.fn()
    expect(toggleFullscreen(true, { exitFullscreen: exit }, {})).toBe(false)
    expect(exit).toHaveBeenCalledTimes(1)
  })
})

describe('frame.ts · refresh 的刷新算法', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('同源：直接 contentWindow.location.reload(true)，返回 reload', () => {
    const reload = vi.fn()
    const iframe: RefreshableIframe = {
      src: '/main',
      contentWindow: { location: { reload } }
    }
    expect(refreshIframe(iframe, fakeTimers())).toBe('reload')
    expect(reload).toHaveBeenCalledWith(true)
    expect(iframe.src).toBe('/main')
  })

  it('跨域：先把 src 置为 about:blank，300ms 后还原（中间值不可省）', () => {
    const timers = fakeTimers()
    const iframe: RefreshableIframe = {
      src: '/eova/meta/table#hash',
      contentWindow: {
        location: {
          reload() {
            throw new Error('cross-origin')
          }
        }
      }
    }
    expect(refreshIframe(iframe, timers)).toBe('fallback')
    // 关键：中间必须是 about:blank —— 带 #hash 时设成同值不会触发重载
    expect(iframe.src).toBe('about:blank')
    expect(timers.pending).toBe(1)
    // 延时值本身也要判：只判"有没有排定时器"会让 300 → 0 这类改动漏网
    expect(timers.delays).toEqual([300])
    timers.run()
    expect(iframe.src).toBe('/eova/meta/table#hash')
    expect(timers.cleared).toHaveLength(1)
  })

  it('兜底延时是 300ms（常量即契约，别改成 0 或 1000）', () => {
    expect(REFRESH_FALLBACK_DELAY_MS).toBe(300)
    // 常量必须真的被用到（只判常量值、不判使用点等于没判）
    const timers = fakeTimers()
    const iframe: RefreshableIframe = {
      src: '/x',
      contentWindow: {
        location: {
          reload() {
            throw new Error('cross-origin')
          }
        }
      }
    }
    refreshIframe(iframe, timers)
    expect(timers.delays).toEqual([REFRESH_FALLBACK_DELAY_MS])
  })

  it('兜底自身也失败：返回 failed 且只打日志（不上抛）', () => {
    const log = vi.spyOn(console, 'log').mockImplementation(() => {})
    const iframe = {
      get src(): string {
        throw new Error('src unavailable')
      },
      contentWindow: {
        location: {
          reload() {
            throw new Error('cross-origin')
          }
        }
      }
    } as unknown as RefreshableIframe
    expect(refreshIframe(iframe, fakeTimers())).toBe('failed')
    expect(log).toHaveBeenCalledWith('页面刷新失败!')
  })

  it('同步失败路径不得吞掉"刷新发生了"这一事实：reload 抛错时返回值必为 fallback/failed', () => {
    const iframe: RefreshableIframe = {
      src: '/main',
      contentWindow: {
        location: {
          reload() {
            throw new Error('cross-origin')
          }
        }
      }
    }
    expect(refreshIframe(iframe, fakeTimers())).not.toBe('reload')
  })
})
