<!--
  导入元数据（阶段 2 第 11 个入口页）

  契约来源（逐条对齐旧 `_view/meta/import/app.html` + `app.js`）：

  结构（旧 app.html）
  · 一个 `<form class="eova-form eova-anim-fadein" @submit.prevent="onSubmit" novalidate>`，
    **没有 `<fieldset>`**；五个字段（顺序即提示顺序）：
    元类型(`ev-select :items="types"`)、数据源(`ev-select :items="dss"`)、
    元数据表(`ev-select :items="tables"`)、元对象编码(`ev-input`)、元对象名称(`ev-input`)
  · `name` 属性逐字：`type` / `ds` / `table` / `code` / `name`
  · 旧 HTML 里那段被注释掉的 data 插值（花括号双写）不迁移

  行为（旧 app.js）
  · `data` 初值 **只有四个键**：`{ds:'', type:'table', name:'测试xxx', code:'test_xxx'}`
    ★ **没有 `table`** —— 但模板绑定了 `v-model="data.table"`（由 Vue 在运行时补上该键）；
      初值里不加 `table` 是既有事实，**不得"顺手补上"**
  · `types` 两项：`table/table`、`view/view`（val 与 txt 相同）
  · `rules` **4 项**：`type`/`ds` 仅 required；`name:['required','len[2~15]']`；
    `code:['required','eova_code']` ★★ 注意是 **`eova_code`（带下划线）**，
    与 `menu/add` 用的 **`eovacode`（无下划线）不同** —— 见下方"规则串取证"
  · `watch(() => [data.ds, data.type], …)`：**getter 每次返回新数组** ⇒ 任一依赖变化都会触发；
    当 `ds` 与 `type` **都非空**时 `POST /meta/findJson/{ds}-{type}`，成功后
    `tables.value.length = 0`（**原地清空**，不是重新赋值）再逐条 push `{val: table_name, txt: table_name}`
    ★ 该 then 里**没有 `state` 判断**、也没有 `ret.data` 存在性判断 ⇒ 响应缺 `data` 会抛；
      catch 里只 `me.layer.msg('客户端请求异常')`（**不带 error.message**，与其它页不同）
  · `selectTemplate` computed 引用 **未声明的 `templates`** ⇒ 一旦被求值就是 ReferenceError；
    但**模板从不引用它** ⇒ 永不求值 ⇒ **既有死代码（含潜在 ReferenceError）**，原样保留且不补声明
  · `onBeforeMount` → `uzoo.vue.mountBefore()`；本页 `app.html:40` 正是定义它的一侧：
    从 `#for(t : dataSources)` 注入 `dss`，每项 `{val: t.key, txt: t.key, type: t.value}`
  · `onMounted` → 注册 `me.cross.on('eova-layer-ok', onSubmit)`
  · `onSubmit(id)`：
    ① 校验不过 ⇒ **只调用 `x.validate.showMsg(...)`，结果被丢弃、没有任何 toast** ⇒ ★★ **既有缺陷：
       校验不过时用户看不到任何提示**（本页模板也不渲染 `rule.msg`）——与其它页的 `wa(txt)` 写法**不同**，原样保留
    ② `POST /meta/doImports`，载荷 `data`
    ③ 判定用 **`ret.state == 'ok'`（宽松相等 `==`）** —— 与其它页的 `===` **不同**，逐字保留
    ④ ok ⇒ `emit('eova-layer-ok_done', id)`；非 ok ⇒ `layer.no(ret.msg)`；异常 ⇒ `msg('客户端请求异常: ' + error.message)`

  ★ 规则串取证（第 113 轮，证据 `validate-rules-contract.json`）
  · 制品 `Gr` 表共 **22 条**，其中注册的是 **`eovacode`**（`/^\w{3,50}$/`），
    **没有 `eova_code`** ⇒ 本页 `code` 的该规则**是未注册规则**：
    `x.validate.start` 会走到最后的 else 分支 `console.log('未知校验规则: …')` 后**跳过**
    ⇒ **等价于只有 `required`**。这是**既有事实**（两个页面拼写不同、其中一个不生效），
    本页**逐字保留规则数组**，并用判据把"`eova_code` 不在冻结规则表内"钉住。

  已声明适配（非静默改写）
  · 独立 HTML + `createApp` → SPA 路由页（运行时由装配期统一提供）
  · `data`/`rules` 用 `reactive`；`types`/`dss`/`tables` 用 `ref`（与旧 `ref` 同形）
  · `#for(t : dataSources)` 的服务端渲染 → 走引导接缝的 `pageParams.dataSources`（未就绪则 `dss` 为空）

  尚未迁移（登记）：`props = uzoo.page`（本页只声明未使用）与 `test`（`ref('测试信息')`，仅返回未用）
  ⇒ 登记为既有死变量，不迁移。
-->
<template>
  <div id="app">
    <form method="post" class="eova-form eova-anim-fadein" @submit.prevent="onSubmit(undefined)" novalidate>
      <div class="eova-form-field">
        <label class="eova-form-label required">元类型</label>
        <ev-select type="select" name="type" :items="types" v-model="data.type"></ev-select>
      </div>
      <div class="eova-form-field">
        <label class="eova-form-label required">数据源</label>
        <ev-select type="select" name="ds" :items="dss" v-model="data.ds"></ev-select>
      </div>
      <div class="eova-form-field">
        <label class="eova-form-label required">元数据表</label>
        <ev-select type="select" name="table" :items="tables" v-model="data.table"></ev-select>
      </div>
      <div class="eova-form-field">
        <label class="eova-form-label required">元对象编码</label>
        <ev-input name="code" v-model="data.code"></ev-input>
      </div>
      <div class="eova-form-field">
        <label class="eova-form-label required">元对象名称</label>
        <ev-input name="name" v-model="data.name"></ev-input>
      </div>
    </form>
  </div>
</template>

<script setup lang="ts">
import { onBeforeMount, onMounted, reactive, ref, watch } from 'vue'
import axios from 'axios'
import { getEovaMe, getEovaTools, type EovaValidateRule } from '@/compat/eova-runtime'
import { callUzooHook } from '@/compat/eova-ext'
import { loadPageBootstrap, type PageBootstrap } from '@/compat/page-bootstrap'

/** 引导数据（本页要用的是"页面自有"的 `dataSources`） */
const bootstrap = ref<PageBootstrap>({ fromServer: false, url: {} })

/** 数据源项（旧 `#for(t : dataSources)` 注入） */
interface DsItem {
  val: string
  txt: string
  type?: string
  [k: string]: unknown
}

/**
 * 表单数据
 *
 * ★ 初值**只有四个键**（**没有 `table`**，尽管模板绑定了 `v-model="data.table"`）——
 * 逐字保留旧实现；不要"顺手补上 `table: ''`"。
 */
const data = reactive<{
  ds: string
  type: string
  name: string
  code: string
  table?: string
}>({
  ds: '',
  type: 'table',
  name: '测试xxx',
  code: 'test_xxx'
})

/**
 * 校验规则（旧 `rules`：**4 项**）
 *
 * ★ `code` 用的是 **`eova_code`（带下划线）**，而 `menu/add` 用的是 `eovacode`。
 *   冻结的规则表（22 条）里只有 **`eovacode`** ⇒ **`eova_code` 是未注册规则**，
 *   `x.validate.start` 会记一条 `未知校验规则` 后**跳过** ⇒ 实际等价于只有 `required`。
 *   这是既有事实，**逐字保留规则数组**（不得"统一拼写"）。
 */
const rules = reactive<Record<string, EovaValidateRule>>({
  type: { label: '元类型', rules: ['required'] },
  ds: { label: '数据源', rules: ['required'] },
  name: { label: '名称', rules: ['required', 'len[2~15]'] },
  code: { label: '编码', rules: ['required', 'eova_code'] }
})

/** 数据源列表（旧 `dss = ref([])`） */
const dss = ref<DsItem[]>([])
/** 元类型（旧 `types`，两项且 val/txt 相同） */
const types = ref([
  { val: 'table', txt: 'table' },
  { val: 'view', txt: 'view' }
])
/** 表名列表（旧 `tables = ref([])`） */
const tables = ref<Array<{ val: unknown; txt: unknown }>>([])

/**
 * 多值监听（旧 `watch(() => [data.ds, data.type], …)`）
 *
 * ★ getter 每次返回**新数组** ⇒ Vue 按引用比较 ⇒ 任一依赖变化都会触发（旧实现如此）。
 * `ds` 与 `type` **都非空**时才请求表名。
 * ★ then 里**没有 state 判断**、也没有 `ret.data` 存在性判断（既有行为，原样保留）。
 */
watch(
  () => [data.ds, data.type],
  (val, oldVal) => {
    console.log(`ds changed from ${oldVal} to ${val}`)
    if (data.ds && data.type) {
      const url = `/meta/findJson/${data.ds}-${data.type}`
      axios
        .post(url, {})
        .then((res) => {
          const ret = res.data
          // ★ 原地清空（不是重新赋值）
          tables.value.length = 0
          ret.data.forEach((o: Record<string, unknown>) => {
            tables.value.push({ val: o.table_name, txt: o.table_name })
          })
        })
        .catch((_error) => {
          // ★ 本页 catch 只给固定文案，**不带 error.message**（与其它页不同）；
          //   形参写 `_error` 仅满足 noUnusedParameters —— 与旧实现的 `(error)` 行为一致（都不读它）
          getEovaMe().layer.msg('客户端请求异常')
        })
    }
  }
)

onBeforeMount(() => {
  console.log('app.js onBeforeMount...')
  // 旧 `index`/`app.js:80` 回调扩展钩子；本页 `app.html:40` 正是定义它的一侧
  callUzooHook('mountBefore')
  // 旧钩子里由服务端 `#for(t : dataSources)` 注入 `dss`
  const src = bootstrap.value.pageParams?.['dataSources']
  if (Array.isArray(src)) {
    dss.value.push(...(src as DsItem[]))
  }
})

onMounted(async () => {
  console.log('app.js onMounted...')
  // 监听提交通知
  getEovaMe().cross.on('eova-layer-ok', (id) => {
    onSubmit(id)
  })
  // 装配引导数据（旧栈是渲染期插值）
  bootstrap.value = await loadPageBootstrap()
})

/**
 * 提交（旧 `onSubmit`）
 *
 * ★★ **既有缺陷**：校验不过时**只调用 `x.validate.showMsg(...)` 并把结果丢弃**，
 *   没有任何 toast ⇒ 用户看不到提示（本页模板也不渲染 `rule.msg`）。
 *   与其它页的 `if (txt !== '') me.layer.wa(txt)` **不同**，原样保留。
 *
 * @param id 弹层句柄（由 `eova-layer-ok` 事件带回）
 */
async function onSubmit(id?: unknown): Promise<void> {
  const me = getEovaMe()
  const x = getEovaTools()

  if (!x.validate.start(rules, data)) {
    x.validate.showMsg(rules)
    return
  }

  try {
    const res = await axios.post('/meta/doImports', data)
    const ret = res.data
    // ★ 宽松相等 `==`（与其它页的 `===` 不同），逐字保留
    if (ret.state == 'ok') {
      me.cross.emit('eova-layer-ok_done', id)
    } else {
      me.layer.no(ret.msg)
    }
  } catch (error) {
    me.layer.msg('客户端请求异常: ' + (error as Error).message)
  }
}

defineExpose({ data, rules, types, dss, tables, bootstrap, onSubmit })
</script>
