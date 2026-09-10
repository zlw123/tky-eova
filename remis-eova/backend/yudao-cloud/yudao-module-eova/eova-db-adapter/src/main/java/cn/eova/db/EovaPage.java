/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.db;

import java.util.List;

/**
 * 分页结果 —— JFinal {@code com.jfinal.plugin.activerecord.Page} 的等价物。
 *
 * <p><b>等价性依据（SP6 实测）：</b>
 * <ul>
 *   <li>{@code totalPage = ceil(totalRow / pageSize)}</li>
 *   <li>{@code isFirstPage} 仅在 {@code pageNumber == 1} 时为 true ——
 *       <b>越界页码（如 999）也是 false</b>，不是通过"是否首行"推断</li>
 *   <li>{@code isLastPage = pageNumber >= totalPage}</li>
 *   <li>越界页返回空 list，而非抛异常</li>
 * </ul>
 *
 * <p>ported from: com.jfinal.plugin.activerecord.Page（语义等价重实现）
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 *
 * @param <T> 行类型
 */
public class EovaPage<T> {

    private int pageNumber;
    private int pageSize;
    private int totalRow;
    private int totalPage;
    private List<T> list;
    private boolean firstPage;
    private boolean lastPage;

    /**
     * 构造分页结果
     *
     * @param pageNumber 当前页码（从 1 开始）
     * @param pageSize   每页条数
     * @param totalRow   总行数
     * @param list       当前页数据
     */
    public EovaPage(int pageNumber, int pageSize, int totalRow, List<T> list) {
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        this.totalRow = totalRow;
        this.list = list;
        this.totalPage = pageSize <= 0 ? 0 : (totalRow + pageSize - 1) / pageSize;
        // 与旧实现一致：仅 pageNumber == 1 时为首行
        this.firstPage = pageNumber == 1;
        this.lastPage = pageNumber >= totalPage;
    }

    /** 取当前页码 */
    public int getPageNumber() {
        return pageNumber;
    }

    /** 取每页条数 */
    public int getPageSize() {
        return pageSize;
    }

    /** 取总行数 */
    public int getTotalRow() {
        return totalRow;
    }

    /** 取总页数 */
    public int getTotalPage() {
        return totalPage;
    }

    /** 取当前页数据 */
    public List<T> getList() {
        return list;
    }

    /** 是否首页（仅 pageNumber == 1） */
    public boolean isFirstPage() {
        return firstPage;
    }

    /** 是否末页 */
    public boolean isLastPage() {
        return lastPage;
    }
}
