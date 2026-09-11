<!--
  创建菜单（阶段 2 第 9 个入口页）

  契约来源（逐条对齐旧 `_view/menu/add/app.html` + `app.js` + `MenuController#add()/props()`）：

  结构（旧 app.html）
  · 页面级 `<style>`：`.app-template_info` / `.app-template_img` 两条（逐字）
  · `<form class="eova-form eova-anim-fadein" @submit.prevent="onSubmit" novalidate>` + 3 个 fieldset
  · 「菜单信息」区字段：菜单类型(`ev-select :items="types"`)、编码、名称、图标(`ev-icon type="eova-icon"`)、序号(`type="number"`)
  · 「应用配置」`<fieldset v-show="data.type === 'app'">`：
    右上角模版示例图 `:src="'/eova/_view/menu/add/img/' + (selectTemplate?.img || 'default.png')"`；
    应用模版 `ev-select :items="templates"`；模版说明 `{{ selectTemplate?.info }}`；
    再按 `templateProps` 逐项渲染（`o.type === 'find'` 用 `ev-find`，否则 `ev-input`；`o.info` 时多一行提示）
  · 「自定义配置」`<fieldset v-show="data.type === 'diy' || data.type === 'open'">`：URL（`ev-input type="texts"`，`:style="'height: 100px; width: 600px'"`）
  · 被注释掉的「关联元对象」`ev-find` 一块：原样不迁移（旧实现已注释）

  行为（旧 app.js）
  · `data` 初值逐字：`{parent_id:0, num:9, type:'app', icon:'eova-icon-app', code:'meta_test_001',
    name:'测试功能1', template:'table', url:'', objects:[]}`
  · `types` 四项：`app/应用`、`dir/目录`、`diy/链接`、`open/弹窗`
  · `rules` **5 项**：`type:['required']`、`name:['required','len[2~15]']`、
    `code:['required','eovacode']`、`icon:['required']`、`num:['required','range[1~9999]']`
    ★ `eovacode` **是真实注册规则**（第 111 轮取证：`Gr` 共 22 条，`eovacode` = `/^\w{3,50}$/`，
      文案「必须是3-50位数字、字母、下划线组成」）—— 此前我一度以为它未注册，是**抽取工具截断**导致的误判
  · `watch(data.value, ...)`：回调只打日志，分支体为空 ⇒ 属既有空实现，保留（`data` 不会被整体替换，故实际不触发）
  · `watch(() => data.template, val => getTemplateProps(val))`：模版变化即拉配置
  · `getTemplateProps(val)`：`POST /menu/props/${val}`（体 `{}`）
    ok ⇒ `templateProps = ret.data`；逐项：`o.props` 存在则 `x.json.toObj(o.props)`；`config[o.key] = o.value`；最后 `x.log(...)`
    非 ok ⇒ `layer.no(ret.msg)`；异常 ⇒ `msg('客户端请求异常: ' + message)`
  · `selectTemplate` = computed：`templates.find(i => i.val === data.template)`
  · `onBeforeMount` → `uzoo.vue.mountBefore()`；`onMounted` → `getTemplateProps(data.template)` + 注册 `eova-layer-ok`
  · `onSubmit(id)`：校验不过 ⇒ `wa(showMsg)` 非空才弹并 return；
    ★ **先把 `config` 序列化进 `data.config`**（`x.json.toStr(config)`，即无缩进 JSON 文本）再 `POST /menu/add`；
    ok ⇒ **`parent.uzoo.app.refTree.value.reload()`** 然后 `emit('eova-layer-ok_done', id)`；
    非 ok ⇒ `layer.no(ret.msg)`；异常 ⇒ `msg('客户端请求异常: ' + message)`

  ★ 两处服务端渲染（第 111 轮取证）
  · `#(parent_id)` 与 `#for(t : templates)`（`eova_template` 表逐行）都由 `uzoo.vue.mountBefore` 注入：
      `data.parent_id = +'#(parent_id)'`（★ 一元 `+` 转数字）
      `templates.push(...[{val:'#(t.code)', txt:'#(t.name)', info:'#(t.info)', img:'#(t.img)'}])`
      `data.template = 'table'`（★ 钩子里**硬编码**为 `table`，覆盖初值也是 table）
    ⇒ 属 DES-004 的「页面自有引导数据」，端点未就绪时 `templates` 为空、`parent_id` 用初值 0
  · `images`（模版示例图）走旧静态路径 `/eova/_view/menu/add/img/**`（第 101 轮已把这 4 张 png 落进账本）

  ★ `parent.uzoo.app.refTree.value.reload()`：**跨窗口调用父页的 `uzoo.app.refTree`**
  · 本页旧栈以弹层 iframe 打开（父页是菜单树页），故"刷新父页的树"是**跨窗口**的。
  · 旧实现**没有任何存在性判断**：直接打开本页（`parent === self`，自身没有 `refTree`）会抛 `TypeError`；
    而它落在**同一个 `try`/`.catch` 链里** ⇒ 被 catch 接住，用户看到
    `客户端请求异常: Cannot read properties of ...`，且 `emit('eova-layer-ok_done')` **不执行**（弹层不关）。
  · **原样保留该既有行为**（不擅自加 `?.`），判据对"父页有 refTree"与"父页没有 refTree"两种情形分别钉住。

  已声明适配（非静默改写）
  · 独立 HTML + `createApp` ⇒ SPA 路由页（运行时由装配期统一提供）
  · 页面级 `<style>` 逐字搬入 SFC（**非 scoped**；与 `MetaField`/`MenuAuth` 同一处置，差异已声明）
  · 模板里 `:option="o.props.option"` → `o.props?.option`：**只加可选链**（find 项在真实数据里一定带 `props`，
    故正常路径逐字等价）；不加则 `props` 缺失时渲染期抛 `TypeError`（Vue 会告警并让该子树不渲染）。
    这是一处**已声明的类型安全适配**，不改变任何正常路径的行为
  · `data`/`rules` 用 `reactive`；`types`/`templates`/`templateProps`/`config` 用 `ref`（与旧 `ref` 同形）
  · 旧 `x.log(...)` 保留为 `x.log`（制品自带）

  尚未迁移（登记）
  · 旧 `#(parent_id)` 的**真实取值来源**（`MenuController#add()` 渲染时的 `parent_id`）需与引导端点一并落地
  · `x.json.toStr(config)` 的缩进参数（旧实现用默认 0）已逐字保留
-->
<template>
  <div id="app">
    <form
      ref="refForm"
      method="post"
      class="eova-form eova-anim-fadein"
      @submit.prevent="onSubmit(undefined)"
      novalidate
    >
      <fieldset>
        <legend>菜单信息</legend>
      </fieldset>
      <div class="eova-form-field">
        <label class="eova-form-label required">菜单类型</label>
        <ev-select type="select" name="type" :items="types" v-model="data.type"></ev-select>
      </div>
      <div class="eova-form-field">
        <label class="eova-form-label required">编码</label>
        <ev-input name="code" v-model="data.code"></ev-input>
      </div>
      <div class="eova-form-field">
        <label class="eova-form-label required">名称</label>
        <ev-input name="name" v-model="data.name"></ev-input>
      </div>
      <br />
      <div class="eova-form-field">
        <label class="eova-form-label required">图标</label>
        <ev-icon type="eova-icon" v-model="data.icon" placeholder="请选择图标"></ev-icon>
      </div>
      <div class="eova-form-field">
        <label class="eova-form-label required">序号</label>
        <ev-input name="num" type="number" v-model="data.num"></ev-input>
      </div>
      <br />

      <fieldset v-show="data.type === 'app'">
        <legend>应用配置</legend>
        <div class="app-template_img">
          <img :src="`/eova/_view/menu/add/img/${selectTemplate?.img || 'default.png'}`" alt="模版示例" />
        </div>

        <div class="eova-form-field">
          <label class="eova-form-label">应用模版</label>
          <ev-select type="select" :items="templates" v-model="data.template"></ev-select>
        </div>
        <div class="eova-form-field">
          <label class="eova-form-label"></label>
          <div class="app-template_info">{{ selectTemplate?.info }}</div>
        </div>

        <br />

        <template v-for="o in templateProps" :key="o.key">
          <div class="eova-form-field">
            <label :class="['eova-form-label', required(o)]">{{ o.name }}</label>
            <template v-if="o.type === 'find'">
              <ev-find
                :option="o.props?.option"
                v-model="config[o.key]"
                :multiple="o.multiple"
                :placeholder="o.title"
                :style="'width: 540px'"
              ></ev-find>
            </template>
            <template v-else>
              <ev-input v-model="config[o.key]" :placeholder="o.title" :style="'width: 540px'"></ev-input>
            </template>

            <div class="eova-form-msg" v-if="o.info">
              <span v-if="o.multiple">(可多选) </span>
              {{ o.info }}
            </div>
          </div>
          <br />
        </template>
      </fieldset>

      <fieldset v-show="data.type === 'diy' || data.type === 'open'">
        <legend>自定义配置</legend>
        <div class="eova-form-field">
          <label class="eova-form-label">URL</label>
          <ev-input
            type="texts"
            v-model="data.url"
            placeholder="自定义链接"
            :style="'height: 100px; width: 600px'"
          ></ev-input>
        </div>
      </fieldset>
    </form>
  </div>
</template>

<!-- 旧 app.html 的页面级 <style> 逐字（**非 scoped**，已声明差异） -->
<style>
.app-template_info {
  margin-left: 10px !important;
  line-height: 32px;
  color: #bfbdbf;
}

.app-template_img {
  position: absolute;
  right: 5px;
  max-width: 250px;
}
</style>

<script setup lang="ts">
import { computed, onBeforeMount, onMounted, reactive, ref, watch } from 'vue'
import axios from 'axios'
import { getEovaMe, getEovaTools, type EovaValidateRule } from '@/compat/eova-runtime'
import { callUzooHook, getUzooPage } from '@/compat/eova-ext'
import { loadPageBootstrap, type PageBootstrap } from '@/compat/page-bootstrap'

/** 引导数据（本页要用的是"页面自有"的 `templates` 与 `parent_id`） */
const bootstrap = ref<PageBootstrap>({ fromServer: false, url: {} })

/** DOM ref（旧 `refForm`） */
const refForm = ref<HTMLFormElement | null>(null)

/** 模版项（旧 `#for(t : templates)` 注入） */
interface TemplateItem {
  val: string
  txt: string
  info?: string
  img?: string
  [k: string]: unknown
}

/** 模版字段项（旧 `/menu/props/:val` 返回） */
interface TemplateProp {
  key: string
  name?: string
  title?: string
  type?: string
  multiple?: boolean
  info?: string
  value?: unknown
  required?: boolean
  props?: Record<string, unknown>
  [k: string]: unknown
}

/** 表单数据（初值逐字对齐旧 `app.js`） */
const data = reactive({
  parent_id: 0,
  num: 9,
  type: 'app',
  icon: 'eova-icon-app',
  code: 'meta_test_001',
  name: '测试功能1',
  template: 'table',
  url: '',
  objects: [] as unknown[],
  config: undefined as string | undefined
})

/** 模版配置（旧 `config = ref({})`） */
const config = ref<Record<string, unknown>>({})
/** 菜单类型（旧 `types`，四项逐字） */
const types = ref([
  { val: 'app', txt: '应用' },
  { val: 'dir', txt: '目录' },
  { val: 'diy', txt: '链接' },
  { val: 'open', txt: '弹窗' }
])
/** 应用模版列表（旧 `templates = ref([])`，由钩子从服务端注入） */
const templates = ref<TemplateItem[]>([])
/** 模版字段（旧 `templateProps = ref([])`） */
const templateProps = ref<TemplateProp[]>([])

/** 表单校验规则（旧 `rules`：**5 项**，含 `len`/`eovacode`/`range` 三种规则串） */
const rules = reactive<Record<string, EovaValidateRule>>({
  type: { label: '类型', rules: ['required'] },
  name: { label: '名称', rules: ['required', 'len[2~15]'] },
  code: { label: '编码', rules: ['required', 'eovacode'] },
  icon: { label: '图标', rules: ['required'] },
  num: { label: '序号', rules: ['required', 'range[1~9999]'] }
})

/**
 * 逆向监听（旧 `watch(data.value, …)`）
 *
 * 旧实现回调体只打日志（`if` 分支为空）⇒ **既有空实现**，原样保留形状；
 * 另：`data` 在本页不会被整体替换，故该 watch 实际不触发（与旧实现一致）。
 */
watch(data, (val) => {
  console.log(val)
  if (val.type === 'app') {
    // 旧实现此处为空
  }
})

/** 模版选择变化 → 拉模版配置（旧 `watch(() => data.template, …)`） */
watch(
  () => data.template,
  (val, oldVal) => {
    console.log(`changed from ${oldVal} to ${val}`)
    getTemplateProps(val)
  }
)

/** 当前选中的模版（旧 `selectTemplate` computed） */
const selectTemplate = computed(() => templates.value.find((i) => i.val === data.template))

/**
 * 必填标记（旧 `required(field)`：有 `required` 才返回 `'required'`）
 *
 * @param field 字段项
 * @returns 类名或 undefined
 */
function required(field: { required?: boolean }): string | undefined {
  if (field.required) {
    return 'required'
  }
  return undefined
}

/**
 * 拉取模版配置（旧 `getTemplateProps`）
 *
 * @param val 模版编码
 */
async function getTemplateProps(val: string): Promise<void> {
  const me = getEovaMe()
  const x = getEovaTools()
  try {
    const res = await axios.post(`/menu/props/${val}`, {})
    const ret = res.data
    if (ret.state === 'ok') {
      templateProps.value = ret.data
      // 自动解析值属性为对象 + 自动解析默认值
      templateProps.value.forEach((o) => {
        if (o.props) {
          o.props = x.json.toObj(o.props as unknown as string) as Record<string, unknown>
        }
        config.value[o.key] = o.value
      })
      x.log(templateProps.value)
    } else {
      me.layer.no(ret.msg)
    }
  } catch (error) {
    me.layer.msg('客户端请求异常: ' + (error as Error).message)
  }
}

onBeforeMount(() => {
  console.log('menu/add/app.js onBeforeMount...')
  // 旧 `app.js:114` 回调扩展钩子；本页 `app.html:110` 正是定义它的一侧
  callUzooHook('mountBefore')
  // 旧钩子里由服务端渲染注入的 `#(parent_id)` 与 `#for(t : templates)`：
  // 分离后走引导数据；端点未就绪时 `templates` 为空、`parent_id` 用初值 0（诚实降级）
  const params = bootstrap.value.pageParams ?? {}
  if (params['parent_id'] != null) {
    data.parent_id = Number(params['parent_id'])
  }
  const tps = params['templates']
  if (Array.isArray(tps)) {
    templates.value.push(...(tps as TemplateItem[]))
    // 旧钩子里**硬编码**把 template 设为 'table'
    data.template = 'table'
  }
})

onMounted(async () => {
  console.log('menu/add/app.js onMounted...')
  console.log(data)
  // 获取当前模版参数
  getTemplateProps(data.template)
  // 监听提交通知
  getEovaMe().cross.on('eova-layer-ok', (id) => {
    onSubmit(id)
  })
  // 装配引导数据（旧栈是渲染期插值）
  bootstrap.value = await loadPageBootstrap()
})

/**
 * 提交（旧 `onSubmit`：先校验、再把 config 序列化进 data、最后请求）
 *
 * @param id 弹层句柄（由 `eova-layer-ok` 事件带回）
 */
async function onSubmit(id?: unknown): Promise<void> {
  const me = getEovaMe()
  const x = getEovaTools()

  if (!x.validate.start(rules, data)) {
    const txt = x.validate.showMsg(rules)
    if (txt !== '') {
      me.layer.wa?.(txt)
    }
    return
  }

  // 更新配置（★ 提交前把 config 序列化成 JSON **文本**塞进 data.config）
  data.config = x.json.toStr(config.value) as string

  try {
    const res = await axios.post('/menu/add', data)
    const ret = res.data
    if (ret.state === 'ok') {
      // ★ 跨窗口刷新父页的菜单树。旧实现**没有任何存在性判断**，本页原样保留：
      //   直接打开本页（`parent === self`，自身没有 `refTree`）时这里抛 TypeError，
      //   而它恰好落在**同一个 `try` 里** ⇒ 被下面的 `catch` 接住，用户看到
      //   `客户端请求异常: Cannot read properties of ...`，且 `emit('eova-layer-ok_done')` **不执行**（弹层不关）。
      //   这是既有行为（旧实现的 `.then(...).catch(...)` 同样是这个结果），不擅自加可选链。
      const parentUzoo = (parent as unknown as {
        uzoo: { app: { refTree: { value: { reload: () => void } } } }
      }).uzoo
      parentUzoo.app.refTree.value.reload()
      me.cross.emit('eova-layer-ok_done', id)
    } else {
      me.layer.no(ret.msg)
    }
  } catch (error) {
    me.layer.msg('客户端请求异常: ' + (error as Error).message)
  }
}

// 保持全局 `uzoo.page` 可读（旧实现由 list/form partial 赋值）
void getUzooPage

defineExpose({ refForm, data, config, types, templates, templateProps, rules, selectTemplate, required, getTemplateProps, onSubmit })
</script>
