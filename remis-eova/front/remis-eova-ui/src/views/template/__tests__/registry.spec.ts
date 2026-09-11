/**
 * 模版组件表的漂移判据（第 119 轮）
 *
 * 钉的契约：`MIGRATED_TEMPLATES`（"已迁移"清单）与 `TEMPLATE_COMPONENTS`（宿主能渲染的组件）
 * **必须是同一个集合**。若只把模版名加进"已迁移"却没加组件，宿主 `state='ready'` 时会
 * `<component :is="undefined">` —— **什么都不渲染**（页面空白、构建与其它判据全绿）。
 */
import { describe, expect, it } from 'vitest'
import { MIGRATED_TEMPLATES, UNMIGRATED_TEMPLATES } from '@/compat/template-dispatch'
import { TEMPLATE_COMPONENTS } from '../registry'

describe('TEMPLATE_COMPONENTS ↔ MIGRATED_TEMPLATES', () => {
  it('★ 双向一致：已迁移清单里的每个模版都有组件，且每个组件都在已迁移清单里', () => {
    expect(Object.keys(TEMPLATE_COMPONENTS).sort()).toEqual([...MIGRATED_TEMPLATES].sort())
  })

  it('每个组件都是可渲染对象（不是 undefined/字符串）', () => {
    for (const [name, comp] of Object.entries(TEMPLATE_COMPONENTS)) {
      expect(comp, `模版 ${name} 的组件为空`).toBeTruthy()
      expect(['object', 'function']).toContain(typeof comp)
    }
  })

  it('已迁移与未迁移清单互不重叠（登记不冲突）', () => {
    for (const t of MIGRATED_TEMPLATES) {
      expect(UNMIGRATED_TEMPLATES).not.toContain(t)
    }
  })
})
