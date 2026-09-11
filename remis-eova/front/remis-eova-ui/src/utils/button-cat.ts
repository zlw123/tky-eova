/**
 * 功能授权页的纯逻辑（第 109 轮）—— 与旧 `_view/menu/auth/app.js` 的 `groupByCat` 对应。
 *
 * 抽成独立模块的理由（与 `tab.ts`/`reorder.ts` 同）：
 *  ① `<script setup>` 不能含 ES `export`；
 *  ② 分组规则要**单独判** —— 它不是"随便分个组"，而是决定表格**列的分组与顺序**。
 */

/**
 * 按 `cat` 把按钮分组成 `{ <cat>: [按钮…] }`。
 *
 * 旧实现原文：
 * ```js
 * array.reduce((acc, item) => { if (!acc[item.cat]) acc[item.cat] = []; acc[item.cat].push(item); return acc }, {})
 * ```
 *
 * ★ 四条必须保留的语义（第 109 轮**实测**，不是推演）：
 *  ① 结果是**普通对象**，键是 `cat` 的值（数字会变成字符串键）；
 *  ② **整数样键按升序枚举**（JS 对象语义），**非整数样的键才按插入顺序**
 *     —— 实测 `{3:…,1:…}` 的 `Object.keys` 是 `['1','3']`。这条**直接决定界面顺序**：
 *     模板 `v-for="(btns, index) in btnCats"` 走 `Object.keys` ⇒ 多张表按**升序的 cat** 渲染，
 *     而不是"按按钮出现顺序"。写判据时若按"插入顺序"断言，会与实现和旧栈都不符。
 *  ③ **不排序、不合并、不去重**（没有 `sort`，同 `cat` 自然合并到一个数组）；
 *  ④ **不改写 `cat` 的类型**（旧实现用对象键隐式转字符串，这里显式 `String(...)`，结果一致）。
 *
 * @param list 按钮列表（元素需带 `cat`）
 * @returns 分组字典
 */
export function groupByCat<T extends { cat?: unknown }>(list: T[] | null | undefined): Record<string, T[]> {
  const out: Record<string, T[]> = {}
  if (!Array.isArray(list)) {
    return out
  }
  for (const item of list) {
    const key = String((item as { cat?: unknown }).cat)
    if (!out[key]) {
      out[key] = []
    }
    out[key].push(item)
  }
  return out
}
