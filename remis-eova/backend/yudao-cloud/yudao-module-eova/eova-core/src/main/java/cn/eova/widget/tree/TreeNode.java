/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.widget.tree;

import java.util.List;

import cn.eova.db.EovaRecord;

/**
 * <p>ported from: cn.eova.widget.tree.TreeNode
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>树节点值对象；字段名属契约（前端经 JSON 消费）</li>
 * </ol>
 */
/**
 * Tree Node VO
 *
 * @author Jieven
 * @date 2014-9-8
 */
public class TreeNode extends EovaRecord {

    private static final long serialVersionUID = -5190761342805087001L;

    // 子节点
    private List<TreeNode> childs;

    public List<TreeNode> getChildList() {
        return childs;
    }

    public void setChildList(List<TreeNode> childList) {
        this.childs = childList;
    }

}