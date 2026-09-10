package cn.eova.sql.dql;

/**
 * <p>ported from: cn.eova.sql.dql.QueryParam
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 */
/**
 * 查询参数
 * @author Jieven
 *
 */
public class QueryParam {

	/**逻辑运算法**/
	public String cond;
	/**单个值**/
	public Object value;
	/**范围开始值**/
	public String start;
	/**范围结束值**/
	public String end;

	public QueryParam(String cond, Object value, String start, String end) {
		super();
		this.cond = cond;
		this.value = value;
		this.start = start;
		this.end = end;
	}

}
