/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.common.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;


/**
 * **登录口令摘要的对外契约（第 174 轮）** —— 用**真实 baseline 里的字面量**钉住。
 *
 * <p><b>为什么补这条（此前是一条完全没人看的契约）：</b>r150/r151 的全量扫描口径下，
 * 全仓**没有任何判据提到 `SM32`** —— 而 `SM32` 恰恰是"能不能登录"的那一步：
 * `UserHook`/`UserIntercept`/`UserController` 三处都按 {@code eova.pwd.encrypt}（默认
 * {@code "SM32"}）对用户输入取摘要，再与库里 `eova_user.login_pwd` 做**字符串相等**比较。
 * 也就是说：摘要算法、十六进制大小写、截断长度**任何一处漂移，登录全挂**，
 * 而阶段 1 的所有判据都不会红。
 *
 * <p><b>本判据的字面量来源（三重独立取证，R74 要求"断字面量"，这里连字面量本身也取证）：</b>
 * <ol>
 *   <li><b>真实 baseline 数据库</b>：`select login_pwd from eova_user where login_id='eova'`
 *       ⇒ `89BDF69372C2EF53EA409CDF020B5694`（MySQL 容器 13306 与 Kingbase `base.platform:54321`
 *       两库同值）；</li>
 *   <li><b>旧 demo 运行态</b>：旧栈实例正在 9090 上运行，用 `eova` / `000000` 实测
 *       `POST /user/doLogin` 返回 `{"state":"ok"}` ⇒ 反推出**明文就是 `000000`**
 *       （旧 demo 登录页脚本自带的默认口令，页面里写死 `login_pwd = '000000'`）；</li>
 *   <li><b>保留类第三方制品</b>：`cn.eova.tools.string.EncryptUtil`（eova-tools 1.1.5，
 *       旧栈同源制品）对本输入的输出与库值逐字相同。</li>
 * </ol>
 * ⇒ 于是"ported 的 `cn.eova.common.utils.EncryptUtil` 与旧栈同算法"这件事，
 * 可以用**库里的真值**来判，而不是"照着实现再抄一遍期望值"。
 *
 * <p><b>为什么还要跨制品比对（R37 纪律）：</b>`cn.eova.common.utils.EncryptUtil`（旧源码 port）
 * 与 `cn.eova.tools.string.EncryptUtil`（保留的第三方 jar）是**两个同名方法的实现**，
 * 二者都参与"口令摘要"。凡新旧两侧都存在的同名能力，都必须**固化并比对**，
 * 不得靠"应该一样"。
 *
 * <p><b>未覆盖（如实登记）</b>：`eova.pwd.encrypt` 配置为**非 SM32**（走 MD5 分支）时的端到端登录；
 * 以及 `UserController.doPassword()` 的库/表/列三配置键链路（需 HTTP 容器层）。
 */
class Sm32LoginDigestGoldenTest {

    /** ★ 真实 baseline 库值：`eova_user.login_pwd`（login_id='eova'），MySQL 与 Kingbase 两库同值 */
    private static final String BASELINE_PWD_HASH = "89BDF69372C2EF53EA409CDF020B5694";

    /** 与库值对应的明文（旧 demo 登录页脚本自带的默认口令；旧栈实测可登录） */
    private static final String BASELINE_PLAIN = "000000";

    @Test
    @DisplayName("★ 真值锚点：SM32('000000') 必须逐字等于真实 baseline 库里的 login_pwd")
    void sm32MatchesRealBaselineHash() {
        assertEquals(BASELINE_PWD_HASH, EncryptUtil.getSM32(BASELINE_PLAIN),
                "登录能否通过完全取决于这一步；与库值不一致 ⇒ 真实环境里登录必然失败");
    }

    @Test
    @DisplayName("★ 十六进制必须是大写且长度 32：库里是逐字字符串比较，大小写漂移即登录失败")
    void hexCaseAndLengthArePartOfTheContract() {
        String hash = EncryptUtil.getSM32(BASELINE_PLAIN);
        assertEquals(32, hash.length(), "SM32 固定 32 位（sha1 截断）");
        assertEquals(hash.toUpperCase(), hash, "★ 必须大写：库值与比较都是逐字字符串（大小写敏感）");
        assertEquals(BASELINE_PWD_HASH.toUpperCase(), BASELINE_PWD_HASH);
    }

    @Test
    @DisplayName("★ 内层 MD5 与整体 SM32 的字面量（取自独立制品，非照抄实现）")
    void innerAndOuterDigestsAreLiteralAnchored() {
        assertEquals("670B14728AD9902AECBA32E22FA4F6BD", EncryptUtil.getMd5(BASELINE_PLAIN));
        // 旧 `EncryptUtil` 源码注释里留下的自证值（与 eova-tools jar 输出一致）
        assertEquals("DE5CAC556F600BEC8E4425383CA7D8E8", EncryptUtil.getSM32("admin"));
        assertEquals("E879D5BD70F68C1777C7B257510261FF", EncryptUtil.getSM32("eova"));
    }

    @Test
    @DisplayName("★ 两个制品里的同名摘要实现必须逐输入一致（R37：同名能力须固化比对）")
    void portedDigestAgreesWithRetainedJar() {
        String[] inputs = {"", "000000", "admin", "eova", "A", "a", "1234567890",
                "eova1234567890123456789012345678901234567890", "中文口令", "pwd with space", "!@#$%^&*()"};
        for (String s : inputs) {
            assertEquals(cn.eova.tools.string.EncryptUtil.getSM32(s), EncryptUtil.getSM32(s),
                    "ported 与保留制品对同一输入的 SM32 必须一致：" + s);
            assertEquals(cn.eova.tools.string.EncryptUtil.getMd5(s), EncryptUtil.getMd5(s),
                    "MD5 层同样必须一致：" + s);
        }
    }

    @Test
    @DisplayName("★ SM32 必须不同于 MD5（否则 `eova.pwd.encrypt` 的两分支不可观测）")
    void sm32IsDistinguishableFromMd5() throws Exception {
        assertNotEquals(EncryptUtil.getMd5(BASELINE_PLAIN), EncryptUtil.getSM32(BASELINE_PLAIN),
                "两者相同 ⇒ SM32/MD5 两分支写反也看不出来");

        // ★ 用 JDK 自带 MessageDigest **独立**算出 sha1(md5hex)[:32] 再比对（R74：期望值不得
        //   由被测实现自己的方法组合出来 —— 否则改了定义两边一起变，断言恒真）
        String md5hex = jdkHex("MD5", BASELINE_PLAIN);
        String expected = jdkHex("SHA-1", md5hex).substring(0, 32);
        assertEquals(expected, EncryptUtil.getSM32(BASELINE_PLAIN),
                "SM32 的定义是 sha1(md5(pwd).大写hex)[:32]（此处用 JDK 独立复算）");
        assertEquals(BASELINE_PWD_HASH, expected, "独立复算结果本身也必须等于 baseline 库值");
    }

    /** 用 JDK MessageDigest 独立计算大写十六进制摘要（判据侧不许借被测实现的代码） */
    private static String jdkHex(String algorithm, String input) throws Exception {
        byte[] d = java.security.MessageDigest.getInstance(algorithm)
                .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString().toUpperCase();
    }
}
