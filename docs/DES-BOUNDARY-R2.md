# DES-BOUNDARY-R2 D 类等价替换边界

> 状态：Design-only
> 目的：为无法直接复制的 JFinal 胶水建立可审计的等价重写边界。

## 1. 适用对象

- `EovaConfig extends JFinalConfig`
- Render/文件下载/流式响应类
- JFinal Interceptor 链
- Plugin、ClassLoader 和启动生命周期胶水

## 2. 每个 D 类必须先填写

1. 旧类完整路径、revision 和生命周期入口。
2. 公开方法、调用方、执行顺序和短路条件。
3. 配置键、默认值、环境变量和副作用。
4. HTTP 状态、Content-Type、文件名、编码和异常映射。
5. 可直接复制的逻辑、必须重写的底座部分、明确不迁移的能力。
6. 等价证明：单测、API golden、启动/关闭日志或运行态检查。

## 3. 禁止事项

D 类不得混入普通 A/B/C 单元；不得以“顺便整理”为理由改变默认配置、拦截器顺序、权限短路、响应 envelope 或插件加载顺序；无法证明等价时保持 Idea/Blocked，创建后续设计任务。

## 4. 放行条件

只有边界说明、依赖清单、适配实现和验证证据齐全，Orchestrator 才能把 D 类任务置为 Ready。`compile-stub`、启动成功或单个单测通过均不足以放行。

