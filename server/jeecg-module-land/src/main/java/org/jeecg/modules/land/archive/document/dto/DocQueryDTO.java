package org.jeecg.modules.land.archive.document.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @Description: 收文 / 发文列表查询条件
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 */
@Data
public class DocQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 文号（登记号，模糊） */
    private String docNo;

    /** 文件标题（模糊） */
    private String docTitle;

    /** 文件类型（精确） */
    private String docType;

    /** 来文单位（收文用，模糊） */
    private String fromDept;

    /** 主送单位（发文用，模糊） */
    private String toDept;

    /** 来文字号（模糊） */
    private String fromDocNo;

    /** 状态（精确，收文用） */
    private String status;

    /** 密级（精确） */
    private String secretLevel;

    /** 紧急程度（精确） */
    private String urgency;

    /** 出让宗地编号（模糊） */
    private String crzdbh;

    /** 配套项目名称（模糊） */
    private String ptxmmc;

    /** 配套项目ID（精确） */
    private String facilityId;

    /** 当前处理人账号（精确，收文用） */
    private String currentHandler;

    /** 只查「我的待办」（当前登录人是 currentHandler 且未办结） */
    private Boolean onlyMine;

    /** 是否只查「已归档」/「未归档」：true 只查已归档，false 只查未归档，null 不限 */
    private Boolean archived;

    /** 日期起（含），对收文是 receive_date、对发文是 issue_date */
    private String beginDate;

    /** 日期止（含） */
    private String endDate;

    /** 页码 */
    private Integer pageNo;

    /** 每页条数 */
    private Integer pageSize;

    public int resolvePageNo() {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    public int resolvePageSize() {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 500);
    }
}
