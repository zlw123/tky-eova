/**
 * **自定义 app 的构建期注册表**（r319 切片 A 建立；r327 切片 B 补齐表单页那一半）。
 *
 * ## 背景（旧机制，逐字取证见 `compat/custom-apps.ts`）
 *
 * 旧栈的自定义 app 是**运行时模板**：`_eova/assets/eova.vue.config.js` 注册
 * `vue.app(key, {template:'/hotel/app.vue', script:'/hotel/app.js', components:['SwordComing']})`，
 * 由全局 Vue 的**完整版编译器**在浏览器里编译，再把模板**叠加挂载**到页面上
 * （r318 用 CDP 实测：列表页 `/app/meta_hotel` 标准表格仍在 + `.sword-go` 也在场 ⇒ **叠加，不是替换**）。
 *
 * SPA 不能运行时编译（runtime-only 打包版 `vue`）⇒ 按旧文件**逐字 port 成构建期 SFC / 模块**：
 *
 * | 注册键 | 旧 template / script | 构建期实现 | 挂载点（宿主） |
 * |---|---|---|---|
 * | `table_meta_hotel`（列表页） | `/hotel/app.vue` + `/hotel/app.js` | `SwordComing.vue` + `metaHotelApp.ts` | `views/template/TemplateTable.vue` |
 * | `meta_hotel`（表单页） | 同上（同一份资产） | `MetaHotelFormApp.vue` + `metaHotelApp.ts` | `views/template/form/{FormAdd,FormUpdate,FormDetail}.vue` |
 * | `meta_product`（表单页） | `/product/app.vue` + `/product/app.js` | `ProductApp.vue` + `metaProductApp.ts` | 同上 |
 *
 * ## 两条口径（改动本文件前必读）
 *
 * 1. **键名口径**（逐字取自旧代码）：列表/树表 = `` `${template}_${menuCode}` ``（`template/table/index.js:318`），
 *    表单 = **元对象编码**（`template/form/{add,update,detail}/index.js` 的 `me.vue.mount(app, uzoo.page.code)`）。
 *    键的**事实源**仍是 `compat/custom-apps.ts`（它由判据与冻结配置双向核对），本表只补"实现"这一半。
 * 2. **不同步就红**：`__tests__/registry.spec.ts` 断言"`CUSTOM_APPS` 里的每个键，要么在本表里有实现，
 *    要么在 `UNIMPLEMENTED_KEYS` 里显式登记" ⇒ 缺口表**不会退化成永久豁免**（r316 的教训）。
 *
 * ## 已声明适配
 *
 * · 运行时模板 → 构建期 SFC：**不改** `src/legacy/**` 任何一个字节（冻结纪律），只新建文件；
 * · `me.vue.mount(app, key)` 的"叠加挂载" → 宿主页面按 key 渲染 `<component :is>`；
 * · 脚本钩子（`uzoo.vue.setup`/`onReady`）→ `installCustomAppHooks(key)`（经 `compat/eova-ext.ts` 的
 *   `setUzooHooks` 安装；旧栈由脚本自己赋值、SPA 由宿主安装）。
 */
import type { Component } from 'vue'
import SwordComing from './SwordComing.vue'
import MetaHotelFormApp from './MetaHotelFormApp.vue'
import ProductApp from './ProductApp.vue'
import { metaHotelAppHooks } from './metaHotelApp'
import { metaProductAppHooks } from './metaProductApp'
import { setUzooHooks, type UzooVueHook } from '@/compat/eova-ext'

/**
 * 自定义 app 的**脚本钩子**（旧 `uzoo.vue.setup` / `uzoo.vue.onReady`）。
 *
 * 形状与旧脚本一致：`setup()` 的返回值由页面摊进 `uzoo.app`；`onReady(fields)` 在表单 DOM 完成后调用。
 */
export interface CustomAppHooks {
  /** 旧 `uzoo.vue.setup = () => ({...})` */
  setup?: () => Record<string, unknown>
  /** 旧 `uzoo.vue.onReady = (fields) => {...}` */
  onReady?: (fields: unknown) => void
}

/** 注册键 → **模版组件**（叠加挂载用；未实现时 undefined ⇒ 调用方走标准模版） */
export const CUSTOM_APP_COMPONENTS: Readonly<Record<string, Component>> = {
  table_meta_hotel: SwordComing,
  meta_hotel: MetaHotelFormApp,
  meta_product: ProductApp
}

/** 注册键 → **脚本钩子**（旧每键的 `script`；同一份脚本可挂多个键，与旧配置一致） */
export const CUSTOM_APP_HOOKS: Readonly<Record<string, CustomAppHooks>> = {
  // 旧 `vue.app('table_meta_hotel', {…, script:'/hotel/app.js'})`
  table_meta_hotel: metaHotelAppHooks,
  // 旧 `vue.app('meta_hotel', {template:'/hotel/app.vue', script:'/hotel/app.js'})`
  meta_hotel: metaHotelAppHooks,
  // 旧 `vue.app('meta_product', {template:'/product/app.vue', script:'/product/app.js'})`
  meta_product: metaProductAppHooks
}

/**
 * **尚未实现**的注册键（必须与 `compat/custom-apps.ts` 的 `CUSTOM_APPS` 键集合构成划分）。
 *
 * r327 起为**空**：三个键（列表页 1 + 表单页 2）都已实现。留这个常量是为了让"缺口"仍是
 * **可枚举、可判据**的：将来配置新增一条定制，`registry.spec.ts` 会因"键没实现又没登记"而红。
 */
export const UNIMPLEMENTED_KEYS: readonly string[] = []

/**
 * 取某个注册键对应的**模版组件**。
 *
 * @param key 注册键（列表页 `template_menuCode`；表单页为元对象编码）
 * @returns 组件；未实现时 undefined（调用方据此走标准模版）
 */
export function customAppComponent(key: string): Component | undefined {
  return CUSTOM_APP_COMPONENTS[key]
}

/**
 * 取某个注册键对应的**脚本钩子**。
 *
 * @param key 注册键
 * @returns 钩子；未登记时 undefined
 */
export function customAppHooks(key: string): CustomAppHooks | undefined {
  return CUSTOM_APP_HOOKS[key]
}

/**
 * **本机制上一次装过**的钩子名（用于精确回退：只撤自己装的，不动外部注册的钩子）。
 *
 * 为什么需要它：SPA 是单页常驻上下文，页面切换时若不撤，上一个页面的钩子会残留在下一个页面上
 * （例如 `meta_product` 的钩子跑到别的元对象表单上）；但"清空所有已知钩子名"会把
 * **外部注册**的钩子（真实部署里别的脚本、判据里手工注册的）一起清掉 —— 实测踩到过
 * （`TemplateTable.spec` ③/⑭ 双红）。故只撤自己装过的那几个名字。
 */
let installedHookNames: readonly UzooVueHook[] = []

/**
 * 把某个注册键的钩子**安装到 `uzoo.vue`**（旧脚本自赋值的等价形态）。
 *
 * ★ **必须传"当前页面命中的键"**：未命中的键 ⇒ 本函数会把**上一次由本机制装过**的钩子撤掉
 * （外部注册的钩子不受影响），从而不会把上一个页面的钩子带过来。
 *
 * @param key 注册键（未命中 ⇒ 回到"未注册"态）
 */
export function installCustomAppHooks(key: string): void {
  const hooks = (customAppHooks(key) ?? {}) as Partial<Record<UzooVueHook, unknown>>
  setUzooHooks(hooks, { remove: installedHookNames })
  installedHookNames = Object.keys(hooks) as UzooVueHook[]
}
