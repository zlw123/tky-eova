<!--
  单表模版页（第 118 轮）—— 旧 `_view/template/table/index.html`(94 行) + `index.js`(318 行) 的等价物

  ## 它在链路里的位置

  `Menu.getUrl()` 对 `template` 非空的菜单返回 `/app/<menu.code>`；`AppController#index()` 按
  `menu.getTemplate()` 渲染 `_view/template/<template>/index.html` ⇒ SPA 侧
  `/app/:menuCode` 路由 → `AppTemplateHost.vue` 取引导数据里的 `menu.template` → **本组件**。

  ## 结构（逐条对齐旧 index.html）

  · `.eova-layout`(100%-20px 外边距) > `.zone` > **两个 `.box`**：
    ① 查询表单盒：高度 `{{queryHeight}}px`，内含 `<ev-form mode="query" name="query_from" ...>`
       （`object`/`biz` 都是 `object.code`；`v-model="data"`；`@submit=onQuery()`、`@resize=doResize`、`@ready=onReady`）
    ② 表格盒：高度 `calc(100% - 10px - {{queryHeight}}px)`、`top: calc(10px + {{queryHeight}}px)`，
       内含 `<ev-table>`（`design=loginUser.isAdmin`、`page`、`height=tableHeight`、
       `is-edit=object.is_celledit??false`、`where=data`、`@row-click=onRowClick`）
  · `<ev-table>` 的 `#toolbar` 槽：左 `.eova-tools_box`（内置 `_block/toolbar.html` 的等价物 = `EovaToolbar`）
    + 右 `.eova-tools_box`（`position:absolute; right:0`，导出气泡：导出Excel `onExport('xlsx')` / 导出CSV `onExport('csv')`）
  · **`.eova-layout` 的兄弟节点** `_block/admin.html`（超管面板，r117 已迁为 `EovaAdminPanel`）
  · 末尾的 `.js` 按钮脚本注入（`#for(btn : btnList) #if(btn.ui.contains(".js")) <script src>`）
    ⇒ `loadButtonScripts(btnList)`（r115 的加载器）

  ## 行为（逐条对齐旧 index.js；括号内是行号）

  · `page = {page:1, limit:15}`（15）；`queryHeight=ref(0)`、`tableHeight=ref(600)`（23-24）
  · `LW/LH = conf.layer_width||720 / conf.layer_height||660`（13）—— ★ 默认高 **660**（tree/tree_table 是 720）
  · `onQuery`(28)：`refTable.query(data)` + `showLinking=false`
  · `doResize`(44)：**在 `nextTick` 里**算 `queryHeight`，`tableHeight = x.dom.getViewSize().height - height - 30`
  · `onReady`(53)：`uzoo.vue.onReady(fieldInstances)`（存在才调）
  · `onAdd`(59)：`/app/add/{{object_code}}?biz={{code}}`（**`biz` 用的是 `uzoo.page.code`**）→ 弹层 '新增数据' → done: `msg('操作成功！')`+`onQuery()`
  · `onUpdate`(66)：未选行 ⇒ `msg('请先选择一行数据')`；URL `/app/update/{{object_code}}?id={{id}}&biz={{menu_code}}`，
    实参是 `{object_code, menu_code: uzoo.page.code, id: rows[0].id}` —— ★ `id` 取的是**硬编码 `row.id`**（不是 `object_pk`）
  · `onDetail`(83)：同上，标题 '查看数据'，done 是**空函数**
  · `onDelete`(98)：未选行 ⇒ `msg('请先选择数据')`；`confirm('确认彻底删除, 不可恢复')` → `POST urls.form.delete {rows}` →
    ok ⇒ `removeRows(rows.map(r => r[object_pk]))` + `msg('删除成功')`；否则 `msg(ret.msg)`
  · `onHide`(126)：同上，`confirm('确认删除')` + `urls.form.hide`
  · ★ **`.catch` 里引用未声明的 `error`（形参是 `e`）⇒ ReferenceError**（121/149）—— 既有缺陷，**原样保留**（r112 同族）
  · `onExport(type)`(179)：`/excel/export/{{object_code}}?type={{type}}&biz={{menu_code||''}}`，
    文件名 `${object_name}.${type}`，走 `x.axios.download(url, data, fileName, type)`
  · `onImport()`(186)：旧栈**就是 stub**（`console.log('导入')` + `msg('待实现...')`）—— 原样保留
  · `onRowClick(row)`(206)：`currentRow=row`；`showLinking=true`；`me.cross.emit('eova-table-row_click', row.id)`
  · `onMounted`(231)：`Object.assign(data, getUrlSearch())` —— ★ **不重新查询**（`// refTable.value.query(data)` 被注释）
  · 结尾(274-275)：`if (typeof uzoo.vue.setup === 'function') data_ = uzoo.vue.setup()`，再
    `return uzoo.app = {...data_, data, auths, refForm, refTable, queryHeight, tableHeight, page, currentRow,
    onQuery, onAdd, onUpdate, onDetail, onHide, onDelete, onImport, onExport, doResize, onReady, onRowClick, showLinking}`
    ⇒ 本组件等价：`callUzooHook('setup')` 的返回值摊平进一个对象并 `setUzooApp(...)`
  · `me.vue.mount(app, `${template}_${code}`)`(318)：SPA 路由页不做 `createApp` ⇒ **登记为待用户口径**
    （`me.vue.app/mount` 的"自定义 app"机制在 SPA 下的等价形态），不在本页发明。

  ## 既有死代码（**不迁移**，登记在案）

  · `setting = ref({})`（8）—— 从未返回，只被注释掉的取设置代码用到
  · `submitForm(url, data)`（155-177）—— 从未被调用
  · 子表一族 `refTable1`/`refTable2`（193-194）、`table1Height`（198）、`table1Page`（200）——
    只被注释掉的"子表联动"代码用到（`showLinking`/`currentRow` 仍活：被 `onQuery`/`onRowClick` 写）
  · `index.html` 的 `#include(..., type="list")` 参数（admin 面板用）—— `admin.html` 全文无 `#(type)`（r117 已取证据）

  ## 已声明适配（非静默改写）

  ① 旧栈 `#(object.code)`/`#(loginUser.isAdmin)`/`#(menu.*)` 是**渲染期服务端插值**（同步可得）；
     分离后它们来自**页面引导数据**（DES-004）⇒ 异步到达。本页把"同步已知的部分"（`code`/`template`/`form`）
     在 setup 期写入 `uzoo.page`，其余在引导数据到达后写入（**等价**：旧栈这些值在页面脚本执行前就已存在，
     而它们只被"点击/首次查询"时才读；首次渲染用本地 ref 承载，行为一致）。
  ② `@click="#(btn.event)()"` 在 SPA 里无法编译成绑定 ⇒ 走 `EovaToolbar` 的**方法表查表**（r116 已声明）。
  ③ `.js` 按钮脚本由"解析期 `<script src>`"改为"挂载后动态注入"（r115 加载器，幂等）。
     影响面已核：脚本内容只**定义全局函数**（`demo/test/btn.js` 只 `function test(){...}`），
     点击时才被 `handlerButtonEvent` 调用 ⇒ 时序差异不可观测。
  ④ 引导数据未就绪时：`object.code` 缺失 ⇒ **响亮告警**并照常渲染（不编造 object code）；
     按钮列表按"未就绪 ⇒ 空列表"处理（与旧栈"服务端已给"的区别在本页顶部告警里点名）。

  尚未迁移（登记）：`template/tree`、`template/tree_table`（同族模版，由宿主 `AppTemplateHost` 明确报出）。
-->
<template>
  <div id="app">
    <div
      class="eova-layout"
      style="width: calc(100% - 20px); height: calc(100% - 20px); margin: 10px"
    >
      <div class="zone" style="width: 100%; height: 100%">
        <div class="box" :style="[{ height: `${queryHeight}px` }]">
          <!-- Query -->
          <ev-form
            ref="refForm"
            mode="query"
            name="query_from"
            :object="objectCode"
            :biz="objectCode"
            v-model="data"
            @submit="onQuery()"
            @resize="doResize"
            @ready="onReady"
          ></ev-form>
        </div>
        <div
          class="box"
          :style="[
            {
              height: `calc(100% - 10px - ${queryHeight}px)`,
              top: `calc(10px + ${queryHeight}px)`
            }
          ]"
        >
          <!-- Table -->
          <ev-table
            ref="refTable"
            :object="objectCode"
            :biz="objectCode"
            :design="isAdmin"
            :page="page"
            :height="tableHeight"
            :is-edit="isCelledit"
            :where="data"
            @row-click="onRowClick"
          >
            <template #toolbar>
              <div class="eova-tools_box">
                <EovaToolbar :list="btnList" :handlers="handlers" />
              </div>
              <div class="eova-tools_box" style="position: absolute; right: 0">
                <ev-popup ref="popUserRef" trigger="hover" placement="bottom">
                  <button class="eova-btn_icon">
                    <i class="eova-icon-export"></i>
                  </button>
                  <template #content>
                    <div class="eova-select-content">
                      <ul class="eova-select_items">
                        <li @click="onExport('xlsx')">导出Excel</li>
                        <li @click="onExport('csv')">导出CSV</li>
                      </ul>
                    </div>
                  </template>
                </ev-popup>
              </div>
            </template>
          </ev-table>
        </div>
      </div>
    </div>
    <EovaAdminPanel :is-admin="isAdmin" />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import axios from 'axios'
import EovaToolbar from '@/components/EovaToolbar.vue'
import EovaAdminPanel from '@/components/EovaAdminPanel.vue'
import { getEovaMe, getEovaTools } from '@/compat/eova-runtime'
import { callUzooHook, getUzooPage, setUzooApp, setUzooPage } from '@/compat/eova-ext'
import { PAGE_URLS } from '@/compat/ui-urls'
import {
  buttonListOf,
  resolveObjectCode,
  type PageBootstrap
} from '@/compat/page-bootstrap'
import { loadButtonScripts } from '@/compat/button-scripts'
import { layerSizeOf, menuConfOf } from '@/compat/template-dispatch'

/** `ev-table` 暴露的实例方法（旧栈 `refTable.value.xxx()` 的等价；组件由冻结制品注册） */
interface EvTableInstance {
  /** 按条件重新查询 */
  query: (where: unknown) => void
  /** 取选中行 */
  getSelectRows: () => Record<string, unknown>[]
  /** 按主键移除行 */
  removeRows: (pks: unknown[]) => void
}

/** 表格 ref（旧 `refTable`） */
const refTable = ref<EvTableInstance | null>(null)
/** 查询表单 ref（旧 `refForm`；模板绑定用） */
const refForm = ref<unknown>(null)
/** 导出气泡 ref（旧 `popUserRef`；模板绑定用） */
const popUserRef = ref<unknown>(null)

const route = useRoute()

/** 菜单编码：旧 `#(menu.code)`（也是 `AppController#index()` 的 `get(0)`） */
const menuCode = ((): string => {
  const p = route.params.menuCode
  return typeof p === 'string' ? p : ''
})()

/**
 * 引导数据（旧栈是渲染期插值，本页从**宿主** `AppTemplateHost` 传入 —— 宿主已为分派取过一次，
 * 再取一次就是重复请求；见宿主文件头"为什么引导数据由宿主加载"）
 */
const props = defineProps<{ bootstrap: PageBootstrap }>()

/**
 * `object_code`（旧 `#(object.code)`）—— 引导数据 > URL 参数 `object` > 无
 *
 * ★ 取不到时**不编造**：告警并按空串渲染（旧栈此值必然存在，因为它是渲染前提）。
 */
const resolvedObject = computed(() =>
  resolveObjectCode(props.bootstrap, props.bootstrap.url.object, undefined)
)
const objectCode = computed(() => resolvedObject.value.code)

/** 是否超管（旧 `#(loginUser.isAdmin)`）——用于 `ev-table` 的 `design` 与超管面板 */
const isAdmin = computed(() => props.bootstrap.loginUser?.isAdmin === true)

/**
 * 单元格编辑开关（旧 `#(object.is_celledit??false)`）
 *
 * ★ 用的是 **`??`**（只兜 null/undefined），不是 `||` —— 原样保留。
 */
const isCelledit = computed(() => (props.bootstrap.object?.is_celledit ?? false) as boolean)

/** 按钮列表（旧 `btnList`；引导数据未就绪 ⇒ 空列表，见文件头适配 ④） */
const btnList = computed(() => buttonListOf(props.bootstrap) ?? [])

/** 分页（旧 `page = reactive({page:1, limit:15})`） */
const page = reactive({ page: 1, limit: 15 })

/** 查询条件（旧 `data = reactive({})`，被 `v-model` 绑到 ev-form / `where` 绑到 ev-table） */
const data = reactive<Record<string, unknown>>({})

/** 既有死值（旧 `auths = ref()`：只被注释掉的取权限代码写，仍被返回 ⇒ 保留） */
const auths = ref<unknown>(undefined)

/** 查询容器实时高度（旧 `let queryHeight = ref(0)`） */
const queryHeight = ref(0)
/** 表格高度（旧 `let tableHeight = ref(600)`） */
const tableHeight = ref(600)

/** 当前选中行（旧 `currentRow = ref({})`） */
const currentRow = ref<Record<string, unknown>>({})
/** 是否显示子表（旧 `showLinking = ref(false)`；子表本身是既有死代码，但该标志仍被读写） */
const showLinking = ref(false)

/**
 * 查询（旧 `onQuery`）
 */
function onQuery(): void {
  refTable.value?.query(data)
  // 查询后关闭级联显示
  showLinking.value = false
}

/**
 * 查询表单大小变化（旧 `doResize`：★ 计算放在 `nextTick` 里）
 *
 * @param height 表单实测高度
 */
function doResize(height: number): void {
  nextTick(() => {
    queryHeight.value = height
    // 总高度 - form - 间距
    tableHeight.value = getEovaTools().dom.getViewSize().height - height - 30
    console.log('form resize:' + height)
  })
}

/**
 * 表单动态构建完成（旧 `onReady`：`uzoo.vue.onReady` 存在才调）
 *
 * @param fieldInstances 字段实例集合
 */
function onReady(fieldInstances: unknown): void {
  callUzooHook('onReady', [fieldInstances])
}

/** 弹层宽高（旧 `LW`/`LH`，默认高 660 是 table 模版自己的口径） */
const layerSize = computed(() => layerSizeOf(menuConfOf(props.bootstrap), 720, 660))

/** 页面方法表（`is_base=1` 的内置按钮按 `btn.event` 查它；见 EovaToolbar 的说明） */
const handlers: Record<string, (() => void) | undefined> = {
  onQuery,
  onAdd,
  onUpdate,
  onDetail,
  onHide,
  onDelete,
  onImport,
  onExport
}

/**
 * 新增（旧 `onAdd`）
 */
function onAdd(): void {
  const x = getEovaTools()
  const url = x.str.template('/app/add/{{object_code}}?biz={{code}}', getUzooPage())
  getEovaMe().layer.open('新增数据', url, layerSize.value.width, layerSize.value.height, () => {
    getEovaMe().layer.msg('操作成功！')
    onQuery()
  })
}

/**
 * 取选中行；未选 ⇒ 提示并返回 null（旧各 handler 开头的同一段）
 *
 * @param emptyMessage 未选行时的提示文案（旧实现三处文案不同：'请先选择一行数据' / '请先选择数据'）
 * @returns 选中行；未选返回 null
 */
function requireSelectRows(emptyMessage: string): Record<string, unknown>[] | null {
  const rows = refTable.value?.getSelectRows() ?? []
  if (getEovaTools().isEmpty(rows)) {
    getEovaMe().layer.msg(emptyMessage)
    return null
  }
  return rows
}

/**
 * 修改（旧 `onUpdate`）
 */
function onUpdate(): void {
  const rows = requireSelectRows('请先选择一行数据')
  if (!rows) {
    return
  }
  const x = getEovaTools()
  const uzoo = getUzooPage()
  const url = x.str.template('/app/update/{{object_code}}?id={{id}}&biz={{menu_code}}', {
    object_code: uzoo['object_code'],
    menu_code: uzoo['code'],
    // ★ 旧实现取的是**硬编码 `row.id`**（不是 `object_pk`）—— 原样保留
    id: rows[0]['id']
  })
  getEovaMe().layer.open('修改数据', url, layerSize.value.width, layerSize.value.height, () => {
    getEovaMe().layer.msg('操作成功！')
    onQuery()
  })
}

/**
 * 查看（旧 `onDetail`：done 是**空函数**）
 */
function onDetail(): void {
  const rows = requireSelectRows('请先选择一行数据')
  if (!rows) {
    return
  }
  const x = getEovaTools()
  const uzoo = getUzooPage()
  const url = x.str.template('/app/detail/{{object_code}}?id={{id}}&biz={{menu_code}}', {
    object_code: uzoo['object_code'],
    menu_code: uzoo['code'],
    id: rows[0]['id']
  })
  getEovaMe().layer.open('查看数据', url, layerSize.value.width, layerSize.value.height, () => {})
}

/**
 * 删除/隐藏的公共后半段（旧 `onDelete`/`onHide` 除了文案与 URL 之外完全相同）
 *
 * @param confirmMessage 确认框文案（'确认彻底删除, 不可恢复' / '确认删除'）
 * @param urlTemplate 提交 URL 模板（`urls.form.delete` / `urls.form.hide`）
 */
function doRemove(confirmMessage: string, urlTemplate: string): void {
  const rows = requireSelectRows('请先选择数据')
  if (!rows) {
    return
  }
  const me = getEovaMe()
  const x = getEovaTools()

  me.layer.confirm?.(confirmMessage, () => {
    const url = x.str.template(urlTemplate, getUzooPage())
    axios
      .post(url, { rows })
      .then((res) => {
        const ret = res.data as { state?: string; msg?: string }
        if (ret.state === 'ok') {
          const pks = rows.map((row) => row[getUzooPage()['object_pk'] as string])
          refTable.value?.removeRows(pks)
          me.layer.msg('删除成功')
        } else {
          me.layer.msg(String(ret.msg))
        }
      })
      .catch((_e) => {
        // ★ 旧实现此处引用**未声明的 `error`**（形参是 `e`）⇒ ReferenceError：既有缺陷，原样保留
        // @ts-expect-error 旧缺陷：`error` 未定义（见文件头与 r112 同族记录），不得"顺手修正"
        me.layer.msg('客户端请求异常: ' + error.message)
      })
  })
}

/**
 * 彻底删除（旧 `onDelete`）
 */
function onDelete(): void {
  doRemove('确认彻底删除, 不可恢复', PAGE_URLS.form.delete)
}

/**
 * 隐藏（旧 `onHide`）
 */
function onHide(): void {
  doRemove('确认删除', PAGE_URLS.form.hide)
}

/**
 * 导出（旧 `onExport`：`x.axios.download` 走制品实现）
 *
 * @param type 导出类型（'xlsx' / 'csv'）—— 可缺省：内置按钮走方法表时旧栈同样是**无参调用**
 */
function onExport(type?: string): void {
  const uzoo = getUzooPage()
  const url = `/excel/export/${String(uzoo['object_code'])}?type=${type}&biz=${
    (uzoo['menu_code'] as string) || ''
  }`
  const fileName = `${String(uzoo['object_name'])}.${type}`
  void getEovaTools().axios.download(url, data, fileName, type)
}

/**
 * 导入（旧 `onImport`：旧栈**就是 stub** —— `console.log('导入')` + `msg('待实现...')`）
 */
function onImport(): void {
  console.log('导入')
  getEovaMe().layer.msg('待实现...')
}

/**
 * 行点击（旧 `onRowClick`：主子表联动的核心 API）
 *
 * @param row 行数据
 */
function onRowClick(row: Record<string, unknown>): void {
  console.log(row)
  currentRow.value = row
  showLinking.value = true
  // 监听提交通知
  getEovaMe().cross.emit('eova-table-row_click', currentRow.value['id'])
}

/**
 * 取 URL 查询串（旧 `getUrlSearch`）
 *
 * @returns 查询参数对象
 */
function getUrlSearch(): Record<string, string> {
  return Object.fromEntries(new URLSearchParams(window.location.search))
}

// ---- setup 期：写 `uzoo.page` 的同步部分 + 挂 `uzoo.app`（旧 index.js:274-275）----

// 旧 index.html:88-92 的内联脚本（模板/表单元数据）
setUzooPage('code', menuCode)
setUzooPage('template', 'table')
setUzooPage('form', 'query')

/** `uzoo.vue.setup()` 的返回值（旧 `data_`；未注册钩子时为空对象） */
const hookData = ((): Record<string, unknown> => {
  const v = callUzooHook('setup')
  return v != null && typeof v === 'object' ? (v as Record<string, unknown>) : {}
})()

// 旧实现：`return uzoo.app = {...data_, data, auths, refForm, refTable, queryHeight, tableHeight, page,
// currentRow, onQuery, onAdd, onUpdate, onDetail, onHide, onDelete, onImport, onExport, doResize,
// onReady, onRowClick, showLinking}`
setUzooApp({
  ...hookData,
  data,
  auths,
  refForm,
  refTable,
  queryHeight,
  tableHeight,
  page,
  currentRow,
  onQuery,
  onAdd,
  onUpdate,
  onDetail,
  onHide,
  onDelete,
  onImport,
  onExport,
  doResize,
  onReady,
  onRowClick,
  showLinking
})

onMounted(async () => {
  console.log('onMounted')

  // URL 查询条件覆盖（旧实现：★ **不重新查询**，`refTable.value.query(data)` 被注释）
  const query = getUrlSearch()
  if (Object.keys(query).length > 0) {
    Object.assign(data, query)
    console.log('默认查询条件' + JSON.stringify(data))
  }

  if (resolvedObject.value.source === 'missing') {
    console.warn(
      '[template/table] 缺少 object.code（引导数据与 URL 参数都没有）⇒ `ev-form`/`ev-table` 拿不到元对象。' +
        '旧栈此值由 `AppController#index()` 渲染期 `set("object", …)` 提供，分离后需要页面引导端点（DES-004）。'
    )
  }
  // 旧 index.html:83-87：把 `btn.ui` 里含 `.js` 的按钮脚本以 `<script src>` 注入
  await loadButtonScripts(btnList.value)

  // 旧 `_page/list.html` 的 `uzoo.page` 赋值（渲染期插值 ⇒ 引导数据到达后写入）
  const menu = props.bootstrap.menu
  const object = props.bootstrap.object
  if (menu) {
    setUzooPage('menu_id', menu['id'])
    setUzooPage('menu_name', menu['name'])
    setUzooPage('menu_code', menu['code'])
  }
  if (object) {
    setUzooPage('object_id', object['id'])
    setUzooPage('object_code', object['code'])
    setUzooPage('object_name', object['name'])
    setUzooPage('object_pk', object['pk'])
  }
  // `menu_conf` 旧栈是**对象字面量文本**（`#(menu.conf)` 不带引号）⇒ 这里写解析后的对象
  setUzooPage('menu_conf', menuConfOf(props.bootstrap))
})

defineExpose({
  refForm,
  refTable,
  popUserRef,
  page,
  data,
  queryHeight,
  tableHeight,
  btnList,
  objectCode,
  handlers,
  onQuery,
  onAdd,
  onUpdate,
  onDetail,
  onHide,
  onDelete,
  onImport,
  onExport,
  doResize,
  onReady,
  onRowClick,
  showLinking,
  currentRow
})
</script>
