<!--
  元字段（阶段 2 第 10 个入口页）

  契约来源（逐条对齐旧 `_view/meta/edit/app.html` + `app.js` + `MetaController`）：

  结构（旧 app.html）
  · `<title>📝#(object.name)元字段 - EovaMeta</title>`（服务端插值的标题；SPA 侧用 `document.title` 保持同形）
  · 页面级 `<style>` **四条**逐字搬入（注意第 4 条里有一行 **`//width: 220px;`** —— CSS 里 `//` 不是合法注释，
    浏览器会当作**非法声明忽略**；这是既有内容，**不得"顺手删掉或改成 /* */"**）
  · `.eova-layout`（`calc(100% - 20px)` / `margin:10px` / 白底）
  · 一个 `<form class="eova-form eova-anim-fadein app-field-edit" novalidate style="height: 30px">`，
    里面 **5 个只读字段**（名称/编码/数据源/数据表/主键），每个都是
    `<div class="eova-form-field" mode="detail"><label class="eova-form-label">X：</label><div class="eova-form-txt">值</div></div>`
  · `<div ref="tableParentRef" class="zone eova-anim-fadein" style="width:100%; height: calc(100% - 30px)">`
    内放 `<ev-table ref="refTable" size="s30" object="eova_field_code" biz="meta_eidt" :where="…" :height="tableHeight" :is-edit="true" :page="page">`
    ★ `biz="meta_eidt"` 是旧实现的**拼写（eidt）**，逐字保留（改成 edit 会改变后端 biz 取值）
  · toolbar 六按钮（文案/图标/样式逐字）：
    刷新(`eova-icon-refresh`)、字段排序(`eova-icon-slider`)、彻底删除(`eova-btn_error`,`eova-icon-delete`)、
    增量同步字段(`eova-btn_warn`,`eova-icon-add-circle`)、覆盖同步字段(`eova-btn_error`,`eova-icon-refresh-3`)、
    添加虚拟字段(`class=""`,`eova-icon-add-1`)

  行为（旧 app.js）
  · `isEdit = ref(true)`、`refTable = ref()`、`page = reactive({page:1, limit:99999, total:0})`
  · `tableHeight = x.dom.getViewSize().height - 40 - 20`（★ 注释原文"表格 = 窗口 - from 40 - padding10*2"）
  · `form = reactive({ object_code: uzoo.page.object_code })`
  · `onMounted` **只打日志**（`// query()` 被注释）⇒ **首屏不自动查询**
  · `onQuery()` → `refTable.value.query(form)`
  · `onReorder()` → `me.layer.open('元字段排序', '/meta/reorder?object=' + code, 250, 0.99, done, confirm, {offset:'r'})`：
    done 为 `msg('操作成功！')` + `onQuery()`；confirm 为**空函数**；opts `{offset:'r'}`
  · `onDelete()`：未选行 ⇒ `msg('请先选择元字段')`；否则 `confirm('确认彻底删除, 不可恢复', cb)`：
    `url = x.str.template(urls.form.delete, {object_code:'eova_field_code'})` ⇒ `POST url {rows}`；
    ok ⇒ 取 `rows[].id` 调 `refTable.removeRows(pks)` 再 `msg('删除成功')`；非 ok ⇒ **`msg(ret.msg)`（不是 `no`）**
  · `onVirtual()` → `me.layer.input('请输入虚拟字段名','text', cb)`；cb 里 `POST /meta/addVirtualField?object_code=<code>` `{input: val}`
  · `onSyncnew()` → `confirm('导入新增字段的元数据', cb)`；`POST /meta/syncnew/<code>` `{}`
  · `onOverride()` → `confirm('删除所有元字段并重新导入', cb)`；`POST /meta/override/<code>` `{}`
    后三者成功都是 `msg('同步成功')` + `onQuery()`；失败 `no(ret.msg)`

  ★★ **既有缺陷（四处相同，原样保留）**：四个 `.catch((e) => { … })` 里写的是 **`error.message`**，
  而形参叫 **`e`** ⇒ **`ReferenceError: error is not defined`**：
  （本页把形参写成 `_e` 仅为满足 `noUnusedParameters`；**引用的仍是未声明的 `error`，缺陷机制逐字保留**）
  表现为**网络异常时既不弹"客户端请求异常"、函数也不返回正常结果**（旧栈是未处理的 promise 拒绝）。
  本页**逐字保留**（用 `// @ts-expect-error` 保留"引用未声明标识符"这一机制本身），
  并各用判据钉住"抛 ReferenceError 且不弹提示"。**这是既有行为，不得顺手"修好"**。

  ★ 引导数据来源（`MetaController`）
  · `objectCode = get("object")`（URL 参数）；`where = String.format("{ object_code: '%s' }", objectCode)`
    ⇒ `:where` 是**服务端渲染的 JS 对象字面量文本**；SPA 侧等价为绑一个对象 `{ object_code: objectCode }`
  · `object.*` 五项同样来自 `sm.meta.getMeta(objectCode)` ⇒ 属 DES-004 元对象族（需端点），未就绪时显示告警态

  已声明适配（非静默改写）
  · `x.str.template(urls.form.delete, …)`：旧实现读**全局** `urls`（`window.urls.form.delete`）；
    SPA 侧用 `pageUrl('form','delete')` 取**同一张冻结表**的同一项（r103 已固化并挂回全局），语义相同
  · 页面级 `<style>` → SFC 非 scoped `<style>`（与前几页同一处置，差异已声明）
  · `:where="#(where)"`（服务端文本）→ `:where="where"`（`computed` 对象）

  尚未迁移（登记）：`onMounted` 里的 `// query()` 若是"临时注释"，旧栈**首屏确实不查询** ⇒ 与本页一致。
-->
<template>
  <div id="app">
    <div
      class="eova-layout"
      style="width: calc(100% - 20px); height: calc(100% - 20px); margin: 10px; background-color: white"
    >
      <form class="eova-form eova-anim-fadein app-field-edit" novalidate style="height: 30px">
        <div class="eova-form-field" mode="detail">
          <label class="eova-form-label">名称：</label>
          <div class="eova-form-txt">{{ object?.name }}</div>
        </div>
        <div class="eova-form-field" mode="detail">
          <label class="eova-form-label">编码：</label>
          <div class="eova-form-txt">{{ object?.code }}</div>
        </div>
        <div class="eova-form-field" mode="detail">
          <label class="eova-form-label">数据源：</label>
          <div class="eova-form-txt">{{ object?.data_source }}</div>
        </div>
        <div class="eova-form-field" mode="detail">
          <label class="eova-form-label">数据表：</label>
          <div class="eova-form-txt">{{ object?.table }}</div>
        </div>
        <div class="eova-form-field" mode="detail">
          <label class="eova-form-label">主键：</label>
          <div class="eova-form-txt">{{ object?.pk_name }}</div>
        </div>
      </form>

      <div ref="tableParentRef" class="zone eova-anim-fadein" style="width: 100%; height: calc(100% - 30px)">
        <ev-table
          ref="refTable"
          size="s30"
          object="eova_field_code"
          biz="meta_eidt"
          :where="where"
          :height="tableHeight"
          :is-edit="true"
          :page="page"
        >
          <template v-slot:toolbar>
            <div class="eova-tools_box">
              <button @click="onQuery()">
                <i class="eova-icon-refresh"></i>
                刷新
              </button>
              <button @click="onReorder()">
                <i class="eova-icon-slider"></i>
                字段排序
              </button>
              <button class="eova-btn_error" @click="onDelete()">
                <i class="eova-icon-delete"></i>
                彻底删除
              </button>

              <button class="eova-btn_warn" @click="onSyncnew()">
                <i class="eova-icon-add-circle"></i>
                增量同步字段
              </button>
              <button class="eova-btn_error" @click="onOverride()">
                <i class="eova-icon-refresh-3"></i>
                覆盖同步字段
              </button>
              <button class="" @click="onVirtual()">
                <i class="eova-icon-add-1"></i>
                添加虚拟字段
              </button>
            </div>
          </template>
        </ev-table>
      </div>
    </div>
  </div>
</template>

<!-- 旧 app.html 的页面级 <style> 逐字（**含那行非法的 `//width: 220px;`**，不得"修正"） -->
<style>
body {
  background-color: var(--eova-color_bg);
}
.eova-form {
  margin-top: 0;
  padding: 0;
}
.app-field-edit .eova-form-label {
  width: auto !important;
}
.app-field-edit .eova-form-field {
  margin-left: 20px !important;
  margin-top: -10px !important;
}
.app-field-edit .eova-form-txt {
  margin-left: 1px !important;
  //width: 220px;
}
</style>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import axios from 'axios'
import { getEovaMe, getEovaTools } from '@/compat/eova-runtime'
import { pageUrl } from '@/compat/ui-urls'
import { loadPageBootstrap, readUrlParams, resolveObjectCode, type PageBootstrap } from '@/compat/page-bootstrap'

/** DOM ref（旧 `tableParentRef`） */
const tableParentRef = ref<HTMLElement | null>(null)
/** 表格 ref（旧 `refTable`） */
const refTable = ref<{
  query?: (where: Record<string, unknown>) => void
  getSelectRows?: () => Array<Record<string, unknown>>
  removeRows?: (pks: unknown[], key?: string) => void
} | null>(null)

/** 是否可编辑（旧 `isEdit = ref(true)`） */
const isEdit = ref(true)
/** 分页（旧 `reactive({page:1, limit:99999, total:0})`；99999 = 关掉分页组件） */
const page = reactive({ page: 1, limit: 99999, total: 0 })
/** 表格高度（旧 `x.dom.getViewSize().height - 40 - 20`） */
const tableHeight = ref(getEovaTools().dom.getViewSize().height - 40 - 20)

const route = useRoute()

/** 引导数据（元对象描述；属 DES-004 元对象族） */
const bootstrap = ref<PageBootstrap>({ fromServer: false, url: readUrlParams() })

/**
 * 取查询串参数（旧 `MetaController` 的 `get("object")`）
 *
 * ★ 必须从**路由查询串**取（`useRoute().query`），不是 `location.search`：
 * SPA 里路由是唯一事实来源；早先写成 `readUrlParams()`（读 location）在组件测试里恒为空。
 *
 * @param key 键
 * @returns 值（不存在时为空串）
 */
function queryParam(key: string): string {
  const v = route.query[key]
  return typeof v === 'string' ? v : ''
}

/** 元对象编码（URL 参数 `object`） */
const objectCode = resolveObjectCode(bootstrap.value, queryParam('object')).code
/** 元对象描述（旧模板里 5 处 `#(object.X)`） */
const object = computed(() => bootstrap.value.object)

/** 查询条件（旧 `reactive({ object_code: uzoo.page.object_code })`） */
const form = reactive({ object_code: objectCode })
/** `:where`（旧 `#(where)` 服务端渲染的 `{ object_code: 'xxx' }`） */
const where = computed(() => ({ object_code: objectCode }))

/**
 * 设标题（旧 `<title>📝#(object.name)元字段 - EovaMeta</title>`）
 *
 * 抽成函数并在**引导数据到位后**再调一次 —— 否则"标题是否带元对象名"在判据里**不可观测**
 * （端点未就绪时名字恒为空，两种实现同结果 ⇒ 等价变异）。
 */
function applyTitle(): void {
  if (typeof document !== 'undefined') {
    document.title = `📝${object.value?.name ?? ''}元字段 - EovaMeta`
  }
}
applyTitle()

/** 查询（旧 `onQuery`） */
function onQuery(): void {
  refTable.value?.query?.(form)
}

/**
 * 字段排序（旧 `onReorder`：宽 250 / 高 0.99 / 第三个参数是 done / 第四个是空 confirm / opts `{offset:'r'}`）
 */
function onReorder(): void {
  const me = getEovaMe()
  me.layer.open(
    '元字段排序',
    `/meta/reorder?object=${objectCode}`,
    250,
    0.99,
    () => {
      me.layer.msg('操作成功！')
      onQuery()
    },
    () => {
      // 旧实现是空函数，原样保留
    },
    { offset: 'r' }
  )
}

/**
 * 彻底删除（旧 `onDelete`）
 *
 * ★ 网络异常分支里旧实现引用的是未声明的 `error`（形参是 `e`）⇒ **ReferenceError**，
 *   表现是"既不弹提示、函数也不正常返回"。本页逐字保留该机制（见文件头"既有缺陷"）。
 */
async function onDelete(): Promise<void> {
  const me = getEovaMe()
  const x = getEovaTools()
  const rows = refTable.value?.getSelectRows?.()
  if (x.isEmpty(rows)) {
    me.layer.msg('请先选择元字段')
    return
  }

  me.layer.confirm?.('确认彻底删除, 不可恢复', () => {
    // 旧实现读全局 `urls.form.delete`；此处取**同一张冻结表**的同一项（语义相同）
    const url = x.str.template(pageUrl('form', 'delete'), { object_code: 'eova_field_code' })
    axios
      .post(url, { rows })
      .then((res) => {
        const ret = res.data
        if (ret.state === 'ok') {
          const pks = (rows as Array<Record<string, unknown>>).map((row) => row['id'])
          refTable.value?.removeRows?.(pks)
          me.layer.msg('删除成功')
        } else {
          me.layer.msg(ret.msg)
        }
      })
      .catch((_e) => {
        // @ts-expect-error 既有缺陷：旧实现写的是 `error.message`，而形参是 `e`（ReferenceError）
        me.layer.msg('客户端请求异常: ' + error.message)
      })
  })
}

/** 添加虚拟字段（旧 `onVirtual`：`me.layer.input` 弹输入框） */
function onVirtual(): void {
  const me = getEovaMe()
  me.layer.input?.('请输入虚拟字段名', 'text', (val) => {
    const url = `/meta/addVirtualField?object_code=${objectCode}`
    axios
      .post(url, { input: val })
      .then((res) => {
        const ret = res.data
        if (ret.state === 'ok') {
          me.layer.msg('同步成功')
          onQuery()
        } else {
          me.layer.no(ret.msg)
        }
      })
      .catch((_e) => {
        // @ts-expect-error 既有缺陷：同上（`error` 未声明）
        me.layer.msg('客户端请求异常: ' + error.message)
      })
  })
}

/** 增量同步字段（旧 `onSyncnew`） */
function onSyncnew(): void {
  const me = getEovaMe()
  me.layer.confirm?.('导入新增字段的元数据', () => {
    const url = '/meta/syncnew/' + objectCode
    axios
      .post(url, {})
      .then((res) => {
        const ret = res.data
        if (ret.state === 'ok') {
          me.layer.msg('同步成功')
          onQuery()
        } else {
          me.layer.no(ret.msg)
        }
      })
      .catch((_e) => {
        // @ts-expect-error 既有缺陷：同上（`error` 未声明）
        me.layer.msg('客户端请求异常: ' + error.message)
      })
  })
}

/** 覆盖同步字段（旧 `onOverride`） */
function onOverride(): void {
  const me = getEovaMe()
  me.layer.confirm?.('删除所有元字段并重新导入', () => {
    const url = '/meta/override/' + objectCode
    axios
      .post(url, {})
      .then((res) => {
        const ret = res.data
        if (ret.state === 'ok') {
          me.layer.msg('同步成功')
          onQuery()
        } else {
          me.layer.no(ret.msg)
        }
      })
      .catch((_e) => {
        // @ts-expect-error 既有缺陷：同上（`error` 未声明）
        me.layer.msg('客户端请求异常: ' + error.message)
      })
  })
}

onMounted(async () => {
  console.log('cell.js init')
  // 旧实现此处 `// query()` 被注释 ⇒ **首屏不自动查询**（原样保留）
  bootstrap.value = await loadPageBootstrap()
  applyTitle()
})

defineExpose({
  tableParentRef,
  refTable,
  isEdit,
  page,
  tableHeight,
  bootstrap,
  object,
  objectCode,
  form,
  where,
  applyTitle,
  onQuery,
  onReorder,
  onDelete,
  onVirtual,
  onSyncnew,
  onOverride
})
</script>
