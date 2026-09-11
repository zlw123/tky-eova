<!--
  树表模版页（第 120 轮）—— 旧 `_view/template/tree_table/index.html`(92 行) + `index.js`(212 行) 的等价物

  ## 它是三个列表模版里的第三种形态

  | 模版 | 左侧 | 查询/表格的数据源 | 工具条 | 导出 |
  |---|---|---|---|---|
  | `table` | 无 | `object.code`（服务端插值） | 有 | `onExport('xlsx'\|'csv')` |
  | `tree` | `ev-tree`（`object.code`，单选/多选联动表单） | 无表格 | 有（含保存） | 无 |
  | **`tree_table`** | `ev-tree`（★ **`conf.tree_object_code`**，`multiple=false`） | ★ **`conf.object_code`** | 有 | ★ 模板里有导出按钮，但**本页没有实现 `onExport`** |

  ## 结构（逐条对齐旧 index.html）

  · `.eova-layout` > `.zone`(100%/100%)：
    ① 左 `.box` **`width:200px`**（tree 是 250px）内 `<ev-tree ref="refTree" :multiple="false"
       :object="conf.tree_object_code" :conf="treeConf" @node-click="onTeeClick" v-model:checked="treeChecked">`
    ② 右嵌套 `.zone`（`width: calc(100% - 10px - 200px)`、`left: calc(10px + 200px)`）内两个 `.box`：
       - 查询盒（`:style="[{height: \`${queryHeight}px\`}]"`）内 `<ev-form mode="query" :object="conf.object_code"
         :biz="conf.object_code" v-model="data" @submit="onQuery()" @resize="doResize">` —— ★ **没有 `@ready`**（table 有）
       - 表格盒（高度/top 由 `queryHeight` 算）内 `<ev-table :design="loginUser.isAdmin" :page="page"
         :object="conf.object_code" :biz="conf.object_code" :height="tableHeight">`
         —— ★ **没有 `:is-edit` / `:where` / `@row-click`**（table 三者都有）
         `#toolbar` 槽：左 `.eova-tools_box`（`EovaToolbar`）+ 右导出气泡（`onExport('xls')` / `onExport('csv')`，
         文案 **导出XLS文件 / 导出CSV文件** —— 与 table 的 `xlsx`/导出Excel **不同**）
  · `.eova-layout` 的兄弟节点：`#include("/eova/_view/_block/admin.html")` —— ★ **不带 `type="list"` 参数**（table 带）
  · 末尾 `.js` 按钮脚本注入 + 内联 `uzoo.page.code/template='tree_table'/form='query'`

  ## 行为（逐条对齐旧 index.js；括号内是行号）

  · `conf = uzoo.page.menu_conf`（14）；`LW = conf.layer_width||720`、`LH = conf.layer_height||720`（16）
  · `treeConf = {root, id, pid, title: conf.name, icon: conf.icon||'', spread: conf.spread||''}`（45-52，与 tree 同）
  · `onQuery()`(32)：`refTable.value.query(data)` —— ★ **没有** `showLinking`（table 才有）
  · `doResize(height)`(37)：★ **不包 `nextTick`**（table 包了！）：立即写 `queryHeight` 与
    `tableHeight = x.dom.getViewSize().height - height - 30`，再 `console.log('form resize:' + height)`
  · `onTeeClick(node)`(60)：`me.layer.msg(\`选择 ${node.name}\`)`（★ 文案是 **"选择"**，tree 是"编辑"）；
    ★ **`data[conf.tree_query_field] = node.id`**（把树节点写成查询条件）然后 `onQuery()`；
    ★ **不设置 `treeId`**（tree 会设）⇒ `treeId` 在本页恒为 0（仍被返回）
  · `onAdd()`(79)：`/app/add/{{object_code}}` —— ★ **没有 `?ref=`**（tree 有）
  · `onUpdate()`(86)：未选行 ⇒ `msg('请先选择一行数据')`；URL `/app/update/{{object_code}}?id={{id}}`
    —— ★ **没有 `&biz=`**（table 有）；`id` 同样取**硬编码 `rows[0].id`**（不是 `object_pk`）
  · `onDetail()`(101)：同上，标题 '查看数据'，done 是**空函数**
  · `onDelete()`(114)：未选行 ⇒ `msg('请先选择数据')`；`confirm('确认彻底删除, 不可恢复')`；
    URL 走 `urls.form.delete`；ok ⇒ `removeRows(rows.map(r => r[object_pk]))` + `msg('删除成功')`
  · `onHide()`(142)：`confirm('确认删除')` + `urls.form.hide`
  · ★ 两处 `.catch((e))` 引用未声明的 `error`（137/165）⇒ **ReferenceError** —— 既有缺陷，原样保留
  · `onImport()`(171)：旧栈**就是 stub**
  · `onMounted()`(55)：只 `console.log("tree_table js Mounted")`
  · 结尾(177-204)：`return uzoo.app = {data, conf, auths, refTable, queryHeight, tableHeight, page,
    refTree, refForm, treeId, treeConf, treeChecked, onQuery, onTeeClick, onAdd, onUpdate, onDelete,
    onDetail, onHide, onImport, doResize}`（**21 个键**，顺序即旧实现顺序）

  ## ★ 导出按钮：旧模板调了一个**本页不存在**的方法

  `index.html:63-64` 的 `@click="onExport('xls')"` / `onExport('csv')` 在旧 `index.js` 里**没有对应函数**
  （`grep` 全文件只有这一处引用）⇒ 旧栈点击会 `ReferenceError: onExport is not defined`，
  **按钮点了没反应**（Vue 渲染期还会 warn「Property "onExport" was accessed during render but is not defined」）。

  SPA 侧的处置（**已声明适配**，与 `EovaToolbar` 的"方法缺失"同一口径）：模板仍按旧标记调用 `onExport`，
  但本页提供一个**响亮告警**的同名函数（点名"旧实现没有实现它"），**不做任何导出** ——
  既不静默假装成功，也不让 Vue 编译期直接断（模板里写未声明的标识符在 SFC + vue-tsc 下无法通过）。

  ## 既有死代码（**不迁移**，登记在案）

  · `treeId`(19)：本页从不写它（`onTeeClick` 不设），但被返回
  · `refForm`(76)：只被返回（本页没有表单方法调用；`tree` 才有 `validate/getData`）
  · `treeChecked`(77)：被返回并 `v-model:checked` 绑定，但**从不被读**（旧注释写明"Tree多选模式SQL条件相对复杂，待后续自定义模版定制"）
  · `page`/`auths`/`queryHeight`/`tableHeight`：`page` 与高度确实在用；`auths` 只被返回
  · `index.html` 的两行 HTML 注释（`<!-- {{form}} -->`）

  尚未迁移（登记）：`template/form/{add,update,detail}`（**归口待用户口径**：保留后端渲染 vs 迁成 SPA 路由）。
-->
<template>
  <div id="app">
    <div
      class="eova-layout"
      style="width: calc(100% - 20px); height: calc(100% - 20px); margin: 10px"
    >
      <div class="zone" style="width: 100%; height: 100%">
        <div class="box" style="width: 200px; overflow: auto">
          <div>
            <ev-tree
              ref="refTree"
              :multiple="false"
              :object="conf.tree_object_code"
              :conf="treeConf"
              @node-click="onTeeClick"
              v-model:checked="treeChecked"
            ></ev-tree>
          </div>
        </div>
        <div class="zone" style="width: calc(100% - 10px - 200px); left: calc(10px + 200px)">
          <div class="box" :style="[{ height: `${queryHeight}px` }]">
            <!-- Query -->
            <ev-form
              ref="refForm"
              mode="query"
              :object="conf.object_code"
              :biz="conf.object_code"
              v-model="data"
              @submit="onQuery()"
              @resize="doResize"
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
            <ev-table
              ref="refTable"
              :design="isAdmin"
              :page="page"
              :object="conf.object_code"
              :biz="conf.object_code"
              :height="tableHeight"
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
                          <li @click="onExport('xls')">导出XLS文件</li>
                          <li @click="onExport('csv')">导出CSV文件</li>
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
    </div>
    <EovaAdminPanel :is-admin="isAdmin" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import axios from 'axios'
import EovaToolbar from '@/components/EovaToolbar.vue'
import EovaAdminPanel from '@/components/EovaAdminPanel.vue'
import { getEovaMe, getEovaTools } from '@/compat/eova-runtime'
import { getUzooPage, setUzooApp, setUzooPage } from '@/compat/eova-ext'
import { PAGE_URLS } from '@/compat/ui-urls'
import { buttonListOf, type PageBootstrap } from '@/compat/page-bootstrap'
import { loadButtonScripts } from '@/compat/button-scripts'
import { layerSizeOf, menuConfOf } from '@/compat/template-dispatch'

/** `ev-table` 暴露的实例方法（旧 `refTable.value.xxx()` 的等价） */
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
/** 树 ref（旧 `refTree`；★ 必须保持 ref 形态，跨窗口约定见 TemplateTree 的同名判据） */
const refTree = ref<unknown>(null)
/** 表单 ref（旧 `refForm`；本页只被返回，从不调用其方法） */
const refForm = ref<unknown>(null)
/** 导出气泡 ref（旧 `popUserRef`；模板绑定用） */
const popUserRef = ref<unknown>(null)

/** 引导数据（由宿主 `AppTemplateHost` 传入） */
const props = defineProps<{ bootstrap: PageBootstrap }>()

/** 菜单编码（旧 `#(menu.code)`） */
const menuCode = ((): string => {
  const p = useRoute().params.menuCode
  return typeof p === 'string' ? p : ''
})()

/** 菜单配置（旧 `uzoo.page.menu_conf`） */
const conf = computed(() => menuConfOf(props.bootstrap))

/** 弹层宽高（旧 `LW`/`LH`；tree_table 的默认高与 tree 相同：720） */
const layerSize = computed(() => layerSizeOf(conf.value, 720, 720))

/** 是否超管（旧 `#(loginUser.isAdmin)`） */
const isAdmin = computed(() => props.bootstrap.loginUser?.isAdmin === true)

/** 按钮列表（旧 `btnList`） */
const btnList = computed(() => buttonListOf(props.bootstrap) ?? [])

/** 树配置（旧 `treeConf`：`title` 取 `conf.name`，`icon`/`spread` 缺省空串） */
const treeConf = computed(() => {
  const c = conf.value
  return {
    root: c['root'],
    id: c['id'],
    pid: c['pid'],
    title: c['name'],
    icon: c['icon'] || '',
    spread: c['spread'] || ''
  }
})

/** 表单数据（旧 `data = reactive({})`；树节点点击会往里写查询条件） */
const data = reactive<Record<string, unknown>>({})
/** 分页（旧 `page = reactive({page:1, limit:15})`） */
const page = reactive({ page: 1, limit: 15 })
/** 查询容器高度 / 表格高度（旧 `queryHeight`/`tableHeight`） */
const queryHeight = ref(0)
const tableHeight = ref(600)
/** 既有死值（旧 `auths = ref()`，只被返回） */
const auths = ref<unknown>(undefined)
/** 既有死值（旧 `treeId = ref(0)`：**本页从不写它**） */
const treeId = ref(0)
/** 勾选节点（旧 `treeChecked = ref([])`：绑定但从不被读） */
const treeChecked = ref<unknown[]>([])

/**
 * 查询（旧 `onQuery`：★ 没有 table 的 `showLinking` 那一步）
 */
function onQuery(): void {
  refTable.value?.query(data)
}

/**
 * 查询表单大小变化（旧 `doResize`：★ **不包 `nextTick`**，与 table 不同）
 *
 * @param height 表单实测高度
 */
function doResize(height: number): void {
  queryHeight.value = height
  // 总高度 - form - 间距
  tableHeight.value = getEovaTools().dom.getViewSize().height - height - 30
  console.log('form resize:' + height)
}

/**
 * 点树节点（旧 `onTeeClick`：把节点写成**查询条件**并立即查询；★ 文案是"选择"）
 *
 * @param node 节点数据
 */
function onTeeClick(node: { id: unknown; name?: string }): void {
  getEovaMe().layer.msg(`选择 ${node.name}`)
  // 获取树查询字段名
  data[String(conf.value['tree_query_field'])] = node.id
  // 点击树后 自动进行查询
  onQuery()
}

/**
 * 新增（旧 `onAdd`：★ 没有 tree 的 `?ref=` 参数）
 */
function onAdd(): void {
  const x = getEovaTools()
  const url = x.str.template('/app/add/{{object_code}}', getUzooPage())
  getEovaMe().layer.open('新增数据', url, layerSize.value.width, layerSize.value.height, () => {
    getEovaMe().layer.msg('操作成功！')
    onQuery()
  })
}

/**
 * 取选中行；未选 ⇒ 提示并返回 null（旧各 handler 开头的同一段）
 *
 * @param emptyMessage 未选行时的提示文案
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
 * 修改（旧 `onUpdate`：★ URL 没有 `&biz=`；`id` 取硬编码 `row.id`）
 */
function onUpdate(): void {
  const rows = requireSelectRows('请先选择一行数据')
  if (!rows) {
    return
  }
  const x = getEovaTools()
  const url = x.str.template('/app/update/{{object_code}}?id={{id}}', {
    object_code: getUzooPage()['object_code'],
    id: rows[0]['id']
  })
  getEovaMe().layer.open('修改数据', url, layerSize.value.width, layerSize.value.height, () => {
    getEovaMe().layer.msg('操作成功！')
    onQuery()
  })
}

/**
 * 查看（旧 `onDetail`：done 是空函数）
 */
function onDetail(): void {
  const rows = requireSelectRows('请先选择一行数据')
  if (!rows) {
    return
  }
  const x = getEovaTools()
  const url = x.str.template('/app/detail/{{object_code}}?id={{id}}', {
    object_code: getUzooPage()['object_code'],
    id: rows[0]['id']
  })
  getEovaMe().layer.open('查看数据', url, layerSize.value.width, layerSize.value.height, () => {})
}

/**
 * 删除/隐藏的公共后半段（旧 `onDelete`/`onHide` 除文案与 URL 外完全相同）
 *
 * @param confirmMessage 确认框文案
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
      .catch((_e: Error) => {
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
 * 导出（★ 旧模板调了它，但旧 `index.js` **没有实现**）
 *
 * 旧栈行为：点击 ⇒ `ReferenceError: onExport is not defined`，按钮**没反应**（渲染期还有 Vue warn）。
 * 本页按"方法缺失"的统一口径改为**响亮告警且不导出**（不静默假装成功，也不让 SFC 编译期直接断）。
 *
 * @param type 模板里传的类型（'xls' / 'csv'）—— 仅用于告警文案
 */
function onExport(type: string): void {
  console.warn(
    `[template/tree_table] 导出被点击（type=${type}），但**本模版页旧实现没有 onExport**：` +
      '`template/tree_table/index.js` 全文没有该函数（模板里的按钮是既有缺陷）。' +
      'SPA 侧不做导出，以免"点了像是成功了"；如需导出需先立单元补实现。'
  )
}

/**
 * 导入（旧 `onImport`：旧栈**就是 stub**）
 */
function onImport(): void {
  console.log('导入')
  getEovaMe().layer.msg('待实现...')
}

/** 页面方法表（`is_base=1` 的内置按钮按 `btn.event` 查它；★ 不含 `onExport`，与旧实现一致） */
const handlers: Record<string, (() => void) | undefined> = {
  onQuery,
  onAdd,
  onUpdate,
  onDelete,
  onDetail,
  onHide,
  onImport
}

// ---- setup 期：`uzoo.page` 的同步三项 + 挂 `uzoo.app`（旧 index.js:177-204）----
setUzooPage('code', menuCode)
setUzooPage('template', 'tree_table')
setUzooPage('form', 'query')

// 旧实现：`return uzoo.app = {data, conf, auths, refTable, queryHeight, tableHeight, page, refTree, refForm,
// treeId, treeConf, treeChecked, onQuery, onTeeClick, onAdd, onUpdate, onDelete, onDetail, onHide, onImport, doResize}`
setUzooApp({
  data,
  conf,
  auths,
  refTable,
  queryHeight,
  tableHeight,
  page,
  refTree,
  refForm,
  treeId,
  treeConf,
  treeChecked,
  onQuery,
  onTeeClick,
  onAdd,
  onUpdate,
  onDelete,
  onDetail,
  onHide,
  onImport,
  doResize
})

onMounted(async () => {
  console.log('tree_table js Mounted')

  getEovaTools().log(treeConf.value)

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
  setUzooPage('menu_conf', menuConfOf(props.bootstrap))

  // 旧 index.html:80-85：把 `btn.ui` 里含 `.js` 的按钮脚本以 `<script src>` 注入
  await loadButtonScripts(btnList.value)
})

defineExpose({
  data,
  conf,
  treeConf,
  page,
  queryHeight,
  tableHeight,
  treeId,
  treeChecked,
  refTable,
  refTree,
  refForm,
  popUserRef,
  menuCode,
  handlers,
  onQuery,
  doResize,
  onTeeClick,
  onAdd,
  onUpdate,
  onDetail,
  onExport,
  onDelete,
  onHide,
  onImport
})
</script>
