/**
 * 判据辅助函数。
 *
 * 说明：这里导出的是**测试辅助**，不是生产代码；放在 `__tests__/helpers.ts` 而不是
 * `helpers.spec.ts`，避免 vitest 把它当成用例文件（`include: ['src/**\/*.spec.ts']` 只收 spec）。
 */
import type { RouteRecordRaw } from 'vue-router'

/**
 * 展开路由表，收集所有**绝对路径**（含子路由拼接）。
 *
 * @param routes 路由记录数组
 * @param parent 父路径（递归用）
 * @returns 绝对路径数组
 */
export function routePathsOf(routes: readonly RouteRecordRaw[], parent = ''): string[] {
  const out: string[] = []
  for (const r of routes) {
    const path = r.path.startsWith('/') ? r.path : `${parent}/${r.path}`.replace(/\/+/g, '/')
    out.push(path)
    if (r.children && r.children.length > 0) {
      out.push(...routePathsOf(r.children, path))
    }
  }
  return out
}
