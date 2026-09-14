<template>
  <!--
    ★ r327（切片 B）：`legacy/product/app.vue` 的**构建期 SFC 等价物**。

    冻结资产 804 字节，但**活代码只有一行**：`<br>{{ data }}`（其余全是注释：`my-demo`/`input-num`/
    `input-nums`/`rate`/`eova-tags` 的用法示例、`{{ confs.web_cdn }}`、`{{ txt }}`/`{{ num }}` 等
    都被注释掉了 —— 逐字见冻结文件）。故本 SFC 也只有那一行，**不发明**被注释掉的控件。

    `data`：表单数据对象。旧栈由模板所在 app 的作用域解析（表单页 setup 里
    `data = reactive({})`，经 `<ev-form v-model="data">` 填充，并挂在 `uzoo.app.data`）
    ⇒ SPA 由表单页以 prop 传入**同一个**响应式对象本体，`{{ data }}` 的渲染结果
    （Vue 的 `toDisplayString`：对象走 JSON）与旧栈一致。
  -->
  <br />{{ data }}
</template>

<script setup lang="ts">
/**
 * 表单数据（旧模板里的 `data`；由表单页传 `uzoo.app.data` 本体）。
 *
 * 可选：旧栈里 `data` 一定存在（页面 setup 里必然定义），这里声明为可选只是为了让
 * **未挂载表单数据的场景**不炸；渲染语义与旧栈一致（`undefined` ⇒ 输出空串）。
 */
defineProps<{ data?: Record<string, unknown> }>()
</script>
