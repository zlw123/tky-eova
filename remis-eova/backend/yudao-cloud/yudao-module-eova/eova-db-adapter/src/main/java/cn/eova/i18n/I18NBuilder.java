/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.i18n;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import cn.eova.tools.x;
import cn.eova.common.utils.util.RegexUtil;
import cn.eova.db.EovaModel;
import cn.eova.db.EovaRecord;

/**
 * <p>ported from: cn.eova.i18n.I18NBuilder
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>i18nMap 为空时 models/records/model/record 直接返回</li>
 *   <li>model()/record() 仅在译文非空时才 set（不覆盖为空）</li>
 *   <li>I18N 覆写了 get：无词条或译文为空时返回【键本身】而非 null（与 Map 约定不同），故此处 s.isEmpty() 不会 NPE —— 不得按 Map 惯例改成返回 null</li>
 *   <li>get(txt) 对空串返回空串而非原文 —— 由 x.isEmpty 的分支顺序决定</li>
 * </ol>
 */
/**
 * 国际化构建器
 *
 * @author Jieven
 *
 */

public class I18NBuilder {

    private static HashMap<String, I18N> i18nMap = new HashMap<>();
    private static ThreadLocal<String> local = new ThreadLocal<>();

    public static void init(List<EovaRecord> list) {
        for (EovaRecord e : list) {
            String code = e.getStr("code");
            I18N i18n = i18nMap.get(code);
            if (i18n == null) {
                i18n = new I18N();
                i18nMap.put(code, i18n);
            }
            i18n.put(e.getStr("txt"), e.getStr("val"));
        }
    }

    public static String getLocal() {
        return local.get();
    }

    public static void setLocal(String code) {
        local.set(code);
    }

    public static String get(String txt) {
        if (x.isEmpty(txt)) {
            return "";
        }
        if (i18nMap == null) {
            return txt;
        }
        I18N i18n = i18nMap.get(local.get());
        if (i18n == null) {
            return txt;
        }
        String s = i18n.get(txt);
        if (s.isEmpty()) {
            return txt;
        }
        return s;
    }

    public static void models(List<? extends EovaModel> ms, String... fileds) {
        if (i18nMap.isEmpty())
            return;
        for (EovaModel m : ms) {
            model(m, fileds);
        }
    }

    public static void model(EovaModel m, String... fileds) {
        if (i18nMap.isEmpty() || m == null)
            return;
        for (String filed : fileds) {
            String s = get(m.getStr(filed));
            if (!x.isEmpty(s)) {
                m.set(filed, s);
            }
        }
    }

    public static void records(List<EovaRecord> rs, String... fileds) {
        if (i18nMap.isEmpty())
            return;
        for (EovaRecord m : rs) {
            record(m, fileds);
        }
    }

    public static void record(EovaRecord e, String... fileds) {
        if (i18nMap.isEmpty() || e == null)
            return;
        for (String filed : fileds) {
            String s = get(e.getStr(filed));
            if (!x.isEmpty(s)) {
                e.set(filed, s);
            }
        }
    }

    /**
     * 文案混杂，提取中文词分别翻译
     * @param str
     */
    public static String blend(String str) {
        if (x.isEmpty(str))
            return "";
        HashSet<String> cns = RegexUtil.getChinese(str);
        for (String cn : cns) {
            String s = get(cn);
            str = str.replaceAll(cn, s);
        }
        return str;
    }

}