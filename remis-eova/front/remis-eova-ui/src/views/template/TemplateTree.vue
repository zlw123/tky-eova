<!--
  树模版页（第 119 轮）—— 旧 `_view/template/tree/index.html`(66 行) + `index.js`(292 行) 的等价物

  ## 它服务谁（种子数据取证）

  `eova_menu` 里 `template='tree'` 的菜单有 **3 个**（`table` 24 个、`tree_table` 2 个）：
  `meta_menu`（菜单管理）、`meta_test_area`（省市区）等 —— 即**平台自身的菜单管理页**就是树模版，
  不是演示页。

  ## 结构（逐条对齐旧 index.html）

  · `.eova-layout` > `.zone`(100%/100%)，里面是**左右两栏**：
    ① 左 `.box`（`width:250px;overflow:auto`）内 `<ev-tree ref="refTree" object=object.code :multiple="true"
       :conf="treeConf" @node-click="onTeeClick" v-model:checked="treeChecked">`
    ② 右**嵌套 `.zone`**（`width: calc(100% - 10px - 250px)`、`left: calc(10px + 250px)`）内两个 `.box`：
       - 工具条盒（`height:43px`）内 `.eova-tools_box`（`_block/toolbar.html` 的等价物 = `EovaToolbar`）
       - 表单盒（`height: calc(100% - 10px - 43px)`、`top: calc(43px + 10px)`）：
         `treeId > 0` ⇒ `<ev-form mode="update" name="tree_from" biz=menu.code object=object.code :pk="treeId">`；
         否则 ⇒ `<h2 style="color:#b3afaf; margin:5px" v-html="conf.tips||''">`（**`v-html`**，内容来自菜单配置）
  · `.eova-layout` 的兄弟节点：`_block/admin.html`（超管面板）
  · 末尾 `.js` 按钮脚本注入 + 内联 `uzoo.page.code/template='tree'/form='query'`

  ## 行为（逐条对齐旧 index.js；括号内是行号）

  · `conf = uzoo.page.menu_conf`（13）；`LW = conf.layer_width||720`、`LH = conf.layer_height||720`（15）
    —— ★ 与 table 模版不同：**tree 的默认高是 720**
  · `treeConf = {root: conf.root, id: conf.id, pid: conf.pid, title: conf.name, icon: conf.icon||'', spread: conf.spread||''}`（33-40）
    —— ★ `title` 取的是 `conf.name`（键名映射，不是同名）
  · `x.log(treeConf)`（51）
  · `onTeeClick(node)`(86)：`me.layer.msg(\`编辑 ${node.name}\`)` + `treeId = node.id`
    （旧实现是先提示后记 id；两者互不依赖 ⇒ **顺序本身不可观测**，本页按原顺序书写但不声称有判据钉它）
  · `onAdd()`(111)：`/app/add/{{object_code}}?ref={{ref}}`，其中 `ref` 是 **`\${menu_conf.pid}:${treeId}`**（树节点自动带父键）
  · `onSave(id)`(124)：`treeId==0` ⇒ `msg('请选择需要编辑的树节点')`；`refForm.validate()==false` ⇒ 直接返回；
    取 `refForm.getData()`；URL 走 **`me.urls.url('form_update', props)`**（制品自带 URL 表，不是我方拼串）；
    ok ⇒ `refTree.reload()` + `msg('操作成功！')`；非 ok ⇒ `msg(ret.msg)` + `console.log('加载错误')`；
    ★ 这里的 `.catch((error))` 形参**就是 `error`** ⇒ 无缺陷
  · `onHide()`(167)：勾选数为 0 ⇒ `msg('请先勾选需要删除的数据')`；`confirm('确认删除')`；
    URL `x.str.template('/api/form/hide/{{object_code}}', props)`；rows 由 `treeChecked` 映射成 `{[object_pk]: id}`；
    ok ⇒ `reload()` + `msg('删除成功')`
  · `onDelete()`(208)：同上，`confirm('确认彻底删除')` + URL 走 `urls.form.delete`
  · ★ **`.catch((e))` 里引用未声明的 `error`（202/237）⇒ ReferenceError** —— 既有缺陷，原样保留（与 table 同族）
  · `onQuery()`(243)：**`refTree.reload()`**（这里是树重载，不是表格查询）
  · `onImport()`(247)：旧栈**就是 stub**
  · 结尾(255-284)：`return uzoo.app = {conf, auths, refTable, queryHeight, tableHeight, page, form, json,
    cityData, refTree, refForm, treeId, treeConf, treeChecked, onQuery, onAdd, onTeeClick, onSave, onDelete, onHide, onImport}`
    —— ★ 比 table 少 `onReady`/`doResize`/`onRowClick`/`onExport`（树页没有这些方法）

  ## 既有死代码（**不迁移**，登记在案）

  · `refTable`(25)、`queryHeight`(27)、`tableHeight`(28)、`auths`(30) —— 树页不用，只在注释掉的"取设置/取权限"代码里出现，仍被返回
  · `page`(21)、`form`(23) —— 只被返回，从未被读
  · `cityData`(93-105) —— 演示数据（11 条省市），只被返回
  · `json`(252) —— 演示串 `{"objectCode":"goods_style","params":{}}`，只被返回
  · `index.html:50-52` 的空 `<script>` 块；`index.html` 的 `#include(..., type="list")` 参数（admin.html 无 `#(type)`）
  · `onMounted`(54-84) 里两段取设置/取权限的代码**全被注释** ⇒ 本页**不自动取任何东西**（只打日志）

  ## 已声明适配（非静默改写）

  ① 与 `TemplateTable` 同：服务端插值改由引导数据提供（`menu_conf`/`object.code`/`btnList`/`loginUser`）；
     同步已知的三项（`code`/`template`/`form`）在 setup 期写 `uzoo.page`，其余在引导数据到达后写。
  ② `.js` 按钮脚本由解析期 `<script src>` 改为挂载后动态注入（r115 加载器，幂等）。
  ③ `me.urls.url('form_update', props)` **原样调用制品 API**（URL 表在冻结制品 `eovaui.js` 里，
     键 `form_update = "/api/form/update/{{object_code}}"`）—— 不自己拼串，避免两套 URL 表。
  ④ `me.vue.mount(app, …)` 的 SPA 等价形态仍**待用户口径**（同 table）。

  尚未迁移（登记）：`template/tree_table`（同族，宿主会明确报出）。
-->
<template>
  <div id="app">
    <div
      class="eova-layout"
      style="width: calc(100% - 20px); height: calc(100% - 20px); margin: 10px"
    >
      <div class="zone" style="width: 100%; height: 100%">
        <div class="box" style="width: 250px; overflow: auto">
          <div>
            <ev-tree
              ref="refTree"
              :object="objectCode"
              :multiple="true"
              :conf="treeConf"
              @node-click="onTeeClick"
              v-model:checked="treeChecked"
            ></ev-tree>
          </div>
        </div>
        <div
          class="zone"
          style="width: calc(100% - 10px - 250px); left: calc(10px + 250px)"
        >
          <div class="box" style="height: 43px">
            <div class="eova-tools_box">
              <EovaToolbar :list="btnList" :handlers="handlers" />
            </div>
          </div>
          <div class="box" style="height: calc(100% - 10px - 43px); top: calc(43px + 10px)">
            <template v-if="treeId > 0">
              <ev-form
                ref="refForm"
                mode="update"
                name="tree_from"
                :biz="menuCode"
                :object="objectCode"
                :pk="treeId"
              ></ev-form>
            </template>
            <template v-else>
              <h2 style="color: #b3afaf; margin: 5px" v-html="conf.tips || ''"></h2>
            </template>
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
import { buttonListOf, resolveObjectCode, type PageBootstrap } from '@/compat/page-bootstrap'
import { loadButtonScripts } from '@/compat/button-scripts'
import { layerSizeOf, menuConfOf } from '@/compat/template-dispatch'

/** `ev-tree` 暴露的实例方法（旧 `refTree.value.xxx()` 的等价；组件由冻结制品注册） */
interface EvTreeInstance {
  /** 重新加载树数据（旧实现里"刷新"的唯一手段） */
  reload: () => void
}

/** `ev-form` 暴露的实例方法（旧 `refForm.value.xxx()` 的等价） */
interface EvFormInstance {
  /** 表单校验；未过返回 false */
  validate: () => boolean
  /** 取表单数据 */
  getData: () => Record<string, unknown>
}

/** 树 ref（旧 `refTree`） */
const refTree = ref<EvTreeInstance | null>(null)
/** 表单 ref（旧 `refForm`） */
const refForm = ref<EvFormInstance | null>(null)

/** 引导数据（由宿主 `AppTemplateHost` 传入，见其文件头） */
const props = defineProps<{ bootstrap: PageBootstrap }>()

/** 菜单编码（旧 `#(menu.code)`；也是 `AppController#index()` 的 `get(0)`） */
const menuCode = ((): string => {
  const p = useRoute().params.menuCode
  return typeof p === 'string' ? p : ''
})()

/** 菜单配置（旧 `uzoo.page.menu_conf`；形态见 `menuConfOf` 的取证） */
const conf = computed(() => menuConfOf(props.bootstrap))

/** 弹层宽高（旧 `LW`/`LH`；★ tree 的默认高是 720，table 是 660） */
const layerSize = computed(() => layerSizeOf(conf.value, 720, 720))

/** 元对象编码（旧 `#(object.code)`；取不到时告警并留空，见 TemplateTable 的同一口径） */
const objectCode = computed(() => resolveObjectCode(props.bootstrap, props.bootstrap.url.object).code)

/** 是否超管（旧 `#(loginUser.isAdmin)`，用于超管面板） */
const isAdmin = computed(() => props.bootstrap.loginUser?.isAdmin === true)

/** 按钮列表（旧 `btnList`） */
const btnList = computed(() => buttonListOf(props.bootstrap) ?? [])

/** 树配置（旧 `treeConf`：`title` 取 `conf.name`，`icon`/`spread` 缺省为空串） */
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

/** 当前选中/编辑的树节点 id（旧 `treeId = ref(0)`） */
const treeId = ref(0)
/** 勾选的节点 id 列表（旧 `treeChecked = ref([])`，`v-model:checked` 绑定） */
const treeChecked = ref<unknown[]>([])

/** 既有死值（旧实现里只被注释掉的代码用、但仍被返回）：`page`/`form`/`refTable`/`queryHeight`/`tableHeight`/`auths` */
const page = reactive({ page: 1, limit: 15 })
const form = reactive<Record<string, unknown>>({})
const refTable = ref<unknown>(undefined)
const queryHeight = ref(0)
const tableHeight = ref(600)
const auths = ref<unknown>(undefined)

/** 既有死值：旧实现的演示数据（11 条省市），只被返回 */
const cityData = [
  { id: 1, pid: 0, name: '中国', lv: 1, spread: true },
  { id: 2, pid: 1, name: '北京', lv: 2 },
  { id: 3, pid: 1, name: '上海', lv: 2 },
  { id: 4, pid: 1, name: '广州', lv: 2 },
  { id: 5, pid: 1, name: '深圳', lv: 2 },
  { id: 6, pid: 1, name: '武汉', lv: 2 },
  { id: 7, pid: 6, name: '武昌', lv: 3 },
  { id: 8, pid: 6, name: '光谷', lv: 3 },
  { id: 9, pid: 6, name: '汉口', lv: 3 },
  { id: 10, pid: 1, name: '台湾', lv: 2 },
  { id: 11, pid: 1, name: '海南', lv: 2 }
]

/** 既有死值：旧实现的演示串（只被返回） */
const json = ref('{"objectCode":"goods_style","params":{}}')

/**
 * 点树节点（旧 `onTeeClick`：先提示再记 id —— 两者互不依赖，顺序不可观测）
 *
 * @param node 节点数据（`name`/`id` 来自树配置里的 `title`/`id` 列）
 */
function onTeeClick(node: { id: number; name?: string }): void {
  getEovaMe().layer.msg(`编辑 ${node.name}`)
  treeId.value = node.id
}

/**
 * 新增（旧 `onAdd`：`ref` 参数是 `\${menu_conf.pid}:${treeId}`，即"父键列:当前节点"）
 */
function onAdd(): void {
  const x = getEovaTools()
  const url = x.str.template('/app/add/{{object_code}}?ref={{ref}}', {
    object_code: getUzooPage()['object_code'],
    // Tree Pid 自动传参
    ref: `${String((getUzooPage()['menu_conf'] as Record<string, unknown>)?.['pid'])}:${
      treeId.value
    }`
  })
  getEovaMe().layer.open('新增数据', url, layerSize.value.width, layerSize.value.height, () => {
    getEovaMe().layer.msg('操作成功！')
    onQuery()
  })
}

/**
 * 保存当前树节点（旧 `onSave`：URL 走**制品自带的 `me.urls` 表**）
 */
function onSave(): void {
  const me = getEovaMe()
  if (treeId.value === 0) {
    me.layer.msg('请选择需要编辑的树节点')
    return
  }
  if (refForm.value?.validate() === false) {
    return
  }

  const data = refForm.value?.getData()
  const url = me.urls.url('form_update', getUzooPage()) as string
  axios
    .post(url, data)
    .then((res) => {
      const ret = res.data as { state?: string; msg?: string }
      console.log(JSON.stringify(ret))
      if (ret.state === 'ok') {
        refTree.value?.reload()
        me.layer.msg('操作成功！')
      } else {
        me.layer.msg(String(ret.msg))
        // 旧实现此处即 `console.log('加载错误')`（原样保留）
        console.log('加载错误')
      }
    })
    .catch((error: Error) => {
      me.layer.msg('客户端请求异常: ' + error.message)
    })
}

/**
 * 勾选节点 ⇒ 统一 rows 结构（旧 `onHide`/`onDelete` 里同一段）
 *
 * @param objectPk 主键列名（旧 `props.object_pk`）
 * @returns `{[object_pk]: id}` 的数组
 */
function checkedRows(objectPk: unknown): Record<string, unknown>[] {
  const rows: Record<string, unknown>[] = []
  treeChecked.value.forEach((id) => {
    rows.push({ [String(objectPk)]: id })
  })
  return rows
}

/**
 * 隐藏/彻底删除的公共后半段（旧 `onHide`/`onDelete` 除文案与 URL 外完全相同）
 *
 * @param confirmMessage 确认框文案（'确认删除' / '确认彻底删除'）
 * @param urlTemplate 提交 URL 模板（`/api/form/hide/{{object_code}}` / `urls.form.delete`）
 */
function doRemove(confirmMessage: string, urlTemplate: string): void {
  if (treeChecked.value.length === 0) {
    getEovaMe().layer.msg('请先勾选需要删除的数据')
    return
  }
  const me = getEovaMe()
  const x = getEovaTools()

  me.layer.confirm?.(confirmMessage, () => {
    const props2 = getUzooPage()
    const url = x.str.template(urlTemplate, props2)
    const rows = checkedRows(props2['object_pk'])
    axios
      .post(url, { rows })
      .then((res) => {
        const ret = res.data as { state?: string; msg?: string }
        if (ret.state === 'ok') {
          refTree.value?.reload()
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
 * 隐藏（旧 `onHide`）
 */
function onHide(): void {
  doRemove('确认删除', '/api/form/hide/{{object_code}}')
}

/**
 * 彻底删除（旧 `onDelete`）
 */
function onDelete(): void {
  doRemove('确认彻底删除', PAGE_URLS.form.delete)
}

/**
 * 刷新（旧 `onQuery`：树页是 **`refTree.reload()`**，不是表格查询）
 */
function onQuery(): void {
  refTree.value?.reload()
}

/**
 * 导入（旧 `onImport`：旧栈**就是 stub**）
 */
function onImport(): void {
  console.log('导入')
  getEovaMe().layer.msg('待实现...')
}

/** 页面方法表（`is_base=1` 的内置按钮按 `btn.event` 查它） */
const handlers: Record<string, (() => void) | undefined> = {
  onQuery,
  onAdd,
  onSave,
  onDelete,
  onHide,
  onImport
}

// ---- setup 期：`uzoo.page` 的同步三项 + 挂 `uzoo.app`（旧 index.js:255-284）----
setUzooPage('code', menuCode)
setUzooPage('template', 'tree')
setUzooPage('form', 'query')

// 旧实现：`return uzoo.app = {conf, auths, refTable, queryHeight, tableHeight, page, form, json, cityData,
// refTree, refForm, treeId, treeConf, treeChecked, onQuery, onAdd, onTeeClick, onSave, onDelete, onHide, onImport}`
setUzooApp({
  conf,
  auths,
  refTable,
  queryHeight,
  tableHeight,
  page,
  form,
  json,
  cityData,
  refTree,
  refForm,
  treeId,
  treeConf,
  treeChecked,
  onQuery,
  onAdd,
  onTeeClick,
  onSave,
  onDelete,
  onHide,
  onImport
})

onMounted(async () => {
  console.log('crud.js init')

  const tools = getEovaTools()
  tools.log(treeConf.value)

  if (objectCode.value === '') {
    console.warn(
      '[template/tree] 缺少 object.code（引导数据与 URL 参数都没有）⇒ `ev-tree`/`ev-form` 拿不到元对象。' +
        '旧栈此值由 `AppController#index()` 渲染期 `set("object", …)` 提供，分离后需要页面引导端点（DES-004）。'
    )
  }

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

  // 旧 index.html:54-59：把 `btn.ui` 里含 `.js` 的按钮脚本以 `<script src>` 注入
  await loadButtonScripts(btnList.value)
})

defineExpose({
  conf,
  treeConf,
  treeId,
  treeChecked,
  refTree,
  refForm,
  objectCode,
  menuCode,
  handlers,
  cityData,
  json,
  page,
  onTeeClick,
  onAdd,
  onSave,
  onHide,
  onDelete,
  onQuery,
  onImport
})
</script>
