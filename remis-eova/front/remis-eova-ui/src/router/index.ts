import { createRouter, createWebHistory } from 'vue-router'
import Placeholder from '@/views/Placeholder.vue'
import Login from '@/views/Login.vue'
import Home from '@/views/Home.vue'
import Password from '@/views/user/Password.vue'

// 路由表：阶段 2 / T01 骨架。
// 口径 ②：旧 URL（/eova、/meta、/widget）不得加前缀，路由在 T02 逐页接管。
const router = createRouter({
  history: createWebHistory(),
  routes: [
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
      // 旧 URL 不加前缀（工程 README 契约纪律 ②）：登录页在旧栈就是 /user/login
      // （由 UserController.login() 渲染），分离后由前端接管同一路径
      path: '/user/login',
      name: 'login',
      component: Login
    },
    {
      // 修改密码：旧栈由 Home 的 me.layer.open('修改密码', '/user/password', 400, 300) 以 iframe 打开
      // ⇒ 路径必须与旧实现逐字一致（否则弹层会 404）
      path: '/user/password',
      name: 'user-password',
      component: Password
    }
  ]
})

export default router
