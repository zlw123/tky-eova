<!--
  `/app/:menuCode` 的模版分派宿主（第 118 轮）

  ## 它对应旧栈的哪一段

  旧 `AppController#index()`：

  ```java
  String menuCode = get(0);                            // ★ URL 第 0 段
  Menu menu = Menu.dao.findByCode(menuCode);
  String templdate = menu.getTemplate();               // ★ 渲染哪一个模版页
  MetaObject object = sm.meta.getMeta(objectCode);
  if (user == null) { renderMsg("请先登录"); return; }
  List<Button> btnList = Button.dao.queryByMenuCode(menuCode, user.getRid());
  renderEnjoy(String.format("/eova/_view/template/%s/index.html", templdate));
  ```

  ⇒ SPA 侧 `/app/:menuCode` 只做三件事：取引导数据 → 解析 `menu.template` → **分派**到对应组件。

  ## 为什么引导数据由**宿主**加载（而不是各模版页自己取）

  分派**本身**就需要 `menu.template`；若模版页再取一次，同一页会发两次同样的请求
  （旧栈只有一次服务端渲染）。故引导数据在此加载一次并以 prop 传给模版页
  —— 这是**已声明适配**（旧栈的"渲染期插值"在分离后只能有一次来源）。

  ## 取不到 `menu.template` 时**不猜**（本文件最重要的口径）

  `menu.template` 是服务端插值，前端**没有任何等价来源**（URL 上没有、`me.conf` 里没有）。
  缺它时本组件渲染一段**明确的诊断**（点名 `menuCode` 与它需要什么），
  **不**默认按 `table` 渲染：按错模版渲染出来的页面**看起来是能用的**，
  用户会以为"就是这张表"—— 这比直接报错危险得多。

  同理，`tree`/`tree_table` 已登记为**未迁移**，此处也**不降级**渲染成 `table`。

  ## 未登录分支

  旧栈 `user == null` ⇒ `renderMsg("请先登录")`（**不是**重定向到 `/user/login`）。
  本组件等价：引导数据里 `loginUser` 缺失 ⇒ 渲染同一句文案
  （判据断言它，防止"顺手改成跳登录页"—— 那是另一条契约）。
-->
<template>
  <div id="app">
    <!-- 加载中：旧栈没有这个状态（服务端渲染），故不画任何占位内容，避免闪一屏"诊断" -->
    <div v-if="state === 'loading'" data-eova-template="loading"></div>

    <!-- 未登录：旧栈 `renderMsg("请先登录")` -->
    <div v-else-if="state === 'anonymous'" class="eova-admins" data-eova-template="anonymous">
      <div class="eova-tools_box">请先登录</div>
    </div>

    <!-- 取不到 menu.template ⇒ 明确诊断（**不猜默认模版**） -->
    <div v-else-if="state === 'missing'" class="eova-admins" data-eova-template="missing">
      <div class="eova-tools_box">
        无法确定模版类型：菜单 <b>{{ menuCode }}</b> 的 <code>menu.template</code> 未取到。<br />
        旧栈由 <code>AppController#index()</code> 渲染期 <code>menu.getTemplate()</code> 决定渲染哪个模版页；
        前后分离后该值属于**页面引导数据**（<code>POST /api/page/bootstrap</code>，见 DES-004 §3.1），
        而前端没有任何等价来源可推。为避免"按错模版渲染出看起来能用的页面"，此处**不猜测**。
      </div>
    </div>

    <!-- 模版尚未迁移 ⇒ 明确报出（**不降级**成已迁移模版） -->
    <div v-else-if="state === 'unmigrated'" class="eova-admins" data-eova-template="unmigrated">
      <div class="eova-tools_box">
        模版 <b>{{ template }}</b>（菜单 <b>{{ menuCode }}</b>）{{
          unmigrated.includes(template) ? '尚未迁移到 SPA' : '不在已知模版清单内'
        }}。 已迁移：{{ migrated.join('、') }}；未迁移：{{
          unmigrated.length > 0 ? unmigrated.join('、') : '（无）'
        }}。<br />
        此处<b>不降级</b>渲染成已迁移模版：按错模版渲染出来的页面看起来是能用的，比报错更危险。
      </div>
    </div>

    <!-- 已迁移模版：按 `menu.template` 分派到组件表里的组件 -->
    <component :is="readyComponent" v-else :bootstrap="bootstrap!" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { loadPageBootstrap, type PageBootstrap } from '@/compat/page-bootstrap'
import { TEMPLATE_COMPONENTS } from './registry'
import {
  MIGRATED_TEMPLATES,
  UNMIGRATED_TEMPLATES,
  isMigratedTemplate,
  resolveTemplate
} from '@/compat/template-dispatch'

/** 菜单编码（旧 `AppController#index()` 的 `get(0)`） */
const menuCode = ((): string => {
  const p = useRoute().params.menuCode
  return typeof p === 'string' ? p : ''
})()

/** 引导数据（宿主加载一次，见文件头） */
const bootstrap = ref<PageBootstrap | null>(null)
/** 是否已尝试加载（区分"还没取"与"取了但没有"—— 两者渲染不同，不得混为一谈） */
const loaded = ref(false)

/** 已迁移/未迁移模版清单（诊断文案直接读它，避免文案与代码脱节） */
const migrated = MIGRATED_TEMPLATES
const unmigrated = UNMIGRATED_TEMPLATES

/** 模版名与其来源 */
const resolved = computed(() =>
  bootstrap.value ? resolveTemplate(bootstrap.value) : { template: '', source: 'missing' as const }
)

/** 模版名（模板用） */
const template = computed(() => resolved.value.template)

/**
 * 该模版对应的组件（`state='ready'` 时才取用）
 *
 * 组件表与"已迁移模版名"必须同一集合 —— 由 `__tests__/registry.spec.ts` 双向断言。
 */
const readyComponent = computed(() => TEMPLATE_COMPONENTS[template.value])

/** 页面状态（分支必须可观测：判据直接断言它） */
const state = computed<
  'loading' | 'anonymous' | 'missing' | 'unmigrated' | 'ready'
>(() => {
  if (!loaded.value) {
    return 'loading'
  }
  // 旧栈：`user == null` ⇒ `renderMsg("请先登录")`
  if (bootstrap.value?.fromServer && bootstrap.value.loginUser == null) {
    return 'anonymous'
  }
  if (resolved.value.source === 'missing') {
    return 'missing'
  }
  if (!isMigratedTemplate(resolved.value.template)) {
    return 'unmigrated'
  }
  return 'ready'
})

onMounted(async () => {
  bootstrap.value = await loadPageBootstrap()
  loaded.value = true
})

defineExpose({ menuCode, bootstrap, loaded, resolved, template, state })
</script>
