/**
 * **自定义 app 组件的构建期注册表**（r319 · U4 切片 A）。
 *
 * 背景：旧栈的自定义 app 是**运行时模板**（`_eova/assets/eova.vue.config.js` 注册
 * `vue.app(key, {template:'/hotel/app.vue', script:'/hotel/app.js', components:['SwordComing']})`，
 * 由全局 Vue 的完整版编译器在浏览器里编译）。SPA 不能运行时编译 ⇒ 逐文件 port 成**构建期 SFC**，
 * 在这里以「注册键 → 组件」登记；`compat/custom-apps.ts` 仍是**旧栈契约的事实源**（键名与定义逐字保留），
 * 本表只补"实现"这一半。
 *
 * 注册键口径（逐字取自 `compat/custom-apps.ts` 的取证）：
 *   · 列表页：`${template}_${menuCode}`（`template/table/index.js:318` 等）
 *   · 表单页：元对象编码（`template/form/{add,update,detail}/index.js`）
 *
 * 进度（必须与本表同步）：
 *   · ✅ `table_meta_hotel` —— 列表页叠加（本切片）
 *   · ⛔ `meta_hotel` / `meta_product` —— **表单页钩子尚未实现**（切片 B：省→市→区联动 + 表单通知）
 */
import type { Component } from 'vue'
import SwordComing from './SwordComing.vue'

/** 注册键 → 组件（键名与 `compat/custom-apps.ts` 的 `CUSTOM_APPS` 对齐） */
export const CUSTOM_APP_COMPONENTS: Readonly<Record<string, Component>> = {
  table_meta_hotel: SwordComing
}

/**
 * 取某个注册键对应的组件。
 *
 * @param key 注册键（列表页 `template_menuCode`；表单页为元对象编码）
 * @returns 组件；未实现时 undefined（调用方据此走标准模版）
 */
export function customAppComponent(key: string): Component | undefined {
  return CUSTOM_APP_COMPONENTS[key]
}
