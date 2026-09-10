/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.ext.jfinal;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import cn.eova.compat.jfinal.activerecord.IContainerFactory;

/**
 * <p>ported from: cn.eova.ext.jfinal.LinkedCaseInsensitiveContainerFactory
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>R4 核心：保序 + 大小写不敏感容器（LinkedHashMap/LinkedHashSet 基底）</li>
 *   <li>convertCase 的 toLowerCase 开关决定键是否归一化；默认（无参构造）为 null —— 原样保留</li>
 *   <li>内部类 LinkedCaseInsensitiveMap/Set 覆写了 put/get/containsKey 等 —— 属对外行为</li>
 * </ol>
 */
/**
 * 字段有序, 忽略大小写敏感
 * @author Jieven
 *
 */
public class LinkedCaseInsensitiveContainerFactory implements IContainerFactory {
    private static Boolean toLowerCase = null;

    public LinkedCaseInsensitiveContainerFactory(boolean toLowerCase) {
        LinkedCaseInsensitiveContainerFactory.toLowerCase = toLowerCase;
    }

    public Map<String, Object> getAttrsMap() {
        return new LinkedCaseInsensitiveMap<Object>();
    }

    public Map<String, Object> getColumnsMap() {
        return new LinkedCaseInsensitiveMap<Object>();
    }

    public Set<String> getModifyFlagSet() {
        return new LinkedCaseInsensitiveSet();
    }

    private static String convertCase(String key) {
        if (toLowerCase != null) {
            return toLowerCase ? key.toLowerCase() : key.toUpperCase();
        } else {
            return key;
        }
    }

    /*
     * 1：非静态内部类拥有对外部类的所有成员的完全访问权限，包括实例字段和方法，
     *    为实现这一行为，非静态内部类存储着对外部类的实例的一个隐式引用
     * 2：序列化时要求所有的成员变量是Serializable 包括上面谈到的引式引用
     * 3：外部类CaseInsensitiveContainerFactory 需要 implements Serializable 才能被序列化
     * 4：可以使用静态内部类来实现内部类的序列化，而非让外部类实现 implements Serializable
     */
    public static class LinkedCaseInsensitiveSet extends LinkedHashSet<String> {

        private static final long serialVersionUID = 6236541338642353211L;

        public boolean add(String e) {
            return super.add(convertCase(e));
        }

        public boolean addAll(Collection<? extends String> c) {
            boolean modified = false;
            for (String o : c) {
                if (super.add(convertCase(o))) {
                    modified = true;
                }
            }
            return modified;
        }

    }

    public static class LinkedCaseInsensitiveMap<V> extends LinkedHashMap<String, V> {

        private static final long serialVersionUID = 7482853823611007217L;

        public V put(String key, V value) {
            return super.put(convertCase(key), value);
        }

        public void putAll(Map<? extends String, ? extends V> map) {
            for (Map.Entry<? extends String, ? extends V> e : map.entrySet()) {
                super.put(convertCase(e.getKey()), e.getValue());
            }
        }

        public V get(Object key) {
            return super.get(convertCase(key.toString()));
        }

        public V getOrDefault(Object key, V defaultValue) {
            return get(key) == null ? get(key) : defaultValue;
        }

        public V remove(Object key) {
            return super.remove(convertCase(key.toString()));
        }
    }
}

