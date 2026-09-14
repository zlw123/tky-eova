/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * **配置事实源优先级判据（r323 · 金仓收口）**：EOVA 的配置事实源是 `eova/dev.txt`，
 * 宿主（Spring）属性只能作**兜底**。
 *
 * <p><b>为什么必须有这条判据</b>：r322 的金仓端到端验收挖出一个部署级缺陷 ——
 * `LegacyWebBootstrap` 原先**无条件** {@code x.conf.addConfig("eova.url", dbUrl)}，
 * 把 `dev.txt` 里的数据源坐标**静默覆盖成 Spring 默认值**（= 本机 MySQL baseline）。
 * 后果：把 `dev.txt` 切到金仓后，**元数据库仍连 MySQL**（启动日志实测：
 * {@code 自建 DriverManager DataSource → jdbc:mysql://127.0.0.1:13306/eova_meta}），
 * 而 `main` 库走 dev.txt 是金仓 ⇒ **同一进程连两个库**，金仓下 4 个列表页数据面对不上。
 * 这种"配置被另一处静默覆盖"的缺陷，**编译绿、既有判据全绿**，只有真库切换才暴露
 * ⇒ 必须有一条"故意给宿主属性塞假值、断言配置仍然获胜"的判据把它钉住。</p>
 *
 * <p>★ 判据形态经过一次修正：首版用 {@code @SpringBootTest(properties=…)} 把宿主属性指向另一个库，
 * 单跑通过、**整套跑却起不来**（把元数据库指到 `demo` 会在启动期做表自省时失败；
 * 且同 JVM 内多套 Spring 上下文互相影响）⇒ 判据太脆。
 * 现在只钉**真正出错的那一行**：取值优先级函数 {@code LegacyWebBootstrap#pick}（纯函数、无上下文、
 * 可变异）。实测：把参数对调（宿主优先）⇒ 判据立即红。</p>
 */
class LegacyConfigSourcePrecedenceTest {

    @Test
    @DisplayName("★ r323b：元数据 DS 的**取值规则**（`pick`）—— 配置优先、宿主兜底（可变异）")
    void pickPrefersConfigOverHost() {
        // 这条钉的是**真正出错的那一行**：`new DriverManagerDataSource(dbUrl, …)` 曾直接用宿主字段
        // ⇒ dev.txt 切金仓后元数据库仍连 MySQL（r322）。判据必须能区分两种优先级：
        assertEquals("jdbc:kingbase8://kb/eova_meta", LegacyWebBootstrap.pick("jdbc:kingbase8://kb/eova_meta", "jdbc:mysql://127.0.0.1:13306/eova_meta"),
                "★ 配置里有值时必须以配置为准（宿主值不得获胜）");
        assertEquals("jdbc:mysql://127.0.0.1:13306/eova_meta", LegacyWebBootstrap.pick(null, "jdbc:mysql://127.0.0.1:13306/eova_meta"),
                "★ 配置缺省时宿主值兜底");
        // ★ 实测口径（本判据首版写错过）：`x.isEmpty` **不清洗空白** ⇒ 全空白串不算空、
        //   配置里的 `"   "` 会照原样获胜。这里按**项目既有口径**断言（而不是我希望的语义），
        //   否则判据会把"与 `x.isEmpty` 一致"误判成缺陷。
        assertEquals("   ", LegacyWebBootstrap.pick("   ", "host"), "★ x.isEmpty 不清洗空白 ⇒ 空白串照原样生效");
        assertEquals("host", LegacyWebBootstrap.pick("", "host"), "★ 空串按缺省处理");
    }
}
