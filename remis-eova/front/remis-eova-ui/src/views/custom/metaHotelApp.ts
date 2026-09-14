/**
 * `meta_hotel` / `table_meta_hotel` 表单页自定义 app 的**脚本钩子**（r327 · 切片 B）
 * —— `legacy/hotel/app.js`（1731 字节）的**构建期等价物**。
 *
 * ## 契约来源（逐条对齐冻结文件；行内行为逐字保留）
 *
 * ```js
 * let $city, $region;                       // ★ 模块级暂存：跨 onReady / watch 回调共享
 * uzoo.vue.setup = () => { onMounted(() => console.log("hotel app setup onMounted")); return {} }
 * uzoo.vue.onReady = (fields) => {
 *     console.log("eova.form.js onReady...")
 *     if (uzoo.page.form === 'create') me.layer.notify('自定义逻辑', '/hotel/app.js form:create', 'ok')
 *     else if ('update') … else if ('read') … else if ('query') …      // 四条分支，逐字
 *     $city = fields.get('city'); $region = fields.get('region');
 *     $city.setDisabled(true); $region.setDisabled(true)              // ★ 无判空：字段缺失即 TypeError
 *     watch(() => uzoo.app.data.province, (val) => {                   // 省 → 市
 *         if (!x.isEmpty(val)) { me.layer.msg(`省选择: ${val}`)
 *             $city.setDisabled(false); $city.setOption(`area;2,${val}`); $region.setDisabled(true) } })
 *     watch(() => uzoo.app.data.city, (val) => {                       // 市 → 区
 *         if (!x.isEmpty(val)) { me.layer.msg(`市选择: ${val}`)
 *             $region.setDisabled(false); $region.setOption(`area;3,${val}`) } })
 * }
 * ```
 *
 * ⇒ **这就是"省→市→区联动"的全部实现**：初始禁用市/区，选省后放开市并把它换成
 * `area;2,<省>` 的选项集，选市后放开区并换成 `area;3,<市>`。
 *
 * ## ★ 两处"既有缺陷原样保留"（不得顺手修）
 *
 * 1. **`$city.setDisabled(true)` 没有判空** —— 当 `ev-form` 的 `fields` 里没有 `city` 字段
 *    （例如**列表页的查询表单**：真库 `meta_hotel` 全部字段 `is_query=false` ⇒ 查询表单无字段）时，
 *    旧栈会在这里抛 `TypeError`。**原样保留**（用 `!` 断言，不加 `if`）：
 *    那正是旧栈的既有行为，改了就是"顺手修既有缺陷"。
 * 2. **`watch` 注册在 `onReady` 里**（不是在 setup 里）—— 每次 `onReady` 都会**再注册一对** watch。
 *    旧实现如此；本次不"优化"成只注册一次。
 *
 * ## 已声明适配
 *
 * · `Vue` 全局解构 → ESM `import { onMounted, watch } from 'vue'`；
 * · `me = EovaUI.me` → `getEovaMe()`；`x = EovaTools.x` → `getEovaTools()`（`isEmpty` 走制品本体）；
 * · `uzoo.page` / `uzoo.app` → `getUzooPage()` / `getUzooApp()`（同一全局对象）；
 * · `me.layer.notify` 在 `EovaLayer` 里是**可选**方法（`notify?`）⇒ 用 `?.` 调用，
 *   运行时仍是制品的同一个方法（语义不变，只是类型上承认"可能没有"）。
 */
import { onMounted, watch } from 'vue'
import { getEovaMe, getEovaTools } from '@/compat/eova-runtime'
import { getUzooApp, getUzooPage } from '@/compat/eova-ext'

/** 字段实例的最小面（旧 `fields.get('city')` 返回值；制品提供，只声明本文件用到的两个方法） */
interface EovaFieldInstance {
  /** 旧 `$city.setDisabled(true/false)` */
  setDisabled: (disabled: boolean) => void
  /** 旧 `$city.setOption('area;2,北京')` */
  setOption: (option: string) => void
}

/** `ev-form` 的 `@ready` 载荷（旧 `fieldInstances`，制品传的是 `Map`） */
interface FieldInstances {
  get: (name: string) => EovaFieldInstance | undefined
}

/** 模块级暂存（旧 `let $city, $region`；跨 `onReady` 与 watch 回调共享） */
let $city: EovaFieldInstance | undefined
let $region: EovaFieldInstance | undefined

/**
 * `meta_hotel` / `table_meta_hotel` 的钩子（键 = `uzoo.vue` 上的钩子名）。
 *
 * ★ 同一份脚本在旧 `eova.vue.config.js` 里同时挂在 `table_meta_hotel`（列表页）与
 * `meta_hotel`（表单页）两个键上 ⇒ 本模块被两处登记（见 `registry.ts`）。
 */
export const metaHotelAppHooks = {
  /**
   * 旧 `uzoo.vue.setup`（只注册一个 `onMounted` 日志，返回空对象 —— 逐字保留）。
   *
   * @returns 空对象（旧实现 `return {}`）
   */
  setup(): Record<string, unknown> {
    onMounted(() => {
      console.log('hotel app setup onMounted')
    })

    return {}
  },

  /**
   * 旧 `uzoo.vue.onReady(fields)` —— 表单渲染完成（DOM 加载完成）后的联动装配。
   *
   * @param fields `ev-form` 的字段实例集合（旧 `fieldInstances`）
   */
  onReady(fields: unknown): void {
    const me = getEovaMe()
    console.log('eova.form.js onReady...')

    // 判断当前表单类型(例如有的搞, 有的不搞) —— 四条分支逐字保留
    if (getUzooPage().form === 'create') {
      me.layer.notify?.('自定义逻辑', '/hotel/app.js form:create', 'ok')
    } else if (getUzooPage().form === 'update') {
      me.layer.notify?.('自定义逻辑', '/hotel/app.js form:update', 'ok')
    } else if (getUzooPage().form === 'read') {
      me.layer.notify?.('自定义逻辑', '/hotel/app.js form:read', 'ok')
    } else if (getUzooPage().form === 'query') {
      me.layer.notify?.('自定义逻辑', '/hotel/app.js form:query', 'ok')
    }

    const inst = fields as FieldInstances
    $city = inst.get('city')
    $region = inst.get('region')

    // 初始禁用市，区 —— ★ 旧实现无判空（字段缺失即 TypeError，原样保留，见文件头）
    $city!.setDisabled(true)
    $region!.setDisabled(true)

    const x = getEovaTools()

    /** 取表单数据本体（旧 `uzoo.app.data`；`v-model="data"` 绑定的就是它） */
    const appData = (): Record<string, unknown> =>
      (getUzooApp().data ?? {}) as Record<string, unknown>

    // 省 > 市
    watch(
      () => appData().province,
      (val) => {
        if (!x.isEmpty(val)) {
          me.layer.msg(`省选择: ${val}`)
          $city!.setDisabled(false)
          $city!.setOption(`area;2,${val}`)

          $region!.setDisabled(true)
        }
      }
    )

    // 市 > 区
    watch(
      () => appData().city,
      (val) => {
        if (!x.isEmpty(val)) {
          me.layer.msg(`市选择: ${val}`)

          $region!.setDisabled(false)
          $region!.setOption(`area;3,${val}`)
        }
      }
    )
  }
}
