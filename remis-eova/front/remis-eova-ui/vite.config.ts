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
      // 同一规则必须**每个前缀都配**：`/meta` 与 `/widget` 也会承载 SPA 接管的旧路径
      // （如 `/meta/reorder`）—— 只给 `/eova` 配 bypass 是不够的。
      '/meta': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        bypass: (req) => (isSpaOwnedPath(req.url ?? '') ? req.url : undefined)
      },
      '/widget': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        bypass: (req) => (isSpaOwnedPath(req.url ?? '') ? req.url : undefined)
      },
      // ★ 第 118 轮：`/app` 同前缀下既可能是 SPA 菜单模版页（`/app/<menu.code>`），
      //   也可能是**后端渲染页**（`/app/add|update|detail/<object_code>`，由冻结脚本以 iframe 弹层打开）
      //   ⇒ 这里必须放行前者、继续代理后者。判定口径与 router 共用（`isSpaOwnedPath` → `compat/app-routes.ts`）。
      //   忘了配这一条的后果：SPA 菜单页被代理到后端（拿到后端 404/HTML），而**构建与单测全绿**。
      '/app': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        bypass: (req) => (isSpaOwnedPath(req.url ?? '') ? req.url : undefined)
      }
    }
  }
})
