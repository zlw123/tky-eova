<!--
  表单**新增**页（切片 S6，第 295 轮）—— 旧 `_view/template/form/add/index.html`(27 行) + `index.js`(97 行)

  ## 它在链路里的位置

  旧栈这三页**不是** `menu.template` 模版页，而是**动作路由**（第 286 轮实测）：
  `eova_menu.config.template` 全为 NULL；`AppController#add()` 读 `get(0)` 后
  `renderEnjoy("/eova/_view/template/form/add/index.html")`。
  调用方是冻结脚本 `_view/template/eova.template.js` / 各列表模版页里的
  `me.layer.open('新增数据', '/app/add/{{object_code}}?biz={{code}}', …)`（**iframe 弹层**）。
  ⇒ SPA 侧 `/app/add/:objectCode` 路由 → **本组件**（**不**改 `TEMPLATE_COMPONENTS`/`MIGRATED_TEMPLATES`；
  那两个常量只管 `menu.template` 主页面，`registry.spec.ts` 的双向一致约束不适用于动作页）。

  ## 结构（逐条对齐旧 index.html）

  · `<div id="app" v-cloak> > <div>` 内一个 `<ev-form>`：
    `ref="refForm"` · `mode="create"` · `name="#(object.code)"` · `object="#(object.code)"` ·
    `v-model="data"` · `@ready="onReady"`（**没有 `pk`**，也没有 `biz` —— 与 update/detail 的差别）
  · 末尾 `#include("/eova/_view/_block/admin_form.html", mode="create")` ⇒ `<EovaAdminForm mode="create">`
  · `<head>` 里 `#include("/eova/_view/_page/form.html")` 只提供 `window.urls` +
    `uzoo.page = {object_id, object_name, object_code, object_pk}`（**渲染期插值**）

  ## 行为（逐条对齐旧 index.js；括号内是行号）

  · `props = uzoo.page`（13）★ 是**同一个对象引用**（此后 `props.biz`/`props.object_code` 读的是全局）
  · `data = reactive({...props.fixed})`（15）—— ★ **铺开 `fixed`**（update 页是 `reactive({})`，见那页）
  · `onSubmit(id)`（20）：`refForm.value.validate() == false` ⇒ **直接 return（不发请求）**；
    `data_ = refForm.value.getData()`；`url = me.urls.url('form_add', props)`（**按 `{{object_code}}` 拼**）；
    `axios.post(url, data_)`；`state==='ok'` ⇒ `me.cross.emit('eova-layer-ok_done', id)`（**只此一条**）；
    否则 `me.layer.msg(ret.msg)`（★ 是 `msg`，不是 `no`）；异常 ⇒ `me.layer.msg('客户端请求异常: ' + message)`
  · `onMounted`（58）：`console.log('add.vue...')` → `me.cross.on('eova-layer-ok', onSubmit)` →
    `console.log("add index.js onMounted...")`
  · `onReady(fieldInstances)`（65）：`typeof uzoo.vue.onReady === 'function'` 才调（**存在才调**）
  · 结尾（88-96）：`data_ = uzoo.vue.setup()`（存在才调）→ `return uzoo.app = {...data_, props, data,
    refForm, onReady, onSubmit}`
  · `me.vue.created(app)` / `me.vue.mount(app, uzoo.page.code)`（97-98）：★ **SPA 路由页不做
    `createApp`** ⇒ 该"自定义 app"机制在 SPA 下的等价形态仍是**待用户口径**（`eova-ext.ts`/
    `custom-apps.ts` 已登记），本页不发明。

  ## 已声明适配（非静默改写）

  ① 旧 `#(object.code)`（渲染期插值）⇒ 本页取**路由参数**（= 旧 `get(0)`，同一段）。
  ② `uzoo.page` 的写入由 `writeFormPageUzooPage`（`compat/form-page.ts`）统一承担：
     旧栈 `uzoo.page` 每次导航从 `{}` 起步，而 SPA 的 `uzoo` 是**跨路由常驻**的全局
     ⇒ 必须**整体替换**而非逐键写（否则上一页的键会留下来）。
  ③ `fixed` 由 `?ref=` 在前端按 `WidgetManager#getRef` 的同口径解析（**取证**：该静态方法只读
     `c.get("ref")`，不查库、不看会话）—— 见 `compat/form-page.ts` 文件头。
  ④ `.js` 按钮脚本、`ev-form` 的取数（`/api/meta/form/<object>?mode=create`、
     `/api/form/data/<object_code>?pk=`）**都由冻结制品 `eovaui.js` 内部完成**，本页零工作。

  ## ⚠️ 已登记的缺口（**不得当成已完成**）

  · `uzoo.page.object_id/object_name/object_pk` 与 `loginUser.isAdmin` 在旧栈由**渲染期插值**提供
    （后者来自 `LoginInterceptor:123` 的每请求 `ctrl.set(LoginService.USER, user)`）。
    `POST /api/page/bootstrap` 的取数口径是**菜单编码**，动作页的末段是**元对象编码**
    ⇒ 本页**不调用**它（调用会拿错对象），改为**可声明降级**：这三项不写、`EovaAdminForm` 不渲染，
    并在挂载后**响亮告警**。补口属独立单元（DES-005 §16.1 非范围②）。
  · `meta_hotel`/`meta_product` 的**自定义表单**（旧 `me.vue.mount(app, code)` 会**替换整页模版**）
    在 SPA 下未实现 ⇒ 对这两个元对象本页渲染的是标准表单，与旧栈**不等价**（既有登记，非本页引入）。

  尚未迁移（登记）：`template/form/update`、`template/form/detail`（同族两页，同轮一起迁）。
-->
<template>
  <div id="app" v-cloak>
    <!-- ★ 可声明降级：缺元对象编码时**不得静默白屏**（旧栈此值是渲染前提，缺了整页无意义） -->
    <div v-if="objectCode === ''" class="eova-form-degraded">
      <p>缺少元对象编码（URL 第 0 段）：无法确定要渲染哪个元对象的表单。</p>
      <p>当前地址：{{ currentPath }}</p>
    </div>
    <template v-else>
      <div>
        <ev-form
          ref="refForm"
          mode="create"
          :name="objectCode"
          :object="objectCode"
          v-model="data"
          @ready="onReady"
        ></ev-form>
      </div>
      <EovaAdminForm :is-admin="isAdmin" mode="create" />
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
  /** 校验（制品实现：按 `diy.status` 建规则后走 `x.validate.start`） */
  validate: () => boolean
  /** 取表单数据（**就是绑定进去的那个响应式对象本体**） */
  getData: () => Record<string, unknown>
}

/** 表单 ref（旧 `refForm`） */
const refForm = ref<EvFormInstance | null>(null)

const route = useRoute()

/** 页面参数（旧 `AppController#add()`：`get(0)` + `get("biz","")` + `WidgetManager.getRef`） */
const params = resolveFormPageParams({ objectCodeFromRoute: route.params.objectCode })

/** 元对象编码（旧 `#(object.code)` = `get(0)`） */
const objectCode = params.objectCode

/** 当前地址（降级文案里给出，便于定位"哪个 URL 缺参"） */
const currentPath = ((): string => {
  return typeof window === 'undefined' ? '' : `${window.location.pathname}${window.location.search}`
})()

/**
 * 是否超管（旧 `#if(loginUser.isAdmin)`）
 *
 * ⚠️ **动作页当前无来源**（见文件头"已登记的缺口"）⇒ 恒为 `false`，
 * 由 `onMounted` 的告警点名。**不得**为了"让面板显示出来"而猜 true。
 */
const isAdmin = false

// ---- setup 期：写 `uzoo.page`（旧 `_page/form.html` + 页内脚本；见适配②）----
const missingPageKeys = writeFormPageUzooPage({
  objectCode,
  form: 'create',
  biz: params.biz,
  fixed: params.fixed,
  // 动作页无引导数据来源（见文件头缺口头）：`object_id/object_name/object_pk` 记为缺口
  objectMeta: null
})

/** `props = uzoo.page`（旧 index.js:13 —— **同一个对象引用**，不是副本） */
const props = getUzooPage()

/**
 * 表单数据（旧 `data = reactive({...props.fixed})`）
 *
 * ★ 铺开的是 **`uzoo.page.fixed`**（由上面的 `writeFormPageUzooPage` 写入），**不是** `params.fixed` ——
 * 与旧代码同源；且旧栈这一段**恒执行**（空表序列化成 `"{}"` 也是真值，见 `form-page.ts` 注释）。
 */
const data = reactive<Record<string, unknown>>({
  ...((props['fixed'] ?? {}) as Record<string, unknown>)
})

/**
 * 提交（旧 `onSubmit(id)`：先校验、再请求、成功后回传宿主关层）
 *
 * @param id 弹层句柄（由 `eova-layer-ok` 事件带回）
 */
async function onSubmit(id?: unknown): Promise<void> {
  const me = getEovaMe()

  if (refForm.value!.validate() === false) {
    return
  }

  const data_ = refForm.value!.getData()

  try {
    const res = await axios.post(me.urls.url('form_add', props), data_)
    const ret = res.data
    console.log(JSON.stringify(ret))
    if (ret.state === 'ok') {
      // 操作成功后 关闭弹窗
      me.cross.emit('eova-layer-ok_done', id)
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
  console.log('add.vue...')
  // 监听提交通知
  getEovaMe().cross.on('eova-layer-ok', onSubmit)
  console.log('add index.js onMounted...')

  if (objectCode === '') {
    console.warn(
      '[template/form/add] 缺少元对象编码（URL 第 0 段 = 旧 `AppController#add()` 的 `get(0)`）' +
        '⇒ 已停止渲染表单（可声明降级，不是静默白屏）。'
    )
  }
  if (missingPageKeys.length > 0) {
    console.warn(
      `[template/form/add] uzoo.page 缺以下服务端插值键：${missingPageKeys.join('/')}。` +
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
  isAdmin,
  missingPageKeys
})
</script>
