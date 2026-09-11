<!--
  修改密码页（阶段 2 第 3 个入口页）

  契约来源（逐条对齐旧实现 `_view/user/password/app.html` + `app.js`，revision 锁定；
  证据 `docs/.local/baseline/evidence/legacy-runtime-contract.json` 的 me/x 面）：

  结构（旧 app.html）
  · `#include("/eova/_view/_page/form.html")` ⇒ 该 partial 只提供 `window.urls` 与公共依赖，
    本页**不读** `window.urls`（提交地址是硬编码的 `/user/doPassword`）
  · `<form method="post" class="eova-form" @submit.prevent="onSubmit">`，三个字段各一个
    `.eova-form-field` + `.eova-form-label.required` + `<ev-input v-model="data.X">`
    （**label 上没有 for/name 绑定**；`ev-input` 也未传 `name`/`type` ⇒ 走组件默认 `name="input"`、`type="text"`）
  · 字段名与顺序：`oldPwd` / `newPwd` / `confirm`（顺序即"哪条错误先报"的顺序）

  行为（旧 app.js）
  · `onMounted` 注册 `me.cross.on('eova-layer-ok', onSubmit)` —— 弹层的「确认」按钮
    通过 `cross.emit('eova-layer-ok', handle)` 触发本页提交（handle 作为 `id` 参数传回）
  · `onSubmit(id)`：
    ① `x.validate.start(rules, data)` 为假 ⇒ 取 `x.validate.showMsg(rules)`，**非空才** `me.layer.wa(txt)`，然后 return
       —— ★ 提示级别是 `wa`（icon=3），不是 `msg`/`no`；且 showMsg 为空时**不弹任何提示**
    ② `POST /user/doPassword`，载荷就是 `data` 三个字段（**不含 id**）
    ③ `state === 'ok'` ⇒ `me.cross.emit('eova-layer-ok_done', id)`（宿主收到后关层并执行其 done 回调）
       否则 ⇒ `me.layer.no(ret.msg)`
    ④ 异常 ⇒ `me.layer.msg('客户端请求异常: ' + error.message)`
  · `rules` 三项**都只有 `required`**：旧实现**没有**"两次新密码一致"的校验，
    也**没有**新旧密码不同的校验 ⇒ 原样保留，不得"顺手补上"（补了就是改变契约）

  已声明适配（非静默改写）
  · 旧页面是独立 HTML + `createApp` + `app.use(EovaUI)`；本工程是 SPA 路由页，
    EovaUI 组件由 `src/compat/legacy-runtime.ts` 在应用装配期统一注册（`app.use(EovaUI)`）
  · 旧 `data`/`rules` 是 `ref(...)`；本页用 `reactive(...)` —— 语义等价（模板与 `x.validate` 都直接读对象），
    且 `x.validate.start` 对 `rule.msg` 的**原地写回**在 reactive 下同样可驱动视图

  尚未迁移（登记）
  · `app.分别提示.html`（同目录的另一个入口页变体）—— 单独一个单元，未在本轮
  · `window.urls` 与 `me.conf` 的来源（分离后无服务端模板注入）—— 见 DES-002-R4 §102 的待核项
-->
<template>
  <div id="app">
    <form ref="refForm" method="post" class="eova-form" @submit.prevent="onSubmit(undefined)">
      <div class="eova-form-field">
        <label class="eova-form-label required">旧密码</label>
        <ev-input v-model="data.oldPwd"></ev-input>
      </div>
      <div class="eova-form-field">
        <label class="eova-form-label required">新密码</label>
        <ev-input v-model="data.newPwd"></ev-input>
      </div>
      <div class="eova-form-field">
        <label class="eova-form-label required">确认密码</label>
        <ev-input v-model="data.confirm"></ev-input>
      </div>
    </form>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { getEovaMe, getEovaTools, type EovaValidateRule } from '@/compat/eova-runtime'

/** DOM ref（旧实现的 `refForm`，旧页面并未使用其方法，保留以维持引用面） */
const refForm = ref<HTMLFormElement | null>(null)

/** 表单数据（字段名属契约：后端按这些名字取参，且顺序即提示顺序） */
const data = reactive({
  oldPwd: '',
  newPwd: '',
  confirm: ''
})

/** 表单校验规则（旧实现三项都只有 required） */
const rules = reactive<Record<string, EovaValidateRule>>({
  oldPwd: { label: '旧密码', rules: ['required'] },
  newPwd: { label: '新密码', rules: ['required'] },
  confirm: { label: '确认密码', rules: ['required'] }
})

/**
 * 提交（旧 `onSubmit`：先校验、再请求、成功后回传宿主关闭弹层）
 *
 * @param id 弹层句柄（由 `eova-layer-ok` 事件带回；直接提交时为 undefined）
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
    const res = await axios.post('/user/doPassword', data)
    const ret = res.data
    if (ret.state === 'ok') {
      // 通知宿主：关层并执行其 done 回调（旧实现唯一成功路径）
      me.cross.emit('eova-layer-ok_done', id)
    } else {
      me.layer.no(ret.msg)
    }
  } catch (error) {
    me.layer.msg('客户端请求异常: ' + (error as Error).message)
  }
}

onMounted(() => {
  // 监听弹层「确认」通知（旧实现挂在 onMounted）
  getEovaMe().cross.on('eova-layer-ok', (id) => {
    onSubmit(id)
  })
})

defineExpose({ refForm, data, rules, onSubmit })
</script>
