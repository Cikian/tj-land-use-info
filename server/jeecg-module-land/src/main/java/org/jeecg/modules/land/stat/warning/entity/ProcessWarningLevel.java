package org.jeecg.modules.land.stat.warning.entity;

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
 * @Description: 分级预警 · 预警等级阈值（逾期天数 → 蓝/黄/橙/红）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-11-02
 * @Version V1.0
 *
 * <p>表 {@code t_process_warning_level}，建表与种子脚本
 * {@code jeecg-module-land/src/main/resources/sql/stat/01_t_process_warning_level.sql}。
 * 数据来源：旧库 {@code nutzwk_ywk.process_waring_level}（4 行逐字平移，
 * 旧表名把 warning 拼成了 waring，新表名改正）。
 *
 * <p><b>为什么要有这张表</b>：旧系统里「逾期 25 天」和「逾期 3 天」除了天数不同，
 * 处置力度完全不同（督办层级、是否上会）。把「天数 → 等级」做成数据，
 * 中心的处置口径变化时改配置即可，不用发版；做成代码常量则每次调阈值都要重新部署。
 *
 * <p><b>★ 区间是闭区间</b>：[{@code minDays}, {@code maxDays}]，且从 1 起（逾期 0 天＝
 * 正好到期，不预警）。实测种子为
 * {@code blue 1-10 / yellow 11-20 / orange 21-30 / red 31-100}。
 *
 * <p><b>★ 上界 100 是软上限</b>：真实业务里逾期 120 天完全可能。
 * 匹配算法（{@code WarningLevelSupport#match}）对「超过最大上界」取最高等级并标记
 * overflow，**不返回 null** —— 逾期越久越不能没有预警。所以不要为了盖住 120 天
 * 把红色上界改成 9999：那会让「级别边界」失去业务含义。
 *
 * <p><b>★ 本实体读了就用、不在进程内缓存</b>：管理端「预警等级维护」改完阈值后
 * 必须立刻生效，缓存只会带来「刚改完还得等一会儿」的投诉。
 * 表只有 4 行，每次重算查一次的成本可以忽略。
 *
 * <p>{@code levelCode}（blue/yellow/orange/red）是**前端着色令牌**，
 * 不是给人看的文字：改 {@code levelName} 不改 {@code levelCode}，
 * 四色卡片与表格标签的颜色才不会跟着变。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@TableName("t_process_warning_level")
public class ProcessWarningLevel implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 预警等级ID（沿用旧库 process_waring_level.id）。
     *
     * <p>用 {@link IdType#ASSIGN_ID}：管理端新增等级时由 MP 生成 snowflake；
     * 已存在的行（含旧库平移来的 40 位 id）在 update 时原样保留，不会重写主键。
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 预警等级名称：蓝色预警 / 黄色预警 / 橙色预警 / 红色预警 */
    private String levelName;

    /** 预警等级别名（前端着色令牌）：blue / yellow / orange / red */
    private String levelCode;

    /** 区间下界（含）：逾期工作日天数 >= minDays */
    private Integer minDays;

    /** 区间上界（含）：逾期工作日天数 <= maxDays */
    private Integer maxDays;

    /** 排序号（升序＝由轻到重；列表、下拉、匹配都按它排） */
    private Integer sortNo;

    /** 备注：该等级的处置口径提示（旧系统没有，新加，便于把话术留在配置里） */
    private String remark;

    private String createBy;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    private String updateBy;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /**
     * 删除标识：0 正常 / 1 已删除。
     *
     * <p>加 {@code @TableLogic} 让实体查询自动带 {@code del_flag = 0}；
     * 但本域 Mapper 的手写 SQL <b>必须自己写 {@code del_flag = 0}</b>
     * （AGENTS.md §3.4：{@code @TableLogic} 对手写 SQL 不生效）。
     */
    @TableLogic
    private Integer delFlag;
}
