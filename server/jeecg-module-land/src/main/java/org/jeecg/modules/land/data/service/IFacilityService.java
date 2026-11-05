package org.jeecg.modules.land.data.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.land.data.entity.Facility;
import org.jeecg.modules.land.data.vo.FacilityOptionVO;

import java.util.List;

/**
 * @Description: 市政配套项目 Service
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 */
public interface IFacilityService extends IService<Facility> {

    /** 指定宗地下的配套项目下拉（先选宗地 → 再选配套项目的第二步） */
    List<FacilityOptionVO> queryOptions(String crzdbh, String keyword, Integer limit);

    /** 跨宗地的配套项目搜索 */
    List<FacilityOptionVO> searchOptions(String keyword, Integer limit);

    /** 按主键取配套项目 */
    Facility queryById(String id);

    /** 按名称取配套项目（用于把历史数据里的项目名反查成外键） */
    Facility queryByPtxmmc(String ptxmmc);

    /** 该宗地下的配套项目数量 */
    long countByCrzdbh(String crzdbh);

    /**
     * 说明：首页配套统计已迁到统计域（{@code stat.home}），
     * 见 {@code IHomeStatService#queryPendingFacility()}。
     * 这里不再保留一份 —— data 域只负责数据管理，不承担看板聚合。
     */

    /**
     * 新增配套项目。
     * 沿用旧系统规则：必须挂到已有宗地，且配套项目名称不可重复。
     */
    void createFacility(Facility facility);

    /**
     * 编辑配套项目。
     * 出让宗地编号、配套项目名称必填，名称不能改成其他记录已占用的名称。
     */
    void updateFacility(Facility facility);

    /**
     * 说明：首页预警行的「查看」明细已迁到统计域，
     * 见 {@code IHomeStatService#queryWarningLands(String, String)} /
     * {@code GET /land/stat/home/warningDetail}。
     * 旧实现在这里按 {@code l.xzqh} 过滤，而区级预警的分组列是 {@code xzqh2}，
     * 区级点「查看」必然空表 —— 迁走后与预警行共用同一套分组列与比率口径。
     */
}
