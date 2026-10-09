package org.jeecg.modules.land.data.oplog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Date;

/**
 * @Description: 数据变更留痕（字段级，含软删与恢复）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p>表 {@code t_data_change_log}，建表脚本
 * {@code jeecg-module-land/src/main/resources/sql/data/05_t_data_change_log.sql}。
 *
 * <p><b>★ 为什么旧日志表 {@code xj_kjkfb_operationrecord} 不够用</b>（实测 786 行）：
 * 它只有 6 列 —— {@code specialName}(操作名称) / {@code operateTime}(<b>字符串</b>) /
 * {@code operatePeople}(<b>786 行全是「普通用户」</b>) / {@code bz} / {@code loginName}。
 * <ol>
 *   <li>只记「添加了 N 条数据」这种一句话，<b>记不下改了哪个字段、改前改后是什么</b>，
 *       事后想回答「这个出让金是谁从 1.2 改成 2.0 的」根本查不出来；</li>
 *   <li>{@code operateTime} 是字符串（{@code 2024/10/15 10:15:03}），
 *       按时间范围筛要先做函数转换，无法走索引；</li>
 *   <li>{@code operatePeople} 恒为「普通用户」，等于没记操作人；</li>
 *   <li>没有业务主键列，无法回答「这一条宗地的完整履历」。</li>
 * </ol>
 * 本实体按「业务对象 + 动作 + 字段级差异 + 操作人 + 时间」重新设计，逐条对上。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@TableName("t_data_change_log")
public class DataChangeLog implements Serializable {

    private static final long serialVersionUID = 1L;

    // ==================================================================
    // 动作取值（列表筛选用，也用在前端标签配色）
    // ==================================================================

    /** 动作：新增 */
    public static final String ACTION_CREATE = "CREATE";
    /** 动作：修改 */
    public static final String ACTION_UPDATE = "UPDATE";
    /** 动作：移除（软删） */
    public static final String ACTION_DELETE = "DELETE";
    /** 动作：恢复 */
    public static final String ACTION_RESTORE = "RESTORE";
    /** 动作：批量导入 */
    public static final String ACTION_IMPORT = "IMPORT";
    /** 动作：附件上传 */
    public static final String ACTION_UPLOAD = "UPLOAD";
    /** 动作：附件删除 */
    public static final String ACTION_ATTACH_DELETE = "ATTACH_DELETE";

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 业务类型：land / facility / process / attachment */
    private String bizType;

    /** 业务主键 */
    private String bizId;

    /** 业务可读键（宗地编号 / 配套项目名称） */
    private String bizKey;

    /** 动作 */
    private String action;

    /** 一句话摘要（列表展示，不点开也能看懂） */
    private String changeSummary;

    /**
     * 字段级变更明细 JSON。
     *
     * <p>形如：{@code [{"field":"crj","label":"出让金（亿元）","before":"1.20","after":"2.00"}]}
     *
     * <p>★ 为什么存 JSON 而不是「每个字段一行」：一条记录的变更天然是
     * <b>一次操作产生的一组值</b>。拆成多行会让「一次修改」在列表里散成七八行，
     * 反而看不出这是一次操作。
     */
    private String changeDetail;

    /** 本次变更的字段个数 */
    private Integer changeCount;

    /** 操作人账号 */
    private String operator;

    /** 操作人姓名 */
    private String operatorName;

    /** 操作 IP */
    private String operatorIp;

    /** 操作时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    // ==================================================================
    // 展示型字段（不入库）
    // ==================================================================

    /** 变更明细解析后的结构化列表（Service 组装，前端直接渲染表格） */
    @TableField(exist = false)
    private java.util.List<Object> details;

    /** 动作中文名 */
    @TableField(exist = false)
    private String actionText;

    /** 动作标签颜色（antd 预设色） */
    @TableField(exist = false)
    private String actionColor;
}
