<!--
  主框架页（阶段 2 第 2 个入口页）

  契约来源（逐条对齐旧实现 `_view/index/index.html` + `index.js`，revision 锁定；
  证据 `docs/.local/baseline/evidence/frame-contract.json`）：
  · 取菜单：`POST /api/home/menu`，空体；`ret.state === 'ok'` ⇒ `menus = ret.menus`、`cats = ret.cats`
  · **cats 过滤规则**（旧实现原文）：先收集 `pids = { menus[].parent_id | parent_id !== null && parent_id >= 0 }`，
    再 `cats = ret.cats.filter(c => pids.has(c.id))` —— 只显示"确实有子菜单"的目录
  · **握手事件**：加载完成（`nextTick` 之后）派发 `document.dispatchEvent(new Event('EovaMenuNextTick'))`
    —— 子组件初始化依赖它；**改名/漏发都会让子组件不再初始化**
  · 失败文案：业务失败 ⇒ `加载菜单错误, 请稍候再试`；网络异常 ⇒ `请求异常`
  · 菜单归属：`m.parent_id == c.id && m.type != 'dir'` 的菜单挂到目录 `c` 下（模板判定，本页沿用）

  尚未迁移（登记）：Tab 的增删/激活/持久化（`index.js` 其余 200+ 行）、菜单图标与排序、
  与 `/eova/ui/meta/eova.meta.js` 的交互、`POST /eova/admin/reLogin`（虚拟用户还原）暂未接。
-->
<template>
  <div class="eova-home">
    <aside class="eova-home_menu">
      <div v-for="c in cats" :key="c.id" v-show="!c.is_hide">
        <div class="eova-home_cat">{{ c.name }}</div>
        <div
          v-for="m in menus"
          v-show="m.parent_id == c.id && m.type != 'dir'"
          :key="m.id"
          class="eova-home_item"
        >
          {{ m.name }}
        </div>
      </div>
    </aside>
    <main class="eova-home_tabs">
      <div v-for="m in tabMenus" :key="m.id" class="eova-home_tab">{{ m.name }}</div>
    </main>
    <span style="color: red">{{ msg }}</span>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import axios from 'axios'
import { filterCats } from '@/utils/menu'

/** 目录（过滤后） */
const cats = ref<Array<Record<string, any>>>([])
/** 菜单 */
const menus = ref<Array<Record<string, any>>>([])
/** 已打开页签（Tab 行为未迁移，先留空数组结构） */
const tabMenus = ref<Array<Record<string, any>>>([])
/** 提示文案 */
const msg = ref('')

/**
 * 加载菜单（逐条对齐旧 index.js 的 init 行为）
 */
async function init(): Promise<void> {
  try {
    const res = await axios.post('/api/home/menu', {})
    const ret = res.data
    if (ret && ret.state === 'ok') {
      menus.value = ret.menus
      cats.value = filterCats(ret.menus, ret.cats)
      // 旧实现：等 DOM 更新后再派发握手事件（子组件依赖它）
      await nextTick()
      document.dispatchEvent(new Event('EovaMenuNextTick'))
    } else {
      msg.value = '加载菜单错误, 请稍候再试'
    }
  } catch (e) {
    msg.value = '请求异常'
  }
}

onMounted(init)

defineExpose({ cats, menus, tabMenus, msg, init })
</script>
