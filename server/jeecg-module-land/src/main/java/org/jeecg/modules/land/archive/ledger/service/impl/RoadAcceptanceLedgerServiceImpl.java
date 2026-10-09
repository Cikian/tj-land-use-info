package org.jeecg.modules.land.archive.ledger.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.archive.ledger.dto.LedgerQueryDTO;
import org.jeecg.modules.land.archive.ledger.dto.LedgerSaveDTO;
import org.jeecg.modules.land.archive.ledger.entity.RoadAcceptanceLedger;
import org.jeecg.modules.land.archive.ledger.enums.LedgerMaterial;
import org.jeecg.modules.land.archive.ledger.enums.LedgerStatus;
import org.jeecg.modules.land.archive.ledger.mapper.RoadAcceptanceLedgerMapper;
import org.jeecg.modules.land.archive.ledger.service.IRoadAcceptanceLedgerService;
import org.jeecg.modules.land.archive.ledger.support.LedgerSupport;
import org.jeecg.modules.land.archive.ledger.vo.LedgerStatVO;
import org.jeecg.modules.land.archive.ledger.vo.RelatedArchiveVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 道路设施验收及移交资料台账 Service 实现（方案 2.3.2 第 7 项）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version: V1.0
 *
 * <p>本类是模块的业务总闸，负责四件事：
 * <ol>
 *   <li><b>台账增删改</b>：台账编号（自动/手工）、13 类资料归一化、{@code material_count} 重算；</li>
 *   <li><b>查询与统计</b>：列表 / 状态计数 / 8 组统计共用同一套条件（SQL 侧同一段 queryWhere）；</li>
 *   <li><b>档案关联</b>：只写自己表的 {@code archive_id} / {@code archive_count}，
 *       <b>不写</b> {@code t_archive}（档案生命周期归档案模块）；</li>
 *   <li><b>迁移数据的可追溯</b>：{@code source_facility_id} 只读不写，
 *       保证「迁移幂等键」永远不会被页面编辑污染。</li>
 * </ol>
 *
 * <p><b>★ 无状态机</b>：验收与移交都是线下办完才回系统登记，{@code status} 由前端传入，
 * 这里只校验「值在 4 个合法取值内」，不做流转顺序判定。
 *
 * <p><b>★ 验收类型 / 验收结果为什么不在后端硬校验取值</b>：
 * 这两列是字典驱动（{@code land_road_acceptance_type} / {@code land_road_acceptance_result}），
 * 中心随时可能加一个取值。若后端写死一份常量，就会出现「字典里加了新类型、页面能选、
 * 保存却被后端拒绝」的故障。它们在本模块只是展示字段（不驱动任何逻辑），
 * 因此<b>只由前端按字典限制输入，后端不做白名单</b>；
 * 而 {@code status} 是代码枚举、且驱动配色与统计口径，必须后端把关。
 */
@Slf4j
@Service
public class RoadAcceptanceLedgerServiceImpl
        extends ServiceImpl<RoadAcceptanceLedgerMapper, RoadAcceptanceLedger>
        implements IRoadAcceptanceLedgerService {

    /** 台账编号前缀，完整格式 YS-{yyyy}-{4位流水}（YS = 验收移交） */
    private static final String LEDGER_NO_PREFIX = "YS-";

    /** 台账编号生成的最大重试次数（并发下唯一键冲突时重试） */
    private static final int NO_RETRY = 3;

    /** 关联档案与挑档案列表的默认条数上限 */
    private static final int ARCHIVE_LIMIT_DEFAULT = 50;
    private static final int ARCHIVE_LIMIT_MAX = 200;

    /** 挑档案时给「查全部」的通配上限（没有过滤条件时不允许无限拉） */
    private static final int ARCHIVE_PICK_DEFAULT = 30;

    @Autowired
    private LedgerSupport ledgerSupport;

    // ==================================================================
    // 查询
    // ==================================================================

    @Override
    public IPage<RoadAcceptanceLedger> queryPage(LedgerQueryDTO query) {
        LedgerQueryDTO condition = normalize(query);
        Page<RoadAcceptanceLedger> page =
                new Page<>(condition.resolvePageNo(), condition.resolvePageSize());
        IPage<RoadAcceptanceLedger> result = baseMapper.selectLedgerPage(page, condition);
        List<RoadAcceptanceLedger> rows = result == null ? null : result.getRecords();
        // 序号在 Service 侧赋值：SQL 里用用户变量会被分页插件的 COUNT 优化搅乱，
        // MySQL 5.7 又没有 ROW_NUMBER()（见 Mapper XML 的说明）
        fillSeq(rows, (condition.resolvePageNo() - 1) * condition.resolvePageSize());
        fillRows(rows);
        return result;
    }

    @Override
    public RoadAcceptanceLedger queryDetail(String id) {
        if (StringUtils.isBlank(id)) {
            return null;
        }
        RoadAcceptanceLedger ledger = getById(id);
        if (ledger == null) {
            return null;
        }
        LedgerSupport.fillMaterialStats(ledger);
        ledger.setRelatedArchives(queryRelatedArchivesByEntity(ledger, ARCHIVE_LIMIT_DEFAULT));
        return ledger;
    }

    @Override
    public List<RoadAcceptanceLedger> queryForExport(LedgerQueryDTO query) {
        List<RoadAcceptanceLedger> rows = baseMapper.selectForExport(normalize(query));
        fillSeq(rows, 0);
        fillRows(rows);
        return rows;
    }

    @Override
    public List<LedgerStatVO.StatusCount> countByStatus(LedgerQueryDTO query) {
        LedgerQueryDTO condition = normalize(query);
        // 状态条件不能参与「各状态计数」的过滤，否则切换 tab 后其它 tab 的角标会全变 0。
        // 这里清空的是本次请求新建的 DTO，不会影响别处
        condition.setStatus(null);
        return statusCounts(condition);
    }

    @Override
    public LedgerStatVO queryStat(LedgerQueryDTO query) {
        LedgerQueryDTO condition = normalize(query);
        LedgerStatVO stat = new LedgerStatVO();

        // 1) 5 个标量指标 + 总数（一次查询）
        Map<String, Object> summary = baseMapper.selectSummary(condition);
        stat.setTotal(longOf(summary, "total"));
        stat.setMigratedCount(longOf(summary, "migratedCount"));
        stat.setManualCount(longOf(summary, "manualCount"));
        stat.setOrphanCount(longOf(summary, "orphanCount"));
        stat.setFullMaterialCount(longOf(summary, "fullMaterialCount"));
        stat.setEmptyMaterialCount(longOf(summary, "emptyMaterialCount"));

        // 2) 按状态（★ 同样清空 status 条件：否则分布图里只剩一根柱子）
        LedgerQueryDTO byStatusCondition = normalize(query);
        byStatusCondition.setStatus(null);
        List<LedgerStatVO.StatusCount> byStatus = statusCounts(byStatusCondition);
        List<LedgerStatVO.NameCount> statusChart = new ArrayList<>(byStatus.size());
        for (LedgerStatVO.StatusCount item : byStatus) {
            statusChart.add(new LedgerStatVO.NameCount(item.getStatus(), item.getCount()));
        }
        stat.setByStatus(statusChart);

        // 3) 分组统计（列名全部是代码里的常量，不接受请求参数）
        stat.setByXzqh(baseMapper.selectStatByColumn(condition, "xzqh", "未填写"));
        stat.setByGnq(baseMapper.selectStatByColumn(condition, "gnq", "非功能区"));
        stat.setByType(baseMapper.selectStatByColumn(condition, "acceptance_type", "未登记"));
        stat.setByResult(baseMapper.selectStatByColumn(condition, "acceptance_result", "未登记"));
        stat.setByPtsslb(baseMapper.selectStatByColumn(condition, "ptsslb", "未填写"));
        stat.setByYear(baseMapper.selectStatByYear(condition));

        // 4) 13 类资料归集情况（一次查询 13 个 SUM，按枚举顺序补中文名）
        Map<String, Object> coverage = baseMapper.selectMaterialCoverage(condition);
        List<LedgerStatVO.NameCount> byMaterial = new ArrayList<>(LedgerSupport.MATERIAL_TOTAL);
        for (LedgerMaterial material : LedgerMaterial.ordered()) {
            byMaterial.add(new LedgerStatVO.NameCount(material.getLabel(), longOf(coverage, material.getProperty())));
        }
        stat.setByMaterial(byMaterial);
        return stat;
    }

    // ==================================================================
    // 新增
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createLedger(LedgerSaveDTO dto) {
        if (dto == null) {
            throw new JeecgBootException("缺少台账数据");
        }
        checkRequired(dto);
        checkStatus(dto.getStatus());

        RoadAcceptanceLedger ledger = new RoadAcceptanceLedger();
        applyDto(ledger, dto);
        if (StringUtils.isBlank(ledger.getStatus())) {
            ledger.setStatus(LedgerStatus.defaultValue());
        }
        ledger.setId(IdWorker.getIdStr());
        ledger.setDelFlag(0);
        ledger.setCreateBy(ledgerSupport.currentUsername());
        ledger.setCreateTime(new Date());
        // 资料统计（material_count）唯一实现：由 13 个勾选值算出
        LedgerSupport.fillMaterialStats(ledger);
        // 关联档案数是冗余缓存，只在「能确定归属」时算（facilityId 或 crzdbh 至少有一个）
        ledger.setArchiveCount(countRelatedArchives(ledger.getFacilityId(), ledger.getCrzdbh()));

        // 台账编号：手工填了就校验唯一；没填就自动生成（并发冲突时重试）
        JeecgBootException lastError = null;
        for (int attempt = 0; attempt < NO_RETRY; attempt++) {
            String ledgerNo = StringUtils.isNotBlank(dto.getLedgerNo())
                    ? dto.getLedgerNo().trim()
                    : nextLedgerNo(resolveYear(ledger));
            checkLedgerNoUnique(ledgerNo, null);
            ledger.setLedgerNo(ledgerNo);
            if (save(ledger)) {
                lastError = null;
                break;
            }
            lastError = new JeecgBootException("台账保存失败，请重试");
        }
        if (lastError != null) {
            throw lastError;
        }
        log.info(String.format("新增道路验收移交台账成功：id=%s, ledgerNo=%s, 道路=%s, 资料数=%s/%s, 操作人=%s",
                ledger.getId(), ledger.getLedgerNo(), ledger.getRoadName(),
                ledger.getMaterialCount(), LedgerSupport.MATERIAL_TOTAL, ledger.getCreateBy()));
        return ledger.getId();
    }

    // ==================================================================
    // 编辑
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLedger(LedgerSaveDTO dto) {
        if (dto == null || StringUtils.isBlank(dto.getId())) {
            throw new JeecgBootException("缺少台账ID");
        }
        RoadAcceptanceLedger old = getById(dto.getId());
        if (old == null) {
            throw new JeecgBootException("台账不存在或已被删除");
        }
        if (StringUtils.isBlank(dto.getRoadName())) {
            throw new JeecgBootException("道路名称不能为空");
        }
        checkStatus(dto.getStatus());
        // 台账编号允许手工改写：传了就校验唯一（排除自身），没传就沿用旧值
        if (StringUtils.isNotBlank(dto.getLedgerNo())) {
            checkLedgerNoUnique(dto.getLedgerNo(), old.getId());
        }

        RoadAcceptanceLedger update = new RoadAcceptanceLedger();
        update.setId(old.getId());
        applyDto(update, dto);
        update.setLedgerNo(StringUtils.isNotBlank(dto.getLedgerNo())
                ? dto.getLedgerNo().trim() : old.getLedgerNo());
        update.setStatus(StringUtils.isNotBlank(dto.getStatus()) ? dto.getStatus().trim() : old.getStatus());
        // ★ 非空归一化：13 个勾选列 null → 0，否则显式 UPDATE 会把 null 写进 NOT NULL 列
        normalizeFlags(update);
        LedgerSupport.fillMaterialStats(update);
        update.setArchiveCount(countRelatedArchives(update.getFacilityId(), update.getCrzdbh()));
        update.setUpdateBy(ledgerSupport.currentUsername());
        update.setUpdateTime(new Date());

        // ★ 走显式 SQL 而不是 updateById：允许把已填字段（如填错的验收日期）清空为 NULL，
        //   原因见 RoadAcceptanceLedgerMapper#updateLedgerAll 的注释
        int rows = baseMapper.updateLedgerAll(update);
        if (rows <= 0) {
            throw new JeecgBootException("台账更新失败（记录可能已被删除）");
        }
        log.info("编辑道路验收移交台账成功：id={}, ledgerNo={}, 资料数={}/{}",
                update.getId(), update.getLedgerNo(), update.getMaterialCount(), LedgerSupport.MATERIAL_TOTAL);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(String id, String status) {
        if (StringUtils.isBlank(id)) {
            throw new JeecgBootException("缺少台账ID");
        }
        if (!LedgerStatus.isValid(status)) {
            throw new JeecgBootException("状态「" + status + "」不合法，允许值："
                    + String.join("/", LedgerStatus.allValues()));
        }
        RoadAcceptanceLedger old = getById(id);
        if (old == null) {
            throw new JeecgBootException("台账不存在或已被删除");
        }
        int rows = baseMapper.updateStatus(id, status.trim(),
                ledgerSupport.currentUsername(), new Date());
        if (rows <= 0) {
            throw new JeecgBootException("状态变更失败（记录可能已被删除）");
        }
        log.info("台账状态变更：id={}, {} → {}", id, old.getStatus(), status.trim());
    }

    // ==================================================================
    // 删除
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteLedger(String id) {
        if (StringUtils.isBlank(id)) {
            throw new JeecgBootException("缺少台账ID");
        }
        RoadAcceptanceLedger ledger = getById(id);
        if (ledger == null) {
            throw new JeecgBootException("台账不存在或已被删除");
        }
        // 逻辑删除：不动档案（资料原件归档案模块）、不动配套项目（底账是旧系统的业务数据）
        removeById(id);
        log.info("删除道路验收移交台账成功：id={}, ledgerNo={}, 道路={}",
                id, ledger.getLedgerNo(), ledger.getRoadName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteLedgers(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (String id : ids) {
            if (StringUtils.isNotBlank(id)) {
                deleteLedger(id.trim());
            }
        }
    }

    // ==================================================================
    // 台账编号
    // ==================================================================

    @Override
    public String generateLedgerNo(Integer year) {
        int targetYear = year == null || year < 1900 || year > 2999
                ? Calendar.getInstance().get(Calendar.YEAR)
                : year;
        return nextLedgerNo(targetYear);
    }

    @Override
    public void checkLedgerNoUnique(String ledgerNo, String excludeId) {
        if (StringUtils.isBlank(ledgerNo)) {
            throw new JeecgBootException("台账编号不能为空");
        }
        if (baseMapper.countByLedgerNo(ledgerNo.trim(), excludeId) > 0) {
            throw new JeecgBootException("台账编号「" + ledgerNo.trim() + "」已存在，请更换");
        }
    }

    /** 按年度流水生成下一个台账编号 */
    private String nextLedgerNo(int year) {
        String prefix = LEDGER_NO_PREFIX + year + "-";
        Integer max = baseMapper.selectMaxSeqOfYear(prefix);
        int next = (max == null ? 0 : max) + 1;
        return prefix + String.format("%04d", next);
    }

    // ==================================================================
    // 关联档案
    // ==================================================================

    @Override
    public List<RelatedArchiveVO> queryRelatedArchives(String id, Integer limit) {
        if (StringUtils.isBlank(id)) {
            return new ArrayList<>();
        }
        RoadAcceptanceLedger ledger = getById(id);
        if (ledger == null) {
            return new ArrayList<>();
        }
        return queryRelatedArchivesByEntity(ledger, resolveArchiveLimit(limit));
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
            throw new JeecgBootException("缺少台账ID");
        }
        if (StringUtils.isBlank(archiveId)) {
            throw new JeecgBootException("请选择要关联的档案");
        }
        RoadAcceptanceLedger ledger = getById(id);
        if (ledger == null) {
            throw new JeecgBootException("台账不存在或已被删除");
        }
        if (baseMapper.countArchiveById(archiveId.trim()) <= 0) {
            throw new JeecgBootException("档案不存在或已被删除，无法关联");
        }
        int rows = baseMapper.updateArchiveLink(ledger.getId(), archiveId.trim(),
                countRelatedArchives(ledger.getFacilityId(), ledger.getCrzdbh()),
                ledgerSupport.currentUsername(), new Date());
        if (rows <= 0) {
            throw new JeecgBootException("关联档案失败（记录可能已被删除）");
        }
        log.info("台账关联档案：ledgerId={}, ledgerNo={}, archiveId={}, 操作人={}",
                ledger.getId(), ledger.getLedgerNo(), archiveId.trim(), ledgerSupport.currentUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlinkArchive(String id) {
        if (StringUtils.isBlank(id)) {
            throw new JeecgBootException("缺少台账ID");
        }
        RoadAcceptanceLedger ledger = getById(id);
        if (ledger == null) {
            throw new JeecgBootException("台账不存在或已被删除");
        }
        // archiveId 传 null 即为取消关联（显式 SQL 能写 NULL）
        int rows = baseMapper.updateArchiveLink(ledger.getId(), null,
                countRelatedArchives(ledger.getFacilityId(), ledger.getCrzdbh()),
                ledgerSupport.currentUsername(), new Date());
        if (rows <= 0) {
            throw new JeecgBootException("取消关联失败（记录可能已被删除）");
        }
        log.info("台账取消关联档案：ledgerId={}, ledgerNo={}, 原 archiveId={}",
                ledger.getId(), ledger.getLedgerNo(), ledger.getArchiveId());
    }

    // ==================================================================
    // 私有工具
    // ==================================================================

    /**
     * 条件归一化 + 校验，并**返回一份独立副本**。
     *
     * <p>★ 为什么要复制：统计接口里「按状态分布」需要把 {@code status} 条件清掉
     * （否则切换筛选后分布图里只剩一根柱子），若直接复用请求传入的同一个 DTO，
     * 这次清空会连带把后面按行政区/类型/年度/资料的分组统计也变成「不带状态筛选」，
     * 于是指标卡、分布图与分组统计的口径就对不上了。
     * 返回副本后，各分组可以放心地各自调整条件。
     *
     * <p>另一个重点是 {@code missingMaterial} 的白名单校验：这个值最终会参与 SQL 的条件选择，
     * 因此在这里先用 {@link LedgerMaterial#ofProperty} 判合法，非法直接报错
     * （Mapper XML 里还有一层 {@code choose/otherwise} 兜底）。
     */
    private LedgerQueryDTO normalize(LedgerQueryDTO query) {
        LedgerQueryDTO condition = new LedgerQueryDTO();
        if (query != null) {
            BeanUtils.copyProperties(query, condition);
        }
        String missing = trimToNull(condition.getMissingMaterial());
        if (missing != null) {
            // 驼峰属性名（hasJgwj）与数据库列名（has_jgwj）都接受：
            // 前者是前端契约，后者方便用 SQL/DBA 里的列名直接深链排查，两种写法最终都归一到属性名
            LedgerMaterial material = LedgerMaterial.ofProperty(missing);
            if (material == null) {
                material = LedgerMaterial.ofColumn(missing);
            }
            if (material == null) {
                throw new JeecgBootException("「缺少资料」筛选值「" + missing + "」不合法，"
                        + "应为 13 类资料的属性名（如 hasJgwj）或列名（如 has_jgwj）");
            }
            // 回写规范化后的属性名，保证 XML 里的 choose 分支能精确命中
            condition.setMissingMaterial(material.getProperty());
        }
        return condition;
    }

    /** 各状态计数：固定返回 4 项，数量为 0 的状态也返回（前端 tab 不必兜底） */
    private List<LedgerStatVO.StatusCount> statusCounts(LedgerQueryDTO condition) {
        Map<String, Long> counted = new LinkedHashMap<>();
        List<LedgerStatVO.StatusCount> rows = baseMapper.selectCountGroupByStatus(condition);
        if (rows != null) {
            for (LedgerStatVO.StatusCount row : rows) {
                if (row != null && StringUtils.isNotBlank(row.getStatus())) {
                    counted.put(row.getStatus().trim(), row.getCount());
                }
            }
        }
        List<LedgerStatVO.StatusCount> result = new ArrayList<>(LedgerStatus.values().length);
        for (String status : LedgerStatus.allValues()) {
            Long count = counted.get(status);
            result.add(new LedgerStatVO.StatusCount(status, count == null ? 0L : count));
        }
        return result;
    }

    /** 台账的关联档案（配套项目ID 优先，宗地编号兜底；两者都空时返回空表） */
    private List<RelatedArchiveVO> queryRelatedArchivesByEntity(RoadAcceptanceLedger ledger, int limit) {
        if (ledger == null) {
            return new ArrayList<>();
        }
        String facilityId = trimToNull(ledger.getFacilityId());
        String crzdbh = trimToNull(ledger.getCrzdbh());
        if (facilityId == null && crzdbh == null) {
            return new ArrayList<>();
        }
        List<RelatedArchiveVO> archives = baseMapper.selectRelatedArchives(facilityId, crzdbh, limit);
        return archives == null ? new ArrayList<>() : archives;
    }

    /** 关联档案数（冗余列 archive_count 的取值；两边都空时为 0，不查库） */
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

    /** 给台账行补「序号」：offset = 前面已经翻过的行数，导出时为 0 */
    private static void fillSeq(List<RoadAcceptanceLedger> rows, int offset) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        int seq = offset + 1;
        for (RoadAcceptanceLedger row : rows) {
            if (row == null) {
                continue;
            }
            row.setSeq(seq++);
        }
    }

    /** 列表 / 导出的每一行都补齐 13 类资料的统计口径 */
    private static void fillRows(List<RoadAcceptanceLedger> rows) {
        if (rows == null) {
            return;
        }
        for (RoadAcceptanceLedger row : rows) {
            LedgerSupport.fillMaterialStats(row);
        }
    }

    /**
     * 必填校验。
     *
     * <p>只强制 {@code road_name}：它是 DDL 里的 NOT NULL，缺了必然抛 SQL 异常，
     * 这里提前拦住把「数据库约束报错」变成「一句看得懂的业务提示」。
     * 其余字段（验收日期、验收结果、接收管养单位等）全部允许为空 ——
     * 台账是「逐步补录」的：迁移过来的 1340 条里有 713 条一类资料都还没勾，
     * 强制必填会让历史数据根本存不下去。
     */
    private void checkRequired(LedgerSaveDTO dto) {
        if (StringUtils.isBlank(dto.getRoadName())) {
            throw new JeecgBootException("道路名称不能为空");
        }
    }

    /** 状态必须是固定 4 值之一（无状态机，只校验取值） */
    private void checkStatus(String status) {
        String value = trimToNull(status);
        if (value == null) {
            return;
        }
        if (!LedgerStatus.isValid(value)) {
            throw new JeecgBootException("状态「" + value + "」不合法，允许值："
                    + String.join("/", LedgerStatus.allValues()));
        }
    }

    /** DTO → 实体（新增与编辑共用；13 个勾选列在这里统一归一化） */
    private void applyDto(RoadAcceptanceLedger ledger, LedgerSaveDTO dto) {
        ledger.setRoadName(trimToNull(dto.getRoadName()));
        ledger.setXzqh(trimToNull(dto.getXzqh()));
        ledger.setGnq(trimToNull(dto.getGnq()));
        ledger.setCrzdbh(trimToNull(dto.getCrzdbh()));
        ledger.setLandId(trimToNull(dto.getLandId()));
        ledger.setDkmc(trimToNull(dto.getDkmc()));
        ledger.setPtsslb(trimToNull(dto.getPtsslb()));
        ledger.setFacilityId(trimToNull(dto.getFacilityId()));
        // 配套项目名称与道路名称同值：台账页的「道路名称」就是配套项目名，
        // 保留两列是为了以后允许「一条道路拆多个配套项目」时不至于无列可用
        ledger.setPtxmmc(StringUtils.isNotBlank(dto.getPtxmmc())
                ? dto.getPtxmmc().trim() : trimToNull(dto.getRoadName()));
        ledger.setDldj(trimToNull(dto.getDldj()));
        ledger.setJsdw(trimToNull(dto.getJsdw()));
        ledger.setSgdw(trimToNull(dto.getSgdw()));
        ledger.setJldw(trimToNull(dto.getJldw()));

        ledger.setAcceptanceType(trimToNull(dto.getAcceptanceType()));
        ledger.setAcceptanceNo(trimToNull(dto.getAcceptanceNo()));
        ledger.setAcceptanceDate(dto.getAcceptanceDate());
        ledger.setAcceptanceOrg(trimToNull(dto.getAcceptanceOrg()));
        ledger.setAcceptanceResult(trimToNull(dto.getAcceptanceResult()));
        ledger.setCompleteDate(dto.getCompleteDate());

        ledger.setHasSgxk(LedgerSupport.flag(dto.getHasSgxk()));
        ledger.setHasYsbg(LedgerSupport.flag(dto.getHasYsbg()));
        ledger.setHasJgtc(LedgerSupport.flag(dto.getHasJgtc()));
        ledger.setHasZljdbg(LedgerSupport.flag(dto.getHasZljdbg()));
        ledger.setHasCljybg(LedgerSupport.flag(dto.getHasCljybg()));
        ledger.setHasAjbg(LedgerSupport.flag(dto.getHasAjbg()));
        ledger.setHasGhyshgz(LedgerSupport.flag(dto.getHasGhyshgz()));
        ledger.setHasJgbaba(LedgerSupport.flag(dto.getHasJgbaba()));
        ledger.setHasDazxys(LedgerSupport.flag(dto.getHasDazxys()));
        ledger.setHasDlyjd(LedgerSupport.flag(dto.getHasDlyjd()));
        ledger.setHasYhxy(LedgerSupport.flag(dto.getHasYhxy()));
        ledger.setHasJgwj(LedgerSupport.flag(dto.getHasJgwj()));
        ledger.setHasYjwj(LedgerSupport.flag(dto.getHasYjwj()));

        ledger.setHandoverUnit(trimToNull(dto.getHandoverUnit()));
        ledger.setReceiveUnit(trimToNull(dto.getReceiveUnit()));
        ledger.setHandoverDate(dto.getHandoverDate());

        ledger.setStatus(trimToNull(dto.getStatus()));
        ledger.setRemark(trimToNull(dto.getRemark()));
        // ★ 注意：这里<b>不</b>处理 source_facility_id / del_flag / archive_id
        //   （迁移幂等键、删除标记、档案指针都不允许被表单改动）
    }

    /** 13 个勾选列的 null → 0（显式 UPDATE 会把 null 直接写进 NOT NULL 列，必须归一） */
    private void normalizeFlags(RoadAcceptanceLedger ledger) {
        ledger.setHasSgxk(LedgerSupport.flag(ledger.getHasSgxk()));
        ledger.setHasYsbg(LedgerSupport.flag(ledger.getHasYsbg()));
        ledger.setHasJgtc(LedgerSupport.flag(ledger.getHasJgtc()));
        ledger.setHasZljdbg(LedgerSupport.flag(ledger.getHasZljdbg()));
        ledger.setHasCljybg(LedgerSupport.flag(ledger.getHasCljybg()));
        ledger.setHasAjbg(LedgerSupport.flag(ledger.getHasAjbg()));
        ledger.setHasGhyshgz(LedgerSupport.flag(ledger.getHasGhyshgz()));
        ledger.setHasJgbaba(LedgerSupport.flag(ledger.getHasJgbaba()));
        ledger.setHasDazxys(LedgerSupport.flag(ledger.getHasDazxys()));
        ledger.setHasDlyjd(LedgerSupport.flag(ledger.getHasDlyjd()));
        ledger.setHasYhxy(LedgerSupport.flag(ledger.getHasYhxy()));
        ledger.setHasJgwj(LedgerSupport.flag(ledger.getHasJgwj()));
        ledger.setHasYjwj(LedgerSupport.flag(ledger.getHasYjwj()));
    }

    /** 编号年度：优先按验收日期，其次按竣工日期，最后按当前年 */
    private int resolveYear(RoadAcceptanceLedger ledger) {
        Date date = ledger.getAcceptanceDate() != null ? ledger.getAcceptanceDate()
                : (ledger.getCompleteDate() != null ? ledger.getCompleteDate() : new Date());
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

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 当前登录账号（供导出等场景复用） */
    public String currentUsername() {
        return ledgerSupport.currentUsername();
    }
}
