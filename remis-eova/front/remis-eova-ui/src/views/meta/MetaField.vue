<!--
  元字段个性化（阶段 2 第 8 个入口页）

  契约来源（逐条对齐旧实现 `_view/meta/field/index.html` + `index.js` + `MetaController#field()`）：

  结构（旧 index.html）
  · `#include("/eova/_view/_page/list.html")`：该 partial 定义 `window.urls` 与 `uzoo`，
    并在 `#if(menu)` 分支里**整体赋值** `uzoo.page = { menu_*, object_* }`（本页 `object_code` 正来自它）
  · `<div class="eova-layout" style="width:100%; height:100%; background-color:white">`
    → `<div class="zone eova-anim-fadein" style="width:100%; height:100%">`
  · `<ev-tab type="base" v-model="currTab">` 下 **4 个 `ev-tab-item`**，
    id/title 依次为 `query/查询`、`create/新增`、`update/修改`、`read/详情`；
    每个页签里一个 `ev-table`，四个表的 props **除 `where.mode` 外完全相同**：
    `size="s30" object="eova_field_diy" biz="eova_field_diy" :height="tableHeight" :is-edit="true" :page="page"`
    而 `:where="{ object_code: '#(object.code)', mode: '<query|create|update|read>' }"`
    ★ 四个表**共用同一个 `ref="tableRef"`**（旧实现如此）—— 见文件末"登记"

  行为（旧 index.js）
  · `currTab = ref('')`；`isEdit = ref(true)`；`tableRef = ref()`
  · `page = reactive({page:1, limit:99999})` ★ 注释原文："子表分页（limit > 99999 不显示分页组件）" ——
    `EvTable` 的分页条件正是 `page.limit < 99999`，故 **99999 就是"关掉分页"的开关值**
  · `tableHeight = ref(x.dom.getViewSize().height - 65)` ★ **减 65**（不是别的页面的 30）
  · `form = reactive({ object_code: uzoo.page.object_code })` ★ 读的是 **`uzoo.page.object_code`**（由 list partial 赋值）
  · `query()` → `tableRef.value.query(form)`
  · `onBeforeMount` → `uzoo.vue.mountBefore()`（本页 `index.html:79` 正是**定义**它的一侧）
  · `onMounted` 为空

  ★ 引导数据来源（第 110 轮取证 `MetaController#field()`）
  · `objectCode = get("object")`、`mode = get("mode", "query")` ⇒ **URL 参数**（`mode` 默认 `query`）
  · `menu = Menu.dao.findByCode("eova_object")` ⇒ **按硬编码 code 查库**（不是 URL 决定）
  · `object = sm.meta.getMeta(objectCode)` ⇒ **查库** ⇒ 属 DES-004 的**元对象族**，**需要引导端点**
  · 端点未就绪时：`object_code` 回退到 URL 参数 `object`；仍缺则**响亮告警**（表格 `where` 会带上空
    `object_code` —— 与其静默查全表，不如让人一眼看到告警）

  已声明适配（非静默改写）
  · 旧页面是独立 HTML + `createApp` + `app.use(EovaUI)`；本页是 SPA 路由页（运行时由装配期统一提供）
  · `:where` 里旧栈是模板插值 `'#(object.code)'`，SPA 改为绑定 `objectCode`（**同一语义**）
  · 旧钩子写的是 `uzoo.app.currTab.value = '#(mode)'`（经 `uzoo.app` 这条"setup 返回值"通道）；
    本页在 setup 内直接设 `currTab`，并**仍写 `uzoo.page.object_code`**（保持全局可读）
  · 旧 `page.limit = 99999`、`tableHeight = 视口高 - 65` 逐字保留

  尚未迁移 / 登记
  · ★ **四个表共用 `ref="tableRef"`**：Vue 对同名模板 ref 的语义是"后挂载者胜"，
    故 `query()` 实际打到哪个表取决于 `EvTab` 是否只渲染激活页签。旧实现如此，本页**原样保留**并登记，
    不在迁移期擅自改名（改名会改变 `query()` 的作用目标 ⇒ 属行为变更，须显式决策）
  · `uzoo.page` 的其余字段（`menu_id`/`menu_name`/`object_id`/`object_pk`）由 list partial 赋值，
    本页只用到 `object_code`；其余字段的来源随引导端点一并落地
-->
<template>
  <div id="app">
    <div class="eova-layout" style="width: 100%; height: 100%; background-color: white">
      <div class="zone eova-anim-fadein" style="width: 100%; height: 100%">
        <ev-tab type="base" v-model="currTab">
          <ev-tab-item id="query" title="查询">
            <ev-table
              ref="tableRef"
              size="s30"
              object="eova_field_diy"
              biz="eova_field_diy"
              :where="{ object_code: objectCode, mode: 'query' }"
              :height="tableHeight"
              :is-edit="true"
              :page="page"
            >
            </ev-table>
          </ev-tab-item>
          <ev-tab-item id="create" title="新增">
            <ev-table
              ref="tableRef"
              size="s30"
              object="eova_field_diy"
              biz="eova_field_diy"
              :where="{ object_code: objectCode, mode: 'create' }"
              :height="tableHeight"
              :is-edit="true"
              :page="page"
            >
            </ev-table>
          </ev-tab-item>
          <ev-tab-item id="update" title="修改">
            <ev-table
              ref="tableRef"
              size="s30"
              object="eova_field_diy"
              biz="eova_field_diy"
              :where="{ object_code: objectCode, mode: 'update' }"
              :height="tableHeight"
              :is-edit="true"
              :page="page"
            >
            </ev-table>
          </ev-tab-item>
          <ev-tab-item id="read" title="详情">
            <ev-table
              ref="tableRef"
              size="s30"
              object="eova_field_diy"
              biz="eova_field_diy"
              :where="{ object_code: objectCode, mode: 'read' }"
              :height="tableHeight"
              :is-edit="true"
              :page="page"
            >
            </ev-table>
          </ev-tab-item>
        </ev-tab>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeMount, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getEovaTools } from '@/compat/eova-runtime'
import { callUzooHook, getUzooPage } from '@/compat/eova-ext'
import {
  loadPageBootstrap,
  readUrlParams,
  resolveObjectCode,
  type PageBootstrap
} from '@/compat/page-bootstrap'

const route = useRoute()

/** 引导数据（本页要用的是元对象的 `object.code`） */
const bootstrap = ref<PageBootstrap>({ fromServer: false, url: readUrlParams() })

/**
 * 取查询串参数（旧 `MetaController#field()` 的两个 `get(...)`）
 *
 * @param key 键
 * @returns 值（不存在时为空串）
 */
function queryParam(key: string): string {
  const v = route.query[key]
  return typeof v === 'string' ? v : ''
}

/** `#(mode)`：URL 参数，**默认 `query`**（旧 `get("mode", "query")`） */
const mode = ((): string => {
  const v = queryParam('mode')
  return v === '' ? 'query' : v
})()

/**
 * 元对象编码（旧 `#(object.code)`）
 *
 * 优先级：引导数据的 `object.code` > URL 参数 `object`。
 * 都没有时**响亮告警** —— 表格 `where.object_code` 会是空串，静默查全表比报错更难查。
 */
const objectCode = ((): string => {
  // ★ 优先级与"来源判定"都由 `resolveObjectCode` 承载（纯函数，可被判据"两侧都给值"地验证）
  const resolved = resolveObjectCode(bootstrap.value, queryParam('object'))
  const code = resolved.code
  if (code === '') {
    console.warn(
      '[meta-field] 缺少 object.code（引导数据与 URL 参数都没有）⇒ 表格 where.object_code 为空。' +
        '引导端点契约见 DES-004；本页不静默假装成功。'
    )
    return ''
  }
  return code
})()

/** 当前页签（旧 `currTab = ref('')`；由 `#(mode)` 决定） */
const currTab = ref(mode)
/** 是否可编辑（旧 `isEdit = ref(true)`） */
const isEdit = ref(true)
/** 表格 ref（旧 `tableRef = ref()`；★ 四个表共用同名 ref，见文件头"登记"） */
const tableRef = ref<{ query?: (where: Record<string, unknown>) => void } | null>(null)
/** 分页（旧 `reactive({page:1, limit:99999})`；99999 = 关掉分页组件） */
const page = reactive({ page: 1, limit: 99999 })
/** 表格高度（旧 `x.dom.getViewSize().height - 65`） */
const tableHeight = ref(getEovaTools().dom.getViewSize().height - 65)
/** 查询条件（旧 `reactive({ object_code: uzoo.page.object_code })`） */
const form = reactive({ object_code: objectCode })

/** 查询（旧 `query()`：把查询条件交给表格） */
function query(): void {
  tableRef.value?.query?.(form)
}

onBeforeMount(() => {
  console.log('app.js onBeforeMount...')
  // 旧 `index.js:30` 回调扩展钩子；本页 `index.html:79` 正是定义它的一侧
  callUzooHook('mountBefore')
  // 旧钩子把 `#(mode)` 写进 `uzoo.app.currTab`；本页直接设 currTab（语义相同），
  // 并保持全局 `uzoo.page.object_code` 可读（旧实现由 list partial 赋值）
  getUzooPage()['object_code'] = objectCode
})

// 引导数据到位后补齐（首帧可能还没拿到元对象）
void loadPageBootstrap().then((bs) => {
  bootstrap.value = bs
  const code = bs.object?.code
  if (code && form.object_code === '') {
    form.object_code = String(code)
    getUzooPage()['object_code'] = String(code)
  }
})

defineExpose({ currTab, isEdit, tableRef, page, tableHeight, form, mode, objectCode, query })
</script>
