import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

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
    // 阶段 2 口径 ②：旧 URL（/eova、/meta、/widget）不得加前缀，代理时原样透传
    proxy: {
      '/eova': { target: 'http://127.0.0.1:8080', changeOrigin: true },
      '/meta': { target: 'http://127.0.0.1:8080', changeOrigin: true },
      '/widget': { target: 'http://127.0.0.1:8080', changeOrigin: true }
    }
  }
})
