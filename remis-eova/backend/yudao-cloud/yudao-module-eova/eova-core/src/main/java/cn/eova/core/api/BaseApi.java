package cn.eova.core.api;

import java.util.ArrayList;
import java.util.List;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import cn.eova.common.base.BaseController;
import cn.eova.compat.jfinal.core.LegacyNotAction;
import cn.eova.compat.jfinal.kit.LegacyJsonKit;
import cn.eova.compat.jfinal.kit.LegacyKv;

/**
 * <p>ported from: cn.eova.core.api.BaseApi
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>对外 API 基类（64 行）：OK/NO 语义化应答 + 4 个入参快速获取入口</li>
 *   <li>【已声明适配 1】com.jfinal.core.NotAction -> cn.eova.compat.jfinal.core.LegacyNotAction（旧实现靠该注解把 OK/NO/getKv 等排除出 action 路由；新栈路由由显式注册决定，注解仍需保留）</li>
 *   <li>【已声明适配 2】com.jfinal.json.Json -> cn.eova.compat.jfinal.kit.LegacyJsonKit</li>
 *   <li>      Json.getJson().parse(json, Kv.class) -> LegacyJsonKit.parse(json, LegacyKv.class)（旧 Json.getJson() 是 JFinalJson 工厂，反序列化出口必须与 LegacyJsonKit 同源，  不得换成 fastjson —— 否则同一段 config JSON 会走两套类型处理）</li>
 *   <li>【已声明适配 3】com.jfinal.kit.Kv -> cn.eova.compat.jfinal.kit.LegacyKv</li>
 *   <li>getJson()/getJsonArray() 读的是 getAttr("_data_str")：该属性由 JSON 入参拦截器写入，故 _data_str 缺席时 JSONObject.parseObject(null) 返回 null，再 .toJSONString() 会 NPE —— 属既有语义（调用方在 action 内，拦截器必已写入），不得加空值兜底</li>
 *   <li>OK(Object)/OK() 两条重载并存：无参版显式传 null 走 ApiResponse.OK(null)，与 OK(new JSONObject()) 的 data 形态不同，不得合并</li>
 * </ol>
 */
/**
 * @author Jieven
 */
public class BaseApi extends BaseController {

    @LegacyNotAction
    public void OK(Object data) {
        renderJson(ApiResponse.OK(data));
    }

    @LegacyNotAction
    public void OK() {
        renderJson(ApiResponse.OK(null));
    }

    @LegacyNotAction
    public void NO(String msg) {
        renderJson(ApiResponse.NO(msg));
    }

    @LegacyNotAction
    public void NO(String msg, int code) {
        renderJson(ApiResponse.NO(code, msg));
    }

    @LegacyNotAction
    public JSONObject getJson() {
        return JSONObject.parseObject(getAttr("_data_str"));
    }

    @LegacyNotAction
    public LegacyKv getKv() {
        return LegacyJsonKit.parse(getJson().toJSONString(), LegacyKv.class);
    }

    @LegacyNotAction
    public JSONArray getJsonArray() {
        return JSONArray.parseArray(getAttr("_data_str"));
    }

    @LegacyNotAction
    public List<LegacyKv> getKvs() {
        List<LegacyKv> kvs = new ArrayList<>();

        JSONArray arrs = getJsonArray();
        for (int i = 0; i < arrs.size(); i++) {
            kvs.add(LegacyJsonKit.parse(arrs.getJSONObject(i).toJSONString(), LegacyKv.class));
        }
        return kvs;
    }

}
