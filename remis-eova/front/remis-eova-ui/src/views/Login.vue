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

  校验（第 93 轮迁移）：旧 `login.js` 的 rules 是
    `login_id: {label:'账号', rules:['required']}`、`login_pwd: {label:'密码', rules:['required']}`
  —— **只对账号与密码做必填**（验证码不在 rules 里）。本页按同一规则集实现校验，
  失败时不发请求、把提示写进 `data.msg`。
  ✅ **文案已实证（第 94 轮）**：旧实现的提示由 EovaTools 生成，其模板在
  `lib/eova/lib/eova-tools.umd.js` 中为 `i.msg = `${i.label}不能为空``（required 分支）
  —— 与本页 `${rule.label}不能为空` **逐字一致**（`账号不能为空` / `密码不能为空`）。
  （该文件属 vendor 资产，第 94 轮已补 `targetPath` 并落地到 `src/legacy/**`，故可核。）

  样式（第 94 轮已可迁移）：旧页面的 `/eova/_view/index/login.css` 已补进账本并落地到
  `src/legacy/eova/_view/index/login.css`（第 93 轮发现"账本无 CSS"，r94 补账 8 个）。
  本页暂以 `@import` 方式引用该冻结文件，保持"只引用、不修改"。

  仍未迁移：iframe 跳出已实现；EovaUI 的下拉/校验库与其它 legacy 组件未纳入。
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
  <!-- ★ r311：页脚（旧 `login.html:43-45` 的 `.eova-footer` + `{{ conf.copyright }}`；样式在冻结 login.css 里）。
       缺它的实测后果：旧栈该元素 40px 高、文案 `© 2015-2026 EOVA.CN`，新栈整块不存在。 -->
  <div class="eova-footer">{{ conf.copyright }}</div>
</template>

<script setup lang="ts">
// 样式取自冻结资产（第 94 轮已补账落地；只引用，不修改 src/legacy 下任何文件）
import '../legacy/eova/_view/index/login.css'
import { computed, onBeforeMount, onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { callUzooHook } from '@/compat/eova-ext'
import { createBootstrapFetcher } from '@/compat/page-bootstrap-fetcher'

/** 与旧页面同名的响应式数据（字段名属契约：后端按这些名字取参） */
const data = reactive({
  login_id: '',
  login_pwd: '',
  captcha: '',
  msg: ''
})

/**
 * 登录配置。
 *
 * <p>旧栈由**服务端渲染**注入（`login.html` 的 `#(isCaptcha??false)` / `#(copyright)` /
 * `#(app_name??'EOVA低代码开发平台')`）；SPA 接管后经引导端点下发（`POST /api/page/bootstrap`，
 * `path=/user/login`，服务端唯一事实源 = `UserController.loginPageConf(...)`）。</p>
 *
 * <p>★ 默认值必须与旧模板的 `??` 兜底一致：`is_captcha` 默认 **false**
 * （旧模板写的是 `#(isCaptcha??false)`）。此前这里硬编码 `true`，而本环境配置 `isCaptcha=false`
 * ⇒ 实测旧栈**不显示**验证码、新栈**恒显示**（可见差异 D3b）。</p>
 */
const conf = reactive({
  app_name: '',
  is_captcha: false,
  copyright: ''
})

/** 应用名兜底（旧模板 `#(app_name??'EOVA低代码开发平台')`；页面标题与 h2 共用） */
const DEFAULT_APP_NAME = 'EOVA低代码开发平台'

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
 * 表单校验规则（与旧 login.js 的 rules 同一规则集：仅账号与密码必填）。
 *
 * 文案模板逐字取自旧制品：`lib/eova/lib/eova-tools.umd.js` 的 required 分支
 * `i.msg = \`${i.label}不能为空\``。
 */
const rules: Record<string, { label: string; rules: string[] }> = {
  login_id: { label: '账号', rules: ['required'] },
  login_pwd: { label: '密码', rules: ['required'] }
}

/**
 * 必填校验（返回首条错误文案；全部通过返回空串）
 */
function validate(): string {
  for (const [field, rule] of Object.entries(rules)) {
    if (rule.rules.includes('required')) {
      const v = (data as Record<string, unknown>)[field]
      if (v === undefined || v === null || String(v).trim() === '') {
        return `${rule.label}不能为空`
      }
    }
  }
  return ''
}

/**
 * 提交登录（逐条对齐旧 login.js 的 onSubmit 行为：先校验、再请求）
 */
async function onSubmit(): Promise<void> {
  const err = validate()
  if (err !== '') {
    data.msg = err
    return
  }
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

/**
 * 拉取登录页配置（页面级引导数据）。
 *
 * 失败**不阻断登录**（旧栈同语义：字段缺失 ⇒ 模板 `??` 兜底为默认值）。
 */
async function loadPageConf(fetcher = createBootstrapFetcher()): Promise<void> {
  try {
    const text = await fetcher({ url: {} })
    if (!text) {
      return
    }
    const kv = JSON.parse(text) as Record<string, unknown>
    if (kv['state'] !== 'ok') {
      return
    }
    // ★ 与旧模板逐字等价：`#(app_name??'EOVA低代码开发平台')` —— 只有 **null** 才兜底，
    //   空串按配置原样（此时 h2 再走 `|| '账号密码登录'`）
    conf.app_name = (kv['app_name'] as string) ?? DEFAULT_APP_NAME
    conf.is_captcha = kv['isCaptcha'] === true || String(kv['isCaptcha']) === 'true'
    conf.copyright = (kv['copyright'] as string) ?? ''
    // 旧栈把 `dev.login_id`/`dev.login_pwd` 直接渲染进输入框（开发免输）；此处按同一口径回填
    if (kv['login_id']) {
      data.login_id = String(kv['login_id'])
    }
    if (kv['login_pwd']) {
      data.login_pwd = String(kv['login_pwd'])
    }
    // 页面标题：旧登录页是 `<title>#(app_name??'EOVA低代码开发平台')</title>`
    // （同族做法见 `views/meta/MetaEdit.vue` 的 document.title）
    document.title = conf.app_name
  } catch {
    // 配置不可得不阻断登录（与旧栈"字段缺失⇒走模板默认值"等价）
  }
}

onBeforeMount(() => {
  // 标题先落到旧模板的兜底值（拿不到配置时与旧栈一致，不留 `EovaMeta`）
  document.title = DEFAULT_APP_NAME
  void loadPageConf()
})

onMounted(() => {
  // 旧页面在 iframe 内会跳出框架（EWOA-LOGIN 关键词）
  if (window !== window.top) {
    window.top!.location.href = window.location.href
  }
})

// 旧 `login.js:20-25`：`onBeforeMount` 里回调扩展钩子 `uzoo.vue.mountBefore()`。
// ★ 第 107 轮补：r92 首版**漏了这一步**（当时只把它当"未迁移项"），而它是**扩展点** ——
//   漏调不会让任何判据变红，只会让依赖它的自定义静默失效。现由接缝统一调用
//   （钩子未注册时是 no-op，与旧实现 `typeof === 'function'` 的守则一致）。
onBeforeMount(() => {
  console.log('onBeforeMount...')
  callUzooHook('mountBefore')
})

defineExpose({ data, conf, onSubmit, refreshCaptcha })
</script>
