package org.jeecg.modules.land.supportingfacilities.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.land.supportingfacilities.entity.SupportingFacilities;
import org.jeecg.modules.land.supportingfacilities.vo.SupportingFacilitiesOptionVO;
import org.jeecg.modules.land.supportingfacilities.vo.SupportingFacilitiesDashboardVO;

import java.util.List;

/**
 * @Description: 市政配套项目 Service
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 */
public interface ISupportingFacilitiesService extends IService<SupportingFacilities> {

    /** 指定宗地下的配套项目下拉（先选宗地 → 再选配套项目的第二步） */
    List<SupportingFacilitiesOptionVO> queryOptions(String crzdbh, String keyword, Integer limit);

    /** 跨宗地的配套项目搜索 */
    List<SupportingFacilitiesOptionVO> searchOptions(String keyword, Integer limit);

    /** 按主键取配套项目 */
    SupportingFacilities queryById(String id);

    /** 按名称取配套项目（用于把历史数据里的项目名反查成外键） */
    SupportingFacilities queryByPtxmmc(String ptxmmc);

    /** 该宗地下的配套项目数量 */
    long countByCrzdbh(String crzdbh);

    /** 首页配套统计、排行与预警。 */
    SupportingFacilitiesDashboardVO queryDashboard();
}
