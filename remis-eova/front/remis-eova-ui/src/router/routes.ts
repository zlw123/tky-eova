/**
 * SPA 路由表（**单一事实来源**）
 *
 * ★ 为什么把路径单独抽出来
 *
 * 阶段 2 的口径是"旧 URL 不加前缀"（契约纪律 ②）⇒ SPA 必须**接管旧栈的原路径**
 * （`/user/login`、`/user/password`、`/eova/admin/su` …）。但开发期 `vite.config.ts` 里
 * `'/eova'`、`'/meta'`、`'/widget'` 是**代理到后端**的 —— 于是同一批路径同时"归 SPA"和"归后端"。
 *
 * 如果只在 router 里加路由、忘了在代理里放行，症状是：**开发环境能构建、能跑测试，
 * 但打开该页面却返回后端的 404/HTML** —— 属"配置漂移"，且不会让任何单测变红。
 *
 * 故此处把"SPA 拥有的路径"抽成**一个数组**，router 与 vite 代理**共用它**，
 * 并有判据（`src/router/__tests__/owned-paths.spec.ts`）断言"router 里每条路由都在数组内"，
 * 让漂移在测试里立刻暴露。
 *
 * 命名注意：这里只声明**路径所有权**，不声明组件（组件在 `router/index.ts` 里懒/直引）。
 */

/** SPA 拥有的路径（router 与 dev 代理共用；新增路由必须同时出现在这里） */
export const SPA_OWNED_PATHS: readonly string[] = [
  '/',
  '/placeholder',
  '/user/login',
  '/user/password',
  '/eova/admin/su',
  // 带参数的入口页写**所有权前缀**（`/eova/button/add/<menuCode>` 的 `<menuCode>` 是路径段，
  // 对应旧栈 ButtonController:36 的 `get(0)`）—— 见 ownedPrefixOf()
  '/eova/button/add',
  // ★ `/meta` 也是 dev 代理前缀之一 ⇒ 不登记就会被代理去后端（见本文件顶部说明）
  '/meta/reorder',
  '/eova/menu/auth',
  '/meta/field',
  '/eova/menu/add',
  '/meta/edit'
]

/**
 * 把路由 path 归一化为**所有权前缀**：去掉动态段（`:xxx`）与其后的内容。
 *
 * 例：`/eova/button/add/:menuCode` → `/eova/button/add`（与 `SPA_OWNED_PATHS` 里的写法一致）。
 *
 * @param path 路由 path
 * @returns 所有权前缀
 */
export function ownedPrefixOf(path: string): string {
  const segs = path.split('/').filter((s) => s !== '')
  const keep: string[] = []
  for (const s of segs) {
    if (s.startsWith(':')) {
      break
    }
    keep.push(s)
  }
  return '/' + keep.join('/')
}

/**
 * 判断某请求路径是否应由 SPA 处理（而非代理给后端）。
 *
 * 规则：**精确匹配**或**以 `path + '/'` 开头**（覆盖子路径）。
 * 不做前缀模糊匹配 —— `/eova/admin/su` 归 SPA 不代表 `/eova/admin/showUserData` 也归 SPA
 * （后者是后端渲染页，仍应走代理）。
 *
 * @param url 请求 URL（可带查询串）
 * @returns 是否由 SPA 处理
 */
export function isSpaOwnedPath(url: string): boolean {
  const path = url.split('?')[0].split('#')[0]
  return SPA_OWNED_PATHS.some((p) => path === p || (p !== '/' && path.startsWith(p + '/')))
}
