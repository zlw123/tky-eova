<!--
  超管功能面板（**表单页变体**）—— 旧 `_view/_block/admin_form.html`（26 行）的等价物

  契约来源：`eova/_view/_block/admin_form.html`，被
  `template/form/add/index.html:26`、`update/index.html:26`、`detail/index.html:27` 以
  `#include("/eova/_view/_block/admin_form.html", mode="create|update|read")` 共用。

  结构（逐条对齐旧实现）
  · `#if(loginUser.isAdmin)` 整块门控 ⇒ 本组件 `v-if="isAdmin"`（服务端插值改为 prop）
  · `<div class="eova-admins"><div class="eova-tools_box">` 包住 1 个 `ev-popup` + 2 个普通按钮
  · `ev-popup`：`trigger="hover" placement="bottom"`，触发体是
    `<button class="eova-btn_icon" title="开发设置"><i class="eova-icon-set"></i></button>`，
    内容槽里 4 条 `li`（**顺序即提示顺序**）：
    ① `📝元字段配置` → `onMetaField()`
    ② `✨表单字段配置` → `onMetaFieldDiy('<mode>')`，**并带 `title="<mode>"`**
    ③ `🔃表单字段排序` → `onReorderForm('<mode>')`
    ④ `🎦窗口宽高保存` → `onLayerSize('<mode>')`
  · 两个普通按钮：`刷新`（`onclick="location.reload()"`）、`关闭`
    （`onclick="document.querySelectorAll('.eova-admins')[0].remove()"`）

  ★ 与同族的 `_block/admin.html`（`EovaAdminPanel.vue`）的差别（**不得合并**，是两份不同的合同）
  · 面板条目从 8 条变 4 条，且**三条带 `mode` 实参**（旧栈由 `#include` 的 `mode=` 参数插值）；
  · 气泡触发按钮多了 `title="开发设置"`；`admin.html` 的按钮上没有 title。

  ★ 为什么 `onclick` 用**字符串属性**而不是 `@click`（与 `EovaAdminPanel` 同口径，已实测）
  · 这 4 条 `li` 的行内 `onclick` 调用的函数由**冻结脚本**
    `eova/_view/template/eova.template.js` 的**顶层 `const`** 提供
    （`onMetaField:41`、`onMetaFieldDiy:77`、`onReorderForm:97`、`onLayerSize:111`）；
    顶层 `const` 落在全局词法环境，行内事件处理器在全局作用域求值 ⇒ 能解析到它们。
  · 改写成 `@click` 会引入一份**平行实现**（同一个 API 两处维护）。
  · Vue 侧能保住它是**有版本依据的**（本轮按 `@vue/runtime-dom@3.5.42` 源码逐行核对，
    不是照抄注释）：`patchProp` 先判 `isOn(key)`（`/^on[^a-z]/`）⇒ `onclick` **不命中**，
    落到 `shouldSetAsProp`，其末段是
    `if (isNativeOn(key) && isString(value)) return false`（`:794`），
    而 `isNativeOn = key.charCodeAt(2) ∈ (96,123)`（`:730-731`，即 `on` 后接**小写字母**）
    ⇒ `onclick` 命中 ⇒ 走 `patchAttr` 落成**真属性**。
    （若落成 `el.onclick = '字符串'`，按事件 IDL 属性规范会被判成 null，处理器**静默丢失**。）
  · ★ 因此"点击能调到函数"这一环在 jsdom 里**不可执行**（行内属性不执行）——判据改为：
    ① 渲染后的 `onclick` 属性文本与旧标记**逐字相同**（含 `mode` 实参）；
    ② 每个 `名字(实参)` 形态的 `onclick` 都能在**冻结脚本**里找到同名函数且形参够用。
    真浏览器里的"点击→处理器执行"标记为 `not executed`。

  已声明适配（非静默改写）
  · 旧 `#include` 的 `mode=` 参数 ⇒ 本组件的一个 prop（`mode`），只用于**拼那三条 onclick 文本**。
  · 旧 `loginUser` 由 `LoginInterceptor#:123` 每请求 `ctrl.set(LoginService.USER, user)` 提供；
    分离后属页面引导数据（DES-004），本组件只声明 `isAdmin` 一个 prop。

  尚未迁移（登记）：`_view/_block/admin.html` 的**列表页**变体已迁（`EovaAdminPanel.vue`）；
  本文件是表单页变体，随 S6 的三个表单页一起迁。
-->
<template>
  <div v-if="isAdmin" class="eova-admins">
    <div class="eova-tools_box">
      <ev-popup trigger="hover" placement="bottom">
        <button class="eova-btn_icon" title="开发设置">
          <i class="eova-icon-set"></i>
        </button>
        <template #content>
          <div class="eova-select-content">
            <ul class="eova-select_items">
              <li :onclick="onMetaFieldExpr">📝元字段配置</li>
              <li :onclick="onMetaFieldDiyExpr" :title="mode">✨表单字段配置</li>
              <li :onclick="onReorderFormExpr">🔃表单字段排序</li>
              <li :onclick="onLayerSizeExpr">🎦窗口宽高保存</li>
            </ul>
          </div>
        </template>
      </ev-popup>
      <button class="eova-btn_icon" title="刷新" onclick="location.reload()">
        <i class="eova-icon-refresh"></i>
      </button>
      <button
        class="eova-btn_icon"
        title="关闭"
        onclick="document.querySelectorAll('.eova-admins')[0].remove()"
      >
        <i class="eova-icon-close"></i>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/** 组件 props（旧 `#if(loginUser.isAdmin)` 与 `#include(..., mode=…)` 的等价物） */
const props = defineProps<{
  /** 是否超管（旧 `loginUser.isAdmin`，由页面引导数据给） */
  isAdmin?: boolean
  /**
   * 表单模式（旧 `#include("/eova/_view/_block/admin_form.html", mode="…")` 的实参）。
   *
   * 逐字取自三个旧模板：`add` ⇒ `create`、`update` ⇒ `update`、`detail` ⇒ `read`。
   */
  mode: string
}>()

/**
 * 5 条 onclick 文本（`mode` 由旧 `#(mode)` 插值而来）
 *
 * ★ 用**计算属性**而不是在模板里拼字符串：判据要能独立读出这 5 条文本，
 * 且"用错了 mode"必须可观测（旧栈它就是被 `#include` 的实参决定的）。
 */
const onMetaFieldExpr = computed(() => 'onMetaField()')
const onMetaFieldDiyExpr = computed(() => `onMetaFieldDiy('${props.mode}')`)
const onReorderFormExpr = computed(() => `onReorderForm('${props.mode}')`)
const onLayerSizeExpr = computed(() => `onLayerSize('${props.mode}')`)

defineExpose({ onMetaFieldExpr, onMetaFieldDiyExpr, onReorderFormExpr, onLayerSizeExpr })
</script>
