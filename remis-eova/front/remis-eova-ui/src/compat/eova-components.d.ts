/**
 * EovaUI legacy 组件（`<ev-*>`）的全局类型声明
 *
 * 这 20 个组件由 legacy 制品 `eovaui.js` 在应用装配期注册（`app.use(EovaUI)`，
 * 见 `src/compat/legacy-runtime.ts`），组件 name 为 PascalCase，
 * 模板里用 kebab 名（`<ev-input>`）解析。
 *
 * ★ 为什么要有这个文件（第 106 轮修正了这里的说法）
 *   ⚠️ 先纠正一个**曾写错、且被实测证伪**的理由：初版说它"让 `vue-tsc` 认识 `<ev-*>`，
 *   而不是靠'未知标签不报错'蒙过去"。**实测：把 `EvIcon` 从声明里删掉，`vue-tsc` 照样通过**
 *   —— Vue 对未知标签不报错，所以**类型声明本身不构成任何闸门**。
 *   故本文件的定位是：**组件面的清单 + 由外部判据背书的契约**，靠
 *   `docs/.local/spikes/verify-legacy-components-declared.py` 强制"用到的必须声明"
 *   （正是该判据发现了 `<ev-icon>` 的漏声明）。
 *
 * ★ 清单的来源（第 106 轮订正的口径）
 *   初版按**具名导出**（`ce.EvInput=pa…`，20 个）抄写 —— 这是**错的**：
 *   全局注册的是 `Ar` 数组里的 **24** 个组件，具名导出只是其中一部分。
 *   实测 `eova/_view/**` 用到了 `<ev-icon>`（2 处），而它**不在**具名导出里 ⇒ 初版漏了它。
 *   **现口径：以"页面实际用到的标签 ∪ 制品注册面"为准**，用到的必须声明。
 *   （实测用到的标签共 13 种：`ev-input`/`ev-progress`/`ev-table`/`ev-popup`/`ev-select`/`ev-form`/
 *   `ev-tab-item`/`ev-tree`/`ev-icon`/`ev-find`/`ev-upload`/`ev-tab`/`ev-sortable`。）
 *
 * 注意：这是**运行时由 legacy 制品提供**的组件，本工程不得自己实现同名组件
 * （那会构成"按功能重新设计"，且与制品的 DOM 结构/样式脱钩）。
 */

/** legacy 组件共有的宽松 props 面（只有本工程实际用到的部分要求类型，其余放开） */
type EvAnyProps = Record<string, unknown>

declare module 'vue' {
  export interface GlobalComponents {
    /** 输入框（type/text/password/number/color/select/tree/texts/find/icon…） */
    EvInput: new () => { $props: EvAnyProps }
    /** 按钮 */
    EvButton: new () => { $props: EvAnyProps }
    /** 布尔（开关/勾选） */
    EvBool: new () => { $props: EvAnyProps }
    /** 复选框组 */
    EvCheck: new () => { $props: EvAnyProps }
    /** 弹窗（iframe/内容） */
    EvDialog: new () => { $props: EvAnyProps }
    /** 查找框（弹层选择） */
    EvFind: new () => { $props: EvAnyProps }
    /** 表单（元数据驱动） */
    EvForm: new () => { $props: EvAnyProps }
    /** 图标选择框（★ 第 106 轮补：在 `Ar` 注册面内、但**不在**具名导出里） */
    EvIcon: new () => { $props: EvAnyProps }
    /** 弹层容器 */
    EvPopup: new () => { $props: EvAnyProps }
    /** 进度条 */
    EvProgress: new () => { $props: EvAnyProps }
    /** 单选框组 */
    EvRadio: new () => { $props: EvAnyProps }
    /** 下拉框 */
    EvSelect: new () => { $props: EvAnyProps }
    /** 拖拽排序容器 */
    EvSortable: new () => { $props: EvAnyProps }
    /** 页签 */
    EvTab: new () => { $props: EvAnyProps }
    /** 页签项 */
    EvTabItem: new () => { $props: EvAnyProps }
    /** 表格 */
    EvTable: new () => { $props: EvAnyProps }
    /** 文本展示 */
    EvText: new () => { $props: EvAnyProps }
    /** 时间选择 */
    EvTime: new () => { $props: EvAnyProps }
    /** 时间段选择 */
    EvTimes: new () => { $props: EvAnyProps }
    /** 下拉树 */
    EvTree: new () => { $props: EvAnyProps }
    /** 上传（文件/图片） */
    EvUpload: new () => { $props: EvAnyProps }
  }
}

export {}
