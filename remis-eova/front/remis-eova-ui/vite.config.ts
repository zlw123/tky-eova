import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { isSpaOwnedPath } from './src/router/routes'
import { BACKEND_ROUTE_PREFIXES } from './src/compat/backend-routes'

/** 后端地址（dev 代理目标） */
const BACKEND_TARGET = 'http://127.0.0.1:8080'

/**
 * dev 代理项（**每个后端前缀都必须配**，见第 121 轮的审计）
 *
 * ★ 三条口径：
 *  ① 前缀清单来自 `compat/backend-routes.ts`（其来源是 ported 后端的路由注册 + SPA 实际调用路径，
 *     判据 `router/__tests__/dev-proxy-coverage.spec.ts` 会解析 Java 源码与扫描 SPA 源码双向核对）；
 *  ② 每个前缀都带**同一个** `bypass`：`isSpaOwnedPath(req.url)` 为真 ⇒ 放行给 SPA。
 *     ⇒ 前缀配宽不危险（"哪些路径归 SPA"由那一个函数说了算，单一事实来源）；
 *  ③ **唯一不能配的前缀是 `/`**（它是 SPA 首页）。漏配其它前缀的后果是：
 *     该前缀下的后端请求落到 Vite 的 SPA 回退（拿到 index.html、HTTP 200），
 *     症状是"页面能打开、操作没反应"，而**构建、单测、闸门全绿**（第 108 轮同类漂移的更宽形态）。
 */
const proxy = Object.fromEntries(
  BACKEND_ROUTE_PREFIXES.map((prefix) => [
    prefix,
    {
      target: BACKEND_TARGET,
      changeOrigin: true,
      bypass: (req: { url?: string }) => (isSpaOwnedPath(req.url ?? '') ? req.url : undefined)
    }
  ])
)

// remis-eova-ui 构建配置：工程选型对齐 platform/fornt/yudao-ui，降低集成期摩擦
export default defineConfig({
  // Vitest（阶段 2 行为等价判据）：jsdom 环境下挂载 SFC
  test: {
    environment: 'jsdom',
    include: ['src/**/*.spec.ts']
  },
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 9090,
    proxy
  }
})
