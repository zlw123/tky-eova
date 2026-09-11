<!--
  超管功能面板（旧 `_view/_block/admin.html` 的等价）

  契约来源：`eova/_view/_block/admin.html`（52 行，被 `template/table/index.html:77`、
  `template/tree/index.html`、`template/tree_table/index.html` 以
  `#include("/eova/_view/_block/admin.html", type="list")` 共用）。

  结构（逐条对齐旧实现）
  · `#if(loginUser.isAdmin)` 整块门控 ⇒ 本组件 `v-if="isAdmin"`（服务端插值改为 prop）
  · `<div class="eova-admins"><div class="eova-tools_box">` 包住 3 个 `ev-popup` + 1 个关闭按钮
  · 三个气泡：元数据（`eova-icon-senior`）/ 模版（`eova-icon-template`）/ 按钮（`eova-icon-app`），
    都是 `trigger="hover" placement="bottom"`，内容槽里是
    `.eova-select-content > ul.eova-select_items > li`
  · ★ `title` 的位置在旧实现里**不统一**（第 1 个在 `<button>` 上，第 2/3 个在 `<i>` 上）——
    原样保留，不得"顺手统一"
  · 8 个 `<li>`（第 4 个气泡内那条被注释掉的"查看按钮"**不迁移**，是既有注释）

  ★ 为什么 `onclick` 用**字符串行内属性**而不是 `@click`（第 117 轮取证）
  · 旧栈这 8 条 li 是行内 `onclick="onMetaField()"`，函数由**冻结脚本**
    `eova/_view/template/eova.template.js`（139 行，逐字节复制在 `src/legacy/…`，已在
    `index.html` 以 `<script src>` 装配）里的**顶层 `const`** 提供：
    `onMetaObject` / `onMetaField` / `onMenu` / `onButtonAdd` / `onMetaFieldDiy` / `onReorderList` /
    `onReorderForm`（`eova.template.js:30/41/48/63/77/84/97`）。顶层 `const` 落在**全局词法环境**，
    行内事件处理器在全局作用域求值 ⇒ 能解析到它们。
  · 改写成 `@click` 会引入一份**平行实现**（同一个 API 两处维护），且旧标记里同一批函数
    也被 `template/handler` 与 `.js` 按钮脚本复用 ⇒ 保持字符串形态即保持"谁提供函数"这件事不变。
  · Vue 侧能保住它是**有版本依据的**：`@vue/runtime-dom@3.5.42` 的 `shouldSetAsProp()`
    末尾有 `if (isNativeOn(key) && isString(value)) return false`（`isNativeOn` = `on` 后接小写字母，
    故 `onclick` 命中）⇒ 字符串 `onclick` 走 `patchAttr` 落成**真属性**，而不是被赋给
    `el.onclick` 属性（把非函数赋给事件 IDL 属性会被规范判成 null，处理器就**静默丢失**）。
  · ★ 因此"点击能调到函数"这一环在 jsdom 里**不可执行**（行内属性不执行）——判据改为：
    ① 渲染后的 `onclick` 属性文本与旧标记**逐字相同**；
    ② 每个 `名字(实参)` 形态的 `onclick` 都能在**冻结脚本**里找到同名函数且形参够用
       （`__tests__/EovaAdminPanel.spec.ts` 用 `node:vm` 在同一全局里跑冻结脚本后检查）。
    真浏览器里的"点击→处理器执行"标记为 `not executed`。

  已声明适配（非静默改写）
  · 旧 `#include` 带了 `type="list"`，但 `admin.html` 全文**没有**用到 `#(type)`
    （`grep` 取证：无 `#(type)`）⇒ 该 include 参数是**既有死参数**，不迁移。
  · 旧文件里 `loginUser` 由 `AppController#index()` 在渲染期 `set(...)`；分离后走页面引导数据
    （`DES-004`），本组件只声明 `isAdmin` 一个 prop。

  尚未迁移（登记）：`_view/_block/admin_form.html`（表单页用的变体，含 `#(mode)`，独立单元）。
-->
<template>
  <div v-if="isAdmin" class="eova-admins">
    <div class="eova-tools_box">
      <ev-popup trigger="hover" placement="bottom">
        <button class="eova-btn_icon" title="元数据">
          <i class="eova-icon-senior"></i>
        </button>
        <template #content>
          <div class="eova-select-content">
            <ul class="eova-select_items">
              <li onclick="onMetaField()">📝元字段编辑</li>
              <li onclick="onMetaObject()">🏐元对象编辑</li>
              <li onclick="onReorderList()">🔢表格字段排序</li>
              <li onclick="onMetaFieldDiy('query')">✨查询表单配置</li>
              <li onclick="onReorderForm('query')">🔃查询字段排序</li>
            </ul>
          </div>
        </template>
      </ev-popup>
      <ev-popup trigger="hover" placement="bottom">
        <button class="eova-btn_icon">
          <i class="eova-icon-template" title="模版"></i>
        </button>
        <template #content>
          <div class="eova-select-content">
            <ul class="eova-select_items">
              <li onclick="onMenu()">菜单配置</li>
              <li onclick="onMenu()">模版配置</li>
            </ul>
          </div>
        </template>
      </ev-popup>
      <ev-popup trigger="hover" placement="bottom">
        <button class="eova-btn_icon">
          <i class="eova-icon-app" title="按钮"></i>
        </button>
        <template #content>
          <div class="eova-select-content">
            <ul class="eova-select_items">
              <li onclick="onButtonAdd()">✅自定义按钮</li>
            </ul>
          </div>
        </template>
      </ev-popup>
      <button
        class="eova-btn_icon"
        title="暂时关闭"
        onclick="document.querySelectorAll('.eova-admins')[0].remove()"
      >
        <i class="eova-icon-close"></i>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 超管功能面板。
 *
 * 无 setup 逻辑：面板本身不持有状态，8 个动作都由**冻结脚本**的全局函数处理
 * （见文件头"为什么 onclick 用字符串行内属性"）。
 */
defineProps<{
  /** 是否超管（旧 `loginUser.isAdmin`，由页面引导数据给） */
  isAdmin?: boolean
}>()
</script>
