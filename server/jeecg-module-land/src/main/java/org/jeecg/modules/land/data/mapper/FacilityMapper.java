package org.jeecg.modules.land.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.land.data.entity.Facility;
import org.jeecg.modules.land.data.vo.FacilityOptionVO;

import java.util.List;

/**
 * @Description: 市政配套项目 Mapper
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 */
public interface FacilityMapper extends BaseMapper<Facility> {

    /**
     * 配套项目下拉选项（按宗地联动 + 远程搜索）。
     *
     * @param crzdbh  出让宗地编号（必传——录入时先选宗地再联动本项目列表）
     * @param keyword 配套项目名称 模糊关键字，可空
     * @param limit   最大返回条数
     * @return 选项列表
     */
    List<FacilityOptionVO> selectOptions(@Param("crzdbh") String crzdbh,
                                         @Param("keyword") String keyword,
                                         @Param("limit") int limit);

    /**
     * 全局配套项目搜索（不限宗地，供跨宗地检索场景使用）。
     *
     * @param keyword 配套项目名称 / 出让宗地编号 模糊关键字，可空
     * @param limit   最大返回条数
     */
    List<FacilityOptionVO> searchOptions(@Param("keyword") String keyword,
                                         @Param("limit") int limit);

    /** 按配套项目名称取一条（旧数据迁移/兜底关联用） */
    Facility selectByPtxmmc(@Param("ptxmmc") String ptxmmc);

    /** 该宗地下的配套项目数量 */
    long countByCrzdbh(@Param("crzdbh") String crzdbh);
}
