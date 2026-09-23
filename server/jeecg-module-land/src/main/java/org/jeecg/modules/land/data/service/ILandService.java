package org.jeecg.modules.land.data.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.land.data.entity.Land;
import org.jeecg.modules.land.data.vo.LandOptionVO;
import org.jeecg.modules.land.data.vo.LandDashboardVO;

import java.util.List;
import java.util.Map;

/**
 * @Description: 经营性用地（宗地）Service
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 */
public interface ILandService extends IService<Land> {

    /** 宗地下拉选项（远程搜索） */
    List<LandOptionVO> queryOptions(String keyword, String xzqh, Integer limit);

    /** 按主键取宗地（含软删过滤） */
    Land queryById(String id);

    /** 按出让宗地编号取宗地 */
    Land queryByCrzdbh(String crzdbh);

    /** 行政区划下拉（从已有宗地数据里聚合，避免再单独维护一套字典） */
    List<Map<String, Object>> queryXzqhOptions();

    /** 首页宗地总览与行政区排行。 */
    LandDashboardVO queryDashboard();

    /**
     * 新增经营性用地。
     * 沿用旧系统规则：出让宗地编号必填且不可重复。
     */
    void createLand(Land land);

    /**
     * 编辑经营性用地。
     * 出让宗地编号仍必填，且不能改成其他宗地已占用的编号。
     */
    void updateLand(Land land);
}
