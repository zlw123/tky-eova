/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cn.eova.compat.jfinal.kit.LegacyProp;

/**
 * **配置档位选择器判据（r326 · DES-010）**。
 *
 * <p><b>钉的是什么</b>：旧实现那一行 {@code PropKit.useFirstFound(五档)} 在新栈里会退化成
 * "`dev.txt` 永远胜"（多档同 classpath），新增的 prd / 金仓档**永远选不上**；而宿主又没有任何
 * 通道能指定档位。本判据把选择器的三条语义钉死：</p>
 *
 * <ol>
 *   <li><b>未指定</b> ⇒ 五档原序"首存在者胜"（与旧实现逐字等价；夹具故意只放 `pre.txt`+`prd.txt`，
 *       以区分"按优先级"与"按字典序"）；</li>
 *   <li><b>已指定</b> ⇒ **只用该档**（哪怕优先级更低的档也在 classpath 里）；</li>
 *   <li><b>非法/不存在</b> ⇒ **抛**，绝不回落（r323 的教训：静默兜底会把缺陷藏起来）。</li>
 * </ol>
 *
 * <p>夹具：`eova-compat/src/test/resources/eova/{pre,prd}.txt`（**不含** `dev.txt`，
 * 否则"首存在者"恒等于 dev，判据就退化成永真）。</p>
 */
class LegacyConfigProfileTest {

    @AfterEach
    void clearSpecified() {
        System.clearProperty(LegacyConfigProfile.PROP_KEY);
        System.clearProperty("eova-junk");
    }

    @Test
    @DisplayName("★ 五档常量：与旧实现 EovaConfig:202 的 useFirstFound 调用**逐字同序**（可变异）")
    void legacyProfilesAreVerbatimAndOrdered() {
        // 这条防的是"顺手把 prd 提到前面/改成按字典序" —— 那会静默改变**既有部署**的生效档。
        assertEquals(
                List.of("eova/dev.txt", "eova/test.txt", "eova/pre.txt", "eova/pro.txt", "eova/prd.txt"),
                LegacyConfigProfile.LEGACY_PROFILES,
                "★ 五档名称与顺序必须与 EovaConfig:202（旧实现）逐字一致");
    }

    @Test
    @DisplayName("★ 未指定档：首存在者胜（pre 优先于 prd —— 不是字典序）")
    void unspecifiedFallsBackToFirstExistingLegacyProfile() {
        System.clearProperty(LegacyConfigProfile.PROP_KEY);
        LegacyProp prop = LegacyConfigProfile.load();
        assertNotNull(prop.getFileName(), "必须能报出真实装载的档名（出处不能靠推断）");
        assertEquals("eova/pre.txt", prop.getFileName(),
                "★ 未指定 ⇒ 五档原序的第一个存在者（夹具里 pre 在 prd 之前，字典序则相反）");
        assertEquals("PRE", prop.get("env"));
    }

    @Test
    @DisplayName("★ 已指定档：**只用该档**（优先级更低的 prd 也能被选中）")
    void specifiedProfileIsUsedExclusively() {
        System.setProperty(LegacyConfigProfile.PROP_KEY, "eova/prd.txt");
        LegacyProp prop = LegacyConfigProfile.load();
        assertEquals("eova/prd.txt", prop.getFileName(),
                "★ 宿主指定 ⇒ 必须用该档，而不是被「首存在者」（pre.txt）抢先");
        assertEquals("PRD", prop.get("env"));
        assertEquals("eova/prd.txt", LegacyConfigProfile.active(),
                "真实装载的档名必须可追溯（宿主日志的出处用它，而不是靠推断）");
    }

    @Test
    @DisplayName("★ 非法档名：拒绝（防路径穿越/绝对路径/其它格式）")
    void illegalProfileNameRejected() {
        for (String bad : List.of(
                "/etc/passwd.txt", "eova/../application.yml", "eova/sub/x.txt",
                "eova/x.yaml", "application.yml", "eova/", "eova/x.txt.bak")) {
            System.setProperty(LegacyConfigProfile.PROP_KEY, bad);
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    LegacyConfigProfile::load, "★ 非法档名必须拒绝：" + bad);
            assertTrue(e.getMessage().contains(bad), "报错必须带上被拒的原值：" + e.getMessage());
        }
    }

    @Test
    @DisplayName("★ 指定档不存在：抛（**不回落**到任何五档）")
    void missingSpecifiedProfileFailsFast() {
        System.setProperty(LegacyConfigProfile.PROP_KEY, "eova/nope-not-exists.txt");
        IllegalStateException e = assertThrows(IllegalStateException.class, LegacyConfigProfile::load,
                "★ 指定了档却不存在时必须响亮失败 —— 静默回落 = 跑在没人知道的档上");
        assertTrue(e.getMessage().contains("eova/nope-not-exists.txt"), "报错必须含档名：" + e.getMessage());
        assertTrue(e.getMessage().contains("eova/pre.txt"), "报错必须列出可用候选：" + e.getMessage());
    }

    @Test
    @DisplayName("环境变量 EOVA_PROP 也认（系统属性优先）")
    void specifiedReadsPropertyThenEnv() {
        System.setProperty(LegacyConfigProfile.PROP_KEY, "eova/prd.txt");
        assertEquals("eova/prd.txt", LegacyConfigProfile.specified());
        System.clearProperty(LegacyConfigProfile.PROP_KEY);
        // 环境变量在测试进程里不便注入 ⇒ 只钉"未设置时返回 null"（不臆造 EOVA_PROP 的值）
        if (System.getenv(LegacyConfigProfile.ENV_KEY) == null) {
            assertNull(LegacyConfigProfile.specified(), "两侧都没有 ⇒ 未指定");
        }
    }
}
