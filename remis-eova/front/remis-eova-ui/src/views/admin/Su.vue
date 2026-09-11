<!--
  虚拟用户切换（阶段 2 第 4 个入口页）

  契约来源（逐条对齐旧实现 `_view/user/su/app.html` + `app.js`，revision 锁定；
  后端侧取证 `cn/eova/core/admin/AdminController#su()`）：

  结构（旧 app.html）
  · `#include("/eova/_view/_page/list1.html")` ⇒ 该 partial 只提供 `window.urls` 与公共依赖
  · 外层 `.eova-layout`（`calc(100% - 20px)` / `margin:10px`）→ `.zone`（100%）→ 两个 `.box`：
    上：`.box` 高度绑 `queryHeight`，内放 `<ev-form mode="query" :object="objectCode" v-model="data" @submit="onQuery()" @resize="doResize">`
    下：`.box` 高度绑 `calc(100% - 10px - queryHeight)`、`top` 绑 `calc(10px + queryHeight)`，
        内放 `<ev-table :object="objectCode" :page="page" :height="tableHeight">`，toolbar 里一个「查询」按钮

  行为（旧 app.js）
  · `data = reactive({})`、`page = reactive({page:1, limit:15})`
  · `onQuery()` → `refTable.value.query(data)`
  · `doResize(height)` → `queryHeight = height`；`tableHeight = x.dom.getViewSize().height - height - 30`（**减 30 是间距，不是 0**）
  · `onMounted` → `me.cross.on('eova-layer-ok', onSubmit)`（弹层「确认」触发本页提交）
  · `onSubmit(id)`：
    ① `rows = refTable.value.getSelectRows()`；`x.isEmpty(rows)` ⇒ `me.layer.msg('请选择一个用户')` 并 return
    ② `user = rows[0]`；`POST /eova/admin/doSu`，载荷是**整行 user 对象**（不是挑字段）
    ③ ok ⇒ **先** `me.cross.emit('eova-layer-ok_done_data', `${user.rid}【${user.name}】`)`，
       **再** `me.cross.emit('eova-layer-ok_done', id)`
       ★ 顺序是**承重**的：`ok_done` 会让宿主 `me.layer.open` 的内部监听关层并调用其 `done()`
         （Home 的 done 会读 `currentUser` 并 `init()`）；若先发 `ok_done`，宿主读到的还是**旧**用户。
       且回传值是**格式化字符串** `${rid}【${name}】`，不是 user 对象 —— 宿主（Home）正是这么显示的
    ④ 非 ok ⇒ `me.layer.no(ret.msg)`
    ⑤ 异常 ⇒ `me.layer.msg('客户端请求异常: ' + error.message)`

  ★ `objectCode` 的来源（第 105 轮已收敛到统一契约）
  · 旧栈由服务端渲染：`setAttr("objectCode", x.conf.get("su.object.code", "eova_user_code"))`
    （`AdminController#su()`），模板里写 `#(objectCode)`。
  · 而 `su.object.code` 在 `eova_config` 里是 **`is_server = 1`** ⇒ 不在客户端 `me.conf` 白名单内
    （见 DES-003 §2.3），故**不能**从 `me.conf` 读。
  · **现口径**：走 `DES-004` 的页面引导数据接缝 —— `requireObjectCode(bs, 'eova_user_code')`：
    引导数据（端点就绪后）> URL 查询串 `?object=` > **显式声明的回退值**（库种子数据里
    `su.object.code` 的实际值）。第 104 轮的页内临时取值已删除，改为统一接缝。
  · 另注：`AdminController#su()` 在渲染前会调用 `reLogin()`（**GET 有副作用**）；
    分离后该副作用不再由前端 GET 触发，是否需要在后端保留由后端侧决定（登记）。

  已声明适配（非静默改写）
  · 旧页面是独立 HTML + `createApp` + `app.use(EovaUI)`；本页是 SPA 路由页，
    组件与 `me`/`x` 由 `src/compat/legacy-runtime.ts` 在装配期统一提供
  · 旧 `ref()` 包装的 `queryHeight`/`tableHeight` 语义不变；`SU`（旧实现里声明但**从未使用**）
    登记为既有死变量，不迁移
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
            :object="objectCode"
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
          <!-- Table -->
          <ev-table ref="refTable" :object="objectCode" :page="page" :height="tableHeight">
            <template v-slot:toolbar>
              <div class="eova-tools_box">
                <button @click="onQuery()">
                  <i class="eova-icon-search"></i>
                  查询
                </button>
              </div>
            </template>
          </ev-table>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { getEovaMe, getEovaTools } from '@/compat/eova-runtime'
import {
  loadPageBootstrap,
  readUrlParams,
  requireObjectCode,
  type PageBootstrap
} from '@/compat/page-bootstrap'

/**
 * 查询表单数据（旧 `data = reactive({})`）
 *
 * ★ 必须用 `let`：模板里是 `<ev-form v-model="data">`，而 `EvForm`（制品）在内部
 * `reactive(modelValue)` 后 **emit 的是它自己的对象**（不是父级传入的那个）⇒ `v-model`
 * 会把本绑定**整体替换**掉。用 `const` 会被编译器改写成 `let`（并在构建时告警）；
 * 显式写 `let` 语义相同且无告警。副作用：`onQuery()` 必须读**当前绑定**（闭包天然如此），
 * 而 `defineExpose` 要用 getter 暴露，否则拿到的是替换前的旧对象。
 */
let data = reactive<Record<string, unknown>>({})
/** 分页（旧 `page = reactive({page:1, limit:15})`） */
const page = reactive({ page: 1, limit: 15 })

/** 表单 ref（旧 `refForm`） */
const refForm = ref<unknown>(null)
/** 表格 ref（旧 `refTable`） */
const refTable = ref<{
  query?: (where: Record<string, unknown>) => void
  getSelectRows?: () => Array<Record<string, unknown>>
} | null>(null)

/** 查询容器实时高度（旧注释：默认 1 行 88） */
const queryHeight = ref(0)
/** 表格高度 */
const tableHeight = ref(500)

/**
 * 元对象编码（旧栈由服务端 `#(objectCode)` 渲染）
 *
 * 取值优先级（统一契约，见 DES-004）：引导数据 `object.code` > URL 查询串 `?object=` >
 * **本页显式声明的回退值** `eova_user_code`（库种子数据里 `su.object.code` 的实际值）。
 * 缺到连回退都没有时 `requireObjectCode` 会**抛错** —— 拿空串拼 `/api/meta/table/` 只会 404 且难定位。
 */
const bootstrap = ref<PageBootstrap>({ fromServer: false, url: readUrlParams() })
const objectCode = computed(() => requireObjectCode(bootstrap.value, 'eova_user_code'))

/** 查询（旧 `onQuery`：把查询表单数据交给表格） */
function onQuery(): void {
  refTable.value?.query?.(data)
}

/**
 * 查询表单尺寸变化（旧 `doResize`）
 *
 * @param height 表单高度
 */
function doResize(height: number): void {
  queryHeight.value = height
  // 总高度 - form - 间距（旧实现固定减 30）
  tableHeight.value = getEovaTools().dom.getViewSize().height - height - 30
  console.log('form resize:' + height)
}

/**
 * 提交（旧 `onSubmit`：取选中行 → doSu → 回传宿主）
 *
 * @param id 弹层句柄（由 `eova-layer-ok` 事件带回）
 */
async function onSubmit(id?: unknown): Promise<void> {
  const me = getEovaMe()
  const x = getEovaTools()

  const rows = refTable.value?.getSelectRows?.()
  if (x.isEmpty(rows)) {
    me.layer.msg('请选择一个用户')
    return
  }
  const user = (rows as Array<Record<string, unknown>>)[0]
  try {
    const res = await axios.post('/eova/admin/doSu', user)
    const ret = res.data
    if (ret.state === 'ok') {
      // ★ 顺序承重：先回传数据（宿主监听 eova-layer-ok_done_data 写 currentUser），
      //   再发 ok_done（宿主据此关层并执行 done，done 里会读 currentUser）
      me.cross.emit('eova-layer-ok_done_data', `${user.rid}【${user.name}】`)
      me.cross.emit('eova-layer-ok_done', id)
    } else {
      me.layer.no(ret.msg)
    }
  } catch (error) {
    me.layer.msg('客户端请求异常: ' + (error as Error).message)
  }
}

onMounted(async () => {
  // 监听弹层「确认」通知（旧实现挂在 onMounted）
  getEovaMe().cross.on('eova-layer-ok', (id) => {
    onSubmit(id)
  })
  // 装配页面引导数据（旧栈是渲染期插值；端点未就绪时降级为"仅 URL 参数"并告警）
  bootstrap.value = await loadPageBootstrap()
})

defineExpose({
  // `data` 是 `let` 绑定（会被 `v-model` 整体替换）⇒ 用 getter 暴露"当前值"，否则拿到的是替换前的旧对象
  get data() {
    return data
  },
  page,
  refForm,
  refTable,
  queryHeight,
  tableHeight,
  bootstrap,
  objectCode,
  onQuery,
  doResize,
  onSubmit
})
</script>
