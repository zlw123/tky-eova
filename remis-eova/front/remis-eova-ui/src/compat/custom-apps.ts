/**
 * `me.vue.app` / `me.vue.component` 的**登记**（第 126 轮）—— 旧"自定义模版/自定义组件"机制
 *
 * ## ⚠️ 本模块只**登记**，尚未实现加载（原因见下）
 *
 * 旧栈的页面末尾都调 `me.vue.mount(app, code)`（如 `template/table/index.js:318`
 * 的 `` `${uzoo.page.template}_${uzoo.page.code}` ``）；`me.vue.mount` 会按该 code 查
 * **自定义 app 定义**，命中则加载定义里的 `template`（一个 `.vue` 文件）+ `script` + `components`，
 * **用自定义模版替换页面的 in-DOM 模版**后再挂载。定义由**冻结资产**
 * `_eova/assets/eova.vue.config.js`（每页经 `_eova/include.html` 加载）注册：
 *
 * ```js
 * vue.component('SwordComing', '/_component/SwordComing.js')     // 全局组件（6 个）
 * vue.app('table_meta_hotel', { template: '/hotel/app.vue', script: '/hotel/app.js', components: ['SwordComing'] })
 * vue.app('meta_hotel',       { template: '/hotel/app.vue',   script: '/hotel/app.js' })
 * vue.app('meta_product',     { template: '/product/app.vue', script: '/product/app.js', components: [...] })
 * ```
 *
 * ⇒ 对菜单 `meta_hotel`（种子数据里 `template='table'`，码 `table_meta_hotel` **命中**），
 * 旧栈渲染的**整页模版**其实是 `hotel/app.vue` —— 内容只有一行
 * `<sword-coming ref="link" v-model="currentRow" v-model:show="showLinking"></sword-coming>`
 * （绑定的 `currentRow`/`showLinking` 正是页面 setup 暴露的那两个 ref）。
 *
 * ## ★ 为什么 SPA 侧不能"顺手实现"
 *
 * `_component/*.js` 是 **ES 模块 + 字符串模版**：
 *
 * ```js
 * const {defineComponent, computed, ref} = Vue     // ← 用的是**全局 Vue**
 * export default defineComponent({ template: `<div>…{{ modelValue }}…</div>`, … })
 * ```
 *
 * 字符串模版需要 **Vue 运行时编译器**；而本工程为了避开"两个 Vue 实例"（R66）用的是
 * **runtime-only 的打包版 `vue`** ⇒ 直接加载这些组件会**渲染为空**（且不报错）。
 * 两个选项各有代价：
 *
 * | 选项 | 做法 | 代价 |
 * |---|---|---|
 * | **A** | 把 `vue` 别名指向 `vue/dist/vue.esm-bundler.js`（runtime+compiler，**仍是同一个实例**） | 全应用包体 +≈40KB；运行时编译模板（CSP/eval 相关约束） |
 * | **B** | 把 6 个组件与 3 个 `.vue` 模版改写成构建期 SFC | **必须改冻结资产**（`src/legacy/**` 只可 import，不得修改）⇒ 违反冻结纪律 |
 *
 * ⇒ 这是一条**待用户口径**（要动全应用的 Vue 入口），故本轮**只登记不实现**，
 * 并用判据把"登记是否完整"钉住：`__tests__/custom-apps.spec.ts` 会**解析冻结配置**
 * 与**扫描冻结资产**，双向核对下表。
 *
 * ## 影响面（按 code 归属，判据可推）
 *
 * | 命中项 | 影响的页面 | 当前状态 |
 * |---|---|---|
 * | `table_meta_hotel` | 列表页（`template=table` + 菜单码 `meta_hotel`） | **未实现**（受影响：种子数据里确有该菜单） |
 * | `meta_hotel` / `meta_product` | **表单页**（自定义表单，键是**元对象编码**） | **未实现** ⇒ 对这两个元对象，SPA 渲染的是**标准表单**，与旧栈**不等价**（旧栈换成 `/product/app.vue`） |
 *
 * ## ★★ 口径裁定（拿哥，第 300 轮）—— **改动本模块前必读**
 *
 * > 「暂不实现，登记为已知不等价」（迁移工作全部完成后再单独处理。）
 *
 * ⇒ 本模块**继续只登记、不实现**；`meta_hotel`/`meta_product` 的表单页渲染**标准表单**，
 *   这处与旧栈的**不等价是既定状态，不是缺陷**。
 * ⇒ **不要**为了"补齐等价"去顺手做下面任一件事（两者都超出本条口径，且各有硬代价）：
 *   ① 把 `vue` 别名指向 `vue/dist/vue.esm-bundler.js`（runtime+compiler）—— 属**动全应用 Vue 入口**，
 *      全应用包体 +≈40KB，且引入运行时编译（CSP/eval 相关约束）；
 *   ② 把 3 个 `.vue` 模版（`/hotel/app.vue`、`/product/app.vue`）改写成构建期 SFC ——
 *      那是**修改冻结资产**（`src/legacy/**` 只可引用、不得修改），违反冻结纪律。
 * ⇒ 该口径写在此处（而非只写治理文档）是因为**治理文档按项目规则不提交**，
 *   只有代码能把"这处不等价是有意为之"留在仓库里。
 */

/** 自定义组件登记（名字 → 冻结资产路径），逐字取自 `eova.vue.config.js:10-15` */
export const CUSTOM_COMPONENTS: Readonly<Record<string, string>> = {
  MyDemo: '/_component/MyDemo.js',
  SwordComing: '/_component/SwordComing.js',
  InputNum: '/_component/InputNum.js',
  InputNums: '/_component/InputNums.js',
  Rate: '/_component/Rate.js',
  EovaTags: '/_component/EovaTags.js'
}

/** 自定义 app 定义（逐字取自 `eova.vue.config.js:18-42`；注释掉的那条不算） */
export interface CustomAppDef {
  /** 自定义模版（`.vue`，运行时加载） */
  template: string
  /** 自定义脚本（注册 `uzoo.vue.setup`/`uzoo.vue.onReady` 等钩子） */
  script: string
  /** 该定义依赖的自定义组件名（取自 `CUSTOM_COMPONENTS`） */
  components?: string[]
}

/** 自定义 app 登记（code → 定义） */
export const CUSTOM_APPS: Readonly<Record<string, CustomAppDef>> = {
  table_meta_hotel: {
    template: '/hotel/app.vue',
    script: '/hotel/app.js',
    components: ['SwordComing']
  },
  meta_hotel: { template: '/hotel/app.vue', script: '/hotel/app.js' },
  meta_product: {
    template: '/product/app.vue',
    script: '/product/app.js',
    components: ['MyDemo', 'InputNum', 'InputNums', 'EovaTags']
  }
}

/**
 * 列表/树表模版的自定义 app 键（旧 `` `${uzoo.page.template}_${uzoo.page.code}` ``）。
 *
 * 取证：`template/table/index.js:318`、`template/tree/index.js:292`、`template/tree_table/index.js:212`。
 *
 * @param template 模版名（`table`/`tree`/`tree_table`）
 * @param menuCode 菜单编码
 * @returns 自定义 app 键
 */
export function listCustomAppKey(template: string, menuCode: string): string {
  return `${template}_${menuCode}`
}

/**
 * 表单页的自定义 app 键（旧 `me.vue.mount(app, uzoo.page.code)`）。
 *
 * 取证：`template/form/{add,update,detail}/index.js`（分别 :97/:87/:90）—— 表单页的键就是**元对象编码**。
 *
 * @param objectCode 元对象编码
 * @returns 自定义 app 键
 */
export function formCustomAppKey(objectCode: string): string {
  return objectCode
}

/**
 * 查自定义 app 定义。
 *
 * ★ 返回非空**只表示"旧栈有这条定制"**，不表示 SPA 已实现 —— 实现状态见文件头。
 *
 * @param key 自定义 app 键
 * @returns 定义；无则 undefined
 */
export function findCustomApp(key: string): CustomAppDef | undefined {
  return CUSTOM_APPS[key]
}
