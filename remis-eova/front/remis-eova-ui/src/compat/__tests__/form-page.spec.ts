/**
 * 表单动作页页面契约的判据（`compat/form-page.ts`；切片 S6，第 295 轮）
 *
 * 钉的契约（每条都对应一种"页面看起来正常但行为不对"）：
 *  ① `parseRefFixed` = `WidgetManager#getRef` 的**逐行等价**，含两处"像 bug 实为语义"的地方：
 *     没有冒号的段（含**空段**）**抛出后被吞**、返回**已累积的部分**（不是空表）；多余冒号只取前两段；
 *  ② `resolveFormPageParams`：`objectCode` 来自**路由参数**（= 旧 `get(0)`），
 *     `biz`/`id` 来自查询串且**缺省为空串**（旧 `get("biz","")`），`ref` 缺省空串；
 *  ③ `writeFormPageUzooPage`：`uzoo.page` **整体替换**（SPA 的 `uzoo` 跨路由常驻，
 *     逐键写会把上一页的键留下来）；`object_code` 必须写（`me.urls.url('form_add', props)`
 *     的 `{{object_code}}` 靠它替换）；`fixed` **只在 create 写且无条件写**（旧 add 页的
 *     `if (fixed)` 对 `"{}"` 也是真值）；缺来源的键要**被点名**（页面据此告警）。
 */
import { describe, expect, it } from 'vitest'
import {
  FORM_PAGE_MODE,
  FORM_PAGE_OBJECT_KEYS,
  parseRefFixed,
  resolveFormPageParams,
  writeFormPageUzooPage
} from '../form-page'

describe('parseRefFixed（= WidgetManager#getRef 的逐行等价）', () => {
  it('空/未给/空白 ⇒ 空表（旧 `x.isEmpty(ref)` 早返回）', () => {
    expect(parseRefFixed(undefined)).toEqual({})
    expect(parseRefFixed(null)).toEqual({})
    expect(parseRefFixed('')).toEqual({})
    expect(parseRefFixed('   ')).toEqual({})
    // 制品 `x.isEmpty` 对字符串 `"undefined"` 也判空（取证：eova-tools.umd.js 的 `n === "undefined"`）
    expect(parseRefFixed('undefined')).toEqual({})
  })

  it('正常形态 `k:v,k:v` ⇒ 键值表（值保持字符串，不转数字）', () => {
    expect(parseRefFixed('a:1,b:2')).toEqual({ a: '1', b: '2' })
    expect(parseRefFixed('uid:7')).toEqual({ uid: '7' })
  })

  it('★ 没有冒号的段：旧实现抛 AIOOBE 被 catch 吞掉 ⇒ 返回【已累积的部分】，不是空表', () => {
    // 这条是"像 bug 实为语义"的第一处 —— 写成"整表丢弃"或"跳过该段继续"都是**不等价**的
    expect(parseRefFixed('a:1,b')).toEqual({ a: '1' })
    expect(parseRefFixed('b,a:1')).toEqual({})
  })

  it('★ 空段同样触发"缺冒号"⇒ 停在它之前（不是继续解析后面的段）', () => {
    // `"a:1,,b:2".split(",")` = `['a:1','','b:2']`（Java 也只去掉**尾部**空串）；
    // 空段没有冒号 ⇒ 与"没有冒号的段"同一条路径 ⇒ 返回已累积部分 `{a:'1'}`，`b` 拿不到。
    expect(parseRefFixed('a:1,,b:2')).toEqual({ a: '1' })
    // 多余冒号只取前两段（`strs[1]`）
    expect(parseRefFixed('a:1:2')).toEqual({ a: '1' })
  })

  it('★ 尾部空段不影响已累积结果（Java `split` 去尾空串，JS 不去 ⇒ 结果必须相同）', () => {
    expect(parseRefFixed('a:1,')).toEqual({ a: '1' })
  })

  it('可注入的空值判定：生产走制品 `x.isEmpty`（判据传进去也必须生效）', () => {
    const neverEmpty = (): boolean => false
    // 用"永不判空"的判定 ⇒ `''` 也会被 split 成 `['']`，因缺冒号而立即停手 ⇒ 空表
    expect(parseRefFixed('', neverEmpty)).toEqual({})
    expect(parseRefFixed('a:1', neverEmpty)).toEqual({ a: '1' })
  })
})

describe('resolveFormPageParams（= AppController#add/update/detail 的参数派生）', () => {
  it('objectCode 来自路由参数（旧 `get(0)`）；biz/id/ref 来自查询串', () => {
    const p = resolveFormPageParams({
      objectCodeFromRoute: 'eova_object_code',
      search: '?biz=meta_menu&id=12&ref=uid:7,type:9'
    })
    expect(p.objectCode).toBe('eova_object_code')
    expect(p.biz).toBe('meta_menu')
    expect(p.id).toBe('12')
    expect(p.ref).toBe('uid:7,type:9')
    expect(p.fixed).toEqual({ uid: '7', type: '9' })
  })

  it('★ 缺省全是空串（旧 `get("biz","")` / `get("id")`），不是 undefined', () => {
    const p = resolveFormPageParams({ objectCodeFromRoute: 'x', search: '' })
    expect(p.biz).toBe('')
    expect(p.id).toBe('')
    expect(p.ref).toBe('')
    expect(p.fixed).toEqual({})
  })

  it('路由参数缺失/非字符串 ⇒ objectCode 为空串（由页面显式降级，不在这里抛）', () => {
    expect(resolveFormPageParams({ objectCodeFromRoute: undefined, search: '' }).objectCode).toBe('')
    expect(resolveFormPageParams({ objectCodeFromRoute: ['a', 'b'], search: '' }).objectCode).toBe('')
    expect(resolveFormPageParams({ objectCodeFromRoute: '   ', search: '' }).objectCode).toBe('')
  })
})

describe('writeFormPageUzooPage（= `_page/form.html` 整对象赋值 + 页内脚本）', () => {
  /** 造一个"上一页留下的脏 uzoo" */
  function dirtyTarget(): Record<string, unknown> {
    return {
      uzoo: {
        page: { menu_id: 'OLD', object_code: 'OLD_CODE', fixed: { stale: '1' } },
        vue: {},
        app: {}
      }
    }
  }

  it('★ uzoo.page 整体替换：上一页留下的键必须消失（SPA 的 uzoo 跨路由常驻）', () => {
    const t = dirtyTarget()
    writeFormPageUzooPage(
      {
        objectCode: 'eova_object_code',
        form: 'create',
        biz: '',
        fixed: {},
        objectMeta: { id: 3, name: '对象', pk_name: 'id' }
      },
      t
    )
    const page = (t['uzoo'] as { page: Record<string, unknown> }).page
    expect(page['menu_id']).toBeUndefined()
    expect(page['code']).toBe('eova_object_code')
    expect(page['object_code']).toBe('eova_object_code')
    expect(page['form']).toBe('create')
    expect(page['biz']).toBe('')
    expect(page['object_id']).toBe(3)
    expect(page['object_name']).toBe('对象')
    expect(page['object_pk']).toBe('id')
  })

  it('★ fixed 只在 create 写、且**无条件写**（旧 `if (fixed)` 对 `"{}"` 为真）', () => {
    const create = dirtyTarget()
    const missingCreate = writeFormPageUzooPage(
      { objectCode: 'x', form: 'create', biz: '', fixed: {}, objectMeta: null },
      create
    )
    expect((create['uzoo'] as { page: Record<string, unknown> }).page['fixed']).toEqual({})
    // create 页的 fixed 恒有值 ⇒ 它**不**出现在缺口里
    expect(missingCreate).not.toContain('fixed')

    // update/detail 的旧 html 没有那一段脚本 ⇒ **不得**写 fixed（写了就与旧栈不等价）
    for (const form of ['update', 'read'] as const) {
      const t = dirtyTarget()
      writeFormPageUzooPage(
        { objectCode: 'x', form, biz: 'b', fixed: { uid: '7' }, objectMeta: null },
        t
      )
      const page = (t['uzoo'] as { page: Record<string, unknown> }).page
      expect(page['fixed'], `form=${form} 不应写 fixed`).toBeUndefined()
      expect(page['form']).toBe(form)
      expect(page['biz']).toBe('b')
    }
  })

  it('★ 缺来源的键被点名（页面据此告警），且**不写 undefined**', () => {
    const t = dirtyTarget()
    const missing = writeFormPageUzooPage(
      { objectCode: 'eova_object_code', form: 'create', biz: '', fixed: {}, objectMeta: null },
      t
    )
    // objectMeta 为 null ⇒ 那三个服务端插值键全是缺口；object_code 有来源 ⇒ 不在缺口里
    expect(missing).toEqual(['object_id', 'object_name', 'object_pk'])
    const page = (t['uzoo'] as { page: Record<string, unknown> }).page
    for (const k of ['object_id', 'object_name', 'object_pk']) {
      expect(k in page, `${k} 不得被写成 undefined`).toBe(false)
    }
  })

  it('objectCode 为空 ⇒ object_code 也进缺口（页面据此走可声明降级）', () => {
    const t = dirtyTarget()
    const missing = writeFormPageUzooPage(
      { objectCode: '', form: 'create', biz: '', fixed: {}, objectMeta: null },
      t
    )
    expect(missing).toContain('object_code')
  })

  it('常量与旧栈三个模板逐字对应（form 名是本模块与页面的唯一约定）', () => {
    expect(FORM_PAGE_MODE).toEqual({ add: 'create', update: 'update', detail: 'read' })
    expect(FORM_PAGE_OBJECT_KEYS).toEqual(['object_id', 'object_name', 'object_code', 'object_pk'])
  })
})
