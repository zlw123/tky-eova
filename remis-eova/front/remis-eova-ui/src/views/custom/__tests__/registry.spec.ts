/**
 * **自定义 app 注册表判据**（r327 · 切片 B；切片 A 建立本表）
 *
 * ## 钉的是什么
 *
 * 1. ★ **登记 ↔ 实现构成划分**：`compat/custom-apps.ts` 的 `CUSTOM_APPS`（旧配置的事实源）里的每个键，
 *    要么在 `registry.ts` 里有**模版组件**与/或**钩子**实现，要么必须出现在 `UNIMPLEMENTED_KEYS` 里
 *    ⇒ "缺口"永远是**有限的、可枚举的**，不会退化成永久豁免（r316 的教训：缺口表会变成永久免责）。
 *    r327 起该表为**空**（三个键全部实现）。
 * 2. **键名口径**（逐字取自旧代码）：列表页 = `` `${template}_${menuCode}` ``；表单页 = **元对象编码**。
 * 3. **组件 / 钩子映射**：`meta_hotel` 与 `table_meta_hotel` 共用**同一份脚本**（旧 `eova.vue.config.js`
 *    里两个键都挂 `/hotel/app.js`）；`meta_product` **只有 `setup`、没有 `onReady`**（旧文件事实）。
 * 4. ★ **安装语义**（`installCustomAppHooks`）：
 *    ① 命中 ⇒ 装进 `uzoo.vue`；② 换成未命中 ⇒ **撤掉自己装过的**（不许残留到下一页）；
 *    ③ **绝不碰外部注册的钩子**（真实部署别的脚本 / 判据手工注册的钩子）。
 */
import { beforeEach, describe, expect, it } from 'vitest'
import { CUSTOM_APPS, formCustomAppKey, listCustomAppKey } from '@/compat/custom-apps'
import { getUzoo, setUzooHooks } from '@/compat/eova-ext'
import {
  CUSTOM_APP_COMPONENTS,
  CUSTOM_APP_HOOKS,
  UNIMPLEMENTED_KEYS,
  customAppComponent,
  customAppHooks,
  installCustomAppHooks
} from '../registry'
import { metaHotelAppHooks } from '../metaHotelApp'
import { metaProductAppHooks } from '../metaProductApp'
import ProductApp from '../ProductApp.vue'
import MetaHotelFormApp from '../MetaHotelFormApp.vue'
import SwordComing from '../SwordComing.vue'

beforeEach(() => {
  delete (globalThis as unknown as Record<string, unknown>)['uzoo']
  // 清掉模块级的"上次装过什么"，让每个用例从确定状态开始
  setUzooHooks({}, { remove: ['setup', 'onReady'] })
})

describe('自定义 app 注册表（r327 切片 B）', () => {
  it('① ★ 登记 ↔ 实现构成划分：每个旧键要么有实现，要么显式登记为未实现', () => {
    const implemented = new Set([
      ...Object.keys(CUSTOM_APP_COMPONENTS),
      ...Object.keys(CUSTOM_APP_HOOKS),
      ...UNIMPLEMENTED_KEYS
    ])
    const missing = Object.keys(CUSTOM_APPS).filter((k) => !implemented.has(k))
    expect(missing, `旧配置里的键既没实现也没登记：${missing.join(', ')}`).toEqual([])
    // 反向：登记为"未实现"的键不得同时又"有实现"（否则缺口表与实现互相打脸）
    const both = UNIMPLEMENTED_KEYS.filter(
      (k) => k in CUSTOM_APP_COMPONENTS || k in CUSTOM_APP_HOOKS
    )
    expect(both, `既登记未实现又有实现：${both.join(', ')}`).toEqual([])
  })

  it('② 键名口径：列表页 `${template}_${menuCode}`、表单页 = 元对象编码', () => {
    expect(listCustomAppKey('table', 'meta_hotel')).toBe('table_meta_hotel')
    expect(formCustomAppKey('meta_hotel')).toBe('meta_hotel')
    expect(formCustomAppKey('meta_product')).toBe('meta_product')
    expect(customAppComponent('table_meta_hotel')).toBe(SwordComing)
    expect(customAppComponent('meta_hotel')).toBe(MetaHotelFormApp)
    expect(customAppComponent('meta_product')).toBe(ProductApp)
    // 未命中的键 ⇒ undefined（调用方据此**不挂载**，反向用例防"所有表单页都多挂一个组件"）
    expect(customAppComponent('eova_object_code')).toBeUndefined()
    expect(customAppHooks('eova_object_code')).toBeUndefined()
  })

  it('③ ★ 钩子映射：两个 hotel 键共用同一份脚本；meta_product 无 onReady（旧文件事实）', () => {
    expect(customAppHooks('table_meta_hotel')).toBe(metaHotelAppHooks)
    expect(customAppHooks('meta_hotel')).toBe(metaHotelAppHooks)
    expect(customAppHooks('table_meta_hotel')!.onReady).toBe(metaHotelAppHooks.onReady)
    expect(customAppHooks('meta_product')).toBe(metaProductAppHooks)
    expect(typeof customAppHooks('meta_product')!.setup).toBe('function')
    expect(customAppHooks('meta_product')!.onReady).toBeUndefined()
  })

  it('④ ★ installCustomAppHooks：命中 ⇒ 装进 uzoo.vue；换成未命中 ⇒ 撤掉自己装过的', () => {
    installCustomAppHooks('meta_hotel')
    expect(getUzoo().vue.setup).toBe(metaHotelAppHooks.setup)
    expect(getUzoo().vue.onReady).toBe(metaHotelAppHooks.onReady)

    // 换到一个没有自定义 app 的对象 ⇒ 不许残留（旧栈每页重新加载脚本，天然不残留）
    installCustomAppHooks('eova_object_code')
    expect(getUzoo().vue.setup).toBeUndefined()
    expect(getUzoo().vue.onReady).toBeUndefined()
  })

  it('⑤ ★ 不碰外部注册的钩子（反向：撤的范围只限本机制装过的那几个名字）', () => {
    const external = (): void => undefined
    installCustomAppHooks('meta_product') // 只装 setup
    getUzoo().vue.onReady = external // 外部脚本（或判据）注册的钩子
    installCustomAppHooks('meta_product') // 再装一次
    expect(getUzoo().vue.onReady).toBe(external)
    expect(getUzoo().vue.setup).toBe(metaProductAppHooks.setup)
  })
})
