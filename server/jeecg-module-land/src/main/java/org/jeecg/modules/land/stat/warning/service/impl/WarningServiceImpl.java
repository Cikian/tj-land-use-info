package org.jeecg.modules.land.stat.warning.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.api.CommonAPI;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.DictModel;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.land.data.process.entity.FacilityProcess;
import org.jeecg.modules.land.data.process.entity.ProcessConfig;
import org.jeecg.modules.land.data.process.mapper.FacilityProcessMapper;
import org.jeecg.modules.land.data.process.mapper.ProcessConfigMapper;
import org.jeecg.modules.land.data.process.support.WorkdayCalculator;
import org.jeecg.modules.land.stat.warning.dto.WarningQueryDTO;
import org.jeecg.modules.land.stat.warning.entity.DecisionOpinion;
import org.jeecg.modules.land.stat.warning.entity.ProcessWarning;
import org.jeecg.modules.land.stat.warning.entity.ProcessWarningLevel;
import org.jeecg.modules.land.stat.warning.mapper.DecisionOpinionMapper;
import org.jeecg.modules.land.stat.warning.mapper.ProcessWarningLevelMapper;
import org.jeecg.modules.land.stat.warning.mapper.ProcessWarningMapper;
import org.jeecg.modules.land.stat.warning.service.IWarningService;
import org.jeecg.modules.land.stat.warning.support.WarningFields;
import org.jeecg.modules.land.stat.warning.support.WarningLevelSupport;
import org.jeecg.modules.land.stat.warning.vo.RecalcResultVO;
import org.jeecg.modules.land.stat.warning.vo.WarningLevelVO;
import org.jeecg.modules.land.stat.warning.vo.WarningOptionsVO;
import org.jeecg.modules.land.stat.warning.vo.WarningSummaryVO;
import org.jeecg.modules.land.stat.warning.vo.WarningVO;
import org.jeecgframework.poi.excel.entity.ExportParams;
import org.jeecgframework.poi.excel.entity.enmus.ExcelType;
import org.jeecgframework.poi.excel.entity.params.ExcelExportEntity;
import org.jeecgframework.poi.excel.export.ExcelExportServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @Description: 分级预警 · 查询 / 汇总 / 下拉 / 重算 / 导出 实现
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-11-02
 * @Version V1.0
 *
 * <p>本类是「分级预警」的算法主场，对应契约 §3（算法）与 §5.1~§5.5（接口）。
 * 三种口径的一句话概括：
 * <pre>
 *   环节预警：一个事项自己 vs 自己的标准时长      overdue = workdaysBetween(yjjssj, lcjssj ?: 今天)
 *   阶段预警：该阶段下已开始事项的「max 或 sum」 vs 阶段标准时长   overdue = actual - std
 *   总体预警：6 个阶段实际历时之和            vs 6 个阶段标准时长之和 overdue = actual - std
 * </pre>
 *
 * <p><b>★ 三条必须守住的口径（写错任何一条都会让数字「看着对、实际错」）</b>：
 * <ol>
 *   <li><b>只统计已开始且不是「不涉及」的环节</b>。{@code lckssj} 为空 = 未开启，
 *       不参与阶段/总体求和（否则「一个都没开始」会被算成「拖了很久」）。</li>
 *   <li><b>串行/并行按「该阶段全部事项」的 {@code is_parallel} 判定</b>：
 *       全为 1 → 并行取 max；否则（含混合）→ 串行取 sum。
 *       旧代码取的是「子集合 {@code distinct().get(0)}」，混合配置时结果取决于集合顺序，
 *       且子集合为空时直接抛异常。</li>
 *   <li><b>{@code overdueDays > 0} 才产生预警</b>。{@code == 0} 是「正好今天到期」，
 *       不该预警（旧代码是 {@code days >= 区间下界}，下界从 1 起，等价）。</li>
 * </ol>
 *
 * <p><b>★ 决策意见只给环节预警</b>：阶段/总体没有「问题类型」，
 * 硬套一条通用话术会让用户以为「这个阶段的问题是资金问题」（契约 §3.7）。
 *
 * <p><b>★ 依赖方向</b>：本类只依赖 data 域的 <b>Mapper / 实体 / 工作日日历</b>，
 * 不依赖 {@code FacilityAdminService}（否则与它的「保存环节后重算」钩子形成循环依赖）。
 *
 * <p><b>★ 不缓存等级与决策意见</b>：管理端改完阈值/话术必须立刻生效。
 * 每次重算查一次（4 行 + 个位数行），成本可以忽略。
 */
@Slf4j
@Service
public class WarningServiceImpl implements IWarningService {

    /** 列表默认每页条数（与大屏端默认分页一致） */
    private static final int DEFAULT_PAGE_SIZE = 10;
    /** 每页上限：再大就变成「一次拉全表」，与分页的初衷相反 */
    private static final int MAX_PAGE_SIZE = 200;
    /**
     * 单次导出行数上限。
     *
     * <p>easypoi 的内存版导出会把所有行先建成 cell；预警行数 = 环节数 + 阶段数 + 配套数，
     * 全市量级在千行以内，但「不加筛选直接导出」仍要给一个能看懂的上限，
     * 免得某天数据异常时把服务端拖垮。
     */
    private static final int MAX_EXPORT_ROWS = 20000;
    /** 单次 IN 查询的批量（配套数 1433，500 一批够用，也不会撞 SQL 长度限制） */
    private static final int ID_BATCH_SIZE = 500;
    /**
     * 逐日推进的安全阀（约 100 年）。
     *
     * <p>没有上限的 while 是定时炸弹：一条 {@code lckssj = 1900-01-01} 的脏数据
     * 会让重算卡死。正常区间最长也就几百个工作日。
     */
    private static final int MAX_WORKDAY_DAYS = 36500;

    private static final String PATTERN_DATE = "yyyy-MM-dd";
    private static final String PATTERN_DATETIME = "yyyy-MM-dd HH:mm:ss";

    /** 唯一键分隔符（yjType 与 lcId 都不含竖线，用它拼 key 安全） */
    private static final String KEY_SEPARATOR = "|";

    @Autowired
    private ProcessWarningMapper warningMapper;

    @Autowired
    private ProcessWarningLevelMapper warningLevelMapper;

    @Autowired
    private DecisionOpinionMapper decisionOpinionMapper;

    @Autowired
    private ProcessConfigMapper processConfigMapper;

    @Autowired
    private FacilityProcessMapper facilityProcessMapper;

    @Autowired
    private WorkdayCalculator workdayCalculator;

    /**
     * 字典接口（{@code problemTypes} 的取值来源）。
     *
     * <p>{@code required = false}：字典实现在 system 模块，若某个切片/单测环境里没有它，
     * 这里为 null 就走内置 5 项常量 —— 「没有字典」不该让整个预警页打不开。
     */
    @Autowired(required = false)
    private CommonAPI commonApi;

    // ==================================================================
    // 一、查询 / 汇总 / 下拉
    // ==================================================================

    @Override
    public IPage<WarningVO> list(WarningQueryDTO query) {
        WarningQueryDTO q = normalize(query);
        return warningMapper.selectWarningPage(new Page<>(q.getPageNo(), q.getPageSize()), q);
    }

    @Override
    public WarningSummaryVO summary(WarningQueryDTO query) {
        WarningQueryDTO q = normalize(query);
        WarningSummaryVO result = new WarningSummaryVO();
        result.setTotal(warningMapper.countWarnings(q));

        // ① 四色卡：先取等级表全量（保证四档齐全、按 sort_no 升序），再逐档填计数
        Map<String, Long> levelCounts = toCountMap(warningMapper.selectCountByLevel(q), "levelId");
        List<ProcessWarningLevel> levels = warningLevelMapper.selectEnabled();
        List<WarningSummaryVO.LevelCount> byLevel = new ArrayList<>();
        if (levels != null) {
            for (ProcessWarningLevel level : levels) {
                WarningSummaryVO.LevelCount item = new WarningSummaryVO.LevelCount();
                item.setLevelId(level.getId());
                item.setLevelCode(level.getLevelCode());
                item.setLevelName(level.getLevelName());
                // 无数据给 0 而不是 null：前端要直接参与「总数 / 百分比」计算
                item.setCount(countOf(levelCounts, level.getId()));
                byLevel.add(item);
            }
        }
        result.setByLevel(byLevel);
        if (levelCounts.size() > byLevel.size()) {
            // 有预警行的等级已不在等级表里（等级被删）——计入 total 但不进四色卡，
            // 下次重算会改写或删除这些行；这里留一条 warn 方便排查
            log.info("预警汇总：{} 条预警的等级已不在等级表中（total={}），下次重算会处理",
                    result.getTotal() - sumCounts(byLevel), result.getTotal());
        }

        // ② 分类：只返回有数据的分类（前端分类下拉不该凭空多出 0 值项）
        Map<String, Long> typeCounts = toCountMap(warningMapper.selectCountByType(q), "yjType");
        List<WarningSummaryVO.TypeCount> byType = new ArrayList<>();
        for (String yjType : WarningFields.YJ_TYPES) {
            long count = countOf(typeCounts, yjType);
            if (count > 0) {
                byType.add(typeCount(yjType, count));
                typeCounts.remove(yjType);
            }
        }
        // 兜底：库里出现不在三个常量里的分类（历史脏数据）也带出去，别静默吞掉
        for (Map.Entry<String, Long> entry : typeCounts.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0) {
                byType.add(typeCount(entry.getKey(), entry.getValue()));
            }
        }
        result.setByType(byType);

        // ③ 数据截止时刻
        result.setCalcTime(warningMapper.selectLastCalcTime(q));
        return result;
    }

    @Override
    public WarningOptionsVO options() {
        WarningOptionsVO result = new WarningOptionsVO();
        List<ProcessWarningLevel> levels = warningLevelMapper.selectEnabled();
        if (levels != null) {
            for (ProcessWarningLevel level : levels) {
                WarningLevelVO vo = new WarningLevelVO();
                vo.setId(level.getId());
                vo.setLevelName(level.getLevelName());
                vo.setLevelCode(level.getLevelCode());
                vo.setMinDays(level.getMinDays());
                vo.setMaxDays(level.getMaxDays());
                vo.setSortNo(level.getSortNo());
                vo.setRemark(level.getRemark());
                result.getLevels().add(vo);
            }
        }
        result.setYjTypes(new ArrayList<>(WarningFields.YJ_TYPES));
        result.setProblemTypes(loadProblemTypes());
        return result;
    }

    // ==================================================================
    // 二、重算（唯一的写入口）
    // ==================================================================

    @Override
    public RecalcResultVO recalc(String ptId) {
        long startedAt = System.currentTimeMillis();
        Date now = new Date();
        Date today = startOfDay(now);

        RecalcResultVO result = new RecalcResultVO();
        result.setCalcTime(formatDateTime(now));

        // 等级与决策意见各查一次（不缓存，见类注释）
        List<ProcessWarningLevel> levels = warningLevelMapper.selectEnabled();
        if (levels == null) {
            levels = new ArrayList<>();
        }
        result.setLevelMissing(levels.isEmpty());
        if (levels.isEmpty()) {
            log.warn("分级预警重算：预警等级表为空，本轮不会生成任何预警（请先在「预警等级维护」里配置区间）");
        }
        Map<String, String> opinionIndex = loadOpinionIndex();

        // 环节配置（6 阶段 + 24 事项）只查一次，供全部配套复用
        List<ProcessConfig> stages = nullToEmpty(processConfigMapper.selectStages());
        List<ProcessConfig> items = nullToEmpty(processConfigMapper.selectItems());
        Map<String, ProcessConfig> itemById = new HashMap<>();
        Map<String, List<ProcessConfig>> itemsByStage = new HashMap<>();
        for (ProcessConfig item : items) {
            itemById.put(item.getId(), item);
            itemsByStage.computeIfAbsent(item.getParentId(), key -> new ArrayList<>()).add(item);
        }

        List<String> ptIds = resolvePtIds(ptId);
        result.setScannedFacilities(ptIds.size());
        Map<String, Map<String, Object>> facilityInfos = loadFacilityInfos(ptIds);

        for (String id : ptIds) {
            try {
                recomputeOne(id, facilityInfos.get(id), stages, itemsByStage, itemById,
                        levels, opinionIndex, today, now, result);
            } catch (Exception e) {
                // ★ 单个配套算失败不能中断整批：手动全量重算时一条脏数据
                //   （例如某环节日期不可解析）不该让其余 1400 条都算不出来
                log.warn("分级预警重算失败（已跳过该配套）：ptId={}，原因={}", id, e.getMessage(), e);
            }
        }

        result.setElapsedMs(System.currentTimeMillis() - startedAt);
        log.info("分级预警重算完成：扫描配套 {} 个 / 环节 {} 行，新增 {}，更新 {}，删除 {}，"
                        + "未开启跳过 {}，无计划跳过 {}，等级缺失={}，耗时 {}ms",
                result.getScannedFacilities(), result.getScannedProcesses(), result.getCreated(),
                result.getUpdated(), result.getRemoved(), result.getSkippedNoStart(),
                result.getSkippedNoPlan(), result.getLevelMissing(), result.getElapsedMs());
        return result;
    }

    /**
     * 重算单个配套项目（契约 §3.8 的「本轮应当存在的集合 S → 与库内现有行求差」）。
     */
    private void recomputeOne(String ptId,
                              Map<String, Object> info,
                              List<ProcessConfig> stages,
                              Map<String, List<ProcessConfig>> itemsByStage,
                              Map<String, ProcessConfig> itemById,
                              List<ProcessWarningLevel> levels,
                              Map<String, String> opinionIndex,
                              Date today,
                              Date now,
                              RecalcResultVO result) {
        List<FacilityProcess> rows = nullToEmpty(facilityProcessMapper.selectByFacility(ptId));
        result.setScannedProcesses(result.getScannedProcesses() + rows.size());

        if (info == null) {
            // 配套已被移除（软删/物理删）→ 清掉它残留的预警行。
            // 不清的话列表里会一直挂着「已删除项目」的预警，且没有任何界面能删掉它。
            int removed = warningMapper.deleteByPtId(ptId);
            if (removed > 0) {
                result.setRemoved(result.getRemoved() + removed);
                log.info("分级预警重算：配套 {} 已不存在，清理残留预警 {} 条", ptId, removed);
            }
            return;
        }

        // ---- 冗余列：配套表优先，回落宗地表（挂不上宗地时宗地侧为 null）----
        String ptName = trimToNull(str(info, "ptName"));
        String crzdbh = trimToNull(str(info, "crzdbh"));
        String dkmc = firstNonBlank(trimToNull(str(info, "facilityDkmc")), trimToNull(str(info, "landDkmc")));
        String xzqh = firstNonBlank(trimToNull(str(info, "facilityXzqh")), trimToNull(str(info, "landXzqh")));
        String xzqh2 = trimToNull(str(info, "landXzqh2"));
        String yjName = (ptName == null ? "" : ptName) + WarningFields.YJ_NAME_SUFFIX;

        List<ProcessWarning> desired = new ArrayList<>();

        // ============ ① 环节预警 ============
        // 同时收集「参与计算的事项实际历时」，供阶段口径复用（避免每个阶段重算一遍）
        Map<String, Integer> itemActualDays = new LinkedHashMap<>();
        int dirtyFinished = 0;
        for (FacilityProcess row : rows) {
            ProcessConfig item = itemById.get(row.getLcId());
            if (item == null) {
                // 环节配置已删除，或该行其实是阶段汇总行（正常不会）——不参与任何口径
                continue;
            }
            if (row.getLckssj() == null) {
                // 未开启：不参与阶段/总体求和（契约 §3.4）
                result.setSkippedNoStart(result.getSkippedNoStart() + 1);
                continue;
            }
            if (WarningFields.LCQK_NOT_INVOLVED.equals(trim(row.getLcqk()))) {
                continue;
            }
            int stdDays = WarningLevelSupport.normalizeStdDays(
                    item.getProcessTime() == null ? null : item.getProcessTime().intValue());
            Date start = startOfDay(row.getLckssj());
            Date end;
            if (row.getLcjssj() != null) {
                end = startOfDay(row.getLcjssj());
            } else {
                // 未完成 → 算到今天；「已完成但没有结束时间」是历史脏数据，同样回落到今天，
                // 只在日志里记一笔（契约 §3.4：problem_detail 里不额外说明，避免污染业务文本）
                end = today;
                if (WarningFields.LCQK_FINISHED.equals(trim(row.getLcqk()))) {
                    dirtyFinished++;
                }
            }
            int actualDays = workdaysBetween(start, end);
            itemActualDays.put(item.getId(), actualDays);

            Date plan;
            if (row.getYjjssj() != null) {
                plan = startOfDay(row.getYjjssj());
            } else {
                // 预计结束时间缺失 → 按「开始日 + 标准时长（工作日）」推算（契约 §3.1）
                plan = safePlusWorkdays(start, stdDays);
            }
            if (plan == null) {
                result.setSkippedNoPlan(result.getSkippedNoPlan() + 1);
                continue;
            }
            int overdueDays = workdaysBetween(plan, end);
            if (overdueDays <= 0) {
                continue;
            }
            WarningLevelSupport.MatchResult match = WarningLevelSupport.match(levels, overdueDays);
            if (!match.isMatched()) {
                continue;
            }
            ProcessWarningLevel level = match.getLevel();
            ProcessWarning warning = new ProcessWarning();
            warning.setYjType(WarningFields.YJ_TYPE_ITEM);
            warning.setLcId(item.getId());
            warning.setLcName(item.getName());
            warning.setStageId(item.getParentId());
            warning.setProblemType(trimToNull(row.getCzwtlx()));
            warning.setProblemDetail(trimToNull(row.getJtwt()));
            // 决策意见：精确 (问题类型, 等级) → 通用 ('', 等级) → null
            warning.setDecisionOpinion(resolveOpinion(opinionIndex, warning.getProblemType(), level.getId()));
            warning.setZrbm(trimToNull(item.getZgbm()));
            // 环节预警的预警值：(stdDays + overdueDays) / stdDays，与旧代码逐字等价
            warning.setYjValue(WarningLevelSupport.yjValueForItem(stdDays, overdueDays));
            warning.setOverdueDays(overdueDays);
            warning.setStdDays(stdDays);
            warning.setActualDays(actualDays);
            applyLevel(warning, level);
            fillIdentity(warning, yjName, ptId, ptName, crzdbh, xzqh, xzqh2, dkmc, now);
            desired.add(warning);
        }
        if (dirtyFinished > 0) {
            log.info("分级预警重算：配套 {} 有 {} 个环节「已完成但没有结束时间」（历史脏数据），已按今天计算",
                    ptId, dirtyFinished);
        }

        // ============ ② 阶段预警 ============
        int overallActualDays = 0;
        int overallStdDaysRaw = 0;
        for (ProcessConfig stage : stages) {
            List<ProcessConfig> stageItems = itemsByStage.get(stage.getId());
            List<Integer> participating = new ArrayList<>();
            if (stageItems != null) {
                for (ProcessConfig item : stageItems) {
                    Integer days = itemActualDays.get(item.getId());
                    if (days != null) {
                        participating.add(days);
                    }
                }
            }
            int stageActualDays = 0;
            if (!participating.isEmpty()) {
                if (isParallel(stageItems)) {
                    // 并行：阶段历时取最慢的那一项（同时办理，不能相加）
                    stageActualDays = Collections.max(participating);
                } else {
                    // 串行（含混合配置）：一项一项办，历时相加
                    for (Integer days : participating) {
                        stageActualDays += days;
                    }
                }
            }
            int stageStdRaw = stage.getProcessTime() == null ? 0 : stage.getProcessTime().intValue();
            overallActualDays += stageActualDays;
            overallStdDaysRaw += Math.max(stageStdRaw, 0);

            int stageStdDays = WarningLevelSupport.normalizeStdDays(
                    stage.getProcessTime() == null ? null : stage.getProcessTime().intValue());
            int overdueDays = stageActualDays - stageStdDays;
            if (overdueDays <= 0) {
                continue;
            }
            WarningLevelSupport.MatchResult match = WarningLevelSupport.match(levels, overdueDays);
            if (!match.isMatched()) {
                continue;
            }
            ProcessWarning warning = new ProcessWarning();
            warning.setYjType(WarningFields.YJ_TYPE_STAGE);
            warning.setLcId(stage.getId());
            warning.setLcName(stage.getName());
            warning.setStageId(stage.getId());
            // ★ 阶段预警不填 problem_type / problem_detail / decision_opinion（契约 §3.7）
            warning.setZrbm(trimToNull(stage.getZgbm()));
            warning.setYjValue(WarningLevelSupport.yjRatio(stageActualDays, stageStdDays));
            warning.setOverdueDays(overdueDays);
            warning.setStdDays(stageStdDays);
            warning.setActualDays(stageActualDays);
            applyLevel(warning, match.getLevel());
            fillIdentity(warning, yjName, ptId, ptName, crzdbh, xzqh, xzqh2, dkmc, now);
            desired.add(warning);
        }

        // ============ ③ 总体预警 ============
        int overallStdDays = WarningLevelSupport.normalizeStdDays(overallStdDaysRaw);
        int overallOverdueDays = overallActualDays - overallStdDays;
        if (overallOverdueDays > 0) {
            WarningLevelSupport.MatchResult match = WarningLevelSupport.match(levels, overallOverdueDays);
            if (match.isMatched()) {
                ProcessWarning warning = new ProcessWarning();
                warning.setYjType(WarningFields.YJ_TYPE_WHOLE);
                // 总体预警的 lc_id 用空串而不是 null（唯一键允许 NULL 会被插无数次）
                warning.setLcId(WarningFields.WHOLE_LC_ID);
                warning.setLcName(WarningFields.WHOLE_LC_NAME);
                warning.setStageId(null);
                warning.setYjValue(WarningLevelSupport.yjRatio(overallActualDays, overallStdDays));
                warning.setOverdueDays(overallOverdueDays);
                warning.setStdDays(overallStdDays);
                warning.setActualDays(overallActualDays);
                applyLevel(warning, match.getLevel());
                fillIdentity(warning, yjName, ptId, ptName, crzdbh, xzqh, xzqh2, dkmc, now);
                desired.add(warning);
            }
        }

        // ============ ④ 与库内现有行求差（幂等的核心）============
        applyDiff(ptId, desired, now, result);
    }

    /**
     * 按 {@code (pt_id, yj_type, lc_id)} 做 upsert + 过期删除。
     *
     * <p>三步走：
     * <ol>
     *   <li>库里没有 → 插入；</li>
     *   <li>库里有 → <b>整行覆盖</b>（等级/预警值/意见/calc_time），保留 create_by / create_time；</li>
     *   <li>库里有但本轮不在集合里 → <b>物理删除</b>。</li>
     * </ol>
     * 第 3 步是旧系统最大的缺陷（它从不删）：用户把环节补录完成后，
     * 预警还一直挂在列表上，这是旧系统被投诉的点（契约 §3.8）。
     */
    private void applyDiff(String ptId, List<ProcessWarning> desired, Date now, RecalcResultVO result) {
        List<ProcessWarning> existingRows = nullToEmpty(warningMapper.selectByPtId(ptId));
        Map<String, ProcessWarning> existingMap = new LinkedHashMap<>();
        for (ProcessWarning old : existingRows) {
            existingMap.put(uniqueKey(old.getYjType(), old.getLcId()), old);
        }

        String operator = currentUsername();
        Set<String> desiredKeys = new HashSet<>();
        for (ProcessWarning warning : desired) {
            String key = uniqueKey(warning.getYjType(), warning.getLcId());
            desiredKeys.add(key);
            ProcessWarning old = existingMap.get(key);
            if (old == null) {
                warning.setCreateBy(operator);
                warning.setCreateTime(now);
                warning.setUpdateBy(operator);
                warning.setUpdateTime(now);
                try {
                    warningMapper.insert(warning);
                    result.setCreated(result.getCreated() + 1);
                } catch (Exception e) {
                    // 并发重算撞唯一键时可能走到这里：吞掉并在下次重算修正，
                    // 绝不把异常抛给「保存环节进度」的调用方
                    log.warn("分级预警插入失败（下次重算会修正）：ptId={}, yjType={}, lcId={}，原因={}",
                            ptId, warning.getYjType(), warning.getLcId(), e.getMessage());
                }
            } else {
                warning.setId(old.getId());
                // 创建信息不随重算变化，否则列表里的「录入人」会变成最后一次重算的人
                warning.setCreateBy(old.getCreateBy());
                warning.setCreateTime(old.getCreateTime());
                warning.setUpdateBy(operator);
                warning.setUpdateTime(now);
                try {
                    warningMapper.updateWarning(warning);
                    result.setUpdated(result.getUpdated() + 1);
                } catch (Exception e) {
                    log.warn("分级预警更新失败（下次重算会修正）：ptId={}, id={}，原因={}",
                            ptId, old.getId(), e.getMessage());
                }
            }
        }

        for (Map.Entry<String, ProcessWarning> entry : existingMap.entrySet()) {
            if (desiredKeys.contains(entry.getKey())) {
                continue;
            }
            try {
                warningMapper.deleteById(entry.getValue().getId());
                result.setRemoved(result.getRemoved() + 1);
            } catch (Exception e) {
                log.warn("分级预警删除过期行失败：ptId={}, id={}，原因={}",
                        ptId, entry.getValue().getId(), e.getMessage());
            }
        }
    }

    // ==================================================================
    // 三、导出
    // ==================================================================

    @Override
    public Workbook export(WarningQueryDTO query) {
        WarningQueryDTO q = normalize(query);
        long total = warningMapper.countWarnings(q);
        if (total > MAX_EXPORT_ROWS) {
            throw new JeecgBootException("本次导出 " + total + " 行，超过 " + MAX_EXPORT_ROWS
                    + " 行的导出上限，请缩小查询范围（例如按行政区或预警等级筛选）后再导出");
        }
        List<WarningVO> rows = nullToEmpty(warningMapper.selectWarningList(q, 0, MAX_EXPORT_ROWS));

        // 与 stat/export/StatQueryExportServiceImpl 同一套工具：
        // 不能只用 ExcelExportUtil.exportExcel(List<Map>, ExcelType)（autopoi 1.4.4 的那个重载
        // 要求 entity 是 Class），这里列是固定的、但用 ExcelExportServer 更直接。
        Workbook workbook = new XSSFWorkbook();
        ExportParams params = new ExportParams("预警信息", "预警信息");
        // ★ 必须显式 XSSF：否则写出 .xls 内容配 .xlsx 文件名，Excel 打开报「文件格式或扩展名无效」
        params.setType(ExcelType.XSSF);
        new ExcelExportServer().createSheetForMap(workbook, params, exportEntities(), exportData(rows));
        log.info("分级预警导出：{} 行", rows.size());
        return workbook;
    }

    /** 导出列：表头与列表列一致（契约 §5.5） */
    private static List<ExcelExportEntity> exportEntities() {
        List<ExcelExportEntity> entities = new ArrayList<>();
        entities.add(new ExcelExportEntity("预警分类", "yjType", 12));
        entities.add(new ExcelExportEntity("预警等级", "levelName", 12));
        entities.add(new ExcelExportEntity("预警名称", "yjName", 26));
        entities.add(new ExcelExportEntity("配套项目", "ptxmmc", 26));
        entities.add(new ExcelExportEntity("宗地编号", "crzdbh", 22));
        entities.add(new ExcelExportEntity("行政区划", "xzqh", 12));
        entities.add(new ExcelExportEntity("地块名称", "dkmc", 26));
        entities.add(new ExcelExportEntity("流程环节", "lcName", 20));
        entities.add(new ExcelExportEntity("预警值", "yjPercent", 12));
        entities.add(new ExcelExportEntity("逾期天数", "overdueDays", 10));
        entities.add(new ExcelExportEntity("问题类型", "problemType", 12));
        entities.add(new ExcelExportEntity("具体情况", "problemDetail", 32));
        entities.add(new ExcelExportEntity("决策意见", "decisionOpinion", 40));
        entities.add(new ExcelExportEntity("责任部门", "zrbm", 20));
        entities.add(new ExcelExportEntity("计算时间", "calcTime", 20));
        return entities;
    }

    /**
     * 导出数据：把 VO 拍平成导出列。
     *
     * <p>{@code yjPercent} 拼成「110.0%」而不再交给前端：导出是后端直接写文件，
     * 没有人能在中间再乘一次 100，所以这里必须把展示形态定下来
     * （旧系统也是 {@code String.format("%.1f", yjz*100)+"%"}）。
     */
    private static List<Map<String, Object>> exportData(List<WarningVO> rows) {
        List<Map<String, Object>> data = new ArrayList<>(rows.size());
        for (WarningVO row : rows) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("yjType", row.getYjType());
            line.put("levelName", row.getLevelName());
            line.put("yjName", row.getYjName());
            line.put("ptxmmc", blankIfNull(row.getPtxmmc()));
            line.put("crzdbh", blankIfNull(row.getCrzdbh()));
            line.put("xzqh", blankIfNull(row.getXzqh()));
            line.put("dkmc", blankIfNull(row.getDkmc()));
            line.put("lcName", blankIfNull(row.getLcName()));
            line.put("yjPercent", percentText(row.getYjPercent()));
            line.put("overdueDays", row.getOverdueDays() == null ? 0 : row.getOverdueDays());
            line.put("problemType", blankIfNull(row.getProblemType()));
            line.put("problemDetail", blankIfNull(row.getProblemDetail()));
            line.put("decisionOpinion", blankIfNull(row.getDecisionOpinion()));
            line.put("zrbm", blankIfNull(row.getZrbm()));
            line.put("calcTime", blankIfNull(row.getCalcTime()));
            data.add(line);
        }
        return data;
    }

    private static String percentText(BigDecimal percent) {
        return percent == null ? "" : percent.toPlainString() + "%";
    }

    // ==================================================================
    // 四、工作日历（逐日推进，不复用旧代码的递归 getDay）
    // ==================================================================

    /**
     * 两个日期之间的工作日天数，<b>含头不含尾</b>，即区间 {@code (start, end]}。
     *
     * <p><b>★ 为什么不用旧代码 {@code ProcessStatusController.getDay}</b>：
     * 它是「递归 + 补偿」，递归时把「非工作日个数」当成新的补偿天数传给下一次调用，
     * 而每次又以已经顺延过的终点为起点再查一遍非工作日 —— 跨长假时天数会滚雪球
     * （旧代码注释里自己写了这一点）。本实现逐日推进，结果与「节假日分成几个区间」无关。
     *
     * <p>为什么是「含头不含尾」而不是「两头都算」：同一天开始并结束的环节
     * 历时应当是 0 天还是 1 天？取 0 天与旧系统一致（{@code day2 - day1}），
     * 也让「开始日填今天、标准 1 天」不会立刻显示逾期。
     *
     * @param start 起始日（含边界但不计入）
     * @param end   结束日（计入）
     * @return 工作日天数；任一参数为 null 或 end 不晚于 start 时返回 0
     */
    private int workdaysBetween(Date start, Date end) {
        Date from = startOfDay(start);
        Date to = startOfDay(end);
        if (from == null || to == null || !to.after(from)) {
            return 0;
        }
        Calendar cursor = Calendar.getInstance();
        cursor.setTime(from);
        int count = 0;
        int guard = 0;
        while (guard++ < MAX_WORKDAY_DAYS) {
            cursor.add(Calendar.DATE, 1);
            Date current = cursor.getTime();
            if (current.after(to)) {
                break;
            }
            if (workdayCalculator.isWorkday(current)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 按「开始日 + N 个工作日」推算预计结束时间（{@code yjjssj} 缺失时的兜底）。
     *
     * <p>失败一律返回 null 而不是抛异常：这一层是「让结果更准」的补充计算，
     * 不该因为日历表异常把整批重算打断（调用方会记 skippedNoPlan）。
     */
    private Date safePlusWorkdays(Date start, int stdDays) {
        if (start == null) {
            return null;
        }
        try {
            Map<String, Object> calc = workdayCalculator.plusWorkdays(formatDate(start), stdDays);
            Object endDate = calc == null ? null : calc.get("endDate");
            return endDate == null ? null : startOfDay(parseDate(String.valueOf(endDate)));
        } catch (Exception e) {
            log.warn("按工作日推算预计结束时间失败：start={}，天数={}，原因={}",
                    formatDate(start), stdDays, e.getMessage());
            return null;
        }
    }

    // ==================================================================
    // 五、内部工具
    // ==================================================================

    /** 扫描范围：指定配套 / 全部「有环节进度记录」的配套 */
    private List<String> resolvePtIds(String ptId) {
        if (StringUtils.isNotBlank(ptId)) {
            return Collections.singletonList(ptId.trim());
        }
        return nullToEmpty(warningMapper.selectDistinctPtIds());
    }

    /** 冗余列批量查询（分批 IN，避免一次拼上千个占位符） */
    private Map<String, Map<String, Object>> loadFacilityInfos(List<String> ptIds) {
        Map<String, Map<String, Object>> result = new HashMap<>();
        if (ptIds == null || ptIds.isEmpty()) {
            return result;
        }
        for (int from = 0; from < ptIds.size(); from += ID_BATCH_SIZE) {
            List<String> chunk = ptIds.subList(from, Math.min(from + ID_BATCH_SIZE, ptIds.size()));
            List<Map<String, Object>> rows = warningMapper.selectFacilityInfos(chunk);
            if (rows == null) {
                continue;
            }
            for (Map<String, Object> row : rows) {
                String key = trimToNull(str(row, "ptId"));
                if (key != null) {
                    result.put(key, row);
                }
            }
        }
        return result;
    }

    /**
     * 决策意见索引：key = {@code 问题类型|等级ID}。
     *
     * <p>一次取全而不是逐条查库：一次重算要处理几十个环节，每条查一次就是几十次往返。
     * 同键多条时保留排序最靠前的那条（SQL 已按 sort_no 升序），结果稳定可解释。
     */
    private Map<String, String> loadOpinionIndex() {
        Map<String, String> index = new HashMap<>();
        List<DecisionOpinion> opinions = nullToEmpty(decisionOpinionMapper.selectAllEnabled());
        for (DecisionOpinion opinion : opinions) {
            if (opinion.getLevelId() == null || StringUtils.isBlank(opinion.getOpinion())) {
                continue;
            }
            String key = opinionKey(opinion.getProblemType(), opinion.getLevelId());
            if (!index.containsKey(key)) {
                index.put(key, opinion.getOpinion());
            }
        }
        return index;
    }

    /**
     * 取决策意见：精确 (问题类型, 等级) → 通用 ('', 等级) → null。
     *
     * <p>旧系统只有「精确」这一层，也没有兜底：5 类 × 4 级 = 20 格里漏配一格，
     * 那一格就永远没有建议，而且完全静默（契约 §3.7）。
     */
    private static String resolveOpinion(Map<String, String> index, String problemType, String levelId) {
        if (levelId == null) {
            return null;
        }
        String exact = index.get(opinionKey(problemType, levelId));
        if (exact != null) {
            return exact;
        }
        return index.get(opinionKey(WarningFields.OPINION_GENERIC_TYPE, levelId));
    }

    private static String opinionKey(String problemType, String levelId) {
        return (problemType == null ? "" : problemType.trim()) + KEY_SEPARATOR
                + (levelId == null ? "" : levelId);
    }

    /**
     * 唯一键字符串（{@code yj_type + lc_id}，pt_id 在同一批内固定）。
     *
     * <p>lcId 理论上不会是 null（列 {@code NOT NULL DEFAULT ''}），但防御一下：
     * null 与空串若被当成两个不同的 key，差集就会「该删的没删、该更新却插入」。
     */
    private static String uniqueKey(String yjType, String lcId) {
        return (yjType == null ? "" : yjType) + KEY_SEPARATOR + (lcId == null ? "" : lcId);
    }

    /** 把等级三列（id/code/name）一次性写进预警行 —— 冗余是为了列表免联表 */
    private static void applyLevel(ProcessWarning warning, ProcessWarningLevel level) {
        if (level == null) {
            return;
        }
        warning.setLevelId(level.getId());
        warning.setLevelCode(level.getLevelCode());
        warning.setLevelName(level.getLevelName());
    }

    /** 三类预警共用的身份/冗余列 */
    private static void fillIdentity(ProcessWarning warning, String yjName, String ptId, String ptName,
                                     String crzdbh, String xzqh, String xzqh2, String dkmc, Date calcTime) {
        warning.setYjName(yjName);
        warning.setPtId(ptId);
        warning.setPtName(ptName);
        // ptxmmc 与 pt_name 同值：独立列是为了和旧接口入参 ptxmmc 对齐（见建表脚本注释）
        warning.setPtxmmc(ptName);
        warning.setCrzdbh(crzdbh);
        warning.setXzqh(xzqh);
        warning.setXzqh2(xzqh2);
        warning.setDkmc(dkmc);
        warning.setCalcTime(calcTime);
    }

    /**
     * 阶段是否并行：**该阶段全部事项**的 {@code is_parallel} 是否全为 1。
     *
     * <p>★ 判据必须是「事项」而不是「阶段」：实测 6 个阶段的 {@code is_parallel} 全为 0，
     * 24 个事项里只有「项目施工阶段」下的 3 个为 0。拿阶段的 {@code is_parallel} 判定，
     * 会让所有阶段都变成串行，立项阶段（4 项并行）的历时被放大 4 倍。
     *
     * <p>空集合返回 false（串行）：没有事项时 {@code participating} 一定为空，
     * 结果都是 0，这里只是给一个确定的默认值（旧代码在空集合上直接抛异常）。
     */
    private static boolean isParallel(List<ProcessConfig> stageItems) {
        if (stageItems == null || stageItems.isEmpty()) {
            return false;
        }
        for (ProcessConfig item : stageItems) {
            if (!Integer.valueOf(1).equals(item.getIsParallel())) {
                return false;
            }
        }
        return true;
    }

    /** 问题类型：字典优先，查不到/查失败回落内置 5 项 */
    private List<String> loadProblemTypes() {
        try {
            if (commonApi != null) {
                List<DictModel> items = commonApi.queryDictItemsByCode(WarningFields.DICT_PROCESS_ISSUE);
                List<String> values = new ArrayList<>();
                if (items != null) {
                    for (DictModel item : items) {
                        if (item == null) {
                            continue;
                        }
                        String text = trimToNull(item.getText());
                        if (text == null) {
                            // 少量字典只填了 value 没填 text，回落 value（否则下拉会漏项）
                            text = trimToNull(item.getValue());
                        }
                        if (text != null && !values.contains(text)) {
                            values.add(text);
                        }
                    }
                }
                if (!values.isEmpty()) {
                    return values;
                }
            }
        } catch (Exception e) {
            log.warn("读取字典 {} 失败，问题类型回落到内置 {} 项：{}",
                    WarningFields.DICT_PROCESS_ISSUE, WarningFields.DEFAULT_PROBLEM_TYPES.size(), e.getMessage());
        }
        return new ArrayList<>(WarningFields.DEFAULT_PROBLEM_TYPES);
    }

    /** 当前登录账号（无登录上下文时返回 null，不写占位符进库） */
    private static String currentUsername() {
        try {
            Object principal = SecurityUtils.getSubject().getPrincipal();
            if (principal instanceof LoginUser) {
                String username = ((LoginUser) principal).getUsername();
                return StringUtils.isBlank(username) ? null : username;
            }
        } catch (Exception e) {
            // 定时任务 / 集成测试里没有 Shiro 上下文，属正常情况
            log.debug("获取当前登录用户失败：{}", e.getMessage());
        }
        return null;
    }

    /** 参数收敛：去空白 + 分页兜底（pageNo 从 1 起、pageSize 上限 200） */
    private static WarningQueryDTO normalize(WarningQueryDTO query) {
        WarningQueryDTO q = query == null ? new WarningQueryDTO() : query;
        q.setYjType(trimToNull(q.getYjType()));
        q.setLevelId(trimToNull(q.getLevelId()));
        q.setLevelCode(trimToNull(q.getLevelCode()));
        q.setXzqh(trimToNull(q.getXzqh()));
        q.setDkmc(trimToNull(q.getDkmc()));
        q.setPtxmmc(trimToNull(q.getPtxmmc()));
        q.setCrzdbh(trimToNull(q.getCrzdbh()));
        q.setKeyword(trimToNull(q.getKeyword()));
        int pageNo = q.getPageNo() == null || q.getPageNo() < 1 ? 1 : q.getPageNo();
        int pageSize = q.getPageSize() == null || q.getPageSize() < 1
                ? DEFAULT_PAGE_SIZE : Math.min(q.getPageSize(), MAX_PAGE_SIZE);
        q.setPageNo(pageNo);
        q.setPageSize(pageSize);
        return q;
    }

    // ---- 计数结果 Map 读取（忽略大小写，理由见 HomeStatServiceImpl.findValue）----

    private static Map<String, Long> toCountMap(List<Map<String, Object>> rows, String keyName) {
        Map<String, Long> result = new LinkedHashMap<>();
        if (rows == null) {
            return result;
        }
        for (Map<String, Object> row : rows) {
            Object key = findValue(row, keyName);
            Object count = findValue(row, "cnt");
            if (key == null) {
                continue;
            }
            long value = count instanceof Number ? ((Number) count).longValue() : 0L;
            result.put(String.valueOf(key), value);
        }
        return result;
    }

    private static long countOf(Map<String, Long> counts, String key) {
        if (key == null || counts == null) {
            return 0L;
        }
        Long value = counts.get(key);
        return value == null ? 0L : value;
    }

    private static long sumCounts(List<WarningSummaryVO.LevelCount> counts) {
        long sum = 0L;
        for (WarningSummaryVO.LevelCount item : counts) {
            if (item.getCount() != null) {
                sum += item.getCount();
            }
        }
        return sum;
    }

    private static WarningSummaryVO.TypeCount typeCount(String yjType, long count) {
        WarningSummaryVO.TypeCount item = new WarningSummaryVO.TypeCount();
        item.setYjType(yjType);
        item.setCount(count);
        return item;
    }

    private static Object findValue(Map<String, Object> row, String key) {
        if (row == null) {
            return null;
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String str(Map<String, Object> row, String key) {
        Object value = findValue(row, key);
        return value == null ? null : String.valueOf(value);
    }

    // ---- 日期 ----

    /** 截到当天零点：库里这三个日期列都是「日期」语义，带毫秒比较会让 {@code after} 判错 */
    private static Date startOfDay(Date date) {
        if (date == null) {
            return null;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    private static Date parseDate(String text) {
        String value = trimToNull(text);
        if (value == null) {
            return null;
        }
        value = value.replace('/', '-').replace('.', '-');
        for (String pattern : new String[]{PATTERN_DATE, "yyyy-M-d", "yyyyMMdd"}) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern);
                format.setLenient(false);
                return format.parse(value);
            } catch (ParseException ignored) {
                // 试下一种
            }
        }
        return null;
    }

    private static String formatDate(Date date) {
        return date == null ? null : new SimpleDateFormat(PATTERN_DATE).format(date);
    }

    private static String formatDateTime(Date date) {
        return date == null ? null : new SimpleDateFormat(PATTERN_DATETIME).format(date);
    }

    // ---- 字符串 ----

    private static String trimToNull(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return value.trim();
    }

    /** 环节情况等文本列：去空白即可（不做大小写/全角归一，取值是受控枚举） */
    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static String blankIfNull(String value) {
        return value == null ? "" : value;
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? new ArrayList<T>() : list;
    }
}
