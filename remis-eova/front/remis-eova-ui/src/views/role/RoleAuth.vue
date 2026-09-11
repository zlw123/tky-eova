<!--
  功能权限分配（阶段 2 第 12 个入口页）

  契约来源（逐条对齐旧 `_view/role/auth/app.html` + `app.js` + `AuthController#index()/data()/doAuth()`）：

  结构（旧 app.html）
  · 页面级 `<style>` 只有一条：**`body input { height:22px; width:22px; vertical-align:middle }`**
    ★ 选择器是 **`body input`**（不是 `menu/auth` 的 `.eova-table input`）—— 两者**不同**，逐字保留
  · 被注释掉的"继承角色"整块（`#for(r : roles)` + `r.id`/`r.name` + `v-model="extendRids"`）**不迁移**
    （对应后端 `Role.dao.findSubRole(user)` 只服务于这块 ⇒ 前端也不需要它）
  · `.eova-tools > .eova-tools_box` 内 **7 个按钮**：
    全选(`onSelectAll(true)`)、**反选(`onSelectAll(false)`)**、所有查询/新增/修改/查看/删除(`onSelectType('<名>')`)
    ★★ 标签写"反选"，但实现是 `onSelectAll(false)` ⇒ **实为"全不选"**（既有命名缺陷，**不得"修正"文案或行为**）
  · `<table class="eova-table" ev-size="s30">`，`<colgroup><col width="200"><col></colgroup>`，
    表头「菜单名称」「菜单功能」
  · 每行菜单 `<tr v-for="m in menus">`：
    ① 首列：`m.type === 'dir'` 时渲染 `eova-icon-folder-open`（`color:#f59e0b; font-weight:bold`），
       否则 `eova-icon-triangle-r`（`color:#acacab;`）；随后 `{{ m.name }}`；
       **非目录**才有一个 `style="float: right"`、`title="选择本菜单所有功能"` 的复选框：
       `v-model="m.checked"` **且** `@click="onSelectMenu(m.code, m.checked)"`
       ★★ 二者同挂：`@click` 在 `v-model`（checkbox 的 `change`）**之前**触发 ⇒ 传进去的是**旧值**，
          `onSelectMenu` 里 `b.checked = !status` 恰好把本菜单按钮置为**新值** ⇒ 这是"意外正确"，
          **必须保留 `@click` + `v-model` 的组合与 `!status` 的写法**（改成 `@change` 就会取到新值 ⇒ 整列反掉）
    ② 第二列：`<template v-for="b in btns"><label v-if="b.menu_code === m.code">…<input type="checkbox" v-model="b.checked">{{ b.name }}</label></template>`
       ★ 按钮是**在每个菜单行内按 `b.menu_code === m.code` 过滤渲染**的（O(n²) 渲染，逐字保留）；
         这些按钮**没有绑定任何授权事件** —— 授权是"提交时批量保存"（见下）
  · 末尾被注释掉的 `<hr>` 与 `.eova-notes` 不迁移
  · 内联脚本：`uzoo.page.rid = '#(rid)'`

  行为（旧 app.js）
  · `menus`/`btns`/`extendRids` 三个 ref；`props = uzoo.page`（`props.rid` 即上面的内联值）
  · `onMounted` → `init()`；并注册 `me.cross.on('eova-layer-ok', onSubmit)`
  · `init()`：`POST /auth/data` 体 `{rid: props.rid}`；ok ⇒ `menus = ret.menus`、`btns = ret.btns`
    （★ 服务端已按 `RoleBtn.dao.queryByRid(rid)` 给 `btns[].checked` 赋好了值；`menus[].checked` 服务端**没给** ⇒ 初始为 undefined ⇒ 未勾选，原样保留）；
    非 ok ⇒ `layer.no(ret.msg)`；异常 ⇒ `msg('客户端请求异常: ' + message)`
  · `onSelectAll(status)`：把**所有** `btns[].checked` 置为 `status`
  · `onSelectType(name)`：`btns.filter(b => { if (b.name === name) b.checked = true })`
    ★ 用 **`filter` 做副作用**（返回值被丢弃）—— 与 `forEach` 状态等价，但**逐字保留**；
      且只**置 true**（不切换、不按当前状态反转）
  · `onSelectMenu(menuCode, status)`：对 `b.menu_code === menuCode` 的按钮置 `b.checked = !status`
  · `onSubmit(id)`：**没有任何校验**；`auth_btns` = 勾选按钮的 **id 用逗号拼成的字符串**
    （`btns.filter(b=>b.checked).map(b=>b.id).join(',')`，空集时为**空串**）；
    `POST /auth/doAuth` 体 `{rid: props.rid, auth_btns}`；
    ok ⇒ `emit('eova-layer-ok_done', id)`；非 ok ⇒ `layer.no(ret.msg)`；
    异常 ⇒ **先 `console.log(e)`** 再 `msg('客户端请求异常: ' + e.message)`
  · 旧实现里 `//parent.uzoo.app.refTable.value.reload();` 是**注释掉的** ⇒ 本页**不刷新父页**
    （与 `menu/add` 正相反 —— 那边是活的调用），原样保留

  ★ 引导数据来源（第 114 轮取证 `AuthController#index()`）
  · `setAttr("rid", get(0))` ⇒ **`rid` = URL 第 0 段**（与 `button/add`、`menu/auth` 同族）
    ⇒ **本页不需要引导端点**；菜单与按钮由运行时 `POST /auth/data` 取
  · `roles` 虽由服务端查库，但**只服务于被注释掉的"继承角色"块** ⇒ 前端不需要

  ★ 既有死代码（登记，不迁移）
  · `groupByCat`（与 `menu/auth` 同名但**本页从未调用**）、`onAuth`（定义并返回，**模板从不调用**）、
    `sptBtnName`（定义并返回，未被使用）、`extendRids`（只服务于被注释的块）
  ⇒ 均登记为"既有死代码"；**不删除也不实现**（删除会改变 setup 返回面 ⇒ 影响 `uzoo.app` 的形状）

  已声明适配（非静默改写）
  · 独立 HTML + `createApp` → SPA 路由页（运行时由装配期统一提供）
  · `menus`/`btns`/`extendRids` 用 `ref`（与旧同形）；页面级 `<style>` 搬入 SFC（**非 scoped**，差异同前几页）
  · `props.rid`：旧实现经内联脚本写 `uzoo.page.rid`；本页**仍写 `uzoo.page.rid`**（保持全局可读），
    同时用路由参数作为本地来源
-->
<template>
  <div id="app" v-cloak>
    <div style="padding: 10px">
      <div class="eova-tools">
        <div class="eova-tools_box">
          <button @click="onSelectAll(true)">全选</button>
          <button @click="onSelectAll(false)">反选</button>
          <button @click="onSelectType('查询')">所有查询</button>
          <button @click="onSelectType('新增')">所有新增</button>
          <button @click="onSelectType('修改')">所有修改</button>
          <button @click="onSelectType('查看')">所有查看</button>
          <button @click="onSelectType('删除')">所有删除</button>
        </div>
      </div>
      <table class="eova-table" ev-size="s30">
        <colgroup>
          <col width="200" />
          <col />
        </colgroup>
        <thead>
          <tr>
            <th>菜单名称</th>
            <th>菜单功能</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="m in menus" :key="m.code">
            <td>
              <i
                class="eova-icon-folder-open"
                style="color: #f59e0b; font-weight: bold"
                v-if="m.type === 'dir'"
              ></i>
              <i class="eova-icon-triangle-r" style="color: #acacab" v-else></i>
              {{ m.name }}
              <input
                type="checkbox"
                style="float: right"
                v-if="m.type !== 'dir'"
                title="选择本菜单所有功能"
                v-model="m.checked"
                @click="onSelectMenu(m.code, m.checked)"
              />
            </td>
            <td style="text-align: left">
              <template v-for="b in btns" :key="b.id">
                <label v-if="b.menu_code === m.code" style="margin-left: 10px">
                  <input type="checkbox" v-model="b.checked" />
                  {{ b.name }}
                </label>
              </template>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<!-- 旧 app.html 的页面级 <style> 逐字（选择器是 `body input`，与 menu/auth 的 `.eova-table input` **不同**） -->
<style>
body input {
  height: 22px;
  width: 22px;
  vertical-align: middle;
}
</style>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import axios from 'axios'
import { getEovaMe } from '@/compat/eova-runtime'
import { getUzooPage } from '@/compat/eova-ext'

/** 菜单行 */
interface AuthMenu {
  code: string
  name?: string
  type?: string
  checked?: boolean
  [k: string]: unknown
}

/** 按钮行 */
interface AuthBtn {
  id: number | string
  name?: string
  menu_code?: string
  checked?: boolean
  [k: string]: unknown
}

const route = useRoute()

/**
 * `rid`：旧 `AuthController#index()` 的 `setAttr("rid", get(0))` ⇒ **URL 第 0 段**
 *
 * 同时写回 `uzoo.page.rid`（旧内联脚本 `uzoo.page.rid = '#(rid)'` 的位置），
 * 因为 `init`/`onSubmit` 是从 `props = uzoo.page` 读 `props.rid` 的。
 */
const rid = ((): string => {
  const p = route.params.rid
  const v = typeof p === 'string' ? p : ''
  getUzooPage()['rid'] = v
  return v
})()

/** 可分配菜单（旧 `menus = ref()`） */
const menus = ref<AuthMenu[]>([])
/** 可分配按钮（旧 `btns = ref()`；`checked` 由服务端 `/auth/data` 赋予） */
const btns = ref<AuthBtn[]>([])
/** 继承角色（旧 `extendRids = ref([])`；只服务于被注释掉的块，保留以维持 setup 返回面） */
const extendRids = ref<unknown[]>([])

/**
 * 全选 / "反选"（旧 `onSelectAll`）
 *
 * ★ 模板里"反选"按钮调的是 `onSelectAll(false)` ⇒ 实为**全不选**（既有命名缺陷，原样保留）。
 *
 * @param status 目标勾选状态
 */
function onSelectAll(status: boolean): void {
  btns.value.forEach((b) => {
    b.checked = status
  })
}

/**
 * 批量选中某类功能（旧 `onSelectType`）
 *
 * ★ 用 `filter` 做副作用（返回值被丢弃）—— 与 `forEach` 状态等价，逐字保留；
 *   且只**置 true**（不切换、不反转）。
 *
 * @param name 功能名（如 `查询`）
 */
function onSelectType(name: string): void {
  btns.value.filter((b) => {
    if (b.name === name) {
      b.checked = true
    }
    return false
  })
}

/**
 * 选中/取消本菜单的所有功能（旧 `onSelectMenu`）
 *
 * ★ 参数 `status` 是**菜单复选框的旧值**（`@click` 早于 `v-model` 的 `change`）⇒
 *   这里取反恰好得到"新值"。**不得改成 `@change` 或去掉取反**。
 *
 * @param menuCode 菜单编码
 * @param status 菜单复选框的旧勾选状态（**可能为 `undefined`** —— 服务端不给 `menus[].checked`，
 *               故首次点击时是 `!undefined === true` ⇒ 全选本菜单；旧实现正是这个结果）
 */
function onSelectMenu(menuCode: string, status: boolean | undefined): void {
  btns.value.filter((b) => {
    if (b.menu_code === menuCode) {
      b.checked = !status
    }
    return false
  })
}

/**
 * 初始化：取可分配菜单与按钮（旧 `init`）
 */
async function init(): Promise<void> {
  const me = getEovaMe()
  try {
    const res = await axios.post('/auth/data', { rid })
    const ret = res.data
    if (ret.state === 'ok') {
      menus.value = ret.menus
      btns.value = ret.btns
    } else {
      me.layer.no(ret.msg)
    }
  } catch (error) {
    me.layer.msg('客户端请求异常: ' + (error as Error).message)
  }
}

/**
 * 提交（旧 `onSubmit`：**无校验**，把勾选按钮 id 拼成逗号串提交）
 *
 * @param id 弹层句柄（由 `eova-layer-ok` 事件带回）
 */
async function onSubmit(id?: unknown): Promise<void> {
  const me = getEovaMe()
  // 勾选的按钮 ID：★ 逗号拼接的**字符串**（空集时为空串）
  const authBtns = btns.value
    .filter((b) => b.checked)
    .map((b) => b.id)
    .join(',')

  try {
    const res = await axios.post('/auth/doAuth', { rid, auth_btns: authBtns })
    const ret = res.data
    if (ret.state === 'ok') {
      me.cross.emit('eova-layer-ok_done', id)
    } else {
      me.layer.no(ret.msg)
    }
  } catch (e) {
    // 旧实现先打日志再提示（保留）
    console.log(e)
    me.layer.msg('客户端请求异常: ' + (e as Error).message)
  }
}

onMounted(() => {
  init()
  // 监听提交通知
  getEovaMe().cross.on('eova-layer-ok', (id) => {
    onSubmit(id)
  })
})

defineExpose({
  rid,
  menus,
  btns,
  extendRids,
  onSelectAll,
  onSelectType,
  onSelectMenu,
  init,
  onSubmit
})
</script>
