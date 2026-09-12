/**
 * 后端路由**顶层前缀**清单（第 121 轮）
 *
 * ## 为什么需要它（本轮抓出的真实缺口）
 *
 * dev 环境的 `server.proxy` 原本只配了 4 个前缀（`/eova`、`/meta`、`/widget`、`/app`），
 * 而 SPA 实际调用的后端路径远不止这些 —— 实测（`grep -rno "axios\.\(post\|get\)('"` 排除测试）：
 *
 * ```
 * /api/home/menu      Home.vue:275     ← 主框架菜单
 * /user/doLogin       Login.vue:123    ← 登录
 * /user/doPassword    Password.vue:98
 * /button/doAdd       ButtonAdd.vue:236
 * /menu/add           MenuAdd.vue:365
 * /menu/authData      MenuAuth.vue:171
 * /auth/update|data|doAuth  MenuAuth/RoleAuth
 * /excel/export/…     me.urls / x.axios.download（导出）
 * ```
 *
 * ⇒ **开发环境里这些请求会落到 Vite 的 SPA 回退上**（返回 index.html，HTTP 200），
 * 症状是"页面能打开、点登录没反应/提示失败"，而**构建、单测、闸门全绿** ——
 * 与第 108 轮 `bypass` 漂移同一类，只是面更宽（不是"少配 bypass"，而是"少配前缀"）。
 *
 * ## 清单来源（取证，不是拍脑袋）
 *
 * 1. **ported 后端的路由注册**（`eova-core/src/main/java/cn/eova/EovaWebRoutes.java`、
 *    `EovaApiRoutes.java`、`config/EovaConfig.java` 的 `add(...)`/`me.add(...)`）——
 *    判据 `__tests__/dev-proxy-coverage.spec.ts` 会**解析这些 Java 源码**核对本表
 *    （每个 Java 注册前缀的**首段**都必须在本表内）；
 * 2. **SPA 实际调用的路径**（源码扫描，判据同样跑）：每个字面量路径要么归 SPA，要么首段在本表内。
 *
 * ## 与 SPA 所有权的关系
 *
 * 每个前缀的代理项都带同一个 `bypass`：`isSpaOwnedPath(req.url)` 为真 ⇒ 放行给 SPA。
 * ⇒ **前缀可以配得宽**（宁可多配），因为"哪些路径归 SPA"由那一个函数说了算（单一事实来源）。
 * 唯一不能配的前缀是 `/`（它是 SPA 首页）。
 */

/**
 * 后端顶层前缀（顺序无关；vite 代理表按它生成）。
 *
 * 每项都注明来源，便于下次核对：
 */
export const BACKEND_ROUTE_PREFIXES: readonly string[] = [
  // —— EovaWebRoutes.java / EovaApiRoutes.java 的 EovaMeta 路由（首段汇总）——
  '/api',
  '/excel',
  '/upload',
  '/sse',
  '/eova',
  '/user',
  '/meta',
  '/menu',
  '/button',
  '/auth',
  '/task',
  '/dict',
  // —— EovaConfig.java:289 `me.add("/app", AppController.class)` ——
  '/app',
  // —— 演示工程 AppConfig/AppRoutes：demo AppController 的动作注册在 `EOVA_INDEX`(= `/`) 之下 ——
  // ★ r273 口径④（用户裁定）：demo 工程的 `/main`、`/theme`、`/test`、`/ip`、`/sso` **归 SPA 所有**
  //   （属旧 demo 应用而非 EovaMeta core：旧栈带会话 `GET /main` = <title>EovaUI主题风格</title>，
  //   ported 侧只有 core ⇒ 当前端把它们当后端前缀转发时必然 404，真浏览器实测 `404 /main`）。
  //   ⇒ 不再列为后端前缀，改由 SPA 路由承接（router/routes.ts + router/index.ts）。
  '/widget',
  // —— 两个 webapp 根的**顶层静态目录**（源：`meta-eova/eova/{view,demo}/src/main/webapp/` 的 `ls`）——
  //   view 根：`eova`（已在上方）
  //   demo 根：`_component` `_eova` `_static` `_view` `demo` `excel`(已在上方) `hotel` `product` `ui`
  //   （`WEB-INF` 不对外服务，故不列）
  //
  // ★ 其中 `/demo` 是**实证必需**的：种子数据里自定义按钮的 `ui` 形如 `/demo/test/btn.js`
  //   （`demo/src/main/webapp/demo/test/btn.js`），`ButtonAdd.vue` 的 data 初值也指向它 ——
  //   不代理则 `.js` 按钮脚本在 dev 环境取不到（自定义按钮点了没反应）。
  '/demo',
  '/_component',
  '/_eova',
  '/_static',
  '/_view',
  '/hotel',
  '/product',
  '/ui'
]

/**
 * 静态资源前缀（同属后端，但只是"取文件"，与业务 API 区分开便于阅读）
 *
 * `/eova` 同时承载 `/eova/lib/**`、`/eova/ui/**`、`/eova/_view/**` 等静态目录
 * （`index.html` 里直接 `<link>`/`<script src>` 了它们）⇒ 已被上面覆盖。
 */
export const BACKEND_STATIC_PREFIXES: readonly string[] = ['/eova']
