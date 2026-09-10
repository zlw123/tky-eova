package cn.eova.sql.dql.dialect;

import java.util.List;

/**
 * <p>ported from: cn.eova.sql.dql.dialect.MysqlQueryDialect
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>MySQL 查询方言（26 行）：extends QueryDialect</li>
 *   <li>宿主依赖仅 java.util.List —— 【无需任何底座替换】，天然逐字节 port</li>
 * </ol>
 */
public class MysqlQueryDialect extends QueryDialect {

	@Override
	public String timeCondition(String field) {
		return String.format(" and ? <= %s and %s <= ?", field, field);
	}

	@Override
	public String dateCondition(String field) {
		return String.format(" and ? <= date(%s) and date(%s) <= ?", field, field);
	}

	@Override
	public String multipleCondition(String field, String value, List<Object> params) {
		// 最终SQL:where (FIND_IN_SET(?, tag) or FIND_IN_SET(?, tag)) 
		// 原因:in只能进行关系查询, 不能进行字符串查找, 所以用 FIND_IN_SET
		// mysql FIND_IN_SET 给参
		params.add(value);
		return String.format("FIND_IN_SET(?, %s)", field);
	}

}
