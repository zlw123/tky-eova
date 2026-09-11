/**
 * 重新排序页的数据解析（纯逻辑）—— 与旧模板的 `JSON.parse('#json(data)')` 对应。
 *
 * 抽成独立模块的两个理由（与 `tab.ts`/`menu.ts` 同）：
 *  ① `<script setup>` 不能含 ES `export`（编译器会直接报错）；
 *  ② 这条"容错规则"是要**单独判**的契约：什么算合法排序项、非法时怎么办。
 */

/**
 * 解析服务端给的排序项。
 *
 * 旧实现：模板里 `JSON.parse('#json(data)')`，`data` 由 `MetaController#reorder()` 查库后
 * `set("data", tps)`，`tps` 是 `{id, name, num}` 的列表。
 *
 * 本函数兼容两种端点形态：
 *  - **数组**（端点直接返回对象 —— DES-004 的推荐形态）；
 *  - **JSON 字符串**（端点逐字复刻 `#json(data)` 时）。
 *
 * 其它形态一律**告警并退化为 `[]`** —— 不得静默把脏数据当排序项塞进拖拽列表
 * （那会让页面显示出乱序/空白条目而无从诊断）。
 *
 * @param raw 服务端值
 * @param warn 告警出口
 * @returns 排序项数组（元素为对象）
 */
export function parseReorderRows(
  raw: unknown,
  warn: (m: string) => void = console.warn
): Array<Record<string, unknown>> {
  if (raw == null) {
    return []
  }
  let value: unknown = raw
  if (typeof raw === 'string') {
    try {
      value = JSON.parse(raw)
    } catch (e) {
      warn(`[meta-reorder] 服务端 data 不是合法 JSON，已忽略：${(e as Error).message}`)
      return []
    }
  }
  if (!Array.isArray(value)) {
    warn('[meta-reorder] 服务端 data 不是数组，已忽略（排序项必须是数组）')
    return []
  }
  return value as Array<Record<string, unknown>>
}
