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

  Tab 行为（第 97～98 轮）：4 个函数已抽成纯模块 `utils/tab.ts`（含 5 条既有语义与判据），
  本页负责渲染与交互接线：点菜单 → `openTab`（`type=open` 走 `window.open`、不入页签）、
  点页签 → `toTab`、点 × → `closeTab`（旧实现首行的 `event.stopPropagation()` 由 `@click.stop` 承担）、
  「关闭全部」→ `closeAllTab`（只留首页）。

  尚未迁移（登记）：Tab 的**持久化**（旧实现未见落盘/回填逻辑，待核）、菜单图标与排序、
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
          @click="onMenuClick(m)"
        >
          {{ m.name }}
        </div>
      </div>
    </aside>
    <main class="eova-home_tabs">
      <div
        v-for="t in tabMenus"
        :key="t.id"
        class="eova-home_tab"
        :class="{ active: t.active }"
        @click="onTabClick(t)"
      >
        {{ t.name }}
        <span class="eova-home_tab_close" @click.stop="onTabClose(t)">×</span>
      </div>
      <button type="button" @click="onCloseAll">关闭全部</button>
    </main>
    <span style="color: red">{{ msg }}</span>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import axios from 'axios'
import { filterCats } from '@/utils/menu'
import { closeAllTab, closeTab, initTabs, openTab, toTab, type TabItem } from '@/utils/tab'

/** 目录（过滤后） */
const cats = ref<Array<Record<string, any>>>([])
/**
 * 菜单：旧实现里菜单对象本身就是"页签对象"（同一个对象进 tabMenus），
 * 故这里用同时满足两边的类型（`TabItem` + 菜单专有字段）
 */
interface Menu extends TabItem {
  parent_id?: number | null
  type?: string
  is_hide?: boolean
}
/** 菜单 */
const menus = ref<Menu[]>([])
/** 已打开页签（初始含首页；行为全部经 utils/tab 的纯函数） */
const tabMenus = ref<TabItem[]>(initTabs())
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

/**
 * 点击菜单（旧 openTab）：type=open 的菜单走新窗口，不入页签
 *
 * @param m 菜单
 */
function onMenuClick(m: Menu): void {
  const link = openTab(tabMenus.value, m)
  if (link) {
    window.open(link)
  }
}

/**
 * 切换页签（旧 toTab）
 *
 * @param t 页签
 */
function onTabClick(t: TabItem): void {
  toTab(tabMenus.value, t)
}

/**
 * 关闭页签（旧 closeTab）。
 *
 * 旧实现首行是 `event.stopPropagation()`；分离后由模板的 `@click.stop` 承担（已声明适配）。
 *
 * @param t 页签
 */
function onTabClose(t: TabItem): void {
  closeTab(tabMenus.value, t)
}

/** 关闭全部页签（旧 closeAllTab：只留首页） */
function onCloseAll(): void {
  closeAllTab(tabMenus.value)
}

onMounted(init)

defineExpose({ cats, menus, tabMenus, msg, init, onMenuClick, onTabClick, onTabClose, onCloseAll })
</script>
