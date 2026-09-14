/**
 * `me.vue.app` / `me.vue.component` 的**登记**（第 126 轮）—— 旧"自定义模版/自定义组件"机制
 *
 * ## 本模块的职责：**只登记**旧配置（键名与定义的事实源）
 *
 * 旧栈的页面末尾都调 `me.vue.mount(app, code)`（如 `template/table/index.js:318`
 * 的 `` `${uzoo.page.template}_${uzoo.page.code}` ``）；`me.vue.mount` 会按该 code 查
 * **自定义 app 定义**，命中则加载定义里的 `template`（一个 `.vue` 文件）+ `script` + `components`
 * 并**叠加挂载**（r318 CDP 实测：标准页面照常渲染，不是替换）。定义由**冻结资产**
 * `_eova/assets/eova.vue.config.js`（每页经 `_eova/include.html` 加载）注册：
 *
 * ```js
 * vue.component('SwordComing', '/_component/SwordComing.js')     // 全局组件（6 个）
 * vue.app('table_meta_hotel', { template: '/hotel/app.vue', script: '/hotel/app.js', components: ['SwordComing'] })
 * vue.app('meta_hotel',       { template: '/hotel/app.vue',   script: '/hotel/app.js' })
 * vue.app('meta_product',     { template: '/product/app.vue', script: '/product/app.js', components: [...] })
 * ```
 *
 * ★ **本模块不含任何实现**：实现落在 `src/views/custom/registry.ts`（构建期 SFC + 钩子模块）。
 * 本文件仍是**键名与定义的事实源**，并由 `__tests__/custom-apps.spec.ts` 与冻结配置**双向核对**。
 *
 * ## 实现状态（r327 起：三项全部实现）
 *
 * | 命中项 | 影响的页面 | 状态 |
 * |---|---|---|
 * | `table_meta_hotel` | 列表页（`template=table` + 菜单码 `meta_hotel`） | ✅ r319 切片 A：`SwordComing.vue` + `registry.ts` + `TemplateTable.vue` 叠加挂载 |
 * | `meta_hotel` | **表单页**（键是**元对象编码**） | ✅ r327 切片 B：`MetaHotelFormApp.vue`（恒隐藏的 `/hotel/app.vue`）+ `metaHotelApp.ts`（省→市→区联动 + 四支通知）+ 三个表单页的叠加挂载与钩子安装 |
 * | `meta_product` | **表单页** | ✅ r327 切片 B：`ProductApp.vue`（旧模版的活代码只有 `<br>{{ data }}`）+ `metaProductApp.ts`（`setup` 返回值，**无** `onReady` —— 旧文件事实） |
 *
 * ## ★★ 口径沿革（改动本模块前必读）
 *
 * | 轮次 | 口径 | 现状 |
 * |---|---|---|
 * | r126 | 只登记不实现（要动全应用 Vue 入口或改冻结资产，代价高） | 历史 |
 * | r300（拿哥） | 「暂不实现，登记为已知不等价」（迁移完成后单独处理） | **已被 r327 取代** |
 * | **r327（拿哥）** | **切片 B 开工**：把表单页自定义 app 按构建期 SFC + 钩子模块补齐 | ★ **当前有效** |
 *
 * ⇒ 两条**成本约束**仍然有效（切片 A/B 都未触碰）：
 *   ① **不许**把 `vue` 别名指向 `vue/dist/vue.esm-bundler.js`（引入运行时编译器 ⇒ 全应用 +≈40KB、
 *      CSP/eval 约束）；
 *   ② **不许**修改 `src/legacy/**` 任何一个字节（冻结纪律；只新建文件）。
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
