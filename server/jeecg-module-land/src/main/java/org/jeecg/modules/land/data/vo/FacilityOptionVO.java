package org.jeecg.modules.land.data.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * @Description: 配套项目下拉选项（供档案 / 收发文录入时的「再选配套项目」步骤使用）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 */
@Data
public class FacilityOptionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 配套项目ID */
    private String id;

    /** 配套项目名称 */
    private String ptxmmc;

    /** 出让宗地编号 */
    private String crzdbh;

    /** 地块名称 */
    private String dkmc;

    /** 配套设施类别 */
    private String ptsslb;

    /** 行政区划 */
    private String xzqh;

    /** 项目分类 */
    private String xmfl;

    /** 建设单位 */
    private String jsdw;
}
