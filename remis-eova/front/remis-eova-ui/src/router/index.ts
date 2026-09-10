import { createRouter, createWebHistory } from 'vue-router'
import Placeholder from '@/views/Placeholder.vue'

// 路由表：阶段 2 / T01 骨架。
// 口径 ②：旧 URL（/eova、/meta、/widget）不得加前缀，路由在 T02 逐页接管。
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'placeholder',
      component: Placeholder
    }
  ]
})

export default router
