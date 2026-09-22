package org.jeecg.modules.land.supportingfacilities.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.land.supportingfacilities.entity.SupportingFacilities;
import org.jeecg.modules.land.supportingfacilities.vo.SupportingFacilitiesOptionVO;

import java.util.List;
import java.util.Map;

/**
 * @Description: 市政配套项目 Mapper
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 */
public interface SupportingFacilitiesMapper extends BaseMapper<SupportingFacilities> {

    /**
     * 配套项目下拉选项（按宗地联动 + 远程搜索）。
     *
     * @param crzdbh  出让宗地编号（必传——录入时先选宗地再联动本项目列表）
     * @param keyword 配套项目名称 模糊关键字，可空
     * @param limit   最大返回条数
     * @return 选项列表
     */
    List<SupportingFacilitiesOptionVO> selectOptions(@Param("crzdbh") String crzdbh,
                                         @Param("keyword") String keyword,
                                         @Param("limit") int limit);

    /**
     * 全局配套项目搜索（不限宗地，供跨宗地检索场景使用）。
     *
     * @param keyword 配套项目名称 / 出让宗地编号 模糊关键字，可空
     * @param limit   最大返回条数
     */
    List<SupportingFacilitiesOptionVO> searchOptions(@Param("keyword") String keyword,
                                         @Param("limit") int limit);

    /** 按配套项目名称取一条（旧数据迁移/兜底关联用） */
    SupportingFacilities selectByPtxmmc(@Param("ptxmmc") String ptxmmc);

    /** 该宗地下的配套项目数量 */
    long countByCrzdbh(@Param("crzdbh") String crzdbh);

    /** 首页配套总览。 */
    Map<String, Object> selectDashboardOverview();

    /** 首页各行政区待落实配套宗地排行。 */
    List<Map<String, Object>> selectDashboardRanks();

    /** 市级/区级项目预警。 */
    List<Map<String, Object>> selectWarningByProjectType(@Param("xmfl") String xmfl);

    /** 地块预警。 */
    List<Map<String, Object>> selectLandWarnings();
}
