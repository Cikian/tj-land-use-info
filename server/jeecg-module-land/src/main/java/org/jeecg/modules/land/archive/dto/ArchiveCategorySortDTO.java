package org.jeecg.modules.land.archive.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @Description: 档案类别排序 / 移动请求体（方案 2.3.2 第 1 项，接口 /land/archive/category/sort）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 *
 * <p>语义：把 {@code parentId} 下的直接子类别，按 {@code orderedIds} 给出的顺序
 * 重新写入 sort_no（从 1 开始连续编号）。
 *
 * <p>拖拽移动节点时，前端先算出「拖拽节点的新父级」，再把这个新父级下
 * 最终的子节点顺序传进来即可，因此排序与移动共用同一个接口。
 */
@Data
public class ArchiveCategorySortDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标父级ID；为 null 或空串表示顶级类别这一层 */
    private String parentId;

    /** 目标父级下、按新顺序排列的直接子类别ID列表 */
    private List<String> orderedIds;
}
