package org.jeecg.modules.land.data.attachment.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * @Description: 数据管理统一附件 - 列表查询条件
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p>与 {@code LandAdminQueryDTO} 同一取舍：<b>不用 jeecg 的
 * {@code QueryGenerator.initQueryWrapper}</b> —— 后者从 request 参数自动拼条件，
 * 前端传一个实体上没有的字段名会被当成合法列名拼进 SQL。
 * 附件列表的条件就这几个，显式声明换来「可读、可控、可测」。
 *
 * <p><b>★ 日期为什么用 String 而不是 Date</b>：前端传的是 {@code yyyy-MM-dd}
 * （日期选择器给的那一天），而 {@code upload_time} 是 {@code datetime}。
 * 用 String 可以在服务层明确地拼成「当天 00:00:00 ~ 当天 23:59:59」——
 * 用 Date 反序列化会把 {@code 2026-10-08} 变成 00:00:00，
 * 直接用 {@code <=} 比较会漏掉当天上传的所有文件（用户会觉得「筛今天没有数据」）。
 */
@Data
public class AttachmentQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务类型（精确：land / facility / process；空 = 全部） */
    private String bizType;

    /** 业务主键（精确：某一个宗地 / 配套项目 / 环节进度） */
    private String bizId;

    /** 业务可读键（模糊：宗地编号 / 配套项目名称） */
    private String bizKey;

    /** 附件类型码（精确：01~13、99，见字典 land_attach_type） */
    private String fileType;

    /** 文件名关键字（模糊） */
    private String keyword;

    /** 上传人账号（精确） */
    private String uploadBy;

    /** 上传时间起（yyyy-MM-dd，含当天 00:00:00） */
    private String beginDate;

    /** 上传时间止（yyyy-MM-dd，含当天 23:59:59） */
    private String endDate;

    /** 页码（从 1 开始） */
    private Integer pageNo = 1;

    /** 每页条数 */
    private Integer pageSize = 10;
}
