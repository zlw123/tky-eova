/**
 * 页面引导数据的**消费端**（第 124 轮）—— DES-004 §3.1 端点 `POST /api/page/bootstrap` 的前端一侧
 *
 * ## 为什么先做消费端（而不是先做端点）
 *
 * DES-004 §3.1 定义了端点形态，但**端点本身落不了地**——原因不是"没设计"，而是**没有可落的层**：
 *
 * ```
 * $ grep -rl "springframework" --include=*.java remis-eova/backend/yudao-cloud/yudao-module-eova | wc -l
 * 0
 * $ grep -rn "@RestController|@RequestMapping|@Controller" --include=*.java <同目录> | wc -l
 * 0
 * ```
 *
 * 该模块是**JFinal→Spring 的代码级 port**：只有 `jakarta.servlet-api`（`provided`），
 * **没有 Spring 依赖、没有 MVC、没有任何 `@Controller`**。⇒ 落地这个端点 = **引入整个 Web 层**，
 * 属**阶段边界决策**（阶段 1 的"HTTP 容器层"未 port；阶段 3 又要按 yudao 的控制器约定落地）。
 * 该决策与"`template/form/*` 归口"同属**待用户口径**（见 `docs/session-current.md`）。
 *
 * 但**消费端不必等**：`loadPageBootstrap(fetcher)` 的接缝早已存在（r105），只差一个"默认来源"。
 * 本模块把它补上 ⇒ 端点一旦落地（不论以哪种形态），**页面无需改动**即可亮起来；
 * 而端点未落地时的行为与今天一致（**响亮告警 + 只保留 URL 参数**），不会静默假装成功。
 *
 * ## 请求形态（逐条对齐 DES-004 §3.1，不自行发明）
 *
 * ```
 * POST /api/page/bootstrap
 * body: { path: '/eova/template/table/xxx', object?: 'xxx', menu?: 'xxx', biz?: '...', mode?: '...', id?: '...' }
 * ```
 *
 * · **POST**：与旧栈页面/数据请求一致，且避免 `object` 等参数进 URL 日志；
 * · `path` 取**当前地址的 pathname**（服务端据此判定该页需要哪些引导数据）；
 * · 只带 `BOOTSTRAP_URL_KEYS`（`object`/`menu`/`biz`/`mode`/`id`）这 5 个键，**不夹带别的**；
 * · 返回值按"JSON 文本"交给 `loadPageBootstrap`（它的注入点签名是 `Promise<string|null>`）——
 *   若服务端/拦截器已把响应体解析成对象，则回退为 `JSON.stringify`（两种都接受，避免把"谁来解析"变成隐式约定）；
 * · **失败必须抛**（非 2xx / 网络异常）：`loadPageBootstrap` 会响亮告警并降级 ⇒ 各页走各自的可声明回退。
 */

import axios from 'axios'
import { BOOTSTRAP_URL_KEYS, type UrlParams } from './page-bootstrap'

/** 端点路径（DES-004 §3.1） */
export const BOOTSTRAP_ENDPOINT = '/api/page/bootstrap'

/** 引导数据来源签名（与 `page-bootstrap.ts` 的注入点一致） */
export type BootstrapFetcher = (ctx: { url: UrlParams }) => Promise<string | null>

/** 造 fetcher 的注入点（判据用；生产用默认值） */
export interface BootstrapFetcherDeps {
  /** POST 实现（默认 axios.post） */
  post?: (url: string, body: unknown) => Promise<{ status?: number; data: unknown }>
  /** 当前路径（默认 `window.location.pathname`） */
  path?: () => string
}

/**
 * 把服务端响应体归一成"JSON 文本"。
 *
 * @param data 响应体（可能是对象或字符串）
 * @returns JSON 文本
 */
function toJsonText(data: unknown): string {
  return typeof data === 'string' ? data : JSON.stringify(data)
}

/**
 * 造引导数据来源（DES-004 §3.1 的请求侧）。
 *
 * @param deps 注入点（判据可替换 POST 与 path）
 * @returns `loadPageBootstrap` 可直接使用的 fetcher
 */
export function createBootstrapFetcher(deps: BootstrapFetcherDeps = {}): BootstrapFetcher {
  const post = deps.post ?? ((url: string, body: unknown) => axios.post(url, body))
  const pathOf = deps.path ?? (() => (typeof window === 'undefined' ? '' : window.location.pathname))

  return async ({ url }: { url: UrlParams }) => {
    const body: Record<string, string> = { path: pathOf() }
    // 只带约定的 5 个键（顺序固定，便于判据/日志比对）
    for (const k of BOOTSTRAP_URL_KEYS) {
      const v = url[k as keyof UrlParams]
      if (v != null) {
        body[k] = String(v)
      }
    }
    const res = await post(BOOTSTRAP_ENDPOINT, body)
    const status = res.status ?? 200
    if (status >= 400) {
      throw new Error(`引导端点 ${BOOTSTRAP_ENDPOINT} 返回 HTTP ${status}`)
    }
    return toJsonText(res.data)
  }
}

/** 默认来源（启动期由 `main.ts` 安装；未安装时为 null ⇒ 走"未提供服务端引导数据来源"告警） */
let defaultFetcher: BootstrapFetcher | null = null

/**
 * 安装默认引导数据来源。
 *
 * @param fetcher 来源；传 null 表示卸载（判据用）
 */
export function setDefaultBootstrapFetcher(fetcher: BootstrapFetcher | null): void {
  defaultFetcher = fetcher
}

/**
 * 取默认引导数据来源。
 *
 * @returns 已安装的来源；未安装返回 null
 */
export function getDefaultBootstrapFetcher(): BootstrapFetcher | null {
  return defaultFetcher
}
