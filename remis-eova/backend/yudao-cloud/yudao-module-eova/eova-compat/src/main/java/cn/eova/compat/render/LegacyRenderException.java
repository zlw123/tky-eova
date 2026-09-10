/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.render;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.render.RenderException} 的等价接缝。
 *
 * <p>{@code ported from} {@code com.jfinal.render.RenderException}（jfinal 5.2.6）。</p>
 *
 * <p>{@code serialVersionUID} 取自旧字节码的 ConstantValue
 * （{@code -6448434551667513804L}），属序列化契约，<b>不得改动</b>。
 * 四个构造器与旧实现一一对应，均只做父类转发、无附加逻辑。</p>
 */
public class LegacyRenderException extends RuntimeException {

    private static final long serialVersionUID = -6448434551667513804L;

    /** 无参构造，转发父类 */
    public LegacyRenderException() {
        super();
    }

    /**
     * 仅带消息，转发父类。
     *
     * @param message 消息
     */
    public LegacyRenderException(String message) {
        super(message);
    }

    /**
     * 仅带原因，转发父类。
     *
     * @param cause 原因
     */
    public LegacyRenderException(Throwable cause) {
        super(cause);
    }

    /**
     * 带消息与原因，转发父类。
     *
     * @param message 消息
     * @param cause   原因
     */
    public LegacyRenderException(String message, Throwable cause) {
        super(message, cause);
    }

}
