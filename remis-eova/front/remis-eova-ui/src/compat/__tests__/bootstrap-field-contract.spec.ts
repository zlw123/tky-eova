/**
 * 引导数据**字段名契约**的漂移判据（第 127 轮）
 *
 * ## 为什么单独钉字段名
 *
 * DES-004 §3.1 明确写了「字段名**沿用旧 `setAttr` 的名字**，少一层名字映射 = 少一类
 * 『映射写错但两边都绿』的缺陷」。⇒ 一旦有人把 `pk_name` 写成 `pkName`、把 `btnList` 写成
 * `buttons`、或把请求体的 `biz` 去掉，**前端不会报错**：`undefined` 会静默地一路传下去
 * （拼 URL 得到空值、按钮区渲染为空），而所有现有判据（都拿"造出来的引导数据"当输入）**照样绿**。
 *
 * ## 判据口径
 *
 * · **独立字面量**：本文件里的期望值是从 DES-004 §3.1 抄下来的**字面量**，不 import 被测模块
 *   的任何常量（否则改常量两边一起变 ⇒ 判据恒真，R74 已记）。
 * · **跨制品等值**：从源码文本里抽接口字段名与请求键，与上面的字面量比对。
 * · **反空断言**：抽取结果必须非空且含已知项，避免"抽取规则失效 ⇒ 因为找不到而通过"。
 *
 * ⚠️ 依赖说明：本判据只读**已入库**的源码（`src/compat/**`），**不读 `docs/**`**
 * （治理文档按纪律只在本地维护，判据不得依赖它们）。
 */
import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { BOOTSTRAP_URL_KEYS } from '../page-bootstrap'
import { createBootstrapFetcher } from '../page-bootstrap-fetcher'

/** DES-004 §3.1 的响应字段（字面量，独立于被测模块） */
const SPEC_RESPONSE_KEYS = ['state', 'object', 'menu', 'btnList', 'loginUser', 'isQuery']

/** DES-004 §3.1 的 `object` 子字段（字面量） */
const SPEC_OBJECT_KEYS = ['code', 'name', 'pk_name', 'table', 'data_source']

/** DES-004 §3.1 的请求体字段（字面量：`path` + 5 个 URL 参数） */
const SPEC_REQUEST_KEYS = ['path', 'object', 'menu', 'biz', 'mode', 'id']

/**
 * 从源码文本里抽某个 interface 的字段名
 *
 * @param file 源码路径
 * @param name 接口名
 * @returns 字段名数组（保持源码顺序）
 */
function interfaceFields(file: string, name: string): string[] {
  const src = readFileSync(file, 'utf-8')
  const re = new RegExp(`export interface ${name} \\{([\\s\\S]*?)\\n\\}`, 'm')
  const body = re.exec(src)?.[1]
  if (body == null) {
    return []
  }
  const out: string[] = []
  const fieldRe = /^\s{2}([a-zA-Z_$][\w$]*)\??:/gm
  let m: RegExpExecArray | null
  while ((m = fieldRe.exec(body)) !== null) {
    out.push(m[1])
  }
  return out
}

describe('引导数据字段名契约（DES-004 §3.1）', () => {
  it('① 请求参数键就是约定的 5 个（顺序固定）', () => {
    expect([...BOOTSTRAP_URL_KEYS]).toEqual(['object', 'menu', 'biz', 'mode', 'id'])
  })

  it('② fetcher 的请求体键 = `path` + 那 5 个（不多不少）', async () => {
    let sent: Record<string, string> = {}
    const fetcher = createBootstrapFetcher({
      post: async (_url, body) => {
        sent = body as Record<string, string>
        return { status: 200, data: { state: 'ok' } }
      },
      path: () => '/app/menu_x'
    })
    await fetcher({ url: { object: 'o', menu: 'm', biz: 'b', mode: 'q', id: '1' } })
    // 独立字面量（§3.1 的 body 例子）
    expect(Object.keys(sent).sort()).toEqual([...SPEC_REQUEST_KEYS].sort())
    expect(sent['path']).toBe('/app/menu_x')
  })

  it('③ 消费端类型里必须存在 §3.1 的响应字段（含 `btnList`/`isQuery` 的大小写）', () => {
    // 反空断言：抽取规则失效时不能"因为找不到而通过"
    const pageFields = interfaceFields('src/compat/page-bootstrap.ts', 'PageBootstrap')
    expect(pageFields.length).toBeGreaterThanOrEqual(6)
    expect(pageFields).toContain('btnList')

    for (const k of SPEC_RESPONSE_KEYS) {
      // `state` 由端点返回、消费端用 `fromServer` 表达"是否拿到服务端数据" ⇒ 单独说明（见下一条）
      if (k === 'state') {
        expect(pageFields).toContain('fromServer')
        continue
      }
      expect(pageFields, `PageBootstrap 缺 §3.1 的字段 ${k}`).toContain(k)
    }
  })

  it('④ `object` 子字段与 §3.1 逐字一致（`pk_name` 不是 `pkName`）', () => {
    const objectFields = interfaceFields('src/compat/page-bootstrap.ts', 'BootstrapObject')
    expect(objectFields.length).toBeGreaterThanOrEqual(5)
    for (const k of SPEC_OBJECT_KEYS) {
      expect(objectFields, `BootstrapObject 缺 §3.1 的字段 ${k}`).toContain(k)
    }
    // 反面：常见笔误不得出现
    expect(objectFields).not.toContain('pkName')
  })

  it('⑤ `menu` 至少要有 `code`/`name`，且 `template`（r118 的分派键）在位', () => {
    const menuFields = interfaceFields('src/compat/page-bootstrap.ts', 'BootstrapMenu')
    expect(menuFields).toEqual(expect.arrayContaining(['code', 'name', 'template']))
  })

  it("⑥ `state` 字段的语义在消费端有对应物（`fromServer`）且非 ok 的 state 走失败分支", () => {
    const src = readFileSync('src/compat/page-bootstrap.ts', 'utf-8')
    // 端点用 `state` 表示成功；消费端把它折成 `fromServer`（布尔），失败分支必须显式存在
    expect(src).toContain("p['state'] !== 'ok'")
    expect(src).toContain('fromServer')
  })
})
