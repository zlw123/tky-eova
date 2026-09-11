<!--
  重新排序（阶段 2 第 6 个入口页）

  契约来源（逐条对齐旧实现 `_view/meta/reorder/app.html` + `app.js` + `MetaController#reorder()/updateReorder()`）：

  结构（旧 app.html）
  · 一个 `<div style="">` 里放 `<ev-sortable v-model="data" text_field="name" order_field="num">`
    —— 排序项的字段名是 `name`（显示）与 `num`（序），**与 `ev-sortable` 的两个 prop 一一对应**

  行为（旧 app.js）
  · `data = ref([])` —— **本页的 `data` 是"数组 ref"**（与 `button/add` 那类"对象 ref"不同）：
    初始数据由**服务端**给，扩展钩子里 `uzoo.app.data.value.push(...data)` 推进去
  · `onBeforeMount` → `uzoo.vue.mountBefore()`（本页 `app.html:16` 正是**定义**它的一侧）
  · `onMounted` → `me.cross.on('eova-layer-ok', onSubmit)`
  · `onSubmit(id)`：**没有任何表单校验**（旧实现直接提交）；URL 由 `uzoo.page` 拼出
    —— `` `/meta/updateReorder?biz=${uzoo.page.biz}&mode=${uzoo.page.mode}` ``；
    载荷是 `data.value`（排序后的数组）
  · 成功 ⇒ `emit('eova-layer-ok_done', id)`；**业务失败 ⇒ `me.layer.msg(ret.msg)`**
    ★ 注意是 **`msg`**，不是 `no`（与改密页、su 页、button/add 页**都不一样**，逐页不同，不得"统一"）
  · 异常 ⇒ `me.layer.msg('客户端请求异常: ' + error.message)`

  ★ 引导数据来源（第 108 轮取证 `MetaController#reorder()`）
  · `objectCode = get("object")`、`biz = get("biz", "field")`、`mode = get("mode")`
    ⇒ 三者都是 **URL 参数**（`biz` **默认 `field`**；`mode` **只在 `biz == "field_diy"` 时才被 set**）
  · `data` 由**服务端查库**：`biz == "field"` ⇒ `sm.meta.getMetaField(objectCode)`，
    `biz == "field_diy"` ⇒ `sm.meta.getMetaFieldDiy(objectCode, mode)`；
    两者都映射成 `{id, name, num}` 后 `set("data", tps)`，模板里用 **`#json(data)`** 注入
    ⇒ 属 DES-004 的「页面自有引导数据」，**需要引导端点**；端点就绪前用**显式回退 `[]`**
    （页面渲染为空列表 —— 这是诚实的降级状态，不是"假装成功"）

  已声明适配（非静默改写）
  · 数据解析抽成 `src/utils/reorder.ts`（`<script setup>` 不能含 ES `export`，且该容错规则要单独判）
  · 旧页面是独立 HTML + `createApp` + `app.use(EovaUI)`；本页是 SPA 路由页（运行时由装配期统一提供）
  · 旧 `#json(data)` 是**服务端渲染的 JSON 文本**；分离后端点直接返回对象，
    故本页接受"数组"或"JSON 字符串"两种形态（后者是为端点逐字复刻 `#json` 时留的容错），
    非数组一律告警并退化为 `[]`
  · `uzoo.page.biz`/`uzoo.page.mode` **仍然写入**（旧实现如此，且 `onSubmit` 正是从 `uzoo.page` 读的）
    ⇒ 其它读 `uzoo.page` 的自定义仍可工作

  尚未迁移（登记）：`/eova/_view/meta/reorder` 的 `ev-sortable` 真实拖拽交互（依赖制品，
  本页只保证 props 与数据流；真实拖拽属浏览器实跑项）。
-->
<template>
  <div id="app">
    <div style="">
      <ev-sortable v-model="data" text_field="name" order_field="num"></ev-sortable>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeMount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import axios from 'axios'
import { getEovaMe } from '@/compat/eova-runtime'
import { callUzooHook, getUzooPage } from '@/compat/eova-ext'
import { loadPageBootstrap, readUrlParams, type PageBootstrap } from '@/compat/page-bootstrap'
import { parseReorderRows } from '@/utils/reorder'

const route = useRoute()

/** 引导数据（本页要用的是"页面自有"的 `data` 排序项） */
const bootstrap = ref<PageBootstrap>({ fromServer: false, url: readUrlParams() })

/**
 * 取 URL 参数（旧 `MetaController#reorder()` 的三个 `get(...)`）
 *
 * `biz` **默认 `field`**（`get("biz", "field")`）；`mode` **无默认**（只在 `field_diy` 时被 set）。
 *
 * @param key 键
 * @param fallback 默认值
 * @returns 参数值
 */
function param(key: 'object' | 'biz' | 'mode', fallback = ''): string {
  const fromQuery = route.query[key]
  if (typeof fromQuery === 'string' && fromQuery !== '') {
    return fromQuery
  }
  const v = bootstrap.value.url[key]
  return v == null || v === '' ? fallback : v
}

/** 排序项（旧 `data = ref([])`） */
const data = ref<Array<Record<string, unknown>>>([])

onBeforeMount(() => {
  console.log('app.js onBeforeMount...')
  // 旧 `app.js:12` 回调扩展钩子；本页 `app.html:16` 正是定义它的一侧。
  // ★ 端点未就绪时 `pageParams.data` 缺失 ⇒ 回退 `[]`（诚实降级，不装作有数据）。
  callUzooHook('mountBefore')
  const rows = parseReorderRows(bootstrap.value.pageParams?.['data'])
  // 旧钩子用的是 `uzoo.app.data.value.push(...data)`；本页在 setup 内直接 push（同一语义，
  // 且无需依赖 `uzoo.app` 这条"页面 setup 返回值"的挂载点）。
  data.value.push(...rows)
  // 旧钩子把两个 URL 参数写进 `uzoo.page`（`onSubmit` 正是从那里读）
  const p = getUzooPage()
  p['biz'] = param('biz', 'field')
  p['mode'] = param('mode')
})

onMounted(async () => {
  // 监听提交通知
  getEovaMe().cross.on('eova-layer-ok', (id) => {
    onSubmit(id)
  })
  // 装配页面引导数据（旧栈是渲染期 `#json(data)`；端点未就绪时降级并告警）
  bootstrap.value = await loadPageBootstrap()
  // 引导数据到了之后补一次（首帧可能还没拿到）
  const rows = parseReorderRows(bootstrap.value.pageParams?.['data'])
  if (rows.length > 0 && data.value.length === 0) {
    data.value.push(...rows)
  }
})

/**
 * 提交（旧 `onSubmit`：**无校验**，直接提交排序后的数组）
 *
 * @param id 弹层句柄（由 `eova-layer-ok` 事件带回）
 */
async function onSubmit(id?: unknown): Promise<void> {
  const me = getEovaMe()
  // 旧实现从 `uzoo.page` 读这两个值（而不是从本地变量）
  const url = `/meta/updateReorder?biz=${String(getUzooPage()['biz'])}&mode=${String(
    getUzooPage()['mode']
  )}`
  try {
    const res = await axios.post(url, data.value)
    const ret = res.data
    if (ret.state === 'ok') {
      me.cross.emit('eova-layer-ok_done', id)
    } else {
      // ★ 本页业务失败走 `msg`（不是 `no`）—— 逐页不同，不得"统一"
      me.layer.msg(ret.msg)
    }
  } catch (error) {
    me.layer.msg('客户端请求异常: ' + (error as Error).message)
  }
}

defineExpose({ data, bootstrap, param, onSubmit })
</script>
