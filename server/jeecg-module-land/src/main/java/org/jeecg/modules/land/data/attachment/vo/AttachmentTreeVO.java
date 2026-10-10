package org.jeecg.modules.land.data.attachment.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.modules.land.data.attachment.entity.LandAttachment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @Description: 附件目录树（按材料类型分组，两层）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-10
 * @Version V1.0
 *
 * <p><b>★ 结构只有两层：业务对象 → 材料类型 → 文件</b>
 * <pre>
 *   项建批复文件 (3 个 · 12.4 MB)
 *     ├ 立项批复.pdf
 *     └ 批复附件.docx
 *   道路规划 (1 个 · 3.1 MB)
 *     └ 道路规划图.dwg
 * </pre>
 *
 * <p><b>★ 为什么不需要「目录树」本身的数据结构</b>：
 * 目录名就是材料类型名，而材料类型码本来就存在附件表的 {@code file_type} 里 ——
 * 树是「该业务对象下实际有附件的材料类型」这一个**派生结果**，
 * 没有任何需要单独维护的目录数据，也就不可能出现「目录与附件不一致」。
 *
 * <p><b>★ 空目录不出现</b>（2026-10-10 确认的口径）：没有附件的材料类型不进 {@code groups}，
 * 所以前端不需要处理空节点。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
public class AttachmentTreeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务类型（land / facility / process） */
    private String bizType;

    /** 业务可读键（宗地编号 / 配套项目名称），供界面标题用 */
    private String bizKey;

    /** 按材料类型分组的节点（**只含有文件的类型**，顺序 = 材料清单顺序） */
    private List<TypeGroup> groups = new ArrayList<>();

    /** 全部文件数 */
    private Integer totalFiles = 0;

    /** 全部文件总字节数 */
    private Long totalSize = 0L;

    /** 用到的材料类型数（= groups.size()，前端显示「N 类」） */
    private Integer typeCount = 0;

    /**
     * 一个材料类型下的文件集合。
     *
     * <p>节点 key 用 {@code type:<码>}：前端树需要一个稳定唯一的 key，
     * 而类型码在一个业务对象内天然唯一。
     */
    @Data
    @Accessors(chain = true)
    @EqualsAndHashCode(callSuper = false)
    public static class TypeGroup implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 树节点 key：type:01 */
        private String key;

        /** 材料类型码（01…13） */
        private String fileType;

        /** 材料类型名（字典里的中文名；字典缺失时回退为码值） */
        private String fileTypeName;

        /**
         * 归属业务类型与业务对象 id（冗余）。
         *
         * <p>★ 为什么要冗余：树上前端的「上传到该类型」按钮需要知道
         * 「传到哪个项目的哪个材料类型」，而分组节点在跨项目视图里本身不带归属信息 ——
         * 让前端从 {@code files[0]} 去推是可以，但那是个隐式约定（列表为空时就取不到），
         * 显式带上更可靠，也少一层前端推理。
         */
        private String bizType;

        /** 归属业务对象 id */
        private String bizId;

        /** 归属业务可读键（宗地编号 / 配套项目名称） */
        private String bizKey;

        /** 该类型下的文件（按排序号 + 上传时间） */
        private List<LandAttachment> files = new ArrayList<>();

        /** 该类型的文件数（= files.size()） */
        private Integer fileCount = 0;

        /** 该类型的总字节数 */
        private Long totalSize = 0L;
    }
}
