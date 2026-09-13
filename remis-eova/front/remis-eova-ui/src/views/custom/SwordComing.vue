<template>
  <!--
    ★ r319（U4 切片 A）：`legacy/_component/SwordComing.js` 的**构建期 SFC 等价物**。

    旧实现是**运行时模板**（`defineComponent({ template: '...' })` + 全局 `Vue` 的完整版编译器），
    SPA 不能运行时编译 ⇒ 逐字搬成模板（只做两处**已声明适配**）：
      ① 静态资源 `/ui/css/SwordComing.css`、`/ui/img/sword1.png`、`/ui/img/sword2.webp`
         —— `/ui/**` 是**旧 demo 工程**的静态空间，按口径④不迁移 ⇒ 在 SPA 里 404
         （登记为已声明差异：面板皮肤/图片不可得，**行为面不受影响**）。
         ⚠️ 这几处必须用**运行时字符串绑定**（`:href`/`:src`）而不是字面量：字面量会让 Vite 在
         **构建期**尝试解析该资产，而它在工程里不存在 ⇒ 构建直接失败（实测踩到）。
         URL 本身**逐字保留**旧原路径，便于日后补迁时一处对齐；
      ② `me = EovaUI.me` 改为经 `getEovaMe()` 取（SPA 的既有接入方式），其余逻辑逐字保留。

    行为契约（逐条取自旧文件）：
      · `row = computed(() => props.modelValue)`；`page = reactive({page:1, limit:99999})`；
      · `watch(props.modelValue)`：**首次**选中 ⇒ 剑气动画（sword1 显示 500ms ⇒ sword2）+ `body.quake` 3s
        ⇒ 动画结束调用 `handlerRowClick()`；之后每次选中直接 `handlerRowClick()`；
      · `handlerRowClick()`：`me.layer.msg('🗡 剑来... ' + row.name)` → 两个子表 `query({hotel_id: id})`
        → `emit('update:show', true)`；
      · 面板 `v-show="show"`（初始隐藏，但**已挂载** ⇒ 两个 `ev-table` 会取 `hotel_stock`/`hotel_bed` 的元数据，
        这正是旧栈 `/app/meta_hotel` 比新栈多两次 `POST /api/meta/table/*` 的原因）；
      · `me.cross.on('eova-table-row_click', …)` 在旧文件里回调体**被注释掉**（无副作用）⇒ 不搬（登记）。
  -->
  <link rel="stylesheet" :href="'/ui/css/SwordComing.css'" />
  <div ref="sword1Ref" id="imageContainer" style="display: none">
    <img :src="'/ui/img/sword1.png'" style="width: 70vw" alt="剑来" />
  </div>
  <div ref="sword2Ref" id="imageContainer" style="display: none">
    <img :src="'/ui/img/sword2.webp'" style="width: 70vw" alt="剑来" />
  </div>
  <div
    ref="swordRef"
    class="sword-go"
    v-show="show"
    style="
      position: fixed;
      top: 85px;
      bottom: 50px;
      right: 0;
      width: 350px;
      z-index: 999;
      background-color: white;
      border: 1px solid #878484;
    "
  >
    <h3 style="margin: 5px; color: var(--eova-color_main)">🗡 {{ row?.name }}</h3>
    <div style="position: absolute; right: 5px; top: 5px" @click="$emit('update:show', false)" title="隐藏窗口">
      <i class="eova-icon-close"></i>
    </div>
    <h3>&nbsp;酒店布草库存</h3>
    <ev-table ref="refTable1" object="hotel_stock" biz="hotel_stock" :page="page" :height="300" :init-query="false"></ev-table>
    <h3>&nbsp;酒店床位</h3>
    <ev-table ref="refTable2" object="hotel_bed" biz="hotel_bed" :page="page" height="200" :init-query="false"></ev-table>
    <div class="eova-notes" style="margin: 10px">💡 手中有剑，心中有道！剑来！</div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { getEovaMe, getEovaTools } from '@/compat/eova-runtime'

/** 子表 1/2 的实例（`ev-table` 制品暴露 `query`） */
interface EvTableInstance {
  query: (where: Record<string, unknown>) => void
}

const props = defineProps<{
  /** 选中行（旧 `v-model="currentRow"`） */
  modelValue?: Record<string, unknown>
  /** 显隐（旧 `v-model:show="showLinking"`） */
  show: boolean
}>()

const emit = defineEmits<{ (e: 'update:show', v: boolean): void }>()

/** 选中行（旧 `const row = computed(() => props.modelValue)`） */
const row = computed(() => props.modelValue)

const refTable1 = ref<EvTableInstance>()
const refTable2 = ref<EvTableInstance>()

/** 子表分页（旧 `limit > 99999 不显示分页组件`） */
const page = reactive({ page: 1, limit: 99999 })

const sword1Ref = ref<HTMLElement>()
const sword2Ref = ref<HTMLElement>()
const swordRef = ref<HTMLElement>()
/** 是否首次选中（决定要不要播进场动画） */
const isFirst = ref(true)

/**
 * 子表高度动态计算，旧文件逐字：`x.dom.getViewSize().height - 50 - 100 - 15 - 300 - 30`。
 *
 * ★ 旧文件里算完**并未使用**（模板里两个 `ev-table` 的高度是字面量 300/200）⇒ 原样保留，
 *   不"顺手清理"（保留是为了让逐字对照可核）。
 */
function computeTable1Height(): number {
  const dom = (getEovaTools()?.x as { dom?: { getViewSize?: () => { height: number } } } | undefined)?.dom
  const h = dom?.getViewSize?.().height
  return typeof h === 'number' ? h - 50 - 100 - 15 - 300 - 30 : 0
}

/** 动画收尾：藏图 + 去掉 `sword-go` 类（旧 `hideImageAndRemoveClass`） */
function hideImageAndRemoveClass(): void {
  if (sword2Ref.value) {
    sword2Ref.value.style.display = 'none'
  }
  if (swordRef.value) {
    swordRef.value.classList.remove('sword-go')
  }
}

/** 行点击处理（旧的"主子表联动核心 API"） */
function handlerRowClick(): void {
  const id = row.value?.id
  getEovaMe()?.layer?.msg?.(`🗡 剑来... ${row.value?.name}`)
  refTable1.value?.query({ hotel_id: id })
  refTable2.value?.query({ hotel_id: id })
  emit('update:show', true)
}

/** 选中行实时监听（旧 `watch(() => props.modelValue, …)`） */
watch(
  () => props.modelValue,
  () => {
    if (isFirst.value) {
      isFirst.value = false
      if (sword1Ref.value) {
        sword1Ref.value.style.display = 'flex'
      }
      setTimeout(() => {
        if (sword1Ref.value) {
          sword1Ref.value.style.display = 'none'
        }
        if (sword2Ref.value) {
          sword2Ref.value.style.display = 'flex'
        }
      }, 500)
      const bodyElement = document.body
      bodyElement.classList.add('quake')
      setTimeout(() => {
        bodyElement.classList.remove('quake')
        hideImageAndRemoveClass()
        handlerRowClick()
      }, 3000)
    } else {
      handlerRowClick()
    }
  }
)

onMounted(() => {
  // 业务初始化加载（旧文件此处为空）
})

defineExpose({ row, computeTable1Height, handlerRowClick })
</script>
