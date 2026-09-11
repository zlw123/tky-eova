/**
 * EovaUI legacy 组件（`<ev-*>`）的全局类型声明
 *
 * 这 20 个组件由 legacy 制品 `eovaui.js` 在应用装配期注册（`app.use(EovaUI)`，
 * 见 `src/compat/legacy-runtime.ts`），组件 name 为 PascalCase，
 * 模板里用 kebab 名（`<ev-input>`）解析。
 *
 * ★ 为什么要有这个文件
 *   ① 让 `vue-tsc` 认识模板里的 `<ev-*>`，而不是靠"未知标签不报错"蒙过去；
 *   ② 它是**组件面的清单**：多写一个不存在的组件等于宣称一个不存在的契约。
 *      清单取自制品导出（第 102 轮取证，`legacy-runtime-contract.json` 的 `eovaUIExportSurface`）。
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
