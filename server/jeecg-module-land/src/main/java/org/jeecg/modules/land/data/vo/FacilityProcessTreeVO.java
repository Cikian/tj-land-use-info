package org.jeecg.modules.land.data.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @Description: 环节进度视图 —— 六大阶段 × 24 个事项（「29 环节录入」界面的数据源）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p><b>★ 这个结构解决什么问题</b>：配套项目的环节录入界面不是一张平表，
 * 而是「6 个阶段分组、每组下挂 4~8 个事项、阶段有自己的汇总状态与完成度」。
 * 后端如果直接返回 24 条扁平的进度记录，前端就得自己按阶段分组、自己算汇总 ——
 * 汇总口径（尤其是「不涉及」怎么算）一旦在前端实现，就跟服务端的统计对不上了。
 *
 * <p>所以这里由服务端一次性给出「阶段 + 事项 + 汇总」的树，
 * 前端只负责渲染。阶段汇总口径唯一实现见
 * {@link org.jeecg.modules.land.data.process.enums.ProcessStatus#rollUpStageStatus}。
 */
@Data
public class FacilityProcessTreeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 配套项目ID */
    private String ptId;

    /** 出让宗地编号 */
    private String crzdbh;

    /** 配套项目名称 */
    private String facilityName;

    /** 六大阶段（含各自的事项与汇总） */
    private List<StageNode> stages = new ArrayList<>();

    // ---------- 顶层汇总（页面顶部指标卡） ----------

    /** 环节总数（阶段下的事项总数，通常 24；按配置表实际条数算，不写死） */
    private int totalItems;

    /** 已完成事项数 */
    private int finishedItems;

    /** 进行中事项数 */
    private int runningItems;

    /** 不涉及事项数 */
    private int notInvolvedItems;

    /** 未开启事项数 */
    private int notStartedItems;

    /** 已录入过进度的事项数（用于「已填 N/24」的提示） */
    private int filledItems;

    /** 逾期事项数 */
    private int overdueItems;

    /** 整体完成度百分比（剔除「不涉及」） */
    private int percent;

    /**
     * @Description: 一个阶段及其事项
     */
    @Data
    public static class StageNode implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 阶段ID */
        private String stageId;

        /** 阶段名称 */
        private String stageName;

        /** 阶段树路径（0001） */
        private String path;

        /** 标准办理时长（天） */
        private Long processTime;

        /** 阶段汇总状态（未开启 / 进行中 / 已完成 / 不涉及） */
        private String status;

        /** 阶段完成度百分比（剔除「不涉及」） */
        private int percent;

        /** 本阶段下已有进度记录的事项数 */
        private int filledCount;

        /** 本阶段下的事项总数 */
        private int itemCount;

        /** 本阶段下逾期事项数 */
        private int overdueCount;

        /** 事项列表 */
        private List<ItemNode> items = new ArrayList<>();
    }

    /**
     * @Description: 一个事项（环节）及其进度
     */
    @Data
    public static class ItemNode implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 环节ID（t_process_configuration.id） */
        private String lcId;

        /** 环节名称 */
        private String lcName;

        /** 树路径（00010001） */
        private String path;

        /** 主管部门 */
        private String zgbm;

        /** 标准办理时长（天） */
        private Long processTime;

        /** 同级排序号 */
        private Integer location;

        /** 是否可并行办理 */
        private Integer isParallel;

        /** 进度记录ID（未录入时为 null） */
        private String progressId;

        /** 环节情况（未录入时为「未开启」） */
        private String lcqk;

        /** 环节开始时间 */
        private String lckssj;

        /** 预计结束时间 */
        private String yjjssj;

        /** 环节实际结束时间 */
        private String lcjssj;

        /** 存在问题类型 */
        private String czwtlx;

        /** 具体问题 */
        private String jtwt;

        /** 工作建议 */
        private String gzjy;

        /** 录入单位 */
        private String lrdw;

        /** 录入人 */
        private String lrr;

        /** 联系电话 */
        private String lxdh;

        /** 是否逾期 */
        private Boolean overdue;

        /** 逾期天数（未逾期为 0） */
        private Integer overdueDays;

        /** 该环节的附件数量 */
        private Integer attachmentCount;

        /** 是否已录入过（false 时前端显示「未填报」标记） */
        private Boolean filled;
    }
}
