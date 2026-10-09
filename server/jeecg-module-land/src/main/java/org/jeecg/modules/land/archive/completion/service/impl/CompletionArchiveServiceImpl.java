package org.jeecg.modules.land.archive.completion.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.archive.completion.dto.CompletionQueryDTO;
import org.jeecg.modules.land.archive.completion.dto.CompletionSaveDTO;
import org.jeecg.modules.land.archive.completion.entity.CompletionArchive;
import org.jeecg.modules.land.archive.completion.enums.DigitizeStatus;
import org.jeecg.modules.land.archive.completion.mapper.CompletionArchiveMapper;
import org.jeecg.modules.land.archive.completion.service.ICompletionArchiveService;
import org.jeecg.modules.land.archive.completion.support.CompletionSupport;
import org.jeecg.modules.land.archive.completion.vo.CompletionStatVO;
import org.jeecg.modules.land.archive.completion.vo.RelatedArchiveVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 竣工验收项目历史工程资料数字化档案 Service 实现（方案 2.3.2 第 8 项）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version: V1.0
 *
 * <p>本类是模块的业务总闸，负责四件事：
 * <ol>
 *   <li><b>档案增删改</b>：档案编号（自动/手工）、数字化状态校验、进度折算；</li>
 *   <li><b>查询与统计</b>：列表 / 状态计数 / 6 组统计共用同一套条件（SQL 侧同一段 queryWhere）；</li>
 *   <li><b>扫描件关联</b>：只写自己表的 {@code archive_id} / {@code archive_count}，
 *       <b>不写</b> {@code t_archive}（扫描件生命周期归档案模块）；</li>
 *   <li><b>历史项目可空关联</b>：{@code land_id} / {@code facility_id} / {@code crzdbh}
 *       允许为空——存量历史项目不一定有对应的宗地/配套项目记录（清单 §6.3.8）。</li>
 * </ol>
 *
 * <p><b>★ 无状态机</b>：历史资料的数字化是线下加工，完成后回系统登记，
 * {@code digitizeStatus} 由前端传入，这里只校验「值在 3 个合法取值内」，
 * 不做「未数字化 → 数字化中 → 已数字化」的顺序判定
 * （业务上完全可能一次性把一批资料整体标成已数字化）。
 *
 * <p><b>★ 项目类型 / 保管期限为什么不在后端硬校验取值</b>：
 * 这两列是字典驱动（{@code land_completion_project_type} / {@code land_completion_retention}），
 * 中心随时可能在数据字典里加取值。若后端写死一份常量，就会出现
 * 「字典里加了新类型、页面能选、保存却被后端拒绝」的故障。
 * 它们只驱动下拉与统计分组，不驱动任何逻辑，因此<b>只由前端按字典限制输入，后端不做白名单</b>；
 * 而 {@code digitizeStatus} 是代码枚举、且驱动配色与统计口径，必须后端把关。
 * —— 与第 7 项台账模块的口径完全一致。
 */
@Slf4j
@Service
public class CompletionArchiveServiceImpl
        extends ServiceImpl<CompletionArchiveMapper, CompletionArchive>
        implements ICompletionArchiveService {

    /** 档案编号前缀，完整格式 JG-{yyyy}-{4位流水}（JG = 竣工） */
    private static final String ARCHIVE_NO_PREFIX = "JG-";

    /** 档案编号生成的最大重试次数（并发下唯一键冲突时重试） */
    private static final int NO_RETRY = 3;

    /** 关联扫描件与挑档案列表的默认条数上限 */
    private static final int ARCHIVE_LIMIT_DEFAULT = 50;
    private static final int ARCHIVE_LIMIT_MAX = 200;

    /** 挑档案时给「查全部」的通配上限（没有过滤条件时不允许无限拉） */
    private static final int ARCHIVE_PICK_DEFAULT = 30;

    @Autowired
    private CompletionSupport completionSupport;

    // ==================================================================
    // 查询
    // ==================================================================

    @Override
    public IPage<CompletionArchive> queryPage(CompletionQueryDTO query) {
        CompletionQueryDTO condition = normalize(query);
        Page<CompletionArchive> page =
                new Page<>(condition.resolvePageNo(), condition.resolvePageSize());
        IPage<CompletionArchive> result = baseMapper.selectCompletionPage(page, condition);
        List<CompletionArchive> rows = result == null ? null : result.getRecords();
        // 序号在 Service 侧赋值：SQL 里用用户变量会被分页插件的 COUNT 优化搅乱，
        // MySQL 5.7 又没有 ROW_NUMBER()（见 Mapper XML 的说明）
        fillSeq(rows, (condition.resolvePageNo() - 1) * condition.resolvePageSize());
        fillRows(rows);
        return result;
    }

    @Override
    public CompletionArchive queryDetail(String id) {
        if (StringUtils.isBlank(id)) {
            return null;
        }
        CompletionArchive archive = getById(id);
        if (archive == null) {
            return null;
        }
        CompletionSupport.fillDigitizeStats(archive);
        archive.setRelatedArchives(queryRelatedArchivesByEntity(archive, ARCHIVE_LIMIT_DEFAULT));
        return archive;
    }

    @Override
    public List<CompletionArchive> queryForExport(CompletionQueryDTO query) {
        List<CompletionArchive> rows = baseMapper.selectForExport(normalize(query));
        fillSeq(rows, 0);
        fillRows(rows);
        return rows;
    }

    @Override
    public List<CompletionStatVO.StatusCount> countByStatus(CompletionQueryDTO query) {
        CompletionQueryDTO condition = normalize(query);
        // 状态条件不能参与「各状态计数」的过滤，否则切换 tab 后其它 tab 的角标会全变 0。
        // 这里清空的是本次请求的独立副本（normalize 做了拷贝），不会影响别处
        condition.setDigitizeStatus(null);
        return statusCounts(condition);
    }

    @Override
    public CompletionStatVO queryStat(CompletionQueryDTO query) {
        CompletionQueryDTO condition = normalize(query);
        CompletionStatVO stat = new CompletionStatVO();

        // 1) 6 个标量指标（一次查询）
        Map<String, Object> summary = baseMapper.selectSummary(condition);
        stat.setTotal(longOf(summary, "total"));
        stat.setArchivedCount(longOf(summary, "archivedCount"));
        stat.setDigitizedCount(longOf(summary, "digitizedCount"));
        stat.setPageTotal(longOf(summary, "pageTotal"));
        stat.setFileTotal(longOf(summary, "fileTotal"));
        stat.setInvestTotal(decimalOf(summary, "investTotal"));

        // 2) 按数字化状态（★ 用独立副本清空 status 条件；否则分布图里只剩一根柱子）
        CompletionQueryDTO byStatusCondition = normalize(query);
        byStatusCondition.setDigitizeStatus(null);
        List<CompletionStatVO.StatusCount> byStatus = statusCounts(byStatusCondition);
        List<CompletionStatVO.NameCount> statusChart = new ArrayList<>(byStatus.size());
        for (CompletionStatVO.StatusCount item : byStatus) {
            statusChart.add(new CompletionStatVO.NameCount(item.getStatus(), item.getCount()));
        }
        stat.setByStatus(statusChart);

        // 3) 分组统计（列名全部是代码里的常量，不接受请求参数）
        stat.setByXzqh(baseMapper.selectStatByColumn(condition, "xzqh", "未填写"));
        stat.setByType(baseMapper.selectStatByColumn(condition, "project_type", "未填写"));
        stat.setByRetention(baseMapper.selectStatByColumn(condition, "retention", "未填写"));
        stat.setByYear(baseMapper.selectStatByYear(condition));
        stat.setByCompleteYear(baseMapper.selectStatByCompleteYear(condition));
        return stat;
    }

    // ==================================================================
    // 新增
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createArchive(CompletionSaveDTO dto) {
        if (dto == null) {
            throw new JeecgBootException("缺少档案数据");
        }
        checkRequired(dto);
        checkDigitizeStatus(dto.getDigitizeStatus());

        CompletionArchive archive = new CompletionArchive();
        applyDto(archive, dto);
        if (StringUtils.isBlank(archive.getDigitizeStatus())) {
            archive.setDigitizeStatus(DigitizeStatus.defaultValue());
        }
        archive.setId(IdWorker.getIdStr());
        archive.setDelFlag(0);
        archive.setCreateBy(completionSupport.currentUsername());
        archive.setCreateTime(new Date());
        CompletionSupport.fillDigitizeStats(archive);
        // 关联扫描件数是冗余缓存，只在「能确定归属」时算（facilityId 或 crzdbh 至少有一个）
        archive.setArchiveCount(countRelatedArchives(archive.getFacilityId(), archive.getCrzdbh()));

        // 档案编号：手工填了就校验唯一；没填就自动生成（并发冲突时重试）
        JeecgBootException lastError = null;
        for (int attempt = 0; attempt < NO_RETRY; attempt++) {
            String archiveNo = StringUtils.isNotBlank(dto.getArchiveNo())
                    ? dto.getArchiveNo().trim()
                    : nextArchiveNo(resolveYear(archive));
            checkArchiveNoUnique(archiveNo, null);
            archive.setArchiveNo(archiveNo);
            if (save(archive)) {
                lastError = null;
                break;
            }
            lastError = new JeecgBootException("档案保存失败，请重试");
        }
        if (lastError != null) {
            throw lastError;
        }
        log.info(String.format("新增竣工验收历史档案成功：id=%s, archiveNo=%s, 项目=%s, 数字化状态=%s, 操作人=%s",
                archive.getId(), archive.getArchiveNo(), archive.getProjectName(),
                archive.getDigitizeStatus(), archive.getCreateBy()));
        return archive.getId();
    }

    // ==================================================================
    // 编辑
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateArchive(CompletionSaveDTO dto) {
        if (dto == null || StringUtils.isBlank(dto.getId())) {
            throw new JeecgBootException("缺少档案ID");
        }
        CompletionArchive old = getById(dto.getId());
        if (old == null) {
            throw new JeecgBootException("档案不存在或已被删除");
        }
        if (StringUtils.isBlank(dto.getProjectName())) {
            throw new JeecgBootException("历史项目名称不能为空");
        }
        checkDigitizeStatus(dto.getDigitizeStatus());
        // 档案编号允许手工改写：传了就校验唯一（排除自身），没传就沿用旧值
        if (StringUtils.isNotBlank(dto.getArchiveNo())) {
            checkArchiveNoUnique(dto.getArchiveNo(), old.getId());
        }

        CompletionArchive update = new CompletionArchive();
        update.setId(old.getId());
        applyDto(update, dto);
        update.setArchiveNo(StringUtils.isNotBlank(dto.getArchiveNo())
                ? dto.getArchiveNo().trim() : old.getArchiveNo());
        update.setDigitizeStatus(StringUtils.isNotBlank(dto.getDigitizeStatus())
                ? dto.getDigitizeStatus().trim() : old.getDigitizeStatus());
        CompletionSupport.fillDigitizeStats(update);
        update.setArchiveCount(countRelatedArchives(update.getFacilityId(), update.getCrzdbh()));
        update.setUpdateBy(completionSupport.currentUsername());
        update.setUpdateTime(new Date());

        // ★ 走显式 SQL 而不是 updateById：允许把已填字段（如填错的竣工日期、页数）清空为 NULL，
        //   原因见 CompletionArchiveMapper#updateCompletionAll 的注释
        int rows = baseMapper.updateCompletionAll(update);
        if (rows <= 0) {
            throw new JeecgBootException("档案更新失败（记录可能已被删除）");
        }
        log.info("编辑竣工验收历史档案成功：id={}, archiveNo={}, 数字化状态={}",
                update.getId(), update.getArchiveNo(), update.getDigitizeStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeDigitizeStatus(String id, String digitizeStatus) {
        if (StringUtils.isBlank(id)) {
            throw new JeecgBootException("缺少档案ID");
        }
        if (!DigitizeStatus.isValid(digitizeStatus)) {
            throw new JeecgBootException("数字化状态「" + digitizeStatus + "」不合法，允许值："
                    + String.join("/", DigitizeStatus.allValues()));
        }
        CompletionArchive old = getById(id);
        if (old == null) {
            throw new JeecgBootException("档案不存在或已被删除");
        }
        int rows = baseMapper.updateStatus(id, digitizeStatus.trim(),
                completionSupport.currentUsername(), new Date());
        if (rows <= 0) {
            throw new JeecgBootException("数字化状态变更失败（记录可能已被删除）");
        }
        log.info("历史档案数字化状态变更：id={}, {} → {}", id, old.getDigitizeStatus(), digitizeStatus.trim());
    }

    // ==================================================================
    // 删除
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteArchive(String id) {
        if (StringUtils.isBlank(id)) {
            throw new JeecgBootException("缺少档案ID");
        }
        CompletionArchive archive = getById(id);
        if (archive == null) {
            throw new JeecgBootException("档案不存在或已被删除");
        }
        // 逻辑删除：不动 t_archive（扫描件归档案模块）、不动 t_land / 配套项目
        removeById(id);
        log.info("删除竣工验收历史档案成功：id={}, archiveNo={}, 项目={}",
                id, archive.getArchiveNo(), archive.getProjectName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteArchives(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (String id : ids) {
            if (StringUtils.isNotBlank(id)) {
                deleteArchive(id.trim());
            }
        }
    }

    // ==================================================================
    // 档案编号
    // ==================================================================

    @Override
    public String generateArchiveNo(Integer year) {
        int targetYear = year == null || year < 1900 || year > 2999
                ? Calendar.getInstance().get(Calendar.YEAR)
                : year;
        return nextArchiveNo(targetYear);
    }

    @Override
    public void checkArchiveNoUnique(String archiveNo, String excludeId) {
        if (StringUtils.isBlank(archiveNo)) {
            throw new JeecgBootException("档案编号不能为空");
        }
        if (baseMapper.countByArchiveNo(archiveNo.trim(), excludeId) > 0) {
            throw new JeecgBootException("档案编号「" + archiveNo.trim() + "」已存在，请更换");
        }
    }

    /** 按年度流水生成下一个档案编号 */
    private String nextArchiveNo(int year) {
        String prefix = ARCHIVE_NO_PREFIX + year + "-";
        Integer max = baseMapper.selectMaxSeqOfYear(prefix);
        int next = (max == null ? 0 : max) + 1;
        return prefix + String.format("%04d", next);
    }

    // ==================================================================
    // 关联扫描件
    // ==================================================================

    @Override
    public List<RelatedArchiveVO> queryRelatedArchives(String id, Integer limit) {
        if (StringUtils.isBlank(id)) {
            return new ArrayList<>();
        }
        CompletionArchive archive = getById(id);
        if (archive == null) {
            return new ArrayList<>();
        }
        return queryRelatedArchivesByEntity(archive, resolveArchiveLimit(limit));
    }

    @Override
    public List<RelatedArchiveVO> queryArchivesForPick(String facilityId, String crzdbh,
                                                       String keyword, Integer limit) {
        int size = limit == null || limit < 1 ? ARCHIVE_PICK_DEFAULT : Math.min(limit, ARCHIVE_LIMIT_MAX);
        return baseMapper.selectArchivesForPick(trimToNull(facilityId), trimToNull(crzdbh),
                trimToNull(keyword), size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void linkArchive(String id, String archiveId) {
        if (StringUtils.isBlank(id)) {
            throw new JeecgBootException("缺少档案ID");
        }
        if (StringUtils.isBlank(archiveId)) {
            throw new JeecgBootException("请选择要关联的扫描件档案");
        }
        CompletionArchive archive = getById(id);
        if (archive == null) {
            throw new JeecgBootException("档案不存在或已被删除");
        }
        if (baseMapper.countArchiveById(archiveId.trim()) <= 0) {
            throw new JeecgBootException("档案不存在或已被删除，无法关联");
        }
        int rows = baseMapper.updateArchiveLink(archive.getId(), archiveId.trim(),
                countRelatedArchives(archive.getFacilityId(), archive.getCrzdbh()),
                completionSupport.currentUsername(), new Date());
        if (rows <= 0) {
            throw new JeecgBootException("关联扫描件失败（记录可能已被删除）");
        }
        log.info("历史档案关联扫描件：completionId={}, archiveNo={}, archiveId={}, 操作人={}",
                archive.getId(), archive.getArchiveNo(), archiveId.trim(), completionSupport.currentUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlinkArchive(String id) {
        if (StringUtils.isBlank(id)) {
            throw new JeecgBootException("缺少档案ID");
        }
        CompletionArchive archive = getById(id);
        if (archive == null) {
            throw new JeecgBootException("档案不存在或已被删除");
        }
        // archiveId 传 null 即为取消关联（显式 SQL 能写 NULL）
        int rows = baseMapper.updateArchiveLink(archive.getId(), null,
                countRelatedArchives(archive.getFacilityId(), archive.getCrzdbh()),
                completionSupport.currentUsername(), new Date());
        if (rows <= 0) {
            throw new JeecgBootException("取消关联失败（记录可能已被删除）");
        }
        log.info("历史档案取消关联扫描件：completionId={}, archiveNo={}, 原 archiveId={}",
                archive.getId(), archive.getArchiveNo(), archive.getArchiveId());
    }

    // ==================================================================
    // 私有工具
    // ==================================================================

    /**
     * 条件归一化，并**返回一份独立副本**。
     *
     * <p>★ 为什么要复制（这是从第 7 项台账模块的实测 bug 里学来的）：
     * 统计接口里「按数字化状态分布」需要把 {@code digitizeStatus} 条件清掉
     * （否则切换筛选后分布图里只剩一根柱子），若直接复用请求传入的同一个 DTO，
     * 这次清空会连带把后面按行政区/项目类型/保管期限/年度的分组统计也变成
     * 「不带状态筛选」，于是指标卡、分布图与分组统计的口径就对不上了。
     * 返回副本后，各分组可以放心地各自调整条件。
     */
    private CompletionQueryDTO normalize(CompletionQueryDTO query) {
        CompletionQueryDTO condition = new CompletionQueryDTO();
        if (query != null) {
            BeanUtils.copyProperties(query, condition);
        }
        return condition;
    }

    /** 各状态计数：固定返回 3 项，数量为 0 的状态也返回（前端 tab 不必兜底） */
    private List<CompletionStatVO.StatusCount> statusCounts(CompletionQueryDTO condition) {
        Map<String, Long> counted = new LinkedHashMap<>();
        List<CompletionStatVO.StatusCount> rows = baseMapper.selectCountGroupByStatus(condition);
        if (rows != null) {
            for (CompletionStatVO.StatusCount row : rows) {
                if (row != null && StringUtils.isNotBlank(row.getStatus())) {
                    counted.put(row.getStatus().trim(), row.getCount());
                }
            }
        }
        List<CompletionStatVO.StatusCount> result = new ArrayList<>(DigitizeStatus.values().length);
        for (String status : DigitizeStatus.allValues()) {
            Long count = counted.get(status);
            result.add(new CompletionStatVO.StatusCount(status, count == null ? 0L : count));
        }
        return result;
    }

    /** 档案的关联扫描件（配套项目ID 优先，宗地编号兜底；两者都空时返回空表） */
    private List<RelatedArchiveVO> queryRelatedArchivesByEntity(CompletionArchive archive, int limit) {
        if (archive == null) {
            return new ArrayList<>();
        }
        String facilityId = trimToNull(archive.getFacilityId());
        String crzdbh = trimToNull(archive.getCrzdbh());
        if (facilityId == null && crzdbh == null) {
            return new ArrayList<>();
        }
        List<RelatedArchiveVO> archives = baseMapper.selectRelatedArchives(facilityId, crzdbh, limit);
        return archives == null ? new ArrayList<>() : archives;
    }

    /** 关联扫描件数（冗余列 archive_count 的取值；两边都空时为 0，不查库） */
    private int countRelatedArchives(String facilityId, String crzdbh) {
        String fid = trimToNull(facilityId);
        String crzdbhValue = trimToNull(crzdbh);
        if (fid == null && crzdbhValue == null) {
            return 0;
        }
        return baseMapper.countRelatedArchives(fid, crzdbhValue);
    }

    private int resolveArchiveLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return ARCHIVE_LIMIT_DEFAULT;
        }
        return Math.min(limit, ARCHIVE_LIMIT_MAX);
    }

    /** 给档案行补「序号」：offset = 前面已经翻过的行数，导出时为 0 */
    private static void fillSeq(List<CompletionArchive> rows, int offset) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        int seq = offset + 1;
        for (CompletionArchive row : rows) {
            if (row == null) {
                continue;
            }
            row.setSeq(seq++);
        }
    }

    /** 列表 / 导出的每一行都补齐数字化进度的展示字段 */
    private static void fillRows(List<CompletionArchive> rows) {
        if (rows == null) {
            return;
        }
        for (CompletionArchive row : rows) {
            CompletionSupport.fillDigitizeStats(row);
        }
    }

    /**
     * 必填校验。
     *
     * <p>只强制 {@code project_name}：它是 DDL 里的 NOT NULL，缺了必然抛 SQL 异常，
     * 这里提前拦住把「数据库约束报错」变成「一句看得懂的业务提示」。
     * 其余字段（项目编号、竣工日期、页数、DPI、参建单位等）全部允许为空 ——
     * 历史档案是**逐步补录**的：先建账，再逐项补齐；强制必填会让「先登记后完善」这个
     * 最现实的工作方式根本走不通。
     */
    private void checkRequired(CompletionSaveDTO dto) {
        if (StringUtils.isBlank(dto.getProjectName())) {
            throw new JeecgBootException("历史项目名称不能为空");
        }
    }

    /** 数字化状态必须是固定 3 值之一（无状态机，只校验取值） */
    private void checkDigitizeStatus(String status) {
        String value = trimToNull(status);
        if (value == null) {
            return;
        }
        if (!DigitizeStatus.isValid(value)) {
            throw new JeecgBootException("数字化状态「" + value + "」不合法，允许值："
                    + String.join("/", DigitizeStatus.allValues()));
        }
    }

    /** DTO → 实体（新增与编辑共用） */
    private void applyDto(CompletionArchive archive, CompletionSaveDTO dto) {
        archive.setProjectName(trimToNull(dto.getProjectName()));
        archive.setProjectCode(trimToNull(dto.getProjectCode()));
        archive.setXzqh(trimToNull(dto.getXzqh()));
        archive.setProjectType(trimToNull(dto.getProjectType()));

        archive.setLandId(trimToNull(dto.getLandId()));
        archive.setFacilityId(trimToNull(dto.getFacilityId()));
        archive.setCrzdbh(trimToNull(dto.getCrzdbh()));
        archive.setPtxmmc(trimToNull(dto.getPtxmmc()));
        archive.setDkmc(trimToNull(dto.getDkmc()));
        archive.setPtsslb(trimToNull(dto.getPtsslb()));

        archive.setBuildUnit(trimToNull(dto.getBuildUnit()));
        archive.setConstructUnit(trimToNull(dto.getConstructUnit()));
        archive.setDesignUnit(trimToNull(dto.getDesignUnit()));
        archive.setSuperviseUnit(trimToNull(dto.getSuperviseUnit()));

        archive.setStartDate(dto.getStartDate());
        archive.setCompleteDate(dto.getCompleteDate());
        archive.setAcceptanceDate(dto.getAcceptanceDate());

        archive.setInvestAmount(dto.getInvestAmount());
        archive.setRetention(trimToNull(dto.getRetention()));

        archive.setDigitizeStatus(trimToNull(dto.getDigitizeStatus()));
        archive.setDigitizeDate(dto.getDigitizeDate());
        archive.setDigitizeOrg(trimToNull(dto.getDigitizeOrg()));
        archive.setPageCount(dto.getPageCount());
        archive.setFileCount(dto.getFileCount());
        archive.setScanDpi(dto.getScanDpi());

        archive.setRemark(trimToNull(dto.getRemark()));
        // ★ 注意：这里<b>不</b>处理 archive_id / archive_count / del_flag
        //   （扫描件指针与删除标记都不允许被表单改动）
    }

    /**
     * 编号年度：优先按竣工日期，其次按验收日期，最后按当前年。
     *
     * <p>理由：历史档案的「档案年度」通常就是项目竣工/验收的年度，
     * 这样同一个年度批次的项目会拿到同一前缀的连号，便于人工核对。
     */
    private int resolveYear(CompletionArchive archive) {
        Date date = archive.getCompleteDate() != null ? archive.getCompleteDate()
                : (archive.getAcceptanceDate() != null ? archive.getAcceptanceDate() : new Date());
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar.get(Calendar.YEAR);
    }

    /** 从 Map 里取 long（数值列在 JDBC 下可能是 Long / BigInteger / BigDecimal） */
    private static long longOf(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return 0L;
        }
        Object value = map.get(key);
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** 从 Map 里取 BigDecimal（投资额合计） */
    private static BigDecimal decimalOf(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return BigDecimal.ZERO;
        }
        Object value = map.get(key);
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
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
        return completionSupport.currentUsername();
    }
}
