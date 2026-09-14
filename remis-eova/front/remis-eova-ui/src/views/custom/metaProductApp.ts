/**
 * `meta_product` 表单页自定义 app 的**脚本钩子**（r327 · 切片 B）
 * —— `legacy/product/app.js`（726 字节）的**构建期等价物**。
 *
 * ## 契约来源（逐条对齐冻结文件）
 *
 * ```js
 * const {onMounted, ref, watch, useTemplateRef} = Vue;   // ★ watch 是**未使用的解构**（死代码，见下）
 * uzoo.vue.setup = () => {
 *     const inputRef = useTemplateRef('inputRef');
 *     onMounted(() => { console.log("hotel app setup onMounted")
 *         if (inputRef.value) { me.layer.ok(inputRef.value.value) } })
 *     const txt = ref(123); const num = ref(1); const field = ref({en:'test'})
 *     const rate = ref(0); const tags = ref('1,2,3,4,5,6,7,8'); const rangeTime = ref([])
 *     const confs = me.conf.getAll();
 *     return { confs, num, rate, tags, field, txt, rangeTime }
 * }
 * ```
 *
 * · 该文件**只有 `setup`，没有 `onReady`** ⇒ `uzoo.vue.onReady` 不由它注册（`callUzooHook` 什么都不做）。
 * · `me.conf.getAll()` 走制品；`confs` 等返回值由页面 `setUzooApp({...setup(), …})` 摊进 `uzoo.app`。
 *   （`EovaMe.conf` 在 `compat/eova-runtime.ts` 里是 `unknown`，而 port 的 `EovaConf` 只声明了实际用到的
 *   `putAll`/`get` ⇒ 这里就地收窄到 `getAll()`。**不做判空**：旧实现没有判空，制品必然提供 `me.conf`，
 *   缺失即 TypeError —— 原样保留。）
 * · `inputRef`：旧模板里那个 `<input ref="inputRef">` **被注释掉** ⇒ 恒为 `null`
 *   ⇒ `onMounted` 里的 `me.layer.ok(...)` **永不执行**（不是"忘了实现"，是旧栈就如此）。
 *
 * ## 既有死代码（**不迁移**，登记在案）
 *
 * · 解构出来的 `watch` 在本文件里**从未使用** ⇒ 不 port（否则 lint 会红，且引入无意义依赖）。
 *
 * ## 已声明适配
 *
 * · `Vue` 全局解构 → ESM `import { onMounted, ref, useTemplateRef } from 'vue'`（SPA 既有方式）；
 * · `me = EovaUI.me` → `getEovaMe()`（SPA 既有接入方式，制品实例同一个）。
 */
import { onMounted, ref, useTemplateRef } from 'vue'
import { getEovaMe } from '@/compat/eova-runtime'

/**
 * `meta_product` 的钩子（键 = `uzoo.vue` 上的钩子名）。
 *
 * 结构刻意与 {@link ./metaHotelApp} 一致，便于 `registry.ts` 用同一形状登记。
 */
export const metaProductAppHooks = {
  /**
   * 旧 `uzoo.vue.setup` —— 返回值由宿主摊进 `uzoo.app`。
   *
   * @returns 旧实现 return 的那一组字段（逐字保留键名）
   */
  setup(): Record<string, unknown> {
    const me = getEovaMe()

    // 旧 `useTemplateRef('inputRef')`：模板里那个 input 是注释态 ⇒ 恒 null ⇒ 下面的 ok 永不执行
    const inputRef = useTemplateRef<HTMLInputElement>('inputRef')

    onMounted(() => {
      console.log('hotel app setup onMounted')
      if (inputRef.value) {
        me.layer.ok?.(inputRef.value.value)
      }
    })

    const txt = ref(123)

    const num = ref(1)
    const field = ref({ en: 'test' })

    const rate = ref(0)
    const tags = ref('1,2,3,4,5,6,7,8')

    // 定义变量
    const rangeTime = ref<unknown[]>([])

    const confs = (me.conf as { getAll: () => Record<string, unknown> }).getAll()
    return {
      confs,
      num,
      rate,
      tags,
      field,
      txt,
      rangeTime
    }
  }
}
