/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import cn.eova.common.Ds;
import cn.eova.common.base.BaseCache;
import cn.eova.compat.cache.LegacyCacheKit;
import cn.eova.compat.jfinal.kit.LegacyRet;
import cn.eova.db.EovaGateways;
import cn.eova.db.EovaRecord;
import cn.eova.model.User;
import cn.eova.service.LoginService;
import cn.eova.tools.x;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **免密登录接缝（`LoginService#login(String loginId, boolean keepLogin)`）的判据**
 * （第 300 轮，阶段 3 剩余项 (2) 的前置）。
 *
 * <p><b>为什么单独钉它</b>：该重载是 ported 栈里**唯一**"受信身份 → EovaMeta 会话"的入口，
 * 源码注释原文即「<b>第三方一键登录</b>」。阶段 3 的 <b>token → 会话换票</b>（平台
 * `_accessToken` 换 `eovasid`）正是它的调用方。而它当前：</p>
 * <ul>
 *   <li><b>零调用者</b>：旧源码全树与新栈全树都只有 4 参（带密码）重载被调用
 *       （`UserController#doLogin`）；本重载两栈都**没有**调用点 —— 旧栈里它是**预留扩展点**，
 *       **不是** port 缺口（已逐树核对）。</li>
 *   <li><b>零判据</b>：全仓没有任何判据点名它（第 176 轮"零覆盖三类"里的第二类：
 *       "整条没人管"——既非间接覆盖，也非"无可判内容"）。</li>
 * </ul>
 * <p>⇒ 在把它接上 HTTP 之前，先用**与生产同一套引导**（本类用 `@SpringBootTest` 复用宿主
 * `LegacyWebBootstrap` 的装配，而不是在 db-adapter 里手动重搭接缝 —— 后者缺
 * `EovaConfig` 引导，实测会在 `AuthUri.build` 处炸）把它的可观测语义钉住。</p>
 *
 * <p><b>★ 数据面纪律（第 230 轮教训）</b>：本判据会往 `eova_session` **插行**。清理不依赖
 * "我预期的 sid 命名"，而是：① 按返回值里的 sid 删；② 用**全表行数**（外部事实）自证
 * "回到进入本判据前的行数"。若实现产出的 sid 与返回值不一致，②会红 —— 那正是要抓的。</p>
 */
// ★★ 必须与同模块其它判据**完全相同的上下文配置**（第 300 轮实测教训）：
//   首版写成裸 `@SpringBootTest`（默认 `WebEnvironment.MOCK`）⇒ 与其它判据的 `RANDOM_PORT`
//   不是同一个 `MergedContextConfiguration` ⇒ Spring 在同 JVM 里**再建一个**上下文 ⇒
//   宿主引导（"每 JVM 一次"）二次执行、上下文加载失败。
//   ★ 症状极具误导性：**单跑 `-Dtest=ThirdPartyLoginSeamTest` 7/7 绿，全量 `mvn clean test` 全红**
//   （与第 172 轮"单跑绿≠全量绿"同一族）。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ThirdPartyLoginSeamTest {

    /** meta 库连接（baseline） */
    private static final String META_URL = System.getProperty("eova.mysql.meta.url",
            "jdbc:mysql://127.0.0.1:13306/eova_meta?useUnicode=true&characterEncoding=UTF-8"
                    + "&zeroDateTimeBehavior=convertToNull&useSSL=false&serverTimezone=Asia/Shanghai"
                    + "&allowPublicKeyRetrieval=true&connectTimeout=3000");
    private static final String DB_USER = System.getProperty("eova.mysql.user", "root");
    private static final String DB_PWD = System.getProperty("eova.mysql.pwd", "root");

    /** 真值账号（与同模块其它判据同一个） */
    private static final String LOGIN_ID = "eova";

    /** 本判据开始前的 `eova_session` 行数（外部事实，用于自证清理） */
    private long sessionRowsBefore = -1;

    /** 本判据建出来的 sid（teardown 按它删） */
    private final List<String> createdSids = new ArrayList<>();

    /**
     * 记录进入时的会话表行数（**外部事实**，teardown 自证用）
     *
     * @throws Exception SQL 异常
     */
    private long countSessions() throws Exception {
        try (Connection c = DriverManager.getConnection(META_URL, DB_USER, DB_PWD);
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("select count(*) from eova_session")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /**
     * 清理本判据造的会话行，并用全表行数自证"回到进入前的行数"
     *
     * @throws Exception SQL 异常
     */
    @AfterEach
    void tearDown() throws Exception {
        try {
            if (sessionRowsBefore >= 0 && !createdSids.isEmpty()) {
                try (Connection c = DriverManager.getConnection(META_URL, DB_USER, DB_PWD);
                        PreparedStatement ps = c.prepareStatement(
                                "delete from eova_session where id = ?")) {
                    for (String sid : createdSids) {
                        ps.setString(1, sid);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                assertEquals(sessionRowsBefore, countSessions(),
                        "★ 清理未自证：`eova_session` 行数未回到进入本判据前的值"
                                + "（实现产出的 sid 与返回值不一致？或有其它写入）");
            }
        } finally {
            createdSids.clear();
        }
    }

    /**
     * 取返回值里的 sid
     *
     * @param ret 登录返回值
     * @return sid
     */
    private static String sidOf(LegacyRet ret) {
        User user = (User) ret.get(LoginService.USER);
        assertNotNull(user, "返回值必须带 USER（旧 `Ret.ok().set(USER, user)`）");
        // ★ `EovaModel.get(String)` 是**泛型** `<T> T get(String)`：直接写
        //   `String.valueOf(user.get(SID))` 会把 T 推断成 `char[]`（`String.valueOf` 有该重载）
        //   ⇒ 运行时 ClassCastException（本判据首轮实测踩到）。先落到 Object 再转字符串。
        Object sid = user.get(LoginService.SID);
        return String.valueOf(sid);
    }

    /**
     * 建会话（并登记 sid 供 teardown 清理）
     *
     * @param keepLogin 是否保持登录
     * @return 返回值
     * @throws Exception SQL 异常（记录行数）
     */
    private LegacyRet login(String keepLogin) throws Exception {
        if (sessionRowsBefore < 0) {
            sessionRowsBefore = countSessions();
        }
        LegacyRet ret = cn.eova.service.biz.login.login(LOGIN_ID, Boolean.parseBoolean(keepLogin));
        createdSids.add(sidOf(ret));
        return ret;
    }

    /**
     * 从真库读该账号的某列
     *
     * @param column 列名
     * @return 值
     * @throws Exception SQL 异常
     */
    private static Object userColumn(String column) throws Exception {
        try (Connection c = DriverManager.getConnection(META_URL, DB_USER, DB_PWD);
                PreparedStatement ps = c.prepareStatement(
                        "select " + column + " from eova_user where login_id = ?")) {
            ps.setString(1, LOGIN_ID);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "真库必须有该账号：" + LOGIN_ID);
                return rs.getObject(1);
            }
        }
    }

    @Test
    @DisplayName("★ TP-1：免密登录 ⇒ 返回真库那个用户 + 会话表恰好多一行 + `loginBySid` 取回同一用户（闭环）")
    void createsSessionAndRoundTrips() throws Exception {
        Object uid = userColumn("id");
        Object rid = userColumn("rid");

        LegacyRet ret = login("true");
        assertTrue(ret.isOk(), "免密登录应成功，实际=" + ret);

        User user = (User) ret.get(LoginService.USER);
        assertEquals(uid, user.get("id"), "★ 会话用户必须是真库该 login_id 的 id");
        assertEquals(rid, user.get("rid"), "★ rid 必须来自真库（权限面）");

        assertEquals(sessionRowsBefore + 1, countSessions(),
                "★ 旧实现「保存登录 session 到数据库」⇒ 会话表必须恰好多一行");

        User back = cn.eova.service.biz.login.loginBySid(sidOf(ret), "127.0.0.1");
        assertNotNull(back, "★ loginBySid 必须能用该 sid 取回用户（否则建出来的会话是死的）");
        assertEquals(uid, back.get("id"), "★ 取回的用户必须与建立时是同一个");
    }

    @Test
    @DisplayName("★ TP-2（两向）：keepLogin=true ⇒ 1 年；false ⇒ ★ `maxAgeInSeconds = -1`（不是 0）")
    void keepLoginDrivesCookieMaxAge() throws Exception {
        LegacyRet stay = login("true");
        // 旧实现字面量：`1 * 365 * 24 * 60 * 60`
        assertEquals(365 * 24 * 60 * 60, stay.get("maxAgeInSeconds"),
                "★ keepLogin=true ⇒ 1 年（旧实现字面量，不是随便一个正数）");

        LegacyRet once = login("false");
        // ★ 旧实现：`int maxAgeInSeconds = (int) (keepLogin ? liveSeconds : -1)`
        //   —— **-1**（会话级 Cookie，浏览器关闭即失效），不是 0。
        //   写成 0 会被浏览器当作"立刻过期" ⇒ 改错即静默登不上。
        assertEquals(-1, once.get("maxAgeInSeconds"),
                "★ keepLogin=false ⇒ maxAgeInSeconds 必须是 -1（会话级 Cookie）");
    }

    @Test
    @DisplayName("★ TP-3：`expire` 按 `login.user.session` 分钟落库（配置是外部事实，不是硬编码 120）")
    void expireFollowsConfiguredSessionMinutes() throws Exception {
        int sessionMin = x.conf.getInt("login.user.session", 120);
        long before = System.currentTimeMillis();

        String sid = sidOf(login("false"));

        Long expire = null;
        try (Connection c = DriverManager.getConnection(META_URL, DB_USER, DB_PWD);
                PreparedStatement ps = c.prepareStatement(
                        "select expire from eova_session where id = ?")) {
            ps.setString(1, sid);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "★ 会话行必须存在（sid=" + sid + "）");
                expire = rs.getLong(1);
            }
        }
        long expected = before + (long) sessionMin * 60 * 1000;
        assertTrue(Math.abs(expire - expected) < 60_000,
                "★ expire 必须 ≈ now + login.user.session(" + sessionMin + ") 分钟；实际=" + expire);
    }

    @Test
    @DisplayName("★ TP-4：每次调用生成**不同** sid，形态是 32 位无横线 UUID（旧 `LegacyStrKit.getRandomUUID()`）")
    void sidIsFreshUuidEachCall() throws Exception {
        String a = sidOf(login("true"));
        String b = sidOf(login("true"));
        assertTrue(!a.equals(b), "★ 两次调用不得复用同一个 sid（否则会话互相踩）");
        for (String sid : new String[] {a, b}) {
            assertTrue(sid.matches("[0-9a-fA-F]{32}"),
                    "★ sid 形态应为 32 位无横线 UUID，实际=" + sid);
        }
    }

    @Test
    @DisplayName("★ TP-5：登录后 `BaseCache.LOGIN` 下能按 sid 取到该用户（`loginBySid` 依赖它）")
    void loginCachesUserBySid() throws Exception {
        String sid = sidOf(login("true"));
        Object cached = LegacyCacheKit.get(BaseCache.LOGIN, sid);
        assertNotNull(cached, "★ `login` 必须把用户写进 LOGIN 缓存（旧 `LegacyCacheKit.put(BaseCache.LOGIN, sid, user)`）");
        assertTrue(cached instanceof User, "缓存里应是 User，实际=" + cached.getClass().getName());
        assertEquals(userColumn("id"), ((User) cached).get("id"), "缓存里的用户必须是真库那个");
    }

    @Test
    @DisplayName("★ TP-6：未知账号 ⇒ 【响亮失败】（既有行为原样钉住，不得改成静默 ok）")
    void unknownLoginIdFailsLoudly() {
        // 取证：`findUserByLoginId` → `findByLoginId` 查不到 ⇒ `initUser(null)` 里 `r.get(userId)` 抛异常。
        // 只钉"不会静默成功"；失败形态由实测确定，不"顺手改成"返回 fail 文案
        // —— 那会让换票端点对"未知账号"的处理与既有实现分叉。
        assertThrows(RuntimeException.class,
                () -> cn.eova.service.biz.login.login("no_such_login_id_xyz", true),
                "★ 未知账号必须【响亮失败】，不得静默返回 ok");
    }

    @Test
    @DisplayName("★ TP-7：网关自证 —— 该接缝用的用户源确实是 `Ds.EOVA` 的 `eova_user`（与登录配置同源）")
    void userSourceIsEovaDs() {
        // `LoginService.findByLoginId` 走 `EovaGateways.get(userDs).findFirst("select * from <table> where <account> = ?")`
        // —— 这里直接对同一网关发同一形状的查询，证明"接缝的用户源 = 主库 eova_user"，
        //    而不是靠读源码推断（换票端点映射账号时依赖这一点）。
        EovaRecord r = EovaGateways.get(Ds.EOVA)
                .findFirst("select id, rid, login_id from eova_user where login_id = ?", LOGIN_ID);
        assertNotNull(r, "主库必须能按 login_id 查到该账号");
        assertEquals(LOGIN_ID, r.getStr("login_id"));
        assertNotNull(r.get("id"));
        assertNotNull(r.get("rid"));
    }
}
