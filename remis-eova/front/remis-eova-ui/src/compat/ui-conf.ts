/**
 * `me.conf` 的装配接缝（第 103 轮）
 *
 * ## 旧行为（取证：`docs/DES-003-R1-ui-runtime-config-design.md` §2）
 *
 * `_view/_block/base.html:11` 与 `_view/index/index.html:53` 是同一句话：
 * ```html
 * <script>me.conf.putAll('{"web_cdn":"…","web_file":"…","ver":"…","ui.skin":"ele", …}');</script>
 * ```
 * 值是服务端模板 `#(getUIConf())` 的结果 —— 即 `eova_config` 表里 **`is_server = 0`** 的那些行
 * （`EovaConfigPlugin:65-68`），非 `PRD` 环境优先取 `test` 列。
 *
 * 实测交叉验证：库种子数据里 `status=1 且 is_server=0` 共 **8** 个键，
 * 与 S00 录制的真实响应里 `me.conf.putAll` 的 8 个键**集合相等**。
 *
 * ## 分离后的缺口与影响（不是"美观问题"）
 *
 * 前后分离后没有服务端模板注入 ⇒ `me.conf` 空。**具体后果已取证**：
 * `EvUpload`（制品内）用 `K.conf.get("web_cdn") || K.conf.get("web_file")` 作为
 * **已上传文件的预览/下载 URL 基址** ⇒ conf 缺失时历史附件 URL 拼出来是空的（**静默**打不开）。
 *
 * ## 本模块的职责边界
 *
 * - **做**：把一份 conf 对象按旧写法装进 `me`（`putAll`），并在缺失/失败时**响亮告警但不禁用应用**。
 * - **不做**：决定 conf 从哪里来。来源方案（新增端点 / 构建期注入 / 平台配置）属 `DES-003` 待确认项，
 *   本模块只暴露可注入的 `fetcher`，选定后换注入源即可。
 *
 * 纪律：conf 缺失**不得**阻塞启动（页面主体仍可用），但**必须**在控制台留下可诊断的告警 ——
 * 静默缺失会让"附件打不开"变成无差别现象。
 */

import { getEovaMe } from './eova-runtime'

/** conf 取值：字符串键值对 */
export type UiConf = Record<string, string>

/** `me.conf` 的最小面（`putAll` 是旧栈的唯一注入方式） */
export interface EovaConf {
  putAll: (json: string) => unknown
  get?: (key: string, defaultValue?: string) => string
}

/** 装配选项 */
export interface LoadUiConfOptions {
  /**
   * conf 来源：返回 JSON 字符串（与旧 `getUIConf()` 同形）。
   *
   * 返回 null / 空串表示"当前环境没有来源"（例如端点尚未上线）——
   * 此时只告警，不抛错（应用可用性优先，缺口由 DES-003 决策后补齐）。
   */
  fetcher?: () => Promise<string | null>
  /** 目标 `me`（默认取接缝） */
  me?: { conf?: EovaConf } & Record<string, unknown>
  /** 告警出口（判据可注入） */
  warn?: (message: string) => void
}

/**
 * 把 conf JSON 装进 `me.conf`（逐字对应旧 `me.conf.putAll('<json>')`）。
 *
 * @param me   `me` 实例
 * @param json conf JSON 字符串
 * @returns 是否写入成功
 */
export function applyUiConf(
  me: { conf?: EovaConf },
  json: string,
  warn: (m: string) => void = console.warn
): boolean {
  const conf = me.conf
  if (!conf || typeof conf.putAll !== 'function') {
    warn('[ui-conf] me.conf.putAll 不存在 —— conf 未装配（legacy 制品版本不符？）')
    return false
  }
  try {
    const parsed = JSON.parse(json)
    if (parsed == null || typeof parsed !== 'object' || Array.isArray(parsed)) {
      warn('[ui-conf] conf 不是对象，已忽略（不得把非法载荷塞进 me.conf）')
      return false
    }
    conf.putAll(json)
    return true
  } catch (e) {
    warn(`[ui-conf] conf 解析失败，已忽略：${(e as Error).message}`)
    return false
  }
}

/**
 * 启动期装配 `me.conf`（幂等；失败只告警不抛错）。
 *
 * @param options 注入点
 * @returns 是否写入成功
 */
export async function loadUiConf(options: LoadUiConfOptions = {}): Promise<boolean> {
  const warn = options.warn ?? ((m: string) => console.warn(m))
  const me = options.me ?? (getEovaMe() as unknown as { conf?: EovaConf })
  const fetcher = options.fetcher
  if (!fetcher) {
    warn(
      '[ui-conf] 未提供 conf 来源 ⇒ me.conf 为空。已知后果：EvUpload 的文件预览/下载 URL 基址' +
        '（web_cdn/web_file）为空，历史附件链接不可用。来源方案见 DES-003。'
    )
    return false
  }
  let json: string | null
  try {
    json = await fetcher()
  } catch (e) {
    warn(`[ui-conf] 拉取 conf 失败，已忽略（不阻塞启动）：${(e as Error).message}`)
    return false
  }
  if (!json) {
    warn('[ui-conf] conf 来源返回空 ⇒ me.conf 保持为空（来源方案见 DES-003）')
    return false
  }
  return applyUiConf(me as { conf?: EovaConf }, json, warn)
}
