<!--
  EovaUI 组件演示页（第 123 轮）—— 旧 `_view/widget/index.html`(62 行) + `index.js`(7 行) 的等价物

  ## 路由与可达性（取证）

  · **URL `/widget`**：`cn.eova.meta.AppController#widget()`（`demo/src/main/java/cn/eova/meta/AppController.java:21`）
    `render("/_view/widget/index.html")`；该控制器注册在 `EOVA_INDEX`(= `/`) 之下（demo `AppConfig.java:60`）。
  · **种子数据里没有任何菜单指向 `/widget`**（`eova_menu` 的 38 行 code 与 `/widget`、`/main` 均无关）
    ⇒ 它是**开发者直接输 URL 看的演示页**，不在 SPA 的菜单导航里。
  · 同级兄弟页 `_view/theme/index.html`（591 行，URL `/main`）**本轮决定保留后端渲染**：
    它同样只被直接访问，且迁到 SPA 只会让 `/main` 变成"在 iframe 里再嵌一层 SPA"；
    而 r121 已把 `/main` 加进 dev 代理，后端渲染路径在 dev 可用（见 `remaining-entry-pages.json`）。

  ## 旧实现全文（逐条对齐）

  · `head` 里 `#include("/eova/_view/_block/meta.html")` + `base.html`（运行时装配）、
    `<script type="module" src="/_view/widget/index.js">`（★ 内容是**空壳**：`setup()` 返回 `{}`，
    只做 `app.use(EovaUI)` + `app.mount('#app')` ⇒ SPA 侧**无需迁移任何逻辑**）
  · 页级 `<style>`：`body { background-color: var(--eova-color_bg) }`、
    `.main { border: 1px solid #f1f1f1; background-color: #ffffff; padding: 5px; margin: 10px }`、
    `fieldset { margin: 10px }`
  · 结构：`#app > .main > fieldset(legend=进度条) + 7 个 `<div style="width: 50%; margin: 10px">`
    包着的 `<ev-progress>` + `.eova-notes` 提示行`
  · 7 个进度条的属性（**逐字**）：`90/默认进度条`、`80/绿色进度条/#67C23A`、`70/红色进度条/#f56c6c`、
    `60/橙色进度条/#E6A23C`、`60/渐变进度条/#1ea0fc,#67c23a`、`60/细进度条/#67C23A/size=10`、
    `60/粗进度条/#67C23A/size=22`
  · 页面**没有任何数据依赖**（不取数、不用 `uzoo`、不用引导数据）⇒ 迁移后开箱即用

  ## 已声明适配（非静默改写）

  ① **页级样式的作用域**：旧页是**独立文档**，它写的 `body`/`fieldset` 规则只影响自己；
     SPA 里照抄会**污染其它路由**（全局 `fieldset` 会改掉所有页面的字段集样式）。
     故：`.main`/`fieldset` 的声明**逐字保留**但落进 SFC 的 `<style scoped>`；
     `body` 背景改为**挂载时设置、卸载时还原**（等价于"本页是当前文档时的效果"，且不外溢）。
  ② 旧 `<script type="module" src="/_view/widget/index.js">` 是空壳（见上）⇒ 不需要等价物。

  尚未迁移（登记）：`_view/theme/index.html`（`/main`，591 行，决定保留后端渲染）。
-->
<template>
  <div id="app">
    <div class="main">
      <fieldset>
        <legend>进度条</legend>
      </fieldset>
      <div style="width: 50%; margin: 10px">
        <ev-progress :value="90" text="默认进度条"></ev-progress>
      </div>
      <div style="width: 50%; margin: 10px">
        <ev-progress :value="80" text="绿色进度条" color="#67C23A"></ev-progress>
      </div>
      <div style="width: 50%; margin: 10px">
        <ev-progress :value="70" text="红色进度条" color="#f56c6c"></ev-progress>
      </div>
      <div style="width: 50%; margin: 10px">
        <ev-progress :value="60" text="橙色进度条" color="#E6A23C"></ev-progress>
      </div>
      <div style="width: 50%; margin: 10px">
        <ev-progress :value="60" text="渐变进度条" color="#1ea0fc,#67c23a"></ev-progress>
      </div>
      <div style="width: 50%; margin: 10px">
        <ev-progress :value="60" text="细进度条" color="#67C23A" size="10"></ev-progress>
      </div>
      <div style="width: 50%; margin: 10px">
        <ev-progress :value="60" text="粗进度条" color="#67C23A" size="22"></ev-progress>
      </div>

      <div class="eova-notes">💡 逐步补充其他组件Demo, 急用可在会员群里问！</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'

/** 旧页级样式里 `body` 背景的原始值（卸载时还原，避免污染其它路由） */
let prevBodyBackground: string | null = null

onMounted(() => {
  // 旧页 `<style> body { background-color: var(--eova-color_bg); }` 的等价物：
  // 旧页是独立文档 ⇒ 生效范围就是"本页打开期间"；SPA 里用挂载/卸载配对表达同一范围。
  prevBodyBackground = document.body.style.backgroundColor
  document.body.style.backgroundColor = 'var(--eova-color_bg)'
})

onUnmounted(() => {
  document.body.style.backgroundColor = prevBodyBackground ?? ''
})
</script>

<style scoped>
/* 声明逐字取自旧页的 `<style>` 块（作用域见文件头"已声明适配 ①"） */
.main {
  border: 1px solid #f1f1f1;
  background-color: #ffffff;
  padding: 5px;
  margin: 10px;
}

fieldset {
  margin: 10px;
}
</style>
