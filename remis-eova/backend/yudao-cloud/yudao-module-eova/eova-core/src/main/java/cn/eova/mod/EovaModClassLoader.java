/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.mod;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import cn.eova.model.Mod;

/**
 * <p>ported from: cn.eova.mod.EovaModClassLoader
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>Mod 包类加载器（52 行）：扫描 jar 内的类名</li>
 *   <li>宿主依赖仅 JDK（URLClassLoader/JarFile）—— 【无需任何替换】，天然逐字节 port</li>
 * </ol>
 */
public class EovaModClassLoader extends URLClassLoader {

    private List<String> classNameList;

    public EovaModClassLoader(Mod mod) throws IOException {
        super(new URL[]{}, Thread.currentThread().getContextClassLoader());

        String path = String.format("%s%s-%s.jar", EovaModConst.DIR_MOD, mod.getGroup(), mod.getCode());
        File jar = new File(path);

        this.addURL(jar.toURI().toURL());
        this.classNameList = new ArrayList<>();
        // this.initClassNameList(jar);
    }

    public List<String> getClassNameList() {
        return classNameList;
    }

    @SuppressWarnings({"resource", "unused"})
    private void initClassNameList(File jar) throws IOException {
        Enumeration<JarEntry> entries = new JarFile(jar).entries();
        while (entries.hasMoreElements()) {
            JarEntry jarEntry = entries.nextElement();
            String entryName = jarEntry.getName();
            if (!jarEntry.isDirectory() && entryName.endsWith(".class")) {
                String className = entryName.replace("/", ".").substring(0, entryName.length() - 6);
                classNameList.add(className);
                System.out.println(className);
            }
        }
    }
}