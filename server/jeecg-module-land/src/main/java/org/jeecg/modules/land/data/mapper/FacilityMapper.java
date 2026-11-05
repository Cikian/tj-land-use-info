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

    /**
     * 数据版本：全表 MAX(updateTime)。
     *
     * <p>配套写路径（导入 / 编辑 / 移除 / 恢复）都会刷新该行的 updateTime，
     * 铺图概览接口拿它当「版本号」：前端版本与它一致 = 图形没变，直接回
     * 「无变化」，不再传概览数据。不过滤 delFlag——移除/恢复也要推高版本。
     */
    java.util.Date selectMaxUpdateTime();

    /**
     * 批量更新配套地块图形（配套 GeoJSON 导入专用）：一次往返写一批
     * （调用方分批，每批 ≤50 条；依赖 JDBC URL 的 allowMultiQueries=true）。
     */
    void batchUpdateGeo(@Param("list") java.util.List<org.jeecg.modules.land.data.entity.Facility> list);
    // 说明：selectWarningDetails 已删除 —— 首页预警「查看」明细迁到
    // stat/home 的 selectWarningLands（与预警行共用同一套分组列与比率口径）。
}
