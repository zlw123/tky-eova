<script lang="ts">
import { defineComponent, h } from 'vue'

/**
 * `meta_hotel` **表单页**的自定义模版挂载点（r327 · 切片 B）
 * —— 冻结资产 `legacy/hotel/app.vue`（88 字节，只有一行）在**表单页**上的等价物。
 *
 * ## 旧文件全文
 *
 * ```html
 * <sword-coming ref="link" v-model="currentRow" v-model:show="showLinking"></sword-coming>
 * ```
 *
 * ## ★ 关键事实（CDP 实跑取证，不是推断）：表单页上是**空的原生标签**
 *
 * 旧配置 `vue.app('meta_hotel', { template: '/hotel/app.vue', script: '/hotel/app.js' })`
 * **没有 `components` 字段**（对比 `table_meta_hotel` 那条**有** `components: ['SwordComing']`）
 * ⇒ `<sword-coming>` **未被注册** ⇒ Vue 把它当**原生未知元素**渲染。实跑（旧栈
 * `/app/{update,add,detail}/meta_hotel`，脚本 `docs/.local/spikes/s6-sliceB-form-sword-panel.mjs`）：
 *
 * | 观测 | 值 |
 * |---|---|
 * | `document.querySelector('sword-coming')` | **存在**，`outerHTML = "<sword-coming></sword-coming>"`，`childCount = 0` |
 * | 父链 | `SWORD-COMING → DIV#app`（即直接挂在页面根下） |
 * | 可见尺寸 | `[0, 0]` |
 * | `#imageContainer` / `.sword-go` | **各 0 个**（面板本体没有渲染） |
 * | `POST /api/meta/table/hotel_stock` / `hotel_bed` | **0 次**（子表没有挂载） |
 * | console | `[Vue warn]: Failed to resolve component: sword-coming` + `currentRow/showLinking ... not defined` |
 * | `/hotel/app.vue`、`/hotel/app.js` | 均 **200**（模版与脚本确实被加载并执行了 —— 只是组件没注册） |
 *
 * 对照：**列表页** `/app/meta_hotel`（键 `table_meta_hotel`，那条**有** `components`）才是真的挂
 * `SwordComing` 面板（`.sword-go` + 两个 `ev-table` + 两次元数据调用）—— 那一半由切片 A 承担
 * （`views/custom/SwordComing.vue` + `TemplateTable.vue`）。
 *
 * ## 因此本组件**只渲染一个空的原生 `sword-coming` 元素**
 *
 * - **不** import / **不**挂 `SwordComing.vue`（挂上就会多出面板 DOM 与两次元数据调用 ⇒ 与旧栈不等价）；
 * - 用 `h('sword-coming')` 直接造**元素 vnode**（而不是在模板里写 `<sword-coming>`）：
 *   两者渲染结果一致，但前者不产生"未解析组件"告警。**已声明适配**：旧栈那条 `[Vue warn]`
 *   与 `currentRow/showLinking` 未定义告警**不复制**（告警不是契约面，且会污染判据的控制台事实）。
 * - `data` 声明为 prop：宿主统一传 `:data="data"`（与 `ProductApp` 同形），不声明的话它会作为
 *   attr 落到元素上，而旧栈那个标签**没有任何属性**。
 */
export default defineComponent({
  name: 'MetaHotelFormApp',
  props: {
    /** 表单数据（宿主统一传入；本组件不引用它 —— 旧栈的空标签上也没有任何属性） */
    data: { type: Object, required: false, default: undefined }
  },
  setup() {
    // 空的原生标签（0 子节点）——逐字对应旧栈表单页的渲染结果
    return () => h('sword-coming')
  }
})
</script>
