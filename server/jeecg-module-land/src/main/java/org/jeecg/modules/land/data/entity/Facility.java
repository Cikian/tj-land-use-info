package org.jeecg.modules.land.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * @Description: 市政配套项目（配套项目宽表）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 *
 * <p>表 {@code xj_kjkfb_supporting_facilities}（1433 行），**直接复用旧表名**，
 * 不再新建 {@code t_facility}：该表早已复制进 tj-jyxyd 库，且被收发文等场景引用，
 * 改名会带来无谓的迁移成本。
 *
 * <p><b>审计字段的坑</b>：本表三列是 <b>驼峰命名</b>（{@code delFlag} / {@code createTime} /
 * {@code createAccount}），而全局 {@code map-underscore-to-camel-case=true}，
 * 若不加 {@code @TableField} 显式指定列名，MyBatis-Plus 会去找
 * {@code del_flag} / {@code create_time} 而报「Unknown column」。因此这三列必须显式映射。
 *
 * <p><b>关联</b>：{@code crzdbh} → {@link Land#getCrzdbh()}。
 * 1 宗地 : N 配套项目。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@TableName("xj_kjkfb_supporting_facilities")
public class Facility implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 编号ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 出让宗地编号（关联 Land.crzdbh） */
    private String crzdbh;

    /** 配套项目名称 */
    private String ptxmmc;

    /** 地块名称 */
    private String dkmc;

    /** 配套设施类别：道路 / 排水 / 供水 / 中水 / 燃气 / 路灯 / 绿化 / 交通设施… */
    private String ptsslb;

    /** 行政区划 */
    private String xzqh;

    /** 项目分类：市级项目 / 区级项目 */
    private String xmfl;

    /** 建设性质 */
    private String jsxx;

    /** 道路等级 */
    private String dldj;

    /** 是否涉及提级论证 */
    private String sfzsjtjlz;

    /** 提级论证是否通过 */
    private String tjlzsftg;

    /** 规划红线宽度（米） */
    private BigDecimal ghhxkd;

    /** 长度（米） */
    private BigDecimal cd;

    /** 投资估算（万元） */
    private BigDecimal tzgs;

    /** 资金来源 */
    private String zjly;

    /** 地块出让时承诺的配套竣工时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    private Date dkcrscndptjgsj;

    /** 建设单位 */
    private String jsdw;

    /** 设计单位 */
    private String sjdw;

    /** 勘察单位 */
    private String kcdw;

    /** 监理单位 */
    private String jldw;

    /** 施工单位 */
    private String sgdw;

    /** 接收管养单位 */
    private String jsgydw;

    /** 项建批复是否完成 */
    private String xjpfsfwc;

    /** 可研批复是否完成 */
    private String kypfsfwc;

    /** 初设及概算批复是否完成 */
    private String csjgspfsfwc;

    /** 概算批复金额（万元） */
    private BigDecimal gspfje;

    /** 资金落实情况 */
    private String zjlsqk;

    /** 是否开工 */
    private String sfkg;

    /** 预计开工时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    private Date yjkgsj;

    /** 实际开工时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    private Date sjkgsj;

    /** 是否竣工 */
    private String sfjg;

    /** 实际竣工时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    private Date sjjgsj;

    /** 是否移交 */
    private String sfyj;

    /** 备注 */
    private String bz;

    /** 录入单位 */
    private String lrdw;

    /** 录入人 */
    private String lrr;

    /** 联系电话 */
    private String lxdh;

    // ------------------------------------------------------------------
    // 审计字段：本表列名是驼峰，必须显式指定
    // ------------------------------------------------------------------

    /** 删除状态（列名 delFlag，非 del_flag） */
    @TableField("delFlag")
    private String delFlag;

    /** 创建时间（列名 createTime，非 create_time） */
    @TableField("createTime")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 创建账号（列名 createAccount，非 create_account） */
    @TableField("createAccount")
    private String createAccount;
}
