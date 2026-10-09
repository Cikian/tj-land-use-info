package org.jeecg.modules.land.escalation.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.land.escalation.dto.EscalationQueryDTO;
import org.jeecg.modules.land.escalation.dto.EscalationSaveDTO;
import org.jeecg.modules.land.escalation.entity.EscalationProject;
import org.jeecg.modules.land.escalation.vo.EscalationLedgerVO;
import org.jeecg.modules.land.escalation.vo.EscalationStatVO;

import java.util.List;

/**
 * @Description: 提级论证项目主表 Service（方案 2.3.3 第 1/2/3/4 项）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-28
 * @Version: V1.0
 */
public interface IEscalationProjectService extends IService<EscalationProject> {

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /** 项目分页查询（录入列表 / 查询统计共用） */
    IPage<EscalationProject> queryPage(EscalationQueryDTO query);

    /** 项目详情（含材料列表与意见记录，均已补齐展示字段） */
    EscalationProject queryDetail(String id);

    /** 台账分页查询（14 列窄视图） */
    IPage<EscalationLedgerVO> queryLedgerPage(EscalationQueryDTO query);

    /** 台账顶部状态 tab 计数（固定 4 项，缺的状态补 0） */
    List<EscalationLedgerVO.StatusCount> countByStatus(EscalationQueryDTO query);

    // ------------------------------------------------------------------
    // 统计（5 张图，与列表共用同一套条件）
    // ------------------------------------------------------------------

    /**
     * 统计容器：一次给出总数与 5 个维度。
     *
     * <p>5 个单维度接口（byResult / byDept / byXzqh / byMonth / byType）
     * 在实现里都走这里，只是 Controller 各取一段返回 —— 口径只写一遍。
     */
    EscalationStatVO queryStat(EscalationQueryDTO query);

    // ------------------------------------------------------------------
    // 项目编号
    // ------------------------------------------------------------------

    /**
     * 生成项目编号（不落库，仅预览）。
     *
     * @param year 年度；为空时取当前年
     * @return 形如 TJ-2026-0001
     */
    String generateProjectNo(Integer year);

    /** 项目编号重复校验（编辑时用 excludeId 排除自身；唯一键是 project_no 单列） */
    void checkProjectNoUnique(String projectNo, String excludeId);

    // ------------------------------------------------------------------
    // 增删改
    // ------------------------------------------------------------------

    /**
     * 新增项目（含材料列表 + L1/L2 回写）。
     *
     * @return 新项目ID
     */
    String createProject(EscalationSaveDTO dto);

    /** 编辑项目（含材料三向合并 + L1/L2 回写；项目编号允许手工改写） */
    void updateProject(EscalationSaveDTO dto);

    /** 删除项目（逻辑删除，级联逻辑删除其材料） */
    void deleteProject(String id);

    /** 批量删除项目 */
    void deleteProjects(List<String> ids);

    // ------------------------------------------------------------------
    // 导出
    // ------------------------------------------------------------------

    /** 导出用：不分页取出全部命中项目（与列表同一套条件） */
    List<EscalationProject> queryForExport(EscalationQueryDTO query);

    /** 台账导出用：不分页取出全部命中台账行 */
    List<EscalationLedgerVO> queryLedgerForExport(EscalationQueryDTO query);
}
