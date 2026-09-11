package cn.eova.common.utils.web;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import cn.eova.compat.jfinal.core.LegacyConst;
import cn.eova.compat.jfinal.core.LegacyController;
import cn.eova.compat.jfinal.kit.LegacyJsonKit;
import cn.eova.compat.jfinal.kit.LegacyKv;
import cn.eova.compat.jfinal.kit.LegacyLogKit;

/**
 * SSE消息发送工具类 v1.1.0
 * @author 杜福忠
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
/**
 * <p>ported from: cn.eova.common.utils.web.SseKit
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>SSE 工具（221 行）：按用户维护 AsyncContext、推送 JSON 事件</li>
 *   <li>【已声明适配】Const -> LegacyConst；Controller -> LegacyController；JsonKit -> LegacyJsonKit；Kv -> LegacyKv；LogKit -> LegacyLogKit</li>
 *   <li>AsyncContext/AsyncEvent/AsyncListener 为 jakarta.servlet（决策 1）</li>
 * </ol>
 */
public class SseKit {
    private static final Map<String, AsyncContext> sseMap = new ConcurrentHashMap<>();

    /** 心跳间隔（秒），防止 Nginx / 代理空闲断开 */
    private static final int HEARTBEAT_SECONDS = 15;

    private static final ScheduledExecutorService HEARTBEAT = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    static {
        HEARTBEAT.scheduleAtFixedRate(SseKit::heartbeat, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    public static AsyncContext get(String user) {
        return sseMap.get(user);
    }

    public static Set<String> getUsers() {
        return sseMap.keySet();
    }

    /**
     * 开启sse连接
     * @param user  用户名
     * @param c  LegacyController
     * @return AsyncContext
     */
    public static AsyncContext startAsync(String user, LegacyController c) {
        Objects.requireNonNull(user, "user can not be null");
        c.renderNull();
        HttpServletResponse response = c.getResponse();
        response.setCharacterEncoding(LegacyConst.DEFAULT_ENCODING);
        response.setContentType("text/event-stream; charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Connection", "keep-alive");
        // 关闭 Nginx 代理缓冲，否则 SSE 会被攒包或空闲掐断
        response.setHeader("X-Accel-Buffering", "no");

        AsyncContext ac = c.getRequest().startAsync();
        // 默认1小时超时
        ac.setTimeout(60 * 60 * 1000L);

        // 替换旧连接：先登记新连接，再关闭旧连接，避免 onComplete 误删新连接
        AsyncContext old = sseMap.put(user, ac);
        if (old != null) {
            completeQuietly(old);
        }

        ac.addListener(new AsyncListener() {
            @Override
            public void onComplete(AsyncEvent event) {
                // 只清理“当前仍是自己”的登记，避免新连接被旧连接的回调踢掉
                sseMap.remove(user, ac);
            }

            @Override
            public void onTimeout(AsyncEvent event) {
                sseMap.remove(user, ac);
                completeQuietly(ac);
            }

            @Override
            public void onError(AsyncEvent event) {
                sseMap.remove(user, ac);
            }

            @Override
            public void onStartAsync(AsyncEvent event) {
            }
        });

        // 立即写出并 flush，确认链路打通（注释行，前端不触发事件）
        if (!writeRaw(ac, ": connected\n\n")) {
            sseMap.remove(user, ac);
            completeQuietly(ac);
            return null;
        }
        return ac;
    }

    public static void remove(String user) {
        if (user == null) {
            return;
        }
        AsyncContext ac = sseMap.remove(user);
        completeQuietly(ac);
    }

    /**
     * 向用户端推送消息
     * @param uid 用户ID
     * @param kv 消息内容
     * @return
     */
    public static boolean pushMsg(int uid, LegacyKv kv) {
        String s = String.format("event: %s\ndata: %s\n\n", "msg", kv.toJson());
        return sendMessage(uid + "", s);
    }

    public static boolean sendJsonMessage(String user, Object data) {
        String dataStr = String.format("data: %s\n\n", toJson(data));
        return sendMessage(user, dataStr);
    }

    public static boolean sendJsonMessage(String user, String event, Object data) {
        String dataStr = String.format("event: %s\ndata: %s\n\n", event, toJson(data));
        return sendMessage(user, dataStr);
    }

    public static boolean sendJsonMessage(String user, Integer id, Object data) {
        String dataStr = String.format("id: %d\ndata: %s\n\n", id, toJson(data));
        return sendMessage(user, dataStr);
    }

    /**
     * 发送消息
     * @param user  接收者
     * @param id  消息 ID
     * @param event  事件
     * @param data  json消息内容
     * @return 发送成功返回true，失败返回false
     */
    public static boolean sendJsonMessage(String user, Integer id, String event, Object data) {
        String dataStr = String.format("id: %d\nevent: %s\ndata: %s\n\n", id, event, toJson(data));
        return sendMessage(user, dataStr);
    }

    private static String toJson(Object data) {
        if (data == null) {
            return "";
        }
        return data instanceof String ? (String) data : LegacyJsonKit.toJson(data);
    }

    /**
     * 发送消息
     * @param user 用户
     * @param dataStr 消息内容（需做格式化）
     * @return 发送成功返回true，失败返回false
     */
    public static boolean sendMessage(String user, String dataStr) {
        AsyncContext ac = get(user);
        if (ac == null) {
            return false;
        }
        if (!writeRaw(ac, dataStr)) {
            // 写出失败：连接已死，清理登记
            if (sseMap.remove(user, ac)) {
                completeQuietly(ac);
            }
            return false;
        }
        return true;
    }

    private static void heartbeat() {
        for (Map.Entry<String, AsyncContext> e : sseMap.entrySet()) {
            String user = e.getKey();
            AsyncContext ac = e.getValue();
            if (!writeRaw(ac, ": ping\n\n")) {
                if (sseMap.remove(user, ac)) {
                    completeQuietly(ac);
                }
            }
        }
    }

    private static boolean writeRaw(AsyncContext ac, String dataStr) {
        try {
            PrintWriter writer = ac.getResponse().getWriter();
            writer.write(dataStr);
            writer.flush();
            return !writer.checkError();
        } catch (IOException | IllegalStateException e) {
            LegacyLogKit.error(e.getMessage());
            return false;
        }
    }

    private static void completeQuietly(AsyncContext ac) {
        if (ac == null) {
            return;
        }
        try {
            ac.complete();
        } catch (IllegalStateException ignored) {
            // 已完成 / 已超时
        }
    }
}
