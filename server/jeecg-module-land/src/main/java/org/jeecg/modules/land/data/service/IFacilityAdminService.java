package org.jeecg.modules.land.data.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.jeecg.modules.land.data.dto.FacilitySaveDTO;
import org.jeecg.modules.land.data.dto.LandAdminQueryDTO;
import org.jeecg.modules.land.data.entity.Facility;
import org.jeecg.modules.land.data.vo.FacilityProcessTreeVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * @Description: 数据管理 · 配套地块数据录入（1 宗地 N 配套 + 29 环节进度）Service
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p>对应方案 2.3.1（三）第 2 项「配套地块数据录入」。
 * 旧实现：{@code xjKjkfbSupportingFacilitiesController.createSpecial}
 * + {@code ProcessStatusController.createSpecial / getYjjssj /
 * findPrevIsFinsh / selectByIdAndPtssid}，前端 {@code addxjPublicFacilities.html}。
 *
 * <p><b>★★ 这一块最核心的两件事，旧系统都有问题，本实现逐条修正</b>：
 *
 * <p><b>1）阶段汇总口径的漏洞</b>。
 * 旧代码在「阶段下所有子事项」的四个分支（全不涉及/全未开启/有进行中/全已完成）
 * 全不成立时，会把阶段状态落到默认的「进行中」——
 * 而最容易触发这个漏洞的场景恰恰是<b>一个事项都还没录</b>：
 * 明明什么都没开始，阶段却显示「进行中」。
 * 本实现在 {@code ProcessStatus.rollUpStageStatus} 里显式处理空集 → 「未开启」。
 *
 * <p><b>2）预计结束时间的算错</b>。
 * 旧 {@code getYjjssj} 的递归算法有个明显缺陷：它在递归时把
 * {@code getEndDay(end, n)}（把「已顺延的终点」当成新的起点、把「非工作日个数」当成要加的天数）
 * 一路传下去，多个非工作日区间叠加时天数会滚雪球。
 * 本实现改为「逐日推进、跳过非工作日」的直白算法：
 * 从开始日往后推 N 个<b>工作日</b>，语义清晰、可单测、不会因区间个数而失真。
 *
 * <p><b>★ 环节进度与配套项目的关系</b>：{@code t_facility_process} 的唯一键是
 * {@code (pt_id, lc_id)}，所以「同一配套的同一环节填两次」在库层面就被挡住。
 * Service 用「先查后写」给出明确提示（是新增还是更新），而不是让用户撞唯一键异常。
 */
public interface IFacilityAdminService {

    // ==================================================================
    // 一、查询
    // ==================================================================

    /** 分页查询配套项目（按宗地编号 / 项目名称 / 类别 / 区划等条件） */
    IPage<Facility> queryPage(LandAdminQueryDTO query);

    /** 详情（含宗地信息与阶段进度汇总） */
    Facility queryDetail(String id);

    /**
     * 某宗地下的全部配套项目 + 各自的阶段进度汇总。
     *
     * <p>「1 宗地 N 配套」的入口查询：宗地详情页 / 环节录入页用它一次拿到全部配套。
     */
    List<Map<String, Object>> queryByLand(String crzdbh);

    // ==================================================================
    // 二、写入
    // ==================================================================

    /**
     * 新增配套项目。
     *
     * <p>沿用旧系统规则：必须挂到<b>已有宗地</b>（本实现比旧系统更严：
     * 旧系统只校验 {@code crzdbh} 非空，不校验宗地是否真的存在，于是会产生
     * 「孤儿配套」—— 设计文档实测有 60 行配套的 {@code crzdbh} 在宗地表里找不到）。
     *
     * @return 新配套项目主键
     */
    String createFacility(FacilitySaveDTO dto, HttpServletRequest request);

    /** 编辑配套项目（字段级留痕） */
    void updateFacility(FacilitySaveDTO dto, HttpServletRequest request);

    /** 移除（软删：{@code delFlag} 置 '1'） */
    void removeFacility(String id, String reason, HttpServletRequest request);

    /** 批量移除（软删） */
    Map<String, Object> removeFacilityBatch(List<String> ids, String reason, HttpServletRequest request);

    // ==================================================================
    // 三、29 环节进度
    // ==================================================================

    /**
     * 取某配套项目的阶段进度树（六大阶段 × 24 事项 + 汇总）。
     *
     * <p>未录入的环节也会出现在结果里（{@code filled=false}、{@code lcqk=未开启}）——
     * 录入界面必须显示全部 24 个事项，否则用户不知道还有哪些没填。
     */
    FacilityProcessTreeVO queryProcessTree(String ptId);

    /**
     * 保存一个环节的进度（有则更新、无则新增）。
     *
     * <p>保存后会自动重算所属阶段的汇总状态（旧系统也是这么做的，
     * 但它的「没有父阶段记录时才补建」分支写得很绕，本实现统一走「保存后重算」）。
     *
     * @param ptId      配套项目ID
     * @param lcId      环节ID
     * @param payload   进度字段（lcqk / lckssj / yjjssj / lcjssj / czwtlx / jtwt / gzjy / lrdw / lrr / lxdh）
     * @return 保存后的进度记录主键
     */
    String saveProcess(String ptId, String lcId, Map<String, Object> payload, HttpServletRequest request);

    /** 批量保存环节进度（整页保存场景：用户把一屏 24 个环节一次提交） */
    Map<String, Object> saveProcessBatch(String ptId, List<Map<String, Object>> items, HttpServletRequest request);

    /** 删除一个环节的进度（回到「未开启/未填报」） */
    void removeProcess(String ptId, String lcId, HttpServletRequest request);

    /** 取某配套项目的全部环节进度（扁平列表，用于导出与列表页展示） */
    List<Map<String, Object>> queryProcessList(String ptId);

    // ==================================================================
    // 四、校验与辅助
    // ==================================================================

    /**
     * 计算预计结束时间：从开始日往后推 N 个工作日。
     *
     * <p>工作日剔除 {@code non_working_day}（旧库 116 行节假日日历）。
     * 该表尚未迁入新库时退化为「自然日 + N」，并在返回结果里标注口径。
     *
     * @param startDate 开始日期（yyyy-MM-dd）
     * @param days      标准办理时长（工作日天数）
     * @return 含 {@code endDate} 与 {@code calendarBased}（是否退化为自然日）的结果
     */
    Map<String, Object> calcExpectEnd(String startDate, Integer days);

    /** 配套项目名称是否可用（同一宗地下不重复） */
    Map<String, Object> checkPtxmmc(String crzdbh, String ptxmmc, String excludeId);

    /** 全量环节配置（阶段 + 事项），供前端渲染录入界面骨架 */
    List<Map<String, Object>> queryProcessConfig();
}
