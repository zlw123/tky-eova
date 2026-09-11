<!--
  快速添加按钮（阶段 2 第 5 个入口页）

  契约来源（逐条对齐旧实现 `_view/button/add/app.html` + `app.js` + `ButtonController#add()/doAdd()`）：

  结构（旧 app.html）
  · `#include("/eova/_view/_page/form.html")` ⇒ 该 partial 只提供 `window.urls` 与公共依赖
  · `<form class="eova-form eova-anim-fadein" @submit.prevent="onSubmit" novalidate>` + 两个 `<fieldset><legend>`
    （"按钮外观" / "按钮点击事件"，后者 legend 里嵌一个 `<ev-popup placement="right">` 说明气泡；"授权" 一个）
  · 字段（顺序即提示顺序）：序号 `ev-input type=number` / 图标 `ev-icon type=eova-icon` /
    名称 `ev-input` / 样式 `ev-input` / 事件编码 `ev-input` / 事件逻辑 `ev-input`（`:style="'width: 542px'"`）/
    权限配置 `ev-input`（同上）/ 授权角色 `ev-select option="eova_role" :multiple="true"`
  · 6 个样式快捷按钮：`onSelectBtn('')` / `'eova-btn_success'` / `'eova-btn_error'` / `'eova-btn_warn'`
    / `'eova-btn_info'` / `'eova-btn_base'`，class 均为 `eova-btn_s30 [对应样式]`

  行为（旧 app.js）
  · `data` 初值（**含非空的样例值，是真契约不是占位**）：
    `{menu_code:'', num:1, icon:'eova-icon-ok', name:'测试', event:'test', style:'',
      ui:'/demo/test/btn.js', auth:'/demo/xxx', role:null}`
  · `rules` **只有 6 项**必填：`num`/`icon`/`name`/`event`/`ui`/`auth`
    —— **`menu_code`、`role`、`style` 不在校验里**（原样保留，不得"顺手补上"）
  · `onSubmit(id)`：校验不过 ⇒ `showMsg` 非空才 `me.layer.wa(txt)` 并 return；
    `POST /button/doAdd`，载荷是**整个 data 对象**；ok ⇒ `emit('eova-layer-ok_done', id)`（**只有这一条**，
    不像 su 页有 `_data` 那条）；非 ok ⇒ `me.layer.no(ret.msg)`；异常 ⇒ `me.layer.msg('客户端请求异常: ' + message)`
  · `onSelectBtn(txt)` → `data.style = txt`
  · `onMounted` → `me.cross.on('eova-layer-ok', onSubmit)`
  · `onBeforeMount` → `uzoo.vue.mountBefore()`（旧内联脚本用它在挂载前把两个服务端插值写进 `data`）

  ★ 两个服务端插值的来源（第 106 轮取证：**都不是查库**，故本页不需要引导端点）
  · `#(menuCode)` = `set("menuCode", get(0))` —— jfinal 的 **URL 第 0 段**（`ButtonController:36`）
    ⇒ 前端从**路由参数**取（`/eova/button/add/:menuCode`）。
  · `#(role)` = `set("role", EovaConst.ADMIN_RID)` —— **常量**（`ButtonController:37`），
    其值为 `1`（`EovaConst:50` 默认 1；`EovaConfigPlugin:76` 用 `x.conf.getInt("admin_rid", 1)` 覆盖，
    而库里 `admin_rid` 就是 `1`）。
    ⇒ 走引导数据的**页面自有值**（`pageParams.role`）+ **显式回退 `'1'`**。
    注意 `admin_rid` 是 `is_server=1` ⇒ 不在客户端 `me.conf` 白名单（DES-003 §2.3），故**不能**从 `me.conf` 读。
  · 旧实现在 `onBeforeMount` 里直接写 `uzoo.app.data.value.xxx`（用全局 `uzoo` 拿 setup 返回的对象）——
    本页改为在 setup 内直接赋初值（同一语义，去掉了对全局 `uzoo` 的依赖）。

  已声明适配（非静默改写）
  · 旧页面是独立 HTML + `createApp` + `app.use(EovaUI)`；本页是 SPA 路由页（运行时由装配期统一提供）
  · 旧 `data`/`rules` 是 `ref(...)`；本页 `data` 用 `reactive`（`x.validate` 原地写 `rule.msg` 同样驱动视图），
    `rules` 用 `reactive`（与旧 `rules.value` 同形）
  · 旧 `required(field)` 工具函数与 `props = uzoo.page` **在本页模板里都没被使用**（旧实现遗留）
    ⇒ 登记为既有死代码，不迁移

  尚未迁移（登记）：`/eova/button/quick.html`（`ButtonController#quick()` 的另一个入口页，独立单元）。
-->
<template>
  <div id="app">
    <form ref="refForm" method="post" class="eova-form eova-anim-fadein" @submit.prevent="onSubmit(undefined)" novalidate>
      <fieldset>
        <legend>按钮外观</legend>
      </fieldset>
      <div class="eova-form-field">
        <label class="eova-form-label required">序号</label>
        <ev-input type="number" v-model="data.num"></ev-input>
      </div>
      <div class="eova-form-field">
        <label class="eova-form-label required">图标</label>
        <ev-icon type="eova-icon" v-model="data.icon" placeholder="请选择图标"></ev-icon>
      </div>
      <div class="eova-form-field">
        <label class="eova-form-label required">名称</label>
        <ev-input v-model="data.name"></ev-input>
      </div>
      <div class="eova-form-field">
        <label class="eova-form-label">样式</label>
        <ev-input v-model="data.style" placeholder="按钮样式名"></ev-input>
      </div>
      &nbsp;<button type="button" class="eova-btn_s30" @click="onSelectBtn('')">默认</button>
      &nbsp;<button type="button" class="eova-btn_s30 eova-btn_success" @click="onSelectBtn('eova-btn_success')">成功</button>
      &nbsp;<button type="button" class="eova-btn_s30 eova-btn_error" @click="onSelectBtn('eova-btn_error')">危险</button>
      &nbsp;<button type="button" class="eova-btn_s30 eova-btn_warn" @click="onSelectBtn('eova-btn_warn')">警告</button>
      &nbsp;<button type="button" class="eova-btn_s30 eova-btn_info" @click="onSelectBtn('eova-btn_info')">信息</button>
      &nbsp;<button type="button" class="eova-btn_s30 eova-btn_base" @click="onSelectBtn('eova-btn_base')">原始</button>
      <fieldset>
        <legend>
          按钮点击事件
          <ev-popup ref="popup3Ref" placement="right">
            <i class="eova-icon-question"></i>
            <template #content>
              <div class="eova-tooltip">
                <div class="eova-tooltip_content">
                  目前此方案为过渡方案, 按钮模版将以更强大的形式出现，敬请期待！
                </div>
              </div>
            </template>
          </ev-popup>
        </legend>
      </fieldset>
      <div class="eova-form-field">
        <label class="eova-form-label required">事件编码</label>
        <ev-input v-model="data.event" placeholder="按钮点击事件编码"></ev-input>
      </div>
      <br />
      <div class="eova-form-field">
        <label class="eova-form-label required">事件逻辑</label>
        <ev-input v-model="data.ui" placeholder="点击事件实现JS" :style="'width: 542px'"></ev-input>
      </div>
      <div class="eova-form-field">
        <label class="eova-form-label required">权限配置</label>
        <ev-input v-model="data.auth" placeholder="服务端URI配置" :style="'width: 542px'"></ev-input>
      </div>
      <fieldset>
        <legend>授权</legend>
      </fieldset>
      <div class="eova-form-field">
        <label class="eova-form-label">授权角色</label>
        <ev-select
          option="eova_role"
          v-model="data.role"
          :multiple="true"
          placeholder="默认授权给超管"
        ></ev-select>
      </div>
    </form>
  </div>
</template>

<script setup lang="ts">
import { onBeforeMount, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import axios from 'axios'
import { getEovaMe, getEovaTools, type EovaValidateRule } from '@/compat/eova-runtime'
import {
  bootstrapParam,
  loadPageBootstrap,
  readUrlParams,
  type PageBootstrap
} from '@/compat/page-bootstrap'

/** DOM ref（旧 `refForm`） */
const refForm = ref<HTMLFormElement | null>(null)
/** 弹层 ref（旧 `popup3Ref`，模板绑定用） */
const popup3Ref = ref<unknown>(null)

const route = useRoute()

/** 引导数据（本页需要的是"页面自有值" `role`；见文件头来源取证） */
const bootstrap = ref<PageBootstrap>({ fromServer: false, url: readUrlParams() })

/**
 * `menu_code`：旧 `#(menuCode)` = `ButtonController:36` 的 `set("menuCode", get(0))` ⇒ **URL 第 0 段**
 *
 * 取不到时为空串（旧实现 `data.menu_code` 初值就是空串，且它**不在校验规则里**）—— 与原样一致。
 */
const menuCodeFromRoute = ((): string => {
  const p = route.params.menuCode
  return typeof p === 'string' ? p : ''
})()

/**
 * `role`：旧 `#(role)` = `EovaConst.ADMIN_RID`（常量，值为 1）
 *
 * 走引导数据的页面自有值；端点未就绪时用**显式回退 `'1'`**（库种子 `admin_rid` 的实际值）。
 */
const roleFromServer = bootstrapParam(bootstrap.value, 'role', '1')

/** 表单数据（初值逐字对齐旧 `app.js`；`menu_code`/`role` 由服务端插值改为上面两处来源） */
const data = reactive<{
  menu_code: string
  num: number
  icon: string
  name: string
  event: string
  style: string
  ui: string
  auth: string
  role: unknown
}>({
  menu_code: menuCodeFromRoute,
  num: 1,
  icon: 'eova-icon-ok',
  name: '测试',
  event: 'test',
  style: '',
  ui: '/demo/test/btn.js',
  auth: '/demo/xxx',
  role: roleFromServer
})

/**
 * 表单校验规则（旧 `app.js`：**只有 6 项**）
 *
 * ★ `menu_code`/`role`/`style` **不在**规则里 —— 原样保留，不得"顺手补上"。
 */
const rules = reactive<Record<string, EovaValidateRule>>({
  num: { label: '序号', rules: ['required'] },
  icon: { label: '图标', rules: ['required'] },
  name: { label: '名称', rules: ['required'] },
  event: { label: '事件编码', rules: ['required'] },
  ui: { label: '事件逻辑', rules: ['required'] },
  auth: { label: '权限配置', rules: ['required'] }
})

onBeforeMount(() => {
  console.log('button/add/app.js onBeforeMount...')
})

onMounted(async () => {
  console.log('button/add/app.js onMounted...')
  console.log(data)
  // 监听提交通知
  getEovaMe().cross.on('eova-layer-ok', (id) => {
    onSubmit(id)
  })
  // 装配页面引导数据（旧栈是渲染期插值；端点未就绪时降级为"仅 URL 参数"并告警）
  bootstrap.value = await loadPageBootstrap()
})

/**
 * 提交（旧 `onSubmit`：先校验、再请求、成功后回传宿主关层）
 *
 * @param id 弹层句柄（由 `eova-layer-ok` 事件带回）
 */
async function onSubmit(id?: unknown): Promise<void> {
  const me = getEovaMe()
  const x = getEovaTools()

  if (!x.validate.start(rules, data)) {
    const txt = x.validate.showMsg(rules)
    // 旧实现：文案非空才提示（空则不弹）；级别是 wa
    if (txt !== '') {
      me.layer.wa?.(txt)
    }
    return
  }

  try {
    const res = await axios.post('/button/doAdd', data)
    const ret = res.data
    if (ret.state === 'ok') {
      me.cross.emit('eova-layer-ok_done', id)
    } else {
      me.layer.no(ret.msg)
    }
  } catch (error) {
    me.layer.msg('客户端请求异常: ' + (error as Error).message)
  }
}

/**
 * 选择按钮样式（旧 `onSelectBtn`：直接把样式名写进 `data.style`）
 *
 * @param txt 样式名（空串表示"默认"）
 */
function onSelectBtn(txt: string): void {
  data.style = txt
}

defineExpose({ refForm, popup3Ref, data, rules, bootstrap, onSelectBtn, onSubmit })
</script>
