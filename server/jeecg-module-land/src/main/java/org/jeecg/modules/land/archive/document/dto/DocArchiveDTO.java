package org.jeecg.modules.land.archive.document.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @Description: 收发文归档入参
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 *
 * <p>需求约定：办结时先<b>提示用户是否归档</b>，若选择归档则<b>必须选择档案类别</b>
 * （{@link #categoryId} 由服务端校验非空且必须是叶子类别）。
 * 公文的所有附件会作为该档案的卷内文件，统一挂到这个类别下。
 */
@Data
public class DocArchiveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 收文 / 发文ID */
    private String docId;

    /** 档案类别ID（必填，且必须是叶子类别） */
    private String categoryId;

    /** 档案名称；为空时默认取公文标题 */
    private String archiveName;

    /** 归档日期，格式 yyyy-MM-dd；为空时取今天 */
    private String archiveDate;

    /** 密级；为空时取公文密级 */
    private String secretLevel;
}
