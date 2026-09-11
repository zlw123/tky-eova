import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { loadLegacyRuntime } from './compat/legacy-runtime'
import { getEovaUI } from './compat/eova-runtime'
import { installWindowUrls } from './compat/ui-urls'
import { loadUiConf } from './compat/ui-conf'
import { createBootstrapFetcher, setDefaultBootstrapFetcher } from './compat/page-bootstrap-fetcher'

/**
 * 应用启动（阶段 2）
 *
 * 顺序不可换：
 *  ① 装配 legacy 运行时（注入全局 Vue/axios → 按序加载 EovaTools/LayuiVue/EovaUI 三个制品）；
 *  ② 挂回页面级 URL 表（`window.urls`，模板页直接读它）；
 *  ③ 装配 `me.conf`（来源见 DES-003；缺来源只告警不阻塞）；
 *  ④ `app.use(EovaUI)` 注册 24 个 `<ev-*>` 组件；
 *  ⑤ 挂 Pinia / 路由并 mount。
 *
 * 为什么必须在 mount 之前：迁移后的页面模板里直接写 `<ev-input>`、`<ev-table>` 等，
 * 组件未注册时 Vue 只会渲染成空的自定义元素（不报错），症状是"页面没内容"而极难定位。
 * 装配失败会在此**响亮抛出**（见 legacy-runtime.ts 的纪律），不静默继续。
 */
async function bootstrap(): Promise<void> {
  await loadLegacyRuntime()
  installWindowUrls()
  await loadUiConf()

  // 页面引导数据的默认来源（DES-004 §3.1：POST /api/page/bootstrap）。
  // 端点未落地时该请求会失败 ⇒ 各页**响亮告警并降级为"仅 URL 参数"**（不静默假装成功）。
  setDefaultBootstrapFetcher(createBootstrapFetcher())

  const app = createApp(App)
  app.use(getEovaUI() as Parameters<typeof app.use>[0])
  app.use(createPinia())
  app.use(router)
  app.mount('#app')
}

void bootstrap()
