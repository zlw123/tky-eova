import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { loadLegacyRuntime } from './compat/legacy-runtime'
import { getEovaUI } from './compat/eova-runtime'

/**
 * 应用启动（阶段 2）
 *
 * 顺序不可换：
 *  ① 装配 legacy 运行时（注入全局 Vue/axios → 按序加载 EovaTools/LayuiVue/EovaUI 三个制品）；
 *  ② `app.use(EovaUI)` 注册 24 个 `<ev-*>` 组件；
 *  ③ 挂 Pinia / 路由并 mount。
 *
 * 为什么必须在 mount 之前：迁移后的页面模板里直接写 `<ev-input>`、`<ev-table>` 等，
 * 组件未注册时 Vue 只会渲染成空的自定义元素（不报错），症状是"页面没内容"而极难定位。
 * 装配失败会在此**响亮抛出**（见 legacy-runtime.ts 的纪律），不静默继续。
 */
async function bootstrap(): Promise<void> {
  await loadLegacyRuntime()

  const app = createApp(App)
  app.use(getEovaUI() as Parameters<typeof app.use>[0])
  app.use(createPinia())
  app.use(router)
  app.mount('#app')
}

void bootstrap()
