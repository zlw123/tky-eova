/**
 * 模版名 → SPA 组件表（第 119 轮）
 *
 * ★ 为什么单独一个模块，而不是在宿主里写 `v-if` 链
 *
 * `MIGRATED_TEMPLATES`（`compat/template-dispatch.ts`）与"宿主能渲染哪个组件"必须**同一个集合**：
 * 一旦把某个模版名加进"已迁移"却没有对应组件，宿主会退化成渲染**别的模版页**
 * （或什么都不渲染）—— 这正是本轮一直在防的那类**静默错页**。
 * 把映射抽成可 import 的表，判据就能双向断言：
 * `Object.keys(TEMPLATE_COMPONENTS)` ≡ `MIGRATED_TEMPLATES`（见 `__tests__/registry.spec.ts`）。
 *
 * `AppTemplateHost` 用 `<component :is="…">` 取用；`state='ready'` 只会在该表命中时成立。
 */
import type { Component } from 'vue'
import TemplateTable from './TemplateTable.vue'
import TemplateTree from './TemplateTree.vue'
import TemplateTreeTable from './TemplateTreeTable.vue'

/** 模版名 → 组件（键必须与 `MIGRATED_TEMPLATES` 完全一致） */
export const TEMPLATE_COMPONENTS: Record<string, Component> = {
  table: TemplateTable,
  tree: TemplateTree,
  tree_table: TemplateTreeTable
}
