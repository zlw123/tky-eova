# DES-ADAPTER-R2 非数据库旧底座适配设计

> 状态：Design-only
> 目的：冻结 B 类单元中 `Kv`、模板引擎和日志等旧底座的最小等价替换，避免 Worker 在每个文件里各自发明实现。

## 1. 适用范围

当前已发现的典型依赖包括 `com.jfinal.kit.Kv`、`com.jfinal.template.Engine`、`com.jfinal.kit.JsonKit`、`com.jfinal.plugin.activerecord.Record` 和 `cn.eova.tools.x`。其中 `Record`/`Db` 属于数据库适配，由 `DES-DB-ADAPTER` 管理；本设计只覆盖非数据库部分。

## 2. 最小映射

| 旧能力 | 迁移期实现 | 必须保持 |
|---|---|---|
| `Kv` | 有序键值对象或项目统一 Map 值对象 | `get/set/of`、缺失键、null、嵌套值和 JSON 语义 |
| `Engine` 模板渲染 | 独立模板适配接口 | 模板语法、转义、缺失变量和异常行为 |
| `JsonKit` | 项目统一 JSON 序列化接口 | null、数字、日期、字段名和异常 |
| `x.isEmpty` | 项目统一空值工具 | null、空字符串、空集合和空白字符串边界 |
| `x.log`/`LogKit` | slf4j 适配 | 日志级别、关键文本和异常栈 |

本轮 LC-011 试点将上述能力拆成三个可独立验证的 support 单元，避免 `ExpUtil` 或 `EovaExpBuilder` 在未冻结底座时被提前派发：

| support 单元 | 目标类 | 覆盖旧调用 |
|---|---|---|
| `EovaKvAdapter` | `cn.eova.compat.EovaKv` | `Kv.of/get/set`、缺失键、null、嵌套值 |
| `EovaTemplateAdapter` | `cn.eova.compat.EovaTemplate` | `Engine.use().getTemplateByString/getTemplate().renderToString` |
| `EovaLegacyUtilityAdapter` | `cn.eova.compat.EovaLegacySupport` | `x.isEmpty`、`x.str.delStart`、`x.conf.getBool`、`x.log.error`、`EovaTool.toArray`、`JsonKit.toJson`、`xx.splitBlank` |

三个类名是迁移期适配契约，不代表旧 FQCN；业务单元只能依赖这些适配类，不能重新引入 JFinal。

## 3. 适配规则

1. 业务方法体保持源文件结构，不得在每个 Worker 单元中重复实现一套 `Kv`/模板/JSON 工具。
2. 适配层必须放在明确的 `eova-core` 基础包中，并提供单测。
3. 旧 API 尚未被当前单元使用的能力不提前扩展。
4. 无法证明模板渲染或 JSON 行为等价时，单元标记 blocked，先补适配设计和 golden，不得降级为空实现。
5. 适配层公开方法签名必须加简短中文注释。
6. `EovaLegacySupport` 的配置读取、日志文本和异常行为必须分别有测试；不能用恒定返回值或吞异常的 stub。

## 4. 放行测试

至少覆盖：缺失键、null、嵌套值、中文和特殊字符、模板变量缺失、HTML/SQL 特殊字符、日期/数字序列化、空字符串/空集合、`getBool` 默认值、`splitBlank` 边界及异常日志。通过后才允许 `java-core-adapter` 单元进入 Ready。
