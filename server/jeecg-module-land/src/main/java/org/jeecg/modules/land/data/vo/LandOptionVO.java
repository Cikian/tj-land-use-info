package org.jeecg.modules.land.data.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * @Description: 宗地下拉选项（供档案 / 收发文录入时的「先选宗地」步骤使用）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 */
@Data
public class LandOptionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 宗地ID */
    private String id;

    /** 出让宗地编号 */
    private String crzdbh;

    /** 地块名称 */
    private String dkmc;

    /** 行政区划 */
    private String xzqh;

    /** 项目分类：市级项目 / 区级项目 */
    private String xmfl;

    /** 该宗地下的配套项目数量（下拉里显示「N 个配套项目」，便于判断能否继续选） */
    private Integer facilityCount;
}
