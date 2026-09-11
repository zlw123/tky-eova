import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { isSpaOwnedPath } from './src/router/routes'

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
    // 阶段 2 口径 ②：旧 URL（/eova、/meta、/widget）不得加前缀，代理时原样透传。
    //
    // ★ 但 SPA 接管的页面**用的就是这些旧路径**（如 /eova/admin/su）⇒ 必须放行，
    //   否则开发环境打开该页会被代理到后端（拿到后端 404/HTML），而**构建与单测全绿**。
    //   放行规则与 router 共用 `SPA_OWNED_PATHS`（单一事实来源，见 src/router/routes.ts）。
    proxy: {
      '/eova': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        bypass: (req) => (isSpaOwnedPath(req.url ?? '') ? req.url : undefined)
      },
      '/meta': { target: 'http://127.0.0.1:8080', changeOrigin: true },
      '/widget': { target: 'http://127.0.0.1:8080', changeOrigin: true }
    }
  }
})
