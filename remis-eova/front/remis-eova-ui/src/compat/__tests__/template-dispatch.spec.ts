/**
 * 模版分派判据（第 118 轮）
 *
 * 钉的契约：`AppController#index()` 的 `menu.getTemplate()` 在 SPA 侧的等价 ——
 * **只认引导数据**，取不到就 `missing`（**不猜默认模版**：猜成 `table` 会让 `tree` 菜单渲染出错页，
 * 而且看起来"正常"）。另有 `menu.conf` 的两种形态与弹层宽高的**逐模版默认值**。
 */
import { describe, expect, it, vi } from 'vitest'
import type { PageBootstrap } from '../page-bootstrap'
import {
  MIGRATED_TEMPLATES,
  UNMIGRATED_TEMPLATES,
  isMigratedTemplate,
  layerSizeOf,
  menuConfOf,
  resolveTemplate
} from '../template-dispatch'

/** 造引导数据（工厂：避免用例之间共享可变对象） */
function bs(menu: Record<string, unknown> | undefined): PageBootstrap {
  return { fromServer: true, url: {}, menu: menu as never }
}

describe('resolveTemplate（menu.getTemplate 的等价）', () => {
  it('引导数据里有 template ⇒ 取到，来源标 bootstrap', () => {
    expect(resolveTemplate(bs({ code: 'menu_x', template: 'table' }))).toEqual({
      template: 'table',
      source: 'bootstrap'
    })
  })

  it('★ 缺 template / 空串 / 纯空白 ⇒ missing（**不猜默认模版**）', () => {
    for (const menu of [undefined, {}, { template: '' }, { template: '   ' }, { template: null }]) {
      expect(resolveTemplate(bs(menu as never)), String(menu)).toEqual({
        template: '',
        source: 'missing'
      })
    }
  })

  it('非字符串值按 String() 归一（不静默丢弃）', () => {
    expect(resolveTemplate(bs({ template: 0 }))).toEqual({ template: '0', source: 'bootstrap' })
  })

  it('★ 三个列表模版全部已迁移（r120），未迁移清单为空', () => {
    expect(MIGRATED_TEMPLATES).toEqual(['table', 'tree', 'tree_table'])
    expect(UNMIGRATED_TEMPLATES).toEqual([])
    for (const t of MIGRATED_TEMPLATES) {
      expect(UNMIGRATED_TEMPLATES).not.toContain(t)
      expect(isMigratedTemplate(t)).toBe(true)
    }
    expect(isMigratedTemplate('tree')).toBe(true)
    expect(isMigratedTemplate('tree_table')).toBe(true)
    expect(isMigratedTemplate('whatever')).toBe(false)
  })
})

describe('menuConfOf（旧 `#(menu.conf)`）', () => {
  it('已是对象 ⇒ 原样返回（同一个引用，不做拷贝）', () => {
    const conf = { layer_width: 900 }
    expect(menuConfOf(bs({ conf }))).toBe(conf)
  })

  it('JSON 串 ⇒ 解析（旧栈 `#(menu.conf)` 不带引号打印的就是对象字面量文本）', () => {
    expect(menuConfOf(bs({ conf: '{"layer_width":900,"layer_height":700}' }))).toEqual({
      layer_width: 900,
      layer_height: 700
    })
  })

  it('`config` 列名也认（Menu.getConf() 读的是 config 列）', () => {
    expect(menuConfOf(bs({ config: '{"layer_width":1}' }))).toEqual({ layer_width: 1 })
  })

  it('★ 非法 JSON / 非对象 ⇒ 空配置 + 告警（不把脏数据当配置）', () => {
    const warn = vi.fn()
    expect(menuConfOf(bs({ conf: '{bad json' }), warn)).toEqual({})
    expect(warn).toHaveBeenCalledTimes(1)
    expect(String(warn.mock.calls[0][0])).toContain('不是合法 JSON')

    const warn2 = vi.fn()
    expect(menuConfOf(bs({ conf: '[1,2]' }), warn2)).toEqual({})
    expect(warn2).toHaveBeenCalledTimes(1)
  })

  it('缺省/空串 ⇒ 空对象且**不告警**（这是合法状态，不是脏数据）', () => {
    const warn = vi.fn()
    expect(menuConfOf(bs({}), warn)).toEqual({})
    expect(menuConfOf(bs({ conf: '' }), warn)).toEqual({})
    expect(menuConfOf(bs(undefined), warn)).toEqual({})
    expect(warn).not.toHaveBeenCalled()
  })
})

describe('layerSizeOf（旧 `conf.layer_width || 720` / `conf.layer_height || <逐模版默认>`）', () => {
  it('★ 默认高**逐模版不同**：table 是 660，tree/tree_table 是 720', () => {
    // 取证：template/table/index.js:13 = 660；template/tree/index.js:15 与 tree_table/index.js:16 = 720
    expect(layerSizeOf({}, 720, 660)).toEqual({ width: 720, height: 660 })
    expect(layerSizeOf({}, 720, 720)).toEqual({ width: 720, height: 720 })
  })

  it('配置里有时取配置值（字符串原样透传，旧 `me.layer.open` 自己换算）', () => {
    expect(layerSizeOf({ layer_width: 1000, layer_height: 520 }, 720, 660)).toEqual({
      width: 1000,
      height: 520
    })
    expect(layerSizeOf({ layer_width: '0.9' }, 720, 660)).toEqual({ width: '0.9', height: 660 })
  })

  it('`||` 语义原样保留：0 / 空串 / null 都落回默认值', () => {
    expect(layerSizeOf({ layer_width: 0, layer_height: '' }, 720, 660)).toEqual({
      width: 720,
      height: 660
    })
    expect(layerSizeOf({ layer_width: null, layer_height: null }, 720, 660)).toEqual({
      width: 720,
      height: 660
    })
  })
})
