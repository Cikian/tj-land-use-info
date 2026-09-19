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
}
