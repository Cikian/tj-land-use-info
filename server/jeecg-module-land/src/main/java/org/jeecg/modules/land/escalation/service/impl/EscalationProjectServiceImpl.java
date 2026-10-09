package org.jeecg.modules.land.escalation.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.escalation.dto.EscalationQueryDTO;
import org.jeecg.modules.land.escalation.dto.EscalationSaveDTO;
import org.jeecg.modules.land.escalation.entity.EscalationProject;
import org.jeecg.modules.land.escalation.enums.EscalationStatus;
import org.jeecg.modules.land.escalation.mapper.EscalationProjectMapper;
import org.jeecg.modules.land.escalation.mapper.EscalationRecordMapper;
import org.jeecg.modules.land.escalation.mapper.FacilityWritebackMapper;
import org.jeecg.modules.land.escalation.service.IEscalationMaterialService;
import org.jeecg.modules.land.escalation.service.IEscalationProjectService;
import org.jeecg.modules.land.escalation.support.EscalationAuditSupport;
import org.jeecg.modules.land.escalation.vo.EscalationLedgerVO;
import org.jeecg.modules.land.escalation.vo.EscalationStatVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 提级论证项目主表 Service 实现
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-28
 * @Version: V1.0
 *
 * <p>本类是模块的「业务总闸」，负责四件事：
 * <ol>
 *   <li><b>录入与编辑</b>：校验取值合法性（不做流转判定）+ 项目编号唯一 + 材料三向合并；</li>
 *   <li><b>冗余计数</b>：材料数由材料 Service 维护，意见数由意见 Service 维护，这里只保证口径一致；</li>
 *   <li><b>回写旧表</b>（L1/L2）：见 {@link #writebackFacility}，受配置开关控制、失败只 warn；</li>
 *   <li><b>查询与统计</b>：列表 / 台账 / 5 张统计图共用同一套条件。</li>
 * </ol>
 *
 * <p><b>★ 无状态机</b>：{@code status} 与 {@code arg_result} 由前端传入，
 * 这里只校验「值在允许集合内」（设计文档 4.3 第 1 条）。
 */
@Slf4j
@Service
public class EscalationProjectServiceImpl extends ServiceImpl<EscalationProjectMapper, EscalationProject>
        implements IEscalationProjectService {

    /** 项目编号前缀，完整格式 TJ-{yyyy}-{4位流水} */
    private static final String PROJECT_NO_PREFIX = "TJ-";

    /** 项目编号生成的最大重试次数（并发下唯一键冲突时重试） */
    private static final int NO_RETRY = 3;

    /** 回写旧表时写入的值：字典 yn 的中文值（与档案模块一致，不用 0/1） */
    private static final String YES = "是";

    /** L2 的触发集合：论证结果 ∈ {通过, 基本通过} */
    private static final List<String> PASS_RESULTS = Collections.unmodifiableList(
            Arrays.asList(EscalationProject.ARG_RESULT_PASS, EscalationProject.ARG_RESULT_BASIC_PASS));

    /**
     * L1/L2 回写开关。
     *
     * <p>默认 true；配置项缺失、占位符解析失败等任何异常都按 true 处理
     * （默认值写在 SpEL 里，所以即便 {@code land.escalation.writeback-enabled} 没配也不会启动失败）。
     */
    @Value("${land.escalation.writeback-enabled:true}")
    private boolean writebackEnabled;

    @Autowired
    private EscalationAuditSupport auditSupport;

    @Autowired
    private IEscalationMaterialService materialService;

    /** 意见记录数需要重算：直接注入 Mapper，不走 Service，避免 Service 之间成环 */
    @Autowired
    private EscalationRecordMapper recordMapper;

    /** ★ 窄接口：只更新配套表的两个字段 */
    @Autowired
    private FacilityWritebackMapper facilityWritebackMapper;

    // ==================================================================
    // 查询
    // ==================================================================

    @Override
    public IPage<EscalationProject> queryPage(EscalationQueryDTO query) {
        EscalationQueryDTO condition = query == null ? new EscalationQueryDTO() : query;
        Page<EscalationProject> page = new Page<>(condition.resolvePageNo(), condition.resolvePageSize());
        return baseMapper.selectProjectPage(page, condition);
    }

    @Override
    public EscalationProject queryDetail(String id) {
        if (StringUtils.isBlank(id)) {
            return null;
        }
        EscalationProject project = baseMapper.selectDetailById(id);
        if (project == null) {
            return null;
        }
        project.setMaterials(materialService.queryByProjectId(id));
        project.setRecords(recordMapper.selectByProjectId(id));
        return project;
    }

    @Override
    public IPage<EscalationLedgerVO> queryLedgerPage(EscalationQueryDTO query) {
        EscalationQueryDTO condition = query == null ? new EscalationQueryDTO() : query;
        Page<EscalationLedgerVO> page = new Page<>(condition.resolvePageNo(), condition.resolvePageSize());
        IPage<EscalationLedgerVO> result = baseMapper.selectLedgerPage(page, condition);
        // 序号在 Service 侧赋值：SQL 里用用户变量会被分页插件的 COUNT 优化搅乱，
        // MySQL 5.7 又没有 ROW_NUMBER()（见 EscalationProjectMapper.xml 的说明）
        fillSeq(result == null ? null : result.getRecords(),
                (condition.resolvePageNo() - 1) * condition.resolvePageSize());
        return result;
    }

    @Override
    public List<EscalationLedgerVO.StatusCount> countByStatus(EscalationQueryDTO query) {
        EscalationQueryDTO condition = query == null ? new EscalationQueryDTO() : query;
        // 状态条件不能参与「各状态计数」的过滤，否则切换 tab 后其它 tab 的角标会全变 0。
        // 这里清空的是本次请求新建的 DTO（GET 参数绑定对象），不会影响别处
        condition.setStatus(null);
        Map<String, Long> counted = new LinkedHashMap<>();
        for (EscalationLedgerVO.StatusCount row : baseMapper.selectCountGroupByStatus(condition)) {
            if (row != null && StringUtils.isNotBlank(row.getStatus())) {
                counted.put(row.getStatus().trim(), row.getCount());
            }
        }
        // 固定返回 4 项：数量为 0 的状态也要出现在 tab 上，前端不必兜底
        List<EscalationLedgerVO.StatusCount> result = new ArrayList<>(EscalationStatus.values().length);
        for (String status : EscalationStatus.allValues()) {
            Long count = counted.get(status);
            result.add(new EscalationLedgerVO.StatusCount(status, count == null ? 0L : count));
        }
        return result;
    }

    // ==================================================================
    // 统计
    // ==================================================================

    @Override
    public EscalationStatVO queryStat(EscalationQueryDTO query) {
        EscalationQueryDTO condition = query == null ? new EscalationQueryDTO() : query;
        EscalationStatVO stat = new EscalationStatVO();
        stat.setTotal(baseMapper.countByQuery(condition));
        stat.setByResult(baseMapper.selectStatByArgResult(condition));
        stat.setByDept(baseMapper.selectStatByDeclareDept(condition));
        stat.setByXzqh(baseMapper.selectStatByXzqh(condition));
        stat.setByMonth(baseMapper.selectStatByMonth(condition));
        stat.setByType(baseMapper.selectStatByProjectType(condition));
        return stat;
    }

    // ==================================================================
    // 项目编号
    // ==================================================================

    @Override
    public String generateProjectNo(Integer year) {
        int targetYear = year == null || year < 1900 || year > 2999
                ? Calendar.getInstance().get(Calendar.YEAR)
                : year;
        return nextProjectNo(targetYear);
    }

    @Override
    public void checkProjectNoUnique(String projectNo, String excludeId) {
        if (StringUtils.isBlank(projectNo)) {
            throw new JeecgBootException("项目编号不能为空");
        }
        if (baseMapper.countByProjectNo(projectNo.trim(), excludeId) > 0) {
            throw new JeecgBootException("项目编号「" + projectNo.trim() + "」已存在，请更换");
        }
    }

    /** 按年度流水生成下一个项目编号 */
    private String nextProjectNo(int year) {
        String prefix = PROJECT_NO_PREFIX + year + "-";
        Integer max = baseMapper.selectMaxSeqOfYear(prefix);
        int next = (max == null ? 0 : max) + 1;
        return prefix + String.format("%04d", next);
    }

    // ==================================================================
    // 新增
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createProject(EscalationSaveDTO dto) {
        if (dto == null) {
            throw new JeecgBootException("缺少提级论证项目数据");
        }
        checkRequired(dto);
        checkArgResult(dto.getArgResult());
        checkStatus(dto.getStatus());

        EscalationProject project = new EscalationProject();
        applyDto(project, dto);
        if (StringUtils.isBlank(project.getStatus())) {
            project.setStatus(EscalationStatus.defaultValue());
        }
        fillDefaults(project);

        // 项目编号：手工填了就校验唯一；没填就自动生成（并发冲突时重试）
        JeecgBootException lastError = null;
        for (int attempt = 0; attempt < NO_RETRY; attempt++) {
            String projectNo = StringUtils.isNotBlank(dto.getProjectNo())
                    ? dto.getProjectNo().trim()
                    : nextProjectNo(resolveYear(project));
            checkProjectNoUnique(projectNo, null);
            project.setProjectNo(projectNo).setId(IdWorker.getIdStr());
            if (save(project)) {
                lastError = null;
                break;
            }
            lastError = new JeecgBootException("提级论证项目保存失败，请重试");
        }
        if (lastError != null) {
            throw lastError;
        }

        if (dto.getMaterials() != null && !dto.getMaterials().isEmpty()) {
            materialService.syncMaterials(project.getId(), dto.getMaterials());
        }
        writebackFacility(project);
        log.info(String.format("新增提级论证项目成功：id=%s, projectNo=%s, 材料数=%s, 回写开关=%s",
                project.getId(), project.getProjectNo(),
                dto.getMaterials() == null ? 0 : dto.getMaterials().size(), writebackEnabled));
        return project.getId();
    }

    // ==================================================================
    // 编辑
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProject(EscalationSaveDTO dto) {
        if (dto == null || StringUtils.isBlank(dto.getId())) {
            throw new JeecgBootException("缺少提级论证项目ID");
        }
        EscalationProject old = getById(dto.getId());
        if (old == null) {
            throw new JeecgBootException("提级论证项目不存在或已被删除");
        }
        if (StringUtils.isNotBlank(dto.getProjectName())) {
            checkArgResult(dto.getArgResult());
            checkStatus(dto.getStatus());
        }
        // 项目编号允许手工改写：传了就校验唯一（排除自身），没传就沿用旧值
        if (StringUtils.isNotBlank(dto.getProjectNo())) {
            checkProjectNoUnique(dto.getProjectNo(), old.getId());
        }

        // 只更新 DTO 里出现的字段：避免前端漏传把已有数据清空（照档案模块的 updateArchive 写法）
        EscalationProject update = new EscalationProject().setId(old.getId());
        update.setProjectName(trimToNull(dto.getProjectName()));
        update.setProjectNo(StringUtils.isNotBlank(dto.getProjectNo())
                ? dto.getProjectNo().trim() : old.getProjectNo());
        update.setDeclareDept(trimToNull(dto.getDeclareDept()));
        update.setProjectType(trimToNull(dto.getProjectType()));
        update.setProjectScale(trimToNull(dto.getProjectScale()));
        update.setTotalInvestment(dto.getTotalInvestment());
        update.setBuildLocation(trimToNull(dto.getBuildLocation()));
        update.setDeclareDate(dto.getDeclareDate());
        update.setProjectSummary(trimToNull(dto.getProjectSummary()));

        update.setXzqh(trimToNull(dto.getXzqh()));
        update.setGnq(trimToNull(dto.getGnq()));
        update.setDkmc(trimToNull(dto.getDkmc()));
        update.setDkArea(dto.getDkArea());
        update.setGhydxz(trimToNull(dto.getGhydxz()));
        update.setTdzlProject(dto.getTdzlProject() == null ? old.getTdzlProject() : dto.getTdzlProject());

        update.setFacilityId(trimToNull(dto.getFacilityId()));
        update.setPtxmmc(trimToNull(dto.getPtxmmc()));
        update.setCrzdbh(trimToNull(dto.getCrzdbh()));

        update.setArgReason(trimToNull(dto.getArgReason()));
        update.setArgBasis(trimToNull(dto.getArgBasis()));
        update.setArgNecessity(trimToNull(dto.getArgNecessity()));
        update.setArgFeasibility(trimToNull(dto.getArgFeasibility()));
        update.setArgContent(trimToNull(dto.getArgContent()));

        update.setArgResult(trimToNull(dto.getArgResult()));
        update.setArgConclusion(trimToNull(dto.getArgConclusion()));
        update.setArgOrg(trimToNull(dto.getArgOrg()));
        update.setArgMeetingDate(dto.getArgMeetingDate());
        update.setArgExpertList(trimToNull(dto.getArgExpertList()));
        update.setArgDate(dto.getArgDate());

        update.setStatus(StringUtils.isNotBlank(dto.getStatus()) ? dto.getStatus().trim() : old.getStatus());
        update.setRemark(trimToNull(dto.getRemark()));
        update.setUpdateBy(auditSupport.currentUsername());
        update.setUpdateTime(new Date());

        updateById(update);

        // 材料三向合并（前端提交 null 视为「本次不动材料」，空数组才是「清空」）
        if (dto.getMaterials() != null) {
            materialService.syncMaterials(old.getId(), dto.getMaterials());
        }

        // 回写判断要用「合并后的最终值」，所以重新读一次而不是用 update 里的字段
        EscalationProject latest = getById(old.getId());
        writebackFacility(latest == null ? update : latest);
        log.info("编辑提级论证项目成功：id={}, projectNo={}", old.getId(), update.getProjectNo());
    }

    // ==================================================================
    // 删除
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProject(String id) {
        if (StringUtils.isBlank(id)) {
            throw new JeecgBootException("缺少提级论证项目ID");
        }
        EscalationProject project = getById(id);
        if (project == null) {
            throw new JeecgBootException("提级论证项目不存在或已被删除");
        }
        // 材料随项目一起逻辑删除；意见记录保留（append-only 的可追溯底线：
        // 项目被删时意见不物理消失，必要时可由 DBA 从 t_escalation_record 还原）
        // syncMaterials(id, 空表) = 清空该项目的材料；传 null 才是「不动材料」
        materialService.syncMaterials(id, new ArrayList<>());
        removeById(id);
        log.info("删除提级论证项目成功：id={}, projectNo={}", id, project.getProjectNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProjects(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (String id : ids) {
            if (StringUtils.isNotBlank(id)) {
                deleteProject(id.trim());
            }
        }
    }

    // ==================================================================
    // 导出取数
    // ==================================================================

    @Override
    public List<EscalationProject> queryForExport(EscalationQueryDTO query) {
        return baseMapper.selectForExport(query == null ? new EscalationQueryDTO() : query);
    }

    @Override
    public List<EscalationLedgerVO> queryLedgerForExport(EscalationQueryDTO query) {
        List<EscalationLedgerVO> rows = baseMapper.selectLedgerForExport(
                query == null ? new EscalationQueryDTO() : query);
        fillSeq(rows, 0);
        return rows;
    }

    /** 给台账行补「序号」：offset = 前面已经翻过的行数，导出时为 0 */
    private static void fillSeq(List<EscalationLedgerVO> rows, int offset) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        int seq = offset + 1;
        for (EscalationLedgerVO row : rows) {
            if (row == null) {
                continue;
            }
            row.setSeq(seq++);
        }
    }

    // ==================================================================
    // ★ 回写旧表（L1 / L2）
    // ==================================================================

    /**
     * 回写配套项目表的两个字段（设计文档 2.4）。
     *
     * <p>三条硬约束，缺一不可：
     * <ol>
     *   <li><b>先查当前值，相同就不写</b> —— 避免无意义 UPDATE 触发旧系统的审计/同步逻辑；</li>
     *   <li><b>只 UPDATE 两列</b>（走 {@link FacilityWritebackMapper} 窄接口），不碰旧表其它字段；</li>
     *   <li><b>任何异常只记 warn</b> —— 旧表结构变化、权限不足、表不存在都不能影响本模块的保存。</li>
     * </ol>
     * 另外受配置 {@code land.escalation.writeback-enabled} 控制（默认开），
     * 中心若认为「新系统只做记录、不要动旧表」，把它关掉即可，不影响其它任何功能。
     */
    private void writebackFacility(EscalationProject project) {
        if (!writebackEnabled) {
            log.debug("回写开关已关闭（land.escalation.writeback-enabled=false），跳过回写");
            return;
        }
        if (project == null || StringUtils.isBlank(project.getFacilityId())) {
            return;
        }
        String facilityId = project.getFacilityId().trim();

        // L1：关联了配套项目 → 是否涉及提级论证 = 是
        // L2：论证结果 ∈ {通过, 基本通过} → 提级论证是否通过 = 是
        String sfzsjtjlz = YES;
        String tjlzsftg = PASS_RESULTS.contains(trimToNull(project.getArgResult())) ? YES : null;
        try {
            Map<String, Object> state = facilityWritebackMapper.selectWritebackState(facilityId);
            if (state == null) {
                log.warn("回写配套项目失败：配套项目不存在或已删除，facilityId={}", facilityId);
                return;
            }
            if (sameValue(state.get("sfzsjtjlz"), sfzsjtjlz)) {
                sfzsjtjlz = null;
            }
            if (sameValue(state.get("tjlzsftg"), tjlzsftg)) {
                tjlzsftg = null;
            }
            if (sfzsjtjlz == null && tjlzsftg == null) {
                log.debug("回写配套项目：两列当前值已满足，无需写入，facilityId={}", facilityId);
                return;
            }
            int rows = facilityWritebackMapper.updateWritebackFields(facilityId, sfzsjtjlz, tjlzsftg);
            log.info(String.format("回写配套项目成功：facilityId=%s, sfzsjtjlz=%s, tjlzsftg=%s, 影响行数=%s, 操作人=%s",
                    facilityId, sfzsjtjlz, tjlzsftg, rows, auditSupport.currentUsername()));
        } catch (Exception e) {
            // ★ 只 warn：旧表写失败绝不影响主业务的保存结果
            log.warn("回写配套项目失败（不影响主业务流程）：facilityId={}, 原因={}", facilityId, e.getMessage());
        }
    }

    /** 当前值是否已等于目标值（忽略首尾空白；目标值为空表示「本次不写这一列」） */
    private static boolean sameValue(Object current, String target) {
        if (target == null) {
            return true;
        }
        return current != null && target.equals(String.valueOf(current).trim());
    }

    // ==================================================================
    // 校验与字段搬运
    // ==================================================================

    /**
     * 必填校验（设计文档 5.4 里标 ★ 的字段）。
     *
     * <p>{@code project_name} 是 DDL 里的 NOT NULL，缺了必然抛 SQL 异常 ——
     * 这里提前拦住，把「数据库约束报错」变成「一句看得懂的业务提示」；
     * 其余 5 个 ★ 字段 DDL 允许为空，但录入向导标了必填，后端保持一致口径。
     * <b>只校验新增</b>：编辑走逐字段覆盖，前端漏传不该被当成「清空后必填失败」。
     */
    private void checkRequired(EscalationSaveDTO dto) {
        if (StringUtils.isBlank(dto.getProjectName())) {
            throw new JeecgBootException("项目名称不能为空");
        }
        if (StringUtils.isBlank(dto.getDeclareDept())) {
            throw new JeecgBootException("申报单位不能为空");
        }
        if (dto.getTotalInvestment() == null) {
            throw new JeecgBootException("总投资不能为空");
        }
        if (StringUtils.isBlank(dto.getBuildLocation())) {
            throw new JeecgBootException("建设地点不能为空");
        }
        if (dto.getDeclareDate() == null) {
            throw new JeecgBootException("申报时间不能为空");
        }
        if (StringUtils.isBlank(dto.getProjectSummary())) {
            throw new JeecgBootException("项目概述不能为空");
        }
    }

    /** 论证结果必须是字典里的 5 个取值之一（字典给前端用，后端仍要自己把关） */
    private void checkArgResult(String argResult) {
        String value = trimToNull(argResult);
        if (value == null) {
            return;
        }
        if (!EscalationProject.ARG_RESULT_VALUES.contains(value)) {
            throw new JeecgBootException("论证结果「" + value + "」不合法，允许值："
                    + String.join("/", EscalationProject.ARG_RESULT_VALUES));
        }
    }

    /** 办理状态必须是固定 4 值之一（无状态机，只校验取值） */
    private void checkStatus(String status) {
        String value = trimToNull(status);
        if (value == null) {
            return;
        }
        if (!EscalationStatus.isValid(value)) {
            throw new JeecgBootException("办理状态「" + value + "」不合法，允许值："
                    + String.join("/", EscalationStatus.allValues()));
        }
    }

    /** DTO → 实体（新增用；编辑走逐字段覆盖，见 {@link #updateProject}） */
    private void applyDto(EscalationProject project, EscalationSaveDTO dto) {
        project.setProjectName(trimToNull(dto.getProjectName()));
        project.setDeclareDept(trimToNull(dto.getDeclareDept()));
        project.setProjectType(trimToNull(dto.getProjectType()));
        project.setProjectScale(trimToNull(dto.getProjectScale()));
        project.setTotalInvestment(dto.getTotalInvestment());
        project.setBuildLocation(trimToNull(dto.getBuildLocation()));
        project.setDeclareDate(dto.getDeclareDate());
        project.setProjectSummary(trimToNull(dto.getProjectSummary()));

        project.setXzqh(trimToNull(dto.getXzqh()));
        project.setGnq(trimToNull(dto.getGnq()));
        project.setDkmc(trimToNull(dto.getDkmc()));
        project.setDkArea(dto.getDkArea());
        project.setGhydxz(trimToNull(dto.getGhydxz()));
        project.setTdzlProject(dto.getTdzlProject());

        project.setFacilityId(trimToNull(dto.getFacilityId()));
        project.setPtxmmc(trimToNull(dto.getPtxmmc()));
        project.setCrzdbh(trimToNull(dto.getCrzdbh()));

        project.setArgReason(trimToNull(dto.getArgReason()));
        project.setArgBasis(trimToNull(dto.getArgBasis()));
        project.setArgNecessity(trimToNull(dto.getArgNecessity()));
        project.setArgFeasibility(trimToNull(dto.getArgFeasibility()));
        project.setArgContent(trimToNull(dto.getArgContent()));

        project.setArgResult(trimToNull(dto.getArgResult()));
        project.setArgConclusion(trimToNull(dto.getArgConclusion()));
        project.setArgOrg(trimToNull(dto.getArgOrg()));
        project.setArgMeetingDate(dto.getArgMeetingDate());
        project.setArgExpertList(trimToNull(dto.getArgExpertList()));
        project.setArgDate(dto.getArgDate());

        project.setStatus(trimToNull(dto.getStatus()));
        project.setRemark(trimToNull(dto.getRemark()));
    }

    /** 新增时的默认值（对齐 DDL 的 DEFAULT，避免 NOT NULL 列被写成 null） */
    private void fillDefaults(EscalationProject project) {
        if (StringUtils.isBlank(project.getStatus())) {
            project.setStatus(EscalationStatus.defaultValue());
        }
        if (project.getTdzlProject() == null) {
            project.setTdzlProject(0);
        }
        if (project.getMaterialCount() == null) {
            project.setMaterialCount(0);
        }
        if (project.getRecordCount() == null) {
            project.setRecordCount(0);
        }
        project.setDelFlag(0);
        project.setCreateBy(auditSupport.currentUsername());
        project.setCreateTime(new Date());
    }

    /** 编号年度：优先按申报时间，其次按当前年 */
    private int resolveYear(EscalationProject project) {
        Date date = project.getDeclareDate() == null ? new Date() : project.getDeclareDate();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar.get(Calendar.YEAR);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 当前登录账号（供导出等场景复用） */
    public String currentUsername() {
        return auditSupport.currentUsername();
    }
}
