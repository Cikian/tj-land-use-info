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

    /**
     * 跨宗地的配套项目搜索（原口径，两个新参数都取默认值）。
     *
     * <p>保留这个两参重载是为了「现有调用方零改动」：档案查询条件、旧页面都在用它，
     * 多出来的能力（类别过滤 / 道路优先）必须是通过新增参数获得，
     * 不能让老调用方跟着改一遍。
     */
    List<FacilityOptionVO> searchOptions(String keyword, Integer limit);

    /**
     * 跨宗地的配套项目搜索（P1-10：支持「配套类别过滤」与「道路类优先」）。
     *
     * <p><b>为什么需要这两个参数</b>：1433 条配套里道路只占一小部分，按
     * {@code crzdbh, ptxmmc} 排序取前 50 条时常常一条道路都没有，
     * 前端只能对「已取回的 50 条」本地排序 —— 候选集本身就是错的，
     * 于是「打开就有道路类候选」永远不成立。要解决必须在 SQL 的 LIMIT 之前就把道路挑出来。
     *
     * @param keyword   配套项目名称 / 出让宗地编号模糊，可空
     * @param limit     返回条数上限
     * @param category  配套类别（{@code ptsslb}）模糊匹配，可空；空 = 不过滤
     * @param roadFirst 是否把道路类排到最前；null / false = 保持原有排序
     */
    List<FacilityOptionVO> searchOptions(String keyword, Integer limit, String category, Boolean roadFirst);

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
