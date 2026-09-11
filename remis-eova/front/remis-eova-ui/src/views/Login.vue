<!--
  登录页（阶段 2 首个改造页）

  契约来源（逐条对齐旧实现，不得自创）：
  · 旧页面：`meta-eova/eova/view/src/main/resources/webapp/eova/_view/index/login.html`
    + 同目录 `login.js`（source revision 1b1d39e7350f7e031b216aad0399fc8cc55dce08）
  · 提交：`POST /user/doLogin`，载荷 `{login_id, login_pwd, captcha}`
    （旧栈靠 `EovaConfig.configConstant` 的 `me.setResolveJsonRequest(true)` 让 JSON 体中的参数
     可被 `get("login_id")` 读到 —— 故 axios 默认的 JSON 体在旧栈同样可用）
  · 成功判定：`ret.state === 'ok'` ⇒ `location.href = '/'`；否则把 `ret.msg` 显示在表单内
  · 网络异常：显示固定文案 `客户端请求异常`
  · 验证码：`conf.isCaptcha` 为真时才显示，图片来源 `/user/captcha`，点击重新拉取
    （旧实现是 `this.src='/user/captcha?'+Math.random()`，本页等价地加时间戳参数）

  尚未迁移（已登记，不在本页假装完成）：
  · 旧 `login.js` 用 EovaUI 的 `x.validate` 做前端校验（rules）—— 该库尚未纳入本工程依赖；
  · 旧样式 `/eova/_view/index/login.css`（冻结资产，仍在 src/legacy 下，本页暂不引用）；
  · 旧页面在 iframe 内会 `parent.location.href = location.href` 跳出框架（EWOA-LOGIN 关键词）。
-->
<template>
  <div class="eova-login">
    <h2>{{ appName || '账号密码登录' }}</h2>
    <form @submit.prevent="onSubmit" @keyup.enter="onSubmit">
      <div class="eova-login_input">
        <input v-model="data.login_id" type="text" placeholder="账号" maxlength="30" />
      </div>
      <div class="eova-login_input">
        <input v-model="data.login_pwd" type="password" placeholder="密码" maxlength="30" />
      </div>
      <div v-if="isCaptcha" class="eova-login_input eova-login_cap">
        <input v-model="data.captcha" type="text" maxlength="4" placeholder="验证码" autocomplete="off" />
        <div style="margin-left: 10px">
          <img :src="captchaSrc" alt="验证码" @click="refreshCaptcha" />
        </div>
      </div>
      <button type="button" @click="onSubmit">登录</button>
      <span style="color: red" v-html="data.msg"></span>
    </form>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import axios from 'axios'

/** 与旧页面同名的响应式数据（字段名属契约：后端按这些名字取参） */
const data = reactive({
  login_id: '',
  login_pwd: '',
  captcha: '',
  msg: ''
})

/** 登录配置（旧实现由服务端渲染注入；分离后由 `/user/login` 的页面级配置提供，这里先留可注入的口） */
const conf = reactive({
  app_name: '',
  is_captcha: true,
  copyright: ''
})

const appName = computed(() => conf.app_name)
const isCaptcha = computed(() => conf.is_captcha === true || String(conf.is_captcha) === 'true')

const captchaSeed = ref(Date.now())
const captchaSrc = computed(() => `/user/captcha?_=${captchaSeed.value}`)

/**
 * 重新拉取验证码（旧实现点击图片刷新）
 */
function refreshCaptcha(): void {
  captchaSeed.value = Date.now()
}

/**
 * 提交登录（逐条对齐旧 login.js 的 onSubmit 行为）
 */
async function onSubmit(): Promise<void> {
  try {
    const res = await axios.post('/user/doLogin', {
      login_id: data.login_id,
      login_pwd: data.login_pwd,
      captcha: data.captcha
    })
    const ret = res.data
    if (ret && ret.state === 'ok') {
      // 与旧实现一致：登录成功回首页（旧实现 location.href = '/'）
      window.location.href = '/'
      return
    }
    data.msg = (ret && ret.msg) || ''
  } catch (e) {
    data.msg = '客户端请求异常'
  }
}

onMounted(() => {
  // 旧页面在 iframe 内会跳出框架（EWOA-LOGIN 关键词）
  if (window !== window.top) {
    window.top!.location.href = window.location.href
  }
})

defineExpose({ data, conf, onSubmit, refreshCaptcha })
</script>
