<!--
  表单**查看**页（切片 S6，第 295 轮）—— 旧 `_view/template/form/detail/index.html`(27 行) + `index.js`(91 行)

  ## 落点与调用方（与 `FormAdd.vue` 同族，见那页文件头）

  动作路由 `/app/detail/<object_code>`，由 `AppController#detail()` 渲染（**不是** `menu.template`
  模版页，第 286 轮实测）。调用方是各列表模版页的 `me.layer.open(...)` iframe 弹层
  （`template/table/index.js:83` 的 `onDetail` 拼 `/app/detail/{{object_code}}?id={{id}}&biz={{menu_code}}`）。

  ## 结构（逐条对齐旧 index.html）

  · `<ev-form>`：`ref="refForm"` · **`mode="read"`** · `name="detail_from"`（字面量）·
    `biz="#(object.code)"` · `object="#(object.code)"` · `pk="#(id)"` · `v-model="data"` · `@ready="onReady"`
  · 末尾 `#include("/eova/_view/_block/admin_form.html", mode="read")` ⇒ `<EovaAdminForm mode="read">`
  · `<head>` 的 `<title>` 在旧栈**与 update 页逐字相同**（都是 `修改#(object.name)`）——
    这是既有复制粘贴痕迹，**原样保留**（本 SFC 无 `<title>`：SPA 的文档标题由主框架管，
    与其余已迁页面同一处置）。

  ## 行为（逐条对齐旧 index.js；括号内是行号）

  · `props = uzoo.page`（7）；`data = reactive({...props.fixed})`（10）★ 与 add 页同构
    （★ 但旧 detail 的 html **没有**写 `uzoo.page.fixed` 的那段脚本 ⇒ `props.fixed` 恒为
    `undefined`，`{...undefined}` 即 `{}` —— 两条叠加后的实际结果与 update 页一致；原样保留）
  · `onSubmit(id)`（18）：`validate() == false` ⇒ return；
    ★ **`me.urls.url('form_update', props)`** —— 查看页提交的也是 **`form_update`**（不是 `form_detail`）。
    这是旧实现的既有行为，**原样保留**（"查看页只读"由 `mode="read"` 下 `ev-form` 的校验/控件状态承担，
    不由 URL 决定）；ok ⇒ `me.cross.emit('eova-layer-ok_done', id)`；否则 `me.layer.msg(ret.msg)`；
    异常 ⇒ `me.layer.msg('客户端请求异常: ' + error.message)`
  · `onMounted`（55）：`console.log('detail init')` → `me.cross.on('eova-layer-ok', onSubmit)`
  · `onReady` / 结尾 `uzoo.app` 赋值：与 add 页同构
  · `me.vue.created(app)` / `me.vue.mount(app, uzoo.page.code)`（90-91）：SPA 路由页不做 `createApp`
    ⇒ "自定义 app"等价形态仍是**待用户口径**（`eova-ext.ts`/`custom-apps.ts` 已登记），本页不发明

  ## 既有死代码（**不迁移**，登记在案）

  · `const { watch, nextTick, defineAsyncComponent } = Vue`（1）—— 本页只用到
    `createApp`/`ref`/`reactive`/`onMounted`
  · `// buildColumnsByFields()` 一族注释、`onSubmit` 之前被注释的 `refForm 不存在` 判空
  · 提交后 `.then` 里 `console.log('加载错误')` 挂在**失败分支**上（与 add/update 一致）

  ## 已声明适配 / 已登记缺口

  与 `FormAdd.vue` 同：`#(object.code)`/`#(id)` 改由**路由参数**与**查询串**取；
  `uzoo.page` 的写入走 `writeFormPageUzooPage`（**整体替换**）；
  `object_id/object_name/object_pk` 与 `loginUser.isAdmin` 在动作页**无来源** ⇒ 可声明降级 + 响亮告警。
-->
<template>
  <div id="app" v-cloak>
    <!-- ★ 可声明降级：缺元对象编码时**不得静默白屏** -->
    <div v-if="objectCode === ''" class="eova-form-degraded">
      <p>缺少元对象编码（URL 第 0 段）：无法确定要渲染哪个元对象的表单。</p>
      <p>当前地址：{{ currentPath }}</p>
    </div>
    <template v-else>
      <div>
        <ev-form
          ref="refForm"
          mode="read"
          name="detail_from"
          :biz="objectCode"
          :object="objectCode"
          :pk="id"
          v-model="data"
          @ready="onReady"
        ></ev-form>
      </div>
      <EovaAdminForm :is-admin="isAdmin" mode="read" />
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import axios from 'axios'
import EovaAdminForm from '@/components/EovaAdminForm.vue'
import { getEovaMe } from '@/compat/eova-runtime'
import { callUzooHook, getUzooPage, setUzooApp } from '@/compat/eova-ext'
import { resolveFormPageParams, writeFormPageUzooPage } from '@/compat/form-page'

/** `ev-form` 暴露的实例方法（旧栈 `refForm.value.xxx()` 的等价；组件由冻结制品注册） */
interface EvFormInstance {
  /** 校验（★ `mode='read'` 下制品不建规则 ⇒ `validate()` 恒为 true，见 ev-form 的 `g()` 分支） */
  validate: () => boolean
  /** 取表单数据（**就是绑定进去的那个响应式对象本体**） */
  getData: () => Record<string, unknown>
}

/** 表单 ref（旧 `refForm`） */
const refForm = ref<EvFormInstance | null>(null)

const route = useRoute()

/** 页面参数（旧 `AppController#detail()`：`get(0)` + `get("biz","")` + `get("id")`） */
const params = resolveFormPageParams({ objectCodeFromRoute: route.params.objectCode })

/** 元对象编码（旧 `#(object.code)` = `get(0)`） */
const objectCode = params.objectCode

/** 主键值（旧 `#(id)` = `get("id")` ⇒ `ev-form` 的 `pk`） */
const id = params.id

/** 当前地址（降级文案里给出，便于定位"哪个 URL 缺参"） */
const currentPath = ((): string => {
  return typeof window === 'undefined' ? '' : `${window.location.pathname}${window.location.search}`
})()

/**
 * 是否超管（旧 `#if(loginUser.isAdmin)`）
 *
 * ⚠️ **动作页当前无来源**（见 `FormAdd.vue` 文件头"已登记的缺口"）⇒ 恒为 `false`，
 * 由 `onMounted` 的告警点名。**不得**为了"让面板显示出来"而猜 true。
 */
const isAdmin = false

// ---- setup 期：写 `uzoo.page`（旧 `_page/form.html` + 页内脚本）----
const missingPageKeys = writeFormPageUzooPage({
  objectCode,
  form: 'read',
  biz: params.biz,
  fixed: params.fixed,
  objectMeta: null
})

/** `props = uzoo.page`（旧 index.js:7 —— **同一个对象引用**） */
const props = getUzooPage()

/** 表单数据（旧 `data = reactive({...props.fixed})`；detail 页 `props.fixed` 恒为 undefined） */
const data = reactive<Record<string, unknown>>({
  ...((props['fixed'] ?? {}) as Record<string, unknown>)
})

/**
 * 提交（旧 `onSubmit(id)`：先校验、再请求、成功后回传宿主关层）
 *
 * @param layerId 弹层句柄（由 `eova-layer-ok` 事件带回）
 */
async function onSubmit(layerId?: unknown): Promise<void> {
  const me = getEovaMe()

  if (refForm.value!.validate() === false) {
    return
  }

  const formData = refForm.value!.getData()

  try {
    // ★ 旧实现是 `form_update`（**不是** `form_detail`）—— 既有行为，原样保留
    const res = await axios.post(me.urls.url('form_update', props), formData)
    const ret = res.data
    console.log(JSON.stringify(ret))
    if (ret.state === 'ok') {
      // 操作成功后 关闭弹窗
      me.cross.emit('eova-layer-ok_done', layerId)
    } else {
      // ★ 旧实现这里是 `me.layer.msg`（不是 `no`）—— 原样保留
      me.layer.msg(ret.msg)
      console.log('加载错误')
    }
  } catch (error) {
    me.layer.msg('客户端请求异常: ' + (error as Error).message)
  }
}

/**
 * 表单动态构建完成（旧 `onReady`：`uzoo.vue.onReady` **存在才调**）
 *
 * @param fieldInstances 字段实例集合（制品传的是 `Map`）
 */
function onReady(fieldInstances: unknown): void {
  callUzooHook('onReady', [fieldInstances])
}

// ---- `uzoo.vue.setup()` 的返回值（旧 `data_`；未注册钩子时为空对象）----
const hookData = ((): Record<string, unknown> => {
  const v = callUzooHook('setup')
  return v != null && typeof v === 'object' ? (v as Record<string, unknown>) : {}
})()

// 旧实现：`return uzoo.app = {...data_, props, data, refForm, onReady, onSubmit}`
setUzooApp({ ...hookData, props, data, refForm, onReady, onSubmit })

onMounted(() => {
  console.log('detail init')

  // 监听提交通知
  getEovaMe().cross.on('eova-layer-ok', onSubmit)

  if (objectCode === '') {
    console.warn(
      '[template/form/detail] 缺少元对象编码（URL 第 0 段 = 旧 `AppController#detail()` 的 `get(0)`）' +
        '⇒ 已停止渲染表单（可声明降级，不是静默白屏）。'
    )
  }
  if (missingPageKeys.length > 0) {
    console.warn(
      `[template/form/detail] uzoo.page 缺以下服务端插值键：${missingPageKeys.join('/')}。` +
        '旧栈由渲染期插值提供（`loginUser` 来自 `LoginInterceptor:123`），' +
        '动作页的引导数据来源尚未落地（DES-005 §16.1 非范围②）' +
        '⇒ 超管面板不渲染，`onMetaObject()` 一类入口拿不到 id。'
    )
  }
})

defineExpose({
  refForm,
  props,
  data,
  onSubmit,
  onReady,
  objectCode,
  id,
  isAdmin,
  missingPageKeys
})
</script>
