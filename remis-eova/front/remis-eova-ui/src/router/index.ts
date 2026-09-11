import { createRouter, createWebHistory } from 'vue-router'
import Placeholder from '@/views/Placeholder.vue'
import Login from '@/views/Login.vue'

// 路由表：阶段 2 / T01 骨架。
// 口径 ②：旧 URL（/eova、/meta、/widget）不得加前缀，路由在 T02 逐页接管。
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'placeholder',
      component: Placeholder
    },
    {
      // 旧 URL 不加前缀（工程 README 契约纪律 ②）：登录页在旧栈就是 /user/login
      // （由 UserController.login() 渲染），分离后由前端接管同一路径
      path: '/user/login',
      name: 'login',
      component: Login
    }
  ]
})

export default router
