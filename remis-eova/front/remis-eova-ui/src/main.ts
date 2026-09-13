import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
// ★ 第 303 轮：SPA 底座样式 —— 复现旧栈"页面级 CSS 只作用于本文档"的作用域（修登录页 body 规则泄漏）
import './compat/page-document-scope.css'
import router from './router'
import { loadLegacyRuntime } from './compat/legacy-runtime'
import { installAuthGuard } from './router/auth-guard'
import { getEovaUI } from './compat/eova-runtime'
import { installWindowUrls } from './compat/ui-urls'
import { loadUiConf } from './compat/ui-conf'
import { createBootstrapFetcher, setDefaultBootstrapFetcher } from './compat/page-bootstrap-fetcher'
import { consumeEmbedEntry } from './compat/embed-entry'

/**
 * 应用启动（阶段 2 + 阶段 3 嵌入态入口）
 *
 * 顺序不可换：
 *  ⓪ **消费平台嵌入参数**（契约 1/2：读 `_accessToken`/`_tenantId`/`_sourceSystemCode`，
 *     并把**敏感参数**从 URL 清掉）—— 平台要求在"入口（router/permission 之前）"完成，
 *     故它必须是第一步；晚于路由装配会让带 token 的 URL 先进入 router/history；
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
  // ⓪ 嵌入态入口：读取平台参数并清理 URL 上的敏感参数（DES-006 契约 1/2）。
  //    非嵌入场景（绝大多数）走 skip 分支：URL 一个字符都不动。
  consumeEmbedEntry()

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
  // 未登录 ⇒ 去登录页（旧栈由服务端 LoginInterceptor 302；SPA 必须自己判）。
  // 判定用【探针端点】而不是读 Cookie —— 该 Cookie 是 HttpOnly（r268 实测），JS 读不到。
  //
  // ★★ 阶段 3 嵌入态的口径（拿哥裁定，第 300 轮）—— **改动守卫前必读**：
  //   平台 iframe（形态 A）会带 `_accessToken`，但**平台用户与 EovaMeta 账号的对应关系暂不处理**
  //   ⇒ 嵌入态**一律走 EovaMeta 自己的账户体系**（即：未登录时照常落到 `/user/login`，
  //   用户在这个页面用 EovaMeta 账号登录）。**这是既定行为，不是缺陷。**
  //   ⇒ 平台的 `design-iframe-silent-auth.md`（DES-007）§3.3 那条"**不得在 iframe 内跳登录页**"
  //     **本轮明确不采纳**：它属于"平台换票"目标态（平台侧 `LC-012` 未做），且**在换票落地前
  //     关掉登录页会把嵌入态唯一可用的入口掐掉**。
  //   ⇒ 待迁移工作全部完成后，再单独立项处理身份映射（见 `docs/DES-009-R1-embed-session-exchange.md`
  //     §5：A 服务账号 / B 用户级映射 / C 平台换票 三选一，均未定）。
  //   ⇒ **不要**为了让 iframe 内不出现登录页而给守卫加"嵌入态特判"分支 —— 那会同时破坏
  //     本节口径与 S5 已有的行为等价判据（未登录 `/` ⇒ `/user/login`）。
  installAuthGuard(router)
  // ★ 未登录守卫【暂不装配】（r268 实测撤回）：
  //   `auth-guard.ts` 原按"读 Cookie `eovasid`"判定，但**实测证伪** —— 新旧栈该 Cookie 都是
  //   `Path=/; HttpOnly`（`curl -i` 对 9091/8080/9090 三个入口都验过），`document.cookie` 永远读不到
  //   ⇒ 装配后会把**已登录用户**一路锁在 /user/login（真浏览器实测：登录 200 + {"state":"ok"} 后
  //   重新导航 `/`，pathname 仍是 /user/login）。故撤回装配，改用**探针端点**（401 ⇒ 未登录）实现，
  //   见 DES-005 §14.6；模块与判据保留（它们是探针版实现的基础）。
  app.mount('#app')
}

void bootstrap()
