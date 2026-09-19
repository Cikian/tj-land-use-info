package org.jeecg.modules.land.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
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

/**
 * @Description: 经营性用地（出让宗地）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 *
 * <p>表 {@code t_land}，建表脚本
 * {@code jeecg-module-land/src/main/resources/sql/land/01_t_land.sql}。
 * 数据来源：旧库 {@code nutzwk_ywk.xj_kjkfb_commercial_land}（847 条有效宗地）。
 *
 * <p><b>为什么复制一份而不是跨库读</b>：
 * 设计文档 6.3.9（3）明确要求「不要跨库读」——配套项目表
 * {@code xj_kjkfb_supporting_facilities} 已经在 tj-jyxyd 里，
 * 宗地表也复制进来后，「宗地 1 : N 配套项目」的关联就能在同一个库里 JOIN，
 * 不需要给应用加 {@code @DS} 多数据源。
 *
 * <p><b>业务键是 {@code crzdbh}</b>（出让宗地编号），
 * 实测：1433 条配套里 1373 条能按 {@code crzdbh} 匹配到宗地（95.8%），
 * 按 {@code dkmc} 只有 84.9%（且宗地表 dkmc 有重名，会笛卡尔膨胀）。
 * 设计文档 3.0-11 明确要求：一律用 {@code crzdbh} 做关联键。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@TableName("t_land")
public class Land implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键（沿用旧库 xj_kjkfb_commercial_land.id） */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 出让宗地编号（业务主键） */
    private String crzdbh;

    /** 地块名称 */
    private String dkmc;

    /** 行政区划 */
    private String xzqh;

    /** 项目分类：市级项目 / 区级项目 */
    private String xmfl;

    /** 规划用地性质 */
    private String ghydxz;

    /** 出让金（亿元） */
    private BigDecimal crj;

    /** 出让时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date crsj;

    /** 可建设用地面积（平方米） */
    private BigDecimal kjsydmj;

    /** 总用地面积（平方米） */
    private BigDecimal zydmj;

    /** 建设面积（平方米） */
    private BigDecimal jsmj;

    /** 纳入成本的配套费（万元） */
    private BigDecimal nrcbdptf;

    /** 完成度 */
    private BigDecimal wcd;

    /** 配套是否齐全 */
    private String ptsfqq;

    /** 配套建设内容 */
    private String ptjsnr;

    /** 受让人 */
    private String srr;

    /** 合同约定交付时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date htydjfsj;

    /** 楼盘名称 */
    private String lpmc;

    /** 楼盘交付时间（或计划交付时间） */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date lpjfsj;

    /** 土地整理单位 */
    private String tdzldw;

    /** 土地整理计划下达文件号 */
    private String tdzljhxdwjh;

    /** 土地整理计划 */
    private String tdzljh;

    /** 东至 */
    private String dz;

    /** 西至 */
    private String xz;

    /** 南至 */
    private String nz;

    /** 北至 */
    private String bz;

    /** 配套情况函 */
    private String ptqkh;

    /** 配套筹备函 */
    private String ptcbh;

    /** 出让宗地图形数据（shp） */
    private String crzdtxsj;

    /**
     * 录入单位简称。
     * 旧字段名 {@code xzqh2}（注释写「行政区划2」）与实际内容（录入单位简称）不符，
     * 设计文档 3.0-9 记为数据质量问题；这里保留列名、修正注释，不再当作行政区划使用。
     */
    private String xzqh2;

    /** 资料缺失内容及说明 */
    private String zlqsnrsm;

    /** 录入单位 */
    private String lrdw;

    /** 录入人 */
    private String lrr;

    /** 联系电话 */
    private String lxdh;

    /** 备注 */
    private String beizhu;

    /** 旧库主键（迁移溯源，本期与 id 相同） */
    private String sourceId;

    /** 删除状态：0正常 1已删除 */
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
}
