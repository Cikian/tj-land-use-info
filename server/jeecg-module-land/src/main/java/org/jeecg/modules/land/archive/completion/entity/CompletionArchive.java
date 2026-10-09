package org.jeecg.modules.land.archive.completion.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * @Description: 竣工验收项目历史工程资料数字化档案（方案 2.3.2 第 8 项）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version: V1.0
 *
 * <p>表 {@code t_completion_archive}，建表脚本
 * {@code jeecg-module-land/src/main/resources/sql/completion/01_t_completion_archive.sql}。
 *
 * <p><b>★ 这个功能在旧系统里不存在</b>（旧代码全文检索 0 命中、旧库无表、旧菜单无此项），
 * 属方案 2.3.2 第 8 项的全新开发；<b>也没有数据迁移</b> —— 旧系统没有对应数据可迁，
 * 建空表后由页面录入（见清单 §6.3.8 的「存量历史项目」定位）。
 *
 * <p><b>★ 两条业务主线</b>（清单 §6.3.8 的「特殊点」）：
 * <ol>
 *   <li><b>存量历史项目</b>：{@link #landId} / {@link #facilityId} / {@link #crzdbh}
 *       等关联列<b>全部可空</b> —— 历史项目不一定有对应的 {@code t_land}/{@code t_facility} 记录；</li>
 *   <li><b>数字化状态追踪</b>：{@link #digitizeStatus}（3 值枚举，见 DigitizeStatus）+
 *       {@link #pageCount} 总页数 + {@link #scanDpi} 扫描分辨率 + {@link #fileCount} 文件数，
 *       扫描件本体通过 {@link #archiveId} 关联 {@code t_archive}（文件在 {@code t_archive_file}）。</li>
 * </ol>
 *
 * <p><b>★ 为什么列表接口直接返回实体、没有单独建窄 VO</b>：
 * 与第 7 项台账模块同一取舍 —— 本模块的列表页就是主页面，要展示的几乎就是全部字段，
 * 再建一个 30 字段的 VO 只是把同一份字段抄一遍，反而制造「列表与导出取数不一致」的风险。
 * 因此用实体 + 两个非表字段（{@link #seq} 序号、{@link #relatedArchives} 关联扫描件）。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@TableName("t_completion_archive")
public class CompletionArchive implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    // ==================================================================
    // 档案标识
    // ==================================================================

    /** ★档案编号（自动生成 JG-{yyyy}-{4位流水}，允许手工改写；唯一键 uk_ca_no 是单列） */
    private String archiveNo;

    // ==================================================================
    // 历史项目基本信息
    // ==================================================================

    /** ★历史项目名称 */
    private String projectName;

    /** 历史项目原编号（项目自带的编号，可空、不保证唯一；与本系统生成的 archiveNo 是两回事） */
    private String projectCode;

    /** 行政区划（16 区） */
    private String xzqh;

    /** 项目类型（字典 land_completion_project_type） */
    private String projectType;

    // ==================================================================
    // 关联项目信息（★ 全选填：存量历史项目不一定有对应宗地/配套项目）
    // ==================================================================

    /** 出让宗地ID → t_land.id（可空） */
    private String landId;

    /** 配套项目ID → xj_kjkfb_supporting_facilities.id（可空；关联扫描件的第一查找键） */
    private String facilityId;

    /** 出让宗地编号 → t_land.crzdbh（可空；关联扫描件的兜底查找键） */
    private String crzdbh;

    /** 配套项目名称（冗余，便于关联扫描件与展示） */
    private String ptxmmc;

    /** 地块名称（冗余） */
    private String dkmc;

    /** 配套设施类别（道路/排水/供水/燃气…，冗余） */
    private String ptsslb;

    // ==================================================================
    // 参建单位
    // ==================================================================

    /** 建设单位 */
    private String buildUnit;

    /** 施工单位 */
    private String constructUnit;

    /** 设计单位 */
    private String designUnit;

    /** 监理单位 */
    private String superviseUnit;

    // ==================================================================
    // 关键日期
    // ==================================================================

    /** 开工日期 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date startDate;

    /** 竣工日期 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date completeDate;

    /** 验收日期 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date acceptanceDate;

    // ==================================================================
    // 投资与保管
    // ==================================================================

    /** 投资额（万元） */
    private BigDecimal investAmount;

    /** 保管期限（字典 land_completion_retention：永久/定期30年/定期10年/长期） */
    private String retention;

    // ==================================================================
    // ★ 数字化属性（本模块的追踪重点）
    // ==================================================================

    /** 数字化状态：未数字化/数字化中/已数字化（见 DigitizeStatus，不入字典） */
    private String digitizeStatus;

    /** 数字化完成日期 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date digitizeDate;

    /** 数字化加工单位 */
    private String digitizeOrg;

    /** 总页数 */
    private Integer pageCount;

    /** 文件数 */
    private Integer fileCount;

    /** 扫描分辨率（DPI） */
    private Integer scanDpi;

    // ==================================================================
    // 关联档案（扫描件）
    // ==================================================================

    /** 关联档案 → t_archive.id（扫描件本体放 t_archive_file，本表只留指针） */
    private String archiveId;

    /** 关联档案数（冗余，服务端在关联/取消关联时维护） */
    private Integer archiveCount;

    /** 备注 */
    private String remark;

    /** 删除状态 0正常 1已删除 */
    @TableLogic
    private Integer delFlag;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新人 */
    private String updateBy;

    /** 更新时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    // ==================================================================
    // 非表字段
    // ==================================================================

    /**
     * 序号（按当前查询条件的行序，从 1 开始）。
     *
     * <p>不在 SQL 里算：MySQL 5.7 没有 {@code ROW_NUMBER()}，用用户变量又会被
     * 分页插件的 COUNT 优化搅乱（第 7 项台账模块已踩过），因此由 Service 层
     * 用「分页偏移 + 行号」赋值。
     */
    @TableField(exist = false)
    private Integer seq;

    /**
     * 关联扫描件列表（详情接口返回；列表接口为 null）。
     *
     * <p>优先按 {@link #facilityId} 反查 {@code t_archive}，查不到再按 {@link #crzdbh} 兜底
     * —— 与档案管理模块「先选宗地→再选配套项目」的口径一致。
     */
    @TableField(exist = false)
    private List<org.jeecg.modules.land.archive.completion.vo.RelatedArchiveVO> relatedArchives;

    /**
     * 数字化进度（0~100，非表字段，由数字化状态折算：未数字化 0 / 数字化中 50 / 已数字化 100）。
     *
     * <p>列表页的进度条直接读它，避免前端再写一遍状态→百分比的映射。
     */
    @TableField(exist = false)
    private Integer digitizePercent;
}
