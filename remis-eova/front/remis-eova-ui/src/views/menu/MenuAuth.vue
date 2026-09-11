<!--
  功能授权给角色（阶段 2 第 7 个入口页）

  契约来源（逐条对齐旧实现 `_view/menu/auth/app.html` + `app.js` + `MenuController#auth()/authData()`）：

  结构（旧 app.html）
  · `.eova-table` 上带一个 `<style>`：`.eova-table input { height:22px; width:22px; vertical-align:middle }`
    —— 属**本页局部样式**，逐字保留（组件库样式里没有这条）
  · `<h3>{{ menu?.name }}</h3>` —— 用了**可选链**（`menu` 未就绪时不报错、渲染为空）
  · 按 `btnCats` **逐组渲染一张表**：`<colgroup>` 第一列宽 140，后续每列一个按钮；
    表头「角色」+ 每个按钮名；表体逐角色一行、逐按钮一个复选框
  · 复选框 `:id="'CK_' + b.id + '_' + r.id"`（**这个 id 拼接规则是契约**：初始化勾选状态时按同一规则回查）
  · 结尾 `<div class="eova-notes"> 💡 授权后相关角色需要重新登录更新权限</div>`

  行为（旧 app.js）
  · `props = uzoo.page`，`props.id` 来自内联脚本 `uzoo.page.id = '#(id)'`
  · `onMounted` → `initCheckedStatus()`；并注册 `me.cross.on('eova-layer-ok', id => emit('eova-layer-ok_done', id))`
    ★ **弹层「确认」不做任何提交** —— 本页的授权是**逐勾选即时提交**的，确认只是关层
  · `initCheckedStatus()`：`POST /menu/authData`，体 `{id: props.id}`
    ok ⇒ `menu = ret.menu`、`btnCats = groupByCat(ret.btns)`、`roles = ret.roles`、`auths = ret.auths`，
    然后在 **`nextTick`** 里**逐个 `document.getElementById(\`CK_${a.bid}_${a.rid}\`)` 并设 `.checked = true`**
    ★ 这是**命令式**初始化（不是 `v-model`/`:checked` 绑定）—— 原样保留：
      改成绑定会改变"重渲染后勾选状态从哪来"的语义，属行为漂移
    非 ok ⇒ `me.layer.no(ret.msg)`；异常 ⇒ `me.layer.msg('客户端请求异常: ' + message)`
  · `onAuth(event, bid, rid)`：`status = event.target.checked`；`POST /auth/update`
    体 **`{is_check: status, bid, rid}`**（★ 字段名是 **`is_check`**，带下划线）
    ok ⇒ `me.layer.msg('授权成功')`；非 ok ⇒ `me.layer.no(ret.msg)`；异常 ⇒ `msg('客户端请求异常: ' + message)`

  ★ 引导数据来源（第 109 轮取证：**本页不需要引导端点**）
  · `MenuController#auth()` 只有一句 `int id = getInt(0);` ⇒ **`#(id)` = URL 第 0 段**
    （与 `button/add` 的 `get(0)` 同族），故 `id` 由**路由参数**提供；
  · `menu`/`btns`/`roles`/`auths` **不是**渲染期注入，而是页面运行时向 `/menu/authData` 取
    ⇒ 不属 DES-004 的引导数据族。

  已声明适配（非静默改写）
  · 旧页面是独立 HTML + `createApp` + `app.use(EovaUI)`；本页是 SPA 路由页（运行时由装配期统一提供）
  · `groupByCat` 抽到 `src/utils/button-cat.ts`（`<script setup>` 不能含 ES `export`，且分组顺序要单独判）
  · 旧页面级 `<style>` 改为 SFC 的**非 scoped** `<style>` 块 —— **已声明差异**：旧写法只在"该页面文档"内生效
    （旧栈本页常以 iframe 弹层打开），SPA 里一旦该模块被加载，样式即**全局且持续**生效。
    规则选择器本身逐字未改（`.eova-table input`）。
  · 旧页面把 `menu`/`btnCats` 等挂在 `uzoo.app` 上（供外部扩展读取）；本页用 `defineExpose` 暴露，
    并**仍写 `uzoo.page.id`**（旧实现如此，且其它读 `uzoo.page` 的自定义仍可工作）
-->
<template>
  <div id="app" v-cloak>
    <div style="padding: 10px">
      <h3>{{ menu?.name }}</h3>
      <template v-for="(btns, index) in btnCats" :key="index">
        <table class="eova-table" ev-size="s30">
          <colgroup>
            <col width="140" />
            <col v-for="btn in btns" :key="btn.id" />
          </colgroup>
          <thead>
            <tr>
              <th>角色</th>
              <th v-for="btn in btns" :key="btn.id" style="text-align: center">{{ btn.name }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="role in roles" :key="role.id">
              <td>{{ role.name }}</td>
              <td v-for="b in btns" :key="b.id" style="text-align: center">
                <label>
                  <input
                    type="checkbox"
                    @change="onAuth($event, b.id, role.id)"
                    :id="'CK_' + b.id + '_' + role.id"
                  />
                </label>
              </td>
            </tr>
          </tbody>
        </table>
        <hr />
      </template>
      <div class="eova-notes">💡 授权后相关角色需要重新登录更新权限</div>
    </div>
  </div>
</template>

<!-- 旧 app.html 的页面级 <style> 逐字搬到这里（**不加 scoped**：旧选择器就是全局的 `.eova-table input`） -->
<style>
.eova-table input {
  height: 22px;
  width: 22px;
  vertical-align: middle;
}
</style>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import axios from 'axios'
import { getEovaMe } from '@/compat/eova-runtime'
import { getUzooPage } from '@/compat/eova-ext'
import { groupByCat } from '@/utils/button-cat'

const route = useRoute()

/**
 * `id`：旧 `MenuController#auth()` 的 `getInt(0)` ⇒ **URL 第 0 段**
 *
 * 同时写回 `uzoo.page.id`（旧内联脚本 `uzoo.page.id = '#(id)'` 的位置），
 * 因为 `initCheckedStatus` 是从 `props = uzoo.page` 读 `props.id` 的。
 */
const pageId = ((): string => {
  const p = route.params.id
  const v = typeof p === 'string' ? p : ''
  getUzooPage()['id'] = v
  return v
})()

/**
 * 一行（按钮 / 角色 / 授权记录）的最小字段面
 *
 * `id` 必有（模板用它做 `:key` 与拼复选框 id）；其余字段透传。
 */
interface AuthRow {
  id?: number | string
  name?: string
  cat?: unknown
  bid?: unknown
  rid?: unknown
  [k: string]: unknown
}

/** 菜单（旧 `menu = ref()`） */
const menu = ref<{ name?: string } | undefined>(undefined)
/** 按 `cat` 分组的按钮（旧 `btnCats = ref()`；结果由 `groupByCat` 给出） */
const btnCats = ref<Record<string, AuthRow[]>>({})
/** 角色（旧 `roles = ref()`） */
const roles = ref<AuthRow[]>([])
/** 已授权记录（旧 `auths = ref()`；元素含 `bid`/`rid`） */
const auths = ref<AuthRow[]>([])

/**
 * 勾选 / 取消勾选（旧 `onAuth`：**逐次即时提交**）
 *
 * @param event change 事件
 * @param bid 按钮 id
 * @param rid 角色 id
 */
async function onAuth(event: Event, bid: unknown, rid: unknown): Promise<void> {
  const me = getEovaMe()
  const status = (event.target as HTMLInputElement).checked
  console.log('Bid:', bid, 'Rid:', rid, 'Status:', status)
  try {
    // ★ 字段名是 `is_check`（带下划线），逐字对齐旧实现
    const res = await axios.post('/auth/update', { is_check: status, bid, rid })
    const ret = res.data
    if (ret.state === 'ok') {
      me.layer.msg('授权成功')
    } else {
      me.layer.no(ret.msg)
    }
  } catch (error) {
    me.layer.msg('客户端请求异常: ' + (error as Error).message)
  }
}

/**
 * 初始化勾选状态（旧 `initCheckedStatus`）
 *
 * ★ 勾选状态用**命令式**设置（`getElementById` + `.checked = true`），且必须在 **`nextTick`** 之后
 * —— 原样保留：改成 `:checked` 绑定会改变"重渲染后勾选状态从哪来"的语义。
 */
async function initCheckedStatus(): Promise<void> {
  const me = getEovaMe()
  try {
    const res = await axios.post('/menu/authData', { id: pageId })
    const ret = res.data
    if (ret.state === 'ok') {
      menu.value = ret.menu
      btnCats.value = groupByCat(ret.btns)
      roles.value = ret.roles
      auths.value = ret.auths
      // 默认勾选已授权（等待 DOM 更新后）
      await nextTick()
      auths.value.forEach((auth) => {
        const checkbox = document.getElementById(
          `CK_${String(auth.bid)}_${String(auth.rid)}`
        ) as HTMLInputElement | null
        if (checkbox) {
          checkbox.checked = true
        }
      })
    } else {
      me.layer.no(ret.msg)
    }
  } catch (error) {
    me.layer.msg('客户端请求异常: ' + (error as Error).message)
  }
}

onMounted(() => {
  // 在组件挂载完成后初始化复选框的状态
  initCheckedStatus()
  // 监听提交通知：★ 确认不做提交，只回传关层（授权是逐勾选即时提交的）
  getEovaMe().cross.on('eova-layer-ok', (id) => {
    getEovaMe().cross.emit('eova-layer-ok_done', id)
  })
})

defineExpose({ pageId, menu, btnCats, roles, auths, onAuth, initCheckedStatus })
</script>
