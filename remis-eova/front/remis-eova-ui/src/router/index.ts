import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import Placeholder from '@/views/Placeholder.vue'
import Login from '@/views/Login.vue'
import Home from '@/views/Home.vue'
import Password from '@/views/user/Password.vue'
import Su from '@/views/admin/Su.vue'

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
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
