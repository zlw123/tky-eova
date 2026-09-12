/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Eova 自有 Web 层入口（第 245 轮起，形态 A：引入 Web 层）。
 *
 * <p><b>本类是什么：</b>阶段 2 的后端容器入口。设计见
 * {@code docs/DES-005-R1-eova-web-layer-design.md}。
 *
 * <p><b>本类【不是】什么（避免误读为已完成）：</b>此刻它只是**可构建的骨架** —— 尚未接线
 * LegacyJFinalBoot / 路由表 / 分发器 / 静态资源，也未做任何"HTTP 容器层"验收。
 * 逐切片落地：S1 骨架与启动装配 · S2 分发器（登录 + 菜单）· S3 静态资源 · S4 引导端点 ·
 * S5 真浏览器与视觉对照。**在 S5 通过前，阶段 1 的「HTTP 容器层」仍记 not executed。**
 */
@SpringBootApplication
public class EovaWebApplication {

    /** 阶段 2 自有后端入口 */
    public static void main(String[] args) {
        SpringApplication.run(EovaWebApplication.class, args);
    }
}
