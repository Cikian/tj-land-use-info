package org.jeecg.modules.land.data.process.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Date;

/**
 * @Description: 审批流程（环节）配置 —— 六大阶段 + 24 个事项
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p>表 {@code t_process_configuration}，建表与种子脚本
 * {@code jeecg-module-land/src/main/resources/sql/data/02_t_process_configuration.sql}。
 * 数据来源：旧库 {@code nutzwk_ywk.process_configuration}（30 行，实测全量迁移）。
 *
 * <p><b>为什么 29 环节要有一张配置表</b>：环节不是代码里的常量，而是业务配置 ——
 * 中心的审批事项会随政策调整。做成表，中心自己就能改；做成枚举，改一个字就要重新发版。
 *
 * <p><b>层级结构（实测）</b>：
 * <pre>
 *   6 个「阶段」：parent_id IS NULL、has_children = 1
 *     0001 立项用地规划许可阶段(20天)  0002 工程建设许可阶段(30天)
 *     0003 施工许可阶段(15天)          0004 项目施工阶段(60天)
 *     0005 竣工验收许可阶段(15天)      0006 竣工移交许可阶段(15天)
 *   24 个「事项」：parent_id 指向所属阶段
 * </pre>
 *
 * <p><b>★ 空串已归一化为 NULL</b>：旧库用 {@code parentId = ''} 表示「无父节点」，
 * 迁移时统一改成 NULL，这样「阶段 = parent_id IS NULL」这一条判据唯一且可走索引。
 * 判断阶段请一律用 {@link #isStage()}，不要自己写 {@code StringUtils.isBlank(parentId)}
 * —— 后者会把「父节点为空串的脏数据」也当成阶段。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@TableName("t_process_configuration")
public class ProcessConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键（沿用旧库 process_configuration.id） */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 环节名称 */
    private String name;

    /** 环节别名（拼音首字母） */
    private String aliasName;

    /** 环节介绍 */
    private String note;

    /** 主管部门 */
    private String zgbm;

    /** 同级排序号（全局递增，跨阶段也是递增的，直接 order by location 就是业务顺序） */
    private Integer location;

    /** 父环节ID（NULL = 六大阶段之一） */
    private String parentId;

    /** 是否有子节点 */
    private Integer hasChildren;

    /** 标准办理时长（天）：用于算预计结束时间与预警 */
    private Long processTime;

    /** 树路径（0001 / 00010001） */
    private String path;

    /** 是否禁用：0启用 1停用 */
    private Integer disabled;

    /** 是否管线流程 */
    private Integer isPipeline;

    /** 是否可并行办理 */
    private Integer isParallel;

    private String createBy;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    private String updateBy;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /** 删除标识：0正常 1已删除 */
    @TableLogic
    private Integer delFlag;

    // ==================================================================
    // 展示型字段（不入库，Service 组装）
    // ==================================================================

    /** 子事项（仅阶段行会填） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private java.util.List<ProcessConfig> children;

    /** 本阶段的汇总状态（仅阶段行会填，由子事项的环节情况归并而来） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String stageStatus;

    /** 本阶段下已有进度记录的事项数（仅阶段行会填） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Integer filledCount;

    /** 是否阶段（六大阶段之一） */
    public boolean isStage() {
        return parentId == null || parentId.trim().isEmpty();
    }
}
