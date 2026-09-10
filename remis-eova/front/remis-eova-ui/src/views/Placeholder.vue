<script setup lang="ts">
// 占位视图：证明工程可构建可启动。T02 起由真实页面逐个替换。
const gates = [
  '阶段 1（技术栈迁移）未完成前，本工程不接业务页面',
  '阶段 2 / T01 待办：接管旧 URL、挂载 EovaUI、决策 window.urls 处置',
  '阶段 2 / T02 待办：25 个 Enjoy 模板逐页转为 SFC',
  '阶段 2 / T03 待办：66 个手写 JS 模块化'
]
</script>

<template>
  <main style="padding: 32px; font-family: system-ui, sans-serif">
    <h1>remis-eova-ui</h1>
    <p>前端工程骨架已就绪（DES-002-R4 阶段 2 / T01 地基）。</p>
    <ul>
      <li v-for="g in gates" :key="g">{{ g }}</li>
    </ul>
  </main>
</template>
