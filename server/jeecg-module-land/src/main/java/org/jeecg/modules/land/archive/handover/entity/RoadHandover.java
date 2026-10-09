package org.jeecg.modules.land.archive.handover.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.modules.land.archive.ledger.vo.RelatedArchiveVO;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * @Description: 道路交付及养护协议移交事项（方案 2.3.2 第 6 项）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version V1.0
 *
 * <p>表 {@code t_road_handover}，建表脚本
 * {@code jeecg-module-land/src/main/resources/sql/handover/01_t_road_handover.sql}。
 *
 * <p><b>★ 与「道路验收及移交资料台账」（第 7 项）的分工</b>：台账是<b>普查</b>
 * （1340 条道路一条一行，管「13 类资料齐不齐」），本表是<b>事项</b>
 * （只登记真正发生/在办的移交事项，管「协议签了没、养护期从哪天到哪天、谁接收管养」）。
 * 两者通过 {@code facility_id} / {@code crzdbh} 互相关联，但<b>不互相写入</b>。
 *
 * <p><b>旧系统没有本功能</b>（旧代码 0 命中、旧库无表、旧菜单无此项），但有一个可用的迁移依据：
 * 旧配套项目表的 {@code sfyj='是'}，据此生成 84 条「正式移交」初始记录
 * （见 {@code sql/handover/05_migrate_handover.sql}）。这 84 条的协议信息与移交日期
 * 旧库压根没有 → 留空待中心补录，并在 {@link #remark} 里写明。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@TableName("t_road_handover")
public class RoadHandover implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** ★移交事项编号（自动生成 YJ-{yyyy}-{4位流水}，允许手工改写；唯一键 uk_rh_no 单列） */
    private String handoverNo;

    // ==================================================================
    // 道路 / 项目基本信息
    // ==================================================================

    /** ★道路名称 */
    private String roadName;

    /** 道路编号 */
    private String roadCode;

    /** 道路等级：城市主干路/次干路/支路 */
    private String dldj;

    /** 起点 */
    private String startPoint;

    /** 终点 */
    private String endPoint;

    /** 长度(米) */
    private BigDecimal lengthM;

    /** 红线宽度(米) */
    private BigDecimal redLineWidth;

    /** 行政区划（16 区；功能区值已拆到 {@link #gnq}） */
    private String xzqh;

    /** 功能区（生态城/经开区/高新区/保税区/土地发展中心） */
    private String gnq;

    /** 配套设施类别（道路/市政道路/道路及管线…） */
    private String ptsslb;

    // ==================================================================
    // 与主业务系统的挂钩点
    // ==================================================================

    /** 出让宗地编号 → t_land.crzdbh */
    private String crzdbh;

    /** 出让宗地ID → t_land.id（冗余） */
    private String landId;

    /** 地块名称（冗余，迁移时由 crzdbh 反查） */
    private String dkmc;

    /** 配套项目ID → xj_kjkfb_supporting_facilities.id */
    private String facilityId;

    /** 配套项目名称（冗余，与 roadName 同值） */
    private String ptxmmc;

    // ==================================================================
    // 协议与移交信息（迁移时全部为空，待中心补录）
    // ==================================================================

    /** 移交类型（字典 land_road_handover_type：道路交付/养护协议/正式移交） */
    private String handoverType;

    /** 协议编号 */
    private String agreementNo;

    /** 协议名称 */
    private String agreementName;

    /** 协议签订日期 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date agreementDate;

    /** 建设单位 */
    private String buildUnit;

    /** 接收管养单位 */
    private String receiveUnit;

    /** 实际移交日期 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date handoverDate;

    /** 养护起始日期 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date maintenanceStart;

    /** 养护截止日期 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date maintenanceEnd;

    // ==================================================================
    // 状态与档案
    // ==================================================================

    /** 状态：待移交/移交中/已移交（见 HandoverStatus，不入字典） */
    private String status;

    /** 关联档案 → t_archive.id（协议扫描件、移交单挂档案，本表只留指针） */
    private String archiveId;

    /** 关联档案数（冗余，服务端维护） */
    private Integer archiveCount;

    // ==================================================================
    // 迁移
    // ==================================================================

    /** 迁移来源：旧配套项目ID（★ 迁移幂等键 uk_rh_source，人工新增为 NULL） */
    private String sourceFacilityId;

    /** 备注（迁移生成的记录在这里写明「协议信息待补录」与「宗地未匹配」） */
    private String remark;

    /** 删除状态 0正常 1已删除 */
    @TableLogic
    private Integer delFlag;

    private String createBy;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    private String updateBy;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    // ==================================================================
    // 非表字段
    // ==================================================================

    /** 序号（按当前查询条件从 1 开始；Service 用「分页偏移 + 行号」赋值） */
    @TableField(exist = false)
    private Integer seq;

    /**
     * 关联档案列表（详情接口返回）。
     *
     * <p>★ 这里复用了台账模块的 {@link RelatedArchiveVO}：两个模块读的是同一张
     * {@code t_archive}、需要的是同一组列，再造一个同形 VO 只会让「档案行长什么样」
     * 出现两份定义。这是本模块唯一一处跨子包引用，特此注明。
     */
    @TableField(exist = false)
    private List<RelatedArchiveVO> relatedArchives;
}
