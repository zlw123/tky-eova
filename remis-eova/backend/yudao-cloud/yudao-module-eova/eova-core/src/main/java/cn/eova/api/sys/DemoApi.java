package cn.eova.api.sys;

import java.util.List;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import cn.eova.core.api.BaseApi;
import cn.eova.compat.jfinal.kit.LegacyKv;

/**
 * <p>ported from: cn.eova.api.sys.DemoApi
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>演示接口（69 行）：语法测试与验证用，含 ok1/ok2/no1/no2/err/post 六个 action</li>
 *   <li>【已声明适配 1】com.jfinal.kit.Kv -> cn.eova.compat.jfinal.kit.LegacyKv</li>
 *   <li>【既有缺陷，原样保留】err() 故意对 null 取 length() 制造 NPE（即『演示异常渲染』），不得加判空、不得改成抛自定义异常</li>
 *   <li>post() 里 4 个入参入口（getKv/getKvs/getJson/getJsonArray）调用后并未使用结果，只 print 了两个值：属既有代码，不得清理</li>
 *   <li>post() 用 get("_data_str") 而非 getAttr：前者是请求参数入口，语义与 getAttr 不同，原样保留</li>
 * </ol>
 */
/**
 * Demo API
 * 语法测试与验证
 *
 * @author Jieven
 */
public class DemoApi extends BaseApi {

    public void ok1() {
        System.out.println("DemoApi ok1 ...........");

        OK();
    }

    public void ok2() {
        System.out.println("DemoApi ok2 ...........");
        JSONObject tp = new JSONObject();
        tp.put("test1", 111);
        tp.put("test2", 222);
        tp.put("test3", 3333);
        OK(tp);
    }

    public void no1() {
        System.out.println("DemoApi no1 ...........");
        NO("业务异常,balabala");// try
    }

    public void no2() {
        System.out.println("DemoApi no2 ...........");
        NO("IP地址不在白名单之内", 10040);// try
    }

    public void err() {
        System.out.println("DemoApi error ...........");

        String a = null;
        System.out.println(a.length());

        OK();
    }

    public void post() {
        // 参数快速获取 JFinal 风格 推荐+1
        LegacyKv kv = getKv();
        List<LegacyKv> kvs = getKvs();
        // 参数快速获取 FastJson 风格
        JSONObject json = getJson();
        JSONArray array = getJsonArray();

        // 其他非json, 例如文件, 自行封装解析
        String str = get("_data_str");

        System.out.println(kv);
        System.out.println(kv.getStr("id") + " -> " + kv.getStr("name"));

        OK();
    }

}
