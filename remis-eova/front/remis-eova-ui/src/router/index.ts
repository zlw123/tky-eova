import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import Placeholder from '@/views/Placeholder.vue'
import Login from '@/views/Login.vue'
import Home from '@/views/Home.vue'
import Password from '@/views/user/Password.vue'
import Su from '@/views/admin/Su.vue'
import ButtonAdd from '@/views/button/ButtonAdd.vue'
import MetaReorder from '@/views/meta/MetaReorder.vue'
import MenuAuth from '@/views/menu/MenuAuth.vue'
import MetaField from '@/views/meta/MetaField.vue'
import MenuAdd from '@/views/menu/MenuAdd.vue'
import MetaEdit from '@/views/meta/MetaEdit.vue'
import MetaImport from '@/views/meta/MetaImport.vue'
import RoleAuth from '@/views/role/RoleAuth.vue'
import AppTemplateHost from '@/views/template/AppTemplateHost.vue'
import FormAdd from '@/views/template/form/FormAdd.vue'
import FormUpdate from '@/views/template/form/FormUpdate.vue'
import FormDetail from '@/views/template/form/FormDetail.vue'
import Sse from '@/views/test/Sse.vue'
import Widget from '@/views/widget/Widget.vue'

/**
 * 路由表：阶段 2 逐页接管旧 URL。
 *
 * 口径 ②：旧 URL（`/eova`、`/meta`、`/widget`）**不得加前缀** ⇒ 每条 path 都与旧栈逐字一致。
 * ★ 新增路由必须同时加入 `routes.ts` 的 `SPA_OWNED_PATHS`（否则 dev 代理会把它送去后端，
 *   而单测仍全绿 —— 见 `__tests__/owned-paths.spec.ts` 的漂移判据）。
 */
export const routes: RouteRecordRaw[] = [
  {
    // 主框架：旧栈首页就是 /（EovaConfig.EOVA_INDEX），由 _view/index/index.html 渲染
    path: '/',
    name: 'home',
    component: Home
  },
  {
    // ★ r305：旧栈 `/su` 与 `/` **是同一个落地页**（真浏览器实测：两者都跳 `/main`，
    //   title、119 个菜单项、正文逐字相同）⇒ SPA 必须同样接管，否则 `/su` 落到未匹配路由
    //   而**静默渲染空白**（实测：新栈 `/su` 文本长度 0，旧栈 182）。
    path: '/su',
    name: 'su-landing',
    redirect: '/'
  },
  {
    path: '/placeholder',
    name: 'placeholder',
    component: Placeholder
  },
  {
    // 旧 URL 不加前缀：登录页在旧栈就是 /user/login（由 UserController.login() 渲染）
    path: '/user/login',
    name: 'login',
    component: Login
  },
  {
    // 修改密码：旧栈由 Home 的 me.layer.open('修改密码', '/user/password', 400, 300) 以 iframe 打开
    path: '/user/password',
    name: 'user-password',
    component: Password
  },
  {
    // 虚拟用户切换：旧栈由 Home 的 me.layer.open('超级用户切换', '/eova/admin/su', 1200, 0.9) 以 iframe 打开
    // （AdminController#su() 渲染 /eova/user/su/app.html）
    path: '/eova/admin/su',
    name: 'admin-su',
    component: Su
  },
  {
    // 快速添加按钮：旧路径 **/button/add/<menuCode>**（旧栈实测 302；`/eova/button/add/...` 是 404 —— r305 纠正）
    path: '/button/add/:menuCode',
    name: 'button-add',
    component: ButtonAdd
  },
  {
    // 重新排序：旧路径 /meta/reorder?object=…&biz=…&mode=…（MetaController#reorder() 的三个 get 都是查询参数）
    path: '/meta/reorder',
    name: 'meta-reorder',
    component: MetaReorder
  },
  {
    // 功能授权：旧路径 /eova/menu/auth/<id>（MenuController#auth() 的 `getInt(0)` 就是那个 id）
    path: '/menu/auth/:id',
    name: 'menu-auth',
    component: MenuAuth
  },
  {
    // 元字段个性化：旧路径 /meta/field?object=…&mode=…（MetaController#field() 的两个 get 都是查询参数）
    path: '/meta/field',
    name: 'meta-field',
    component: MetaField
  },
  {
    // 创建菜单：旧路径 /eova/menu/add（MenuController#add() 渲染，父页菜单树以弹层 iframe 打开它）
    // ⚠️ 旧**页面**路径是 `/menu/toAdd`（`MenuController#toAdd()`）；`/menu/add` 是提交动作
    path: '/menu/toAdd',
    name: 'menu-add',
    component: MenuAdd
  },
  {
    // 元字段：旧路径 /meta/edit?object=…（`MetaController` 渲染，`where` 由服务端拼）
    path: '/meta/edit',
    name: 'meta-edit',
    component: MetaEdit
  },
  {
    // 导入元数据：旧路径 /meta/import（无查询参数；数据源列表由服务端 `#for(t : dataSources)` 注入）
    path: '/meta/imports',
    name: 'meta-import',
    component: MetaImport
  },
  {
    // 功能权限分配：旧原路径 **/auth/<rid>**（`AuthController#index()` 的 `get(0)` 就是那个 rid）
    //   ★ r306（U2 取证）**纠正**：此前写成 `/eova/role/auth/:rid` —— 那是把**模板路径**
    //     （`/eova/role/auth/app.html`）当成了 URL。带会话实测真值：旧栈 `/auth`、`/auth/1248`
    //     都是 200「功能权限分配」，而 `/eova/role/auth/1` 与 `/role/auth/1` 都是 **404**。
    //   ★ 本路径**不得**登记进 `SPA_OWNED_PATHS`：`/auth` 前缀下还有**动作**
    //     （`/auth/data`、`/auth/doAuth`、`/auth/update`），而 dev 代理的所有权判定**按前缀、不分方法**
    //     ⇒ 登记所有权会把这三个动作一起吞进 SPA（U1 在 `/menu/add` 上记过同类教训）。
    //     页面 URL 的壳由**后端**供给：`AuthController#index()` 已退役为壳，`/auth` 与 `/auth/<rid>`
    //     都返回壳 ⇒ SPA 起来后由本路由渲染。
    path: '/auth/:rid',
    name: 'role-auth',
    component: RoleAuth
  },
  {
    // EovaUI 组件演示页：旧路径 **/widget**（demo `AppController#widget()` 渲染 `_view/widget/index.html`）
    //   ★ 该 URL 同时是 dev 代理前缀之一（`BACKEND_ROUTE_PREFIXES`）⇒ 必须登记所有权才会 bypass 给 SPA
    path: '/widget',
    name: 'widget',
    component: Widget
  },
  {
    // SSE 演示页：旧路径 **/test/sse**（★ 不是 /sse —— 那个是 SSE 推送通道本身）
    //   取证：`TestController#sse()`（demo/…/ctrl/TestController.java:22-25）render("/eova/sse/index.html")，
    //   而 `/test` 是 demo `AppRoutes:17` 注册的前缀；页面里的 EventSource 连的才是 `/sse`。
    path: '/test/sse',
    name: 'test-sse',
    component: Sse
  },
  {
    // ★ 菜单模版页：旧路径 /app/<menu.code>（Menu.getUrl() 对 template 非空的菜单返回它；
    //   AppController#index() 的 `String menuCode = get(0)` 就是这一段）
    //   渲染哪一个模版页由**引导数据里的 menu.template** 决定 ⇒ 由宿主分派（见 AppTemplateHost.vue）。
    //
    //   ⚠️ `/app` 不能进 `SPA_OWNED_PATHS`：同前缀下还有后端渲染页（/app/add|update|detail/<object_code>），
    //   归属规则见 `compat/app-routes.ts`；漂移由 `__tests__/owned-paths.spec.ts` 与
    //   `compat/__tests__/app-routes.spec.ts` 的接线 canary 钉住。
    path: '/app/:menuCode',
    name: 'app-template',
    component: AppTemplateHost
  },
  // ★ S6（第 295 轮）：表单三页从"后端渲染"改为"SPA 路由页"（用户口径②，DES-005 §15.1/§16.7）
  //   旧栈它们是**动作路由**（不是 menu.template 模版页）：`AppController#add()/update()/detail()`
  //   渲染 `_view/template/form/{add,update,detail}/index.html`，由冻结脚本与列表模版页以 iframe 弹层打开。
  //   ⇒ 路径**不加前缀**（逐字为 `/app/<动作>/<object_code>`），归属由 `compat/app-routes.ts`
  //     的 `twoSegment='form-page'` 判定（同一份动作表，单一事实来源）。
  //   ⚠️ 与 `/app/:menuCode` 同前缀 ⇒ 两者靠**段数**区分：本页是 2 段、菜单模版页是 1 段；
  //     3 段仍归后端（见 `resolveAppUrl` 规则⑥）。
  //   过渡期后端 `renderEnjoy` 不删（DES-005 §16.4）：回退 = 去掉这三条路由 + 把动作表改回 `backend`。
  {
    path: '/app/add/:objectCode',
    name: 'form-add',
    component: FormAdd
  },
  {
    path: '/app/update/:objectCode',
    name: 'form-update',
    component: FormUpdate
  },
  {
    path: '/app/detail/:objectCode',
    name: 'form-detail',
    component: FormDetail
  }
,
  // ★ r274 口径④：demo 工程 URL 归 SPA（后端不迁移 demo 应用）⇒ 先落可声明占位（不是静默 404）
  // ★ r307（U3 取证）**收窄口径④**：四个 URL 已按实测交回后端，SPA 不再登记路由：
  //   · `/main` —— SPA 首页把它当 **iframe 内容**（本文件上一段的 `Home.vue` 用
  //     `<iframe :src="m.link">`，初始页签 link 就是 `/main`）⇒ 必须是后端渲染的主题页
  //     （`IndexController#main()`）；登记成 SPA 路由会让 iframe 里装 SPA 自己。
  //   · `/ip` —— 旧栈是 `renderText(getRealIp)` 的**纯文本端点**，不是页面（后端 `#ip()`）。
  //   · `/theme` —— 旧栈**没有**这个页面（实测落首页）⇒ 撤掉后该 URL 落回首页 = 等价。
  //   · `/sso` —— 旧栈该页本来就 **500**（模板缺失）⇒ 死页，不再假装它是一页。
  { path: '/test', component: Placeholder },
  // ★ r307 登记：`/test` 旧栈是 demo `TestController#index()` 的 `renderText`（纯文本端点），
  //   本 SPA 路由仍是占位 ⇒ **语义差异已登记**（未追平：它与已归 SPA 的 `/test/sse` 同前缀，
  //   改由后端供给会把 `/test/sse` 一起牵连，需单独裁定）。
  // ★ r305（U1）：**兜底路由** —— 旧栈对任何未命中路径都会落到 `IndexController#index()`（`/` 是兜底路由，
  //   实测旧栈 `/zzz_unknown`、`/su` 都返回首页 title `Eova Meta 2026`）。SPA 侧必须同样兜底，
  //   否则生产态访问未知 URL 会得到**空白页**（而旧栈给首页）—— 等价性缺口。
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
