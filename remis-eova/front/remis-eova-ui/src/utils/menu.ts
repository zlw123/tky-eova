/**
 * 菜单/目录的纯逻辑（与旧 `_view/index/index.js` 的规则逐条对应）。
 *
 * 抽成独立模块的原因：`<script setup>` 不能含 ES export，而这条规则是**要单独判的契约**
 * （"只显示确实有子菜单的目录"），放在组件里就只能靠端到端渲染间接判。
 */

/**
 * 按旧实现规则过滤目录：只保留"确实有子菜单"的目录。
 *
 * 旧实现原文：先收集 `pids = { menus[].parent_id | parent_id !== null && parent_id >= 0 }`，
 * 再 `cats = ret.cats.filter(c => pids.has(c.id))`。
 *
 * 返回值元素类型与入参一致（泛型），便于调用方保留自己的目录类型（如 `Cat`）。
 *
 * @param menuList 后端返回的菜单
 * @param catList  后端返回的目录
 * @returns 过滤后的目录（元素类型与 catList 相同）
 */
export function filterCats<C extends Record<string, any>>(
  menuList: Array<Record<string, any>>,
  catList: C[]
): C[] {
  const pids = new Set<any>()
  menuList.forEach((m) => {
    if (m.parent_id !== null && m.parent_id !== undefined && m.parent_id >= 0) {
      pids.add(m.parent_id)
    }
  })
  return catList.filter((cat) => pids.has(cat.id))
}
