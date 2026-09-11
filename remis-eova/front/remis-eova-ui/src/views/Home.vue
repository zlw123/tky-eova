<!--
  主框架页（阶段 2 第 2 个入口页）

  契约来源（逐条对齐旧实现 `_view/index/index.html` + `index.js`，revision 锁定；
  证据 `docs/.local/baseline/evidence/frame-contract.json`（第 95 轮）
      + `docs/.local/baseline/evidence/index-remaining-contract.json`（第 101 轮））：

  取菜单（第 95/96 轮已钉）
  · `POST /api/home/menu`，空体；`ret.state === 'ok'` ⇒ `menus = ret.menus`、`cats = ret.cats`
  · **cats 过滤规则**：先收集 `pids = { menus[].parent_id | parent_id !== null && parent_id >= 0 }`，
    再 `cats = ret.cats.filter(c => pids.has(c.id))` —— 只显示"确实有子菜单"的目录
  · **握手事件**：加载完成（`nextTick` 之后）派发 `document.dispatchEvent(new Event('EovaMenuNextTick'))`
    —— 子组件初始化依赖它；**改名/漏发都会让子组件不再初始化**
  · 失败文案：业务失败 ⇒ `加载菜单错误, 请稍候再试`；网络异常 ⇒ `请求异常`

  第 101 轮补齐的剩余契约（旧 `index.js` 其余函数 + `index.html` 结构）
  · **内容区/iframe**：每个页签一个 `<iframe :id="'IF'+m.id" :src="m.link">`，用 `v-show`（不是
    `v-if`）⇒ **iframe 不销毁、切页签保留状态**；`refresh()` 靠 `'IF'+id` 找回它
  · `showMenu` 对布局的**唯一**耦合点：`.eova-home_tabs` 与 `.eova-home_body` 的
    `:style="showMenu ? 'left:200px' : 'left:0'"`
  · `foldMenu`：`showMenu = !showMenu`；按钮图标 `'eova-icon-' + (showMenu ? 'shrink-right' : 'spread-left')`
  · `refresh`：先 `contentWindow.location.reload(true)`；跨域抛错 ⇒ 记 `tmpUrl`、置
    `about:blank`、**300ms 后**还原（URL 带 `#hash` 时必须先变别的值才会重载）；兜底再抛 ⇒ 只打日志
  · `toggleFullScreen`：探测顺序 `requestFullScreen` → `webkitRequestFullScreen` →
    `mozRequestFullScreen` → `msRequestFullscreen`（**旧名不是标准名**，逐字保留）；
    退出**只**探测 `document.exitFullscreen`；无 `fullscreenchange` 监听 ⇒ `isFull` 不会因 ESC 回落
  · 用户菜单：`switchUser`（`me.cross.on('eova-layer-ok_done_data')` 取回 currentUser →
    `me.layer.open('超级用户切换','/eova/admin/su',1200,0.9,cb)`，cb 内 `me.layer.msg('切换为:'+currentUser)`
    并 `init()`；旧实现 `me.cross.off` **被注释**⇒ 监听器泄漏，原样保留）、
    `resetUser`（`POST /eova/admin/reLogin`：ok ⇒ `currentUser=undefined` + `虚拟用户还原` + `init()`；
    非 ok ⇒ `me.layer.no(ret.msg)`；异常 ⇒ `me.layer.msg('客户端请求异常: ' + error.message)`）、
    `updatePwd`（`me.layer.open('修改密码','/user/password',400,300)` ⇒ `修改成功`）、
    `openEovaMsg`（`me.layer.open('系统消息','/app/eova_msg',0.9,0.95)` ⇒ `修改成功` ——
    **文案是旧的复制粘贴缺陷，原样保留**）、`logout`（`location.href = '/user/logout'`）、
    `hideUserMenu`（`setTimeout(300ms)` 后隐藏下拉）

  已声明适配（非静默改写）
  · 旧 `closeTab` 首行 `event.stopPropagation()`（隐式全局 `event`）⇒ 模板 `@click.stop` 承担（第 97/98 轮）
  · 旧用户下拉用 EovaUI 的 `<ev-popup>` 组件；EovaUI 组件库尚未纳入本工程 ⇒ 本页用同 class
    （`eova-select-content` / `eova-select_items`）的普通元素承载，交互（点内容后延时隐藏）逐条保留
  · 菜单图标（`c.icon` / `m.icon`）与目录折叠态 `c.open` 已按旧模板接上；`c.open` 的初值来自后端
    返回的 `cats[].open`，本页不额外赋初值（与旧实现一致）

  尚未迁移（登记）：Tab 的**持久化**、与 `/eova/ui/meta/eova.meta.js` 的交互、顶部导航里的
  静态外链项（九阴真经 / 问题反馈 / 查看用户数据 / 查看运行时配置 —— 属展示层）。
-->
<template>
  <div class="eova-home">
    <div class="eova-home_menu" v-show="showMenu">
      <div class="eova-menu">
        <template v-for="c in cats" :key="c.id">
          <div class="eova-menu_cat" v-show="!c.is_hide">
            <div class="eova-menu_cat__title" @click="c.open = !c.open">
              <i :class="c.icon"></i>
              {{ c.name }}
              <div class="eova-menu_cat__fold">
                <i :class="c.open ? 'eova-icon-up' : 'eova-icon-down'"></i>
              </div>
            </div>
            <div class="eova-menu_items" style="overflow-y: hidden" v-show="c.open">
              <template v-for="m in menus" :key="m.id">
                <div class="eova-menu_item" v-show="isUnder(c, m)">
                  <a @click="onMenuClick(m)" :title="titleOf(m)" :id="String(m.id)">
                    <i :class="m.icon"></i>
                    {{ m.name }}
                  </a>
                </div>
              </template>
            </div>
          </div>
        </template>
      </div>
    </div>

    <div class="eova-home_head">
      <div class="eova-home_logo" v-show="showMenu">
        <a href="/" title="Eova Meta">
          <!--
            旧模板为 `#(app_logo??'/eova/_view/index/meta.png')`（Enjoy 默认值）。
            此处保留**字面 URL**：`/eova/_view/index/**` 是后端静态路径（属前后端契约，
            不得改成 Vite 资产导入）。绑定表达式而非静态属性是有意的 —— 静态 `src` 会被
            Vite 当成构建期资产去解析，而该文件由后端提供。
            未迁移（登记）：`app_logo` 运行时配置的来源读取。
          -->
          <img :src="'/eova/_view/index/meta.png'" />
        </a>
      </div>
      <div class="eova-home_head__left">
        <nav class="eova-nav eova-nav__left">
          <ul class="eova-nav_items">
            <li class="eova-nav_item">
              <a href="javascript:;" title="侧边伸缩" @click="onFoldMenu()">
                <i :class="foldIcon"></i>
              </a>
            </li>
            <li class="eova-nav_item">
              <a href="javascript:;" title="刷新" @click="onRefresh()">
                <i class="eova-icon-refresh-3"></i>
              </a>
            </li>
            <li class="eova-nav_item">
              <a href="javascript:;" title="全屏" @click="onToggleFullScreen()">
                <i class="eova-icon-screen-full"></i>
              </a>
            </li>
          </ul>
        </nav>
      </div>
      <div class="eova-home_head__rigth">
        <nav class="eova-nav eova-nav__right">
          <ul class="eova-nav_items">
            <li class="eova-nav_item">
              <a target="_blank" @click="onOpenEovaMsg()" title="查看消息" id="notice">
                <i class="eova-icon-notice"></i>
                <span class="eova-badge-dot" style="margin-left: -2px"></span>
              </a>
            </li>
            <li class="eova-nav_item eova-home_head__user">
              <div class="eova-home-head__su" v-show="currentUser">
                <i class="eova-icon-user"></i>
                {{ currentUser }}
              </div>
              <span class="eova-icon-username" @click="onShowDrop()"></span>
              <div
                v-show="showDrop"
                class="eova-select-content"
                data-test="user-drop"
                @click="onHideUserMenu()"
              >
                <ul class="eova-select_items">
                  <li @click="onSwitchUser()"><i class="eova-icon-user"></i> 虚拟用户切换</li>
                  <li @click="onResetUser()"><i class="eova-icon-refresh"></i> 虚拟用户还原</li>
                  <li @click="onUpdatePwd()"><i class="eova-icon-password"></i> 修改密码</li>
                  <li @click="onLogout()"><i class="eova-icon-logout"></i> 立即退出</li>
                </ul>
              </div>
            </li>
          </ul>
        </nav>
      </div>
    </div>

    <div class="eova-home_tabs" :style="layoutLeft">
      <div class="eova-tab eova-tab_sys">
        <ul class="eova-tab_title">
          <li
            v-for="m in tabMenus"
            :key="m.id"
            @click="onTabClick(m)"
            :class="m.active ? 'eova-this' : ''"
          >
            <span>{{ m.name }}</span>
            <i class="eova-icon-close" @click.stop="onTabClose(m)"></i>
          </li>
        </ul>
      </div>
      <div class="eova-home_tabs__ops" title="关闭所有" @click="onCloseAll()">
        <i class="eova-icon-close"></i>
      </div>
    </div>

    <div class="eova-home_body" :style="layoutLeft">
      <template v-for="m in tabMenus" :key="m.id">
        <div class="eova-home_tabbody" v-show="m.active">
          <iframe
            :id="iframeId(m)"
            :src="m.link"
            class="eova-home_iframe"
            allowtransparency="true"
            frameborder="0"
          ></iframe>
        </div>
      </template>
    </div>

    <span style="color: red">{{ msg }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import axios from 'axios'
import { filterCats } from '@/utils/menu'
import {
  activeIframeId,
  bodyLeftStyle,
  buildIframeId,
  foldIconClass,
  isMenuUnderCat,
  menuTitle,
  refreshIframe,
  toggleFullscreen as toggleFullscreenByRules,
  type RefreshableIframe
} from '@/utils/frame'
import { getEovaMe } from '@/compat/eova-runtime'
import { closeAllTab, closeTab, initTabs, openTab, toTab, type TabItem } from '@/utils/tab'

/** 目录（过滤后）：`id` 必有（菜单归属判定要用），其余字段透传 */
interface Cat {
  id: number | string
  name?: string
  icon?: string
  open?: boolean
  is_hide?: boolean
  [k: string]: unknown
}
/** 目录（过滤后） */
const cats = ref<Cat[]>([])
/**
 * 菜单：旧实现里菜单对象本身就是"页签对象"（同一个对象进 tabMenus），
 * 故这里用同时满足两边的类型（`TabItem` + 菜单专有字段）
 */
interface Menu extends TabItem {
  parent_id?: number | string | null
  type?: string
  is_hide?: boolean
  icon?: string
}
/** 菜单 */
const menus = ref<Menu[]>([])
/** 已打开页签（初始含首页；行为全部经 utils/tab 的纯函数） */
const tabMenus = ref<TabItem[]>(initTabs())
/** 提示文案 */
const msg = ref('')
/** 是否显示左菜单（旧 `showMenu`） */
const showMenu = ref(true)
/** 是否全屏（旧 `isFull`） */
const isFull = ref(false)
/** 虚拟用户（旧 `currentUser`） */
const currentUser = ref<string | undefined>(undefined)
/** 用户下拉是否展开（旧 `showDrop` 是普通变量，由 ev-popup 驱动；此处需驱动视图故用 ref） */
const showDrop = ref(false)

/** 内容区 / 页签区左侧偏移（旧模板按 `showMenu` 计算） */
const layoutLeft = computed(() => bodyLeftStyle(showMenu.value))
/** 侧边伸缩按钮图标（旧模板按 `showMenu` 计算） */
const foldIcon = computed(() => foldIconClass(showMenu.value))

/**
 * 页签对应 iframe 的 id
 *
 * @param m 页签
 * @returns iframe 的 DOM id
 */
function iframeId(m: TabItem): string {
  return buildIframeId(m.id)
}

/**
 * 菜单是否挂在目录下（旧模板判定）
 *
 * @param c 目录
 * @param m 菜单
 * @returns 是否属于该目录
 */
function isUnder(c: Cat, m: Menu): boolean {
  return isMenuUnderCat(m, c)
}

/**
 * 菜单项标题（旧模板 `:title`）
 *
 * @param m 菜单
 * @returns 标题串
 */
function titleOf(m: Menu): string {
  return menuTitle(m)
}

/**
 * 加载菜单（逐条对齐旧 index.js 的 init 行为）
 */
async function init(): Promise<void> {
  try {
    const res = await axios.post('/api/home/menu', {})
    const ret = res.data
    if (ret && ret.state === 'ok') {
      menus.value = ret.menus
      cats.value = filterCats(ret.menus, ret.cats)
      // 旧实现：等 DOM 更新后再派发握手事件（子组件依赖它）
      await nextTick()
      document.dispatchEvent(new Event('EovaMenuNextTick'))
    } else {
      msg.value = '加载菜单错误, 请稍候再试'
    }
  } catch (e) {
    msg.value = '请求异常'
  }
}

/**
 * 点击菜单（旧 openTab）：type=open 的菜单走新窗口，不入页签
 *
 * @param m 菜单
 */
function onMenuClick(m: Menu): void {
  const link = openTab(tabMenus.value, m)
  if (link) {
    window.open(link)
  }
}

/**
 * 切换页签（旧 toTab）
 *
 * @param t 页签
 */
function onTabClick(t: TabItem): void {
  toTab(tabMenus.value, t)
}

/**
 * 关闭页签（旧 closeTab）。
 *
 * 旧实现首行是 `event.stopPropagation()`；分离后由模板的 `@click.stop` 承担（已声明适配）。
 *
 * @param t 页签
 */
function onTabClose(t: TabItem): void {
  closeTab(tabMenus.value, t)
}

/** 关闭全部页签（旧 closeAllTab：只留首页） */
function onCloseAll(): void {
  closeAllTab(tabMenus.value)
}

/** 折叠 / 展开左菜单面板（旧 foldMenu） */
function onFoldMenu(): void {
  showMenu.value = !showMenu.value
}

/** 展开用户下拉（旧实现由 `ev-popup` 承担，见文件头"已声明适配"） */
function onShowDrop(): void {
  showDrop.value = true
}

/**
 * 隐藏用户下拉（旧 hideUserMenu：延时 300ms 再隐藏）
 */
function onHideUserMenu(): void {
  setTimeout(() => {
    showDrop.value = false
  }, 300)
}

/**
 * 刷新当前页签的 iframe（旧 refresh：同源直刷；跨域走 about:blank 兜底）
 *
 * 旧实现在 `try` **之前**取 `getElementById('IF'+menu.id)` —— 无激活页签会抛错，
 * 本函数经 `activeIframeId` 保留该行为（不静默跳过）。
 */
function onRefresh(): void {
  const el = document.getElementById(activeIframeId(tabMenus.value))
  if (!el) {
    return
  }
  refreshIframe(el as unknown as RefreshableIframe)
}

/** 全屏切换（旧 toggleFullScreen：探测顺序与"退出只认 exitFullscreen"逐条保留） */
function onToggleFullScreen(): void {
  isFull.value = toggleFullscreenByRules(isFull.value, document, document.documentElement)
}

/** 退出登录（旧 logout：整页跳转，不走前端路由） */
function onLogout(): void {
  location.href = '/user/logout'
}

/**
 * 虚拟用户切换（旧 switchUser）。
 *
 * 既有语义（原样保留）：① 每次调用都 `me.cross.on` 注册一次，旧实现的 `off` 被注释掉
 * ⇒ 监听器会累积；② 弹层回调里提示用的是 `currentUser.value`（此时可能仍是旧值）。
 */
function onSwitchUser(): void {
  const me = getEovaMe()
  me.cross.on('eova-layer-ok_done_data', (data) => {
    currentUser.value = data as string
  })
  me.layer.open('超级用户切换', '/eova/admin/su', 1200, 0.9, () => {
    me.layer.msg('切换为:' + currentUser.value)
    init()
  })
}

/**
 * 虚拟用户还原（旧 resetUser）
 *
 * 既有语义：成功文案 `虚拟用户还原`；业务失败走 `me.layer.no`（**不是** `msg`）；
 * 异常文案为 `客户端请求异常: ` + `error.message`。
 */
async function onResetUser(): Promise<void> {
  const me = getEovaMe()
  try {
    const res = await axios.post('/eova/admin/reLogin')
    const ret = res.data
    if (ret.state === 'ok') {
      currentUser.value = undefined
      me.layer.msg('虚拟用户还原')
      init()
    } else {
      me.layer.no(ret.msg)
    }
  } catch (error) {
    me.layer.msg('客户端请求异常: ' + (error as Error).message)
  }
}

/** 修改密码（旧 updatePwd：宽 400 / 高 300，回调文案 `修改成功`） */
function onUpdatePwd(): void {
  const me = getEovaMe()
  me.layer.open('修改密码', '/user/password', 400, 300, () => {
    me.layer.msg('修改成功')
  })
}

/**
 * 系统消息（旧 openEovaMsg：宽 0.9 / 高 0.95）。
 *
 * ★ 回调文案是 `修改成功` —— 旧实现的复制粘贴缺陷（打开的是"系统消息"却提示"修改成功"），
 *   判据已钉住，**不得顺手改掉**。
 */
function onOpenEovaMsg(): void {
  const me = getEovaMe()
  me.layer.open('系统消息', '/app/eova_msg', 0.9, 0.95, () => {
    me.layer.msg('修改成功')
  })
}

onMounted(init)

defineExpose({
  cats,
  menus,
  tabMenus,
  msg,
  showMenu,
  isFull,
  currentUser,
  showDrop,
  layoutLeft,
  foldIcon,
  init,
  onMenuClick,
  onTabClick,
  onTabClose,
  onCloseAll,
  onFoldMenu,
  onRefresh,
  onToggleFullScreen,
  onLogout,
  onSwitchUser,
  onResetUser,
  onUpdatePwd,
  onOpenEovaMsg,
  onShowDrop,
  onHideUserMenu,
  iframeId,
  isUnder,
  titleOf
})
</script>
