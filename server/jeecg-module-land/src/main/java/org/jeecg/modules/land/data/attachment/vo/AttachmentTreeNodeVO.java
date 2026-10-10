package org.jeecg.modules.land.data.attachment.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.modules.land.data.attachment.entity.LandAttachment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @Description: 附件目录树的节点（目录 + 该目录下的文件）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-10
 * @Version V1.0
 *
 * <p><b>★ 为什么目录节点要能带文件，而不是只返回目录</b>：
 * 前端要渲染的是「一棵能看见文件的树」——点开目录就能看到里面的文件，
 * 而不是点一下再去请求一次。附件数量在这个场景是可控的（单个业务对象下，
 * 实测几十到几百条），一次性返回比逐层懒加载体验好很多，也少一堆 loading 态。
 * 若将来某个项目附件特别多，再给 {@code /dir/tree} 加「是否含文件」的开关即可。
 *
 * <p><b>★ 统计口径</b>：{@code fileCount} / {@code totalSize} 都是**含子目录递归累加**的 ——
 * 树上父节点的角标要能回答「这一支下一共有多少东西」，
 * 否则用户得自己把每层的数字加起来。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
public class AttachmentTreeNodeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 节点键：目录用 {@code dir:<相对路径>}，根目录用 {@code dir:}（空路径），
     * 文件用 {@code file:<附件id>}。
     *
     * <p>★ 目录与文件必须能放进同一棵树的 key 空间里：纯路径做 key 时，
     * 「文件名叫 dir: 的目录名」这种极端情况会撞车，所以显式加类型前缀。
     */
    private String key;

    /** 节点类型：dir 目录 / file 文件 */
    private String nodeType;

    /** 显示名：目录名 或 文件名 */
    private String label;

    /** 相对业务对象的目录路径（文件节点 = 它所在目录；根目录 = 空串） */
    private String dirPath;

    /** 层级：根目录为 0，其下第一层为 1 */
    private Integer depth;

    /** 该节点（含子目录）下的文件总数 */
    private Integer fileCount;

    /** 该节点（含子目录）下的文件总字节数 */
    private Long totalSize;

    /** 目录节点：是否为空目录（含子目录都没有文件）—— 界面可据此给个「空」的提示 */
    private Boolean empty;

    /** 文件节点的附件详情（目录节点为 null） */
    private LandAttachment file;

    /** 子节点：子目录在前、文件在后 */
    private List<AttachmentTreeNodeVO> children = new ArrayList<>();
}
