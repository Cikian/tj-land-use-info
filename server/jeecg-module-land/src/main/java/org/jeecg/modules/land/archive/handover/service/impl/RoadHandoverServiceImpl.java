package org.jeecg.modules.land.archive.handover.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.archive.handover.dto.HandoverQueryDTO;
import org.jeecg.modules.land.archive.handover.dto.HandoverSaveDTO;
import org.jeecg.modules.land.archive.handover.entity.RoadHandover;
import org.jeecg.modules.land.archive.handover.enums.HandoverStatus;
import org.jeecg.modules.land.archive.handover.mapper.RoadHandoverMapper;
import org.jeecg.modules.land.archive.handover.service.IRoadHandoverService;
import org.jeecg.modules.land.archive.handover.support.HandoverSupport;
import org.jeecg.modules.land.archive.handover.vo.HandoverStatVO;
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
 * @Description: 道路交付及养护协议移交事项 Service 实现（方案 2.3.2 第 6 项）
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version V1.0
 *
 * <p>与台账模块同一套做法与取舍：
 * <ul>
 *   <li>列表/统计/导出共用同一套条件（SQL 侧同一段 queryWhere）；</li>
 *   <li>序号在 Service 层算（MySQL 5.7 无 ROW_NUMBER，用户变量会被分页插件搅乱）；</li>
 *   <li><b>编辑走显式 SQL</b>（{@code updateHandoverAll}）而不是 {@code updateById}：
 *       MP 默认 NOT_NULL 策略会让「把填错的协议日期清空」做不到；</li>
 *   <li>{@code sourceFacilityId}（迁移幂等键）只读不写，避免人工编辑把重跑迁移搞乱；</li>
 *   <li>状态用代码枚举把关；移交类型由字典驱动、不在后端硬校验（那两组措辞中心会调整）；</li>
 *   <li>{@code normalize} 返回**独立副本**：统计时「按状态分布」要清掉 status 条件，
 *       若复用同一个 DTO 会连带把其它分组统计也变成「不带状态筛选」（台账模块踩过）。</li>
 * </ul>
 */
@Slf4j
@Service
public class RoadHandoverServiceImpl extends ServiceImpl<RoadHandoverMapper, RoadHandover>
        implements IRoadHandoverService {

    /** 移交编号前缀，完整格式 YJ-{yyyy}-{4位流水}（YJ = 移交） */
    private static final String NO_PREFIX = "YJ-";

    /** 编号生成的最大重试次数（并发下唯一键冲突时重试） */
    private static final int NO_RETRY = 3;

    /** 关联档案与挑档案列表的默认/最大条数 */
    private static final int ARCHIVE_LIMIT_DEFAULT = 50;
    private static final int ARCHIVE_LIMIT_MAX = 200;
    private static final int ARCHIVE_PICK_DEFAULT = 30;

    /** 「接收管养单位 Top N」统计的默认条数 */
    private static final int RECEIVE_UNIT_TOP = 10;

    @Autowired
    private HandoverSupport handoverSupport;

    // ==================================================================
    // 查询
    // ==================================================================

    @Override
    public IPage<RoadHandover> queryPage(HandoverQueryDTO query) {
        HandoverQueryDTO condition = normalize(query);
        Page<RoadHandover> page = new Page<>(condition.resolvePageNo(), condition.resolvePageSize());
        IPage<RoadHandover> result = baseMapper.selectHandoverPage(page, condition);
        fillSeq(result == null ? null : result.getRecords(),
                (condition.resolvePageNo() - 1) * condition.resolvePageSize());
        return result;
    }

    @Override
    public RoadHandover queryDetail(String id) {
        if (StringUtils.isBlank(id)) {
            return null;
        }
        RoadHandover handover = getById(id);
        if (handover == null) {
            return null;
        }
        handover.setRelatedArchives(queryRelatedArchivesByEntity(handover, ARCHIVE_LIMIT_DEFAULT));
        return handover;
    }

    @Override
    public List<RoadHandover> queryForExport(HandoverQueryDTO query) {
        List<RoadHandover> rows = baseMapper.selectForExport(normalize(query));
        fillSeq(rows, 0);
        return rows;
    }

    @Override
    public List<HandoverStatVO.StatusCount> countByStatus(HandoverQueryDTO query) {
        HandoverQueryDTO condition = normalize(query);
        // 状态条件不参与「各状态计数」，否则切换 tab 后其它 tab 的角标会全变 0
        condition.setStatus(null);
        return statusCounts(condition);
    }

    @Override
    public HandoverStatVO queryStat(HandoverQueryDTO query) {
        HandoverQueryDTO condition = normalize(query);
        HandoverStatVO stat = new HandoverStatVO();

        Map<String, Object> summary = baseMapper.selectSummary(condition);
        stat.setTotal(longOf(summary, "total"));
        stat.setMigratedCount(longOf(summary, "migratedCount"));
        stat.setManualCount(longOf(summary, "manualCount"));
        stat.setArchivedCount(longOf(summary, "archivedCount"));
        stat.setMissingAgreementCount(longOf(summary, "missingAgreementCount"));
        stat.setMaintenanceExpiringCount(longOf(summary, "maintenanceExpiringCount"));
        stat.setMaintenanceExpiredCount(longOf(summary, "maintenanceExpiredCount"));
        stat.setOrphanCount(longOf(summary, "orphanCount"));

        // 按状态（清掉 status 条件，否则分布图只剩一根柱子）
        HandoverQueryDTO byStatusCondition = normalize(query);
        byStatusCondition.setStatus(null);
        List<HandoverStatVO.NameCount> statusChart = new ArrayList<>();
        for (HandoverStatVO.StatusCount item : statusCounts(byStatusCondition)) {
            statusChart.add(new HandoverStatVO.NameCount(item.getStatus(), item.getCount()));
        }
        stat.setByStatus(statusChart);

        // 分组统计（列名全部是代码常量，不接受请求参数）
        stat.setByType(baseMapper.selectStatByColumn(condition, "handover_type", "未登记"));
        stat.setByXzqh(baseMapper.selectStatByColumn(condition, "xzqh", "未填写"));
        stat.setByDldj(baseMapper.selectStatByColumn(condition, "dldj", "未填写"));
        stat.setByYear(baseMapper.selectStatByYear(condition));
        stat.setByReceiveUnit(baseMapper.selectStatByReceiveUnit(condition, RECEIVE_UNIT_TOP));
        return stat;
    }

    // ==================================================================
    // 新增 / 编辑 / 状态 / 删除
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createHandover(HandoverSaveDTO dto) {
        if (dto == null) {
            throw new JeecgBootException("缺少移交事项数据");
        }
        if (StringUtils.isBlank(dto.getRoadName())) {
            throw new JeecgBootException("道路名称不能为空");
        }
        checkStatus(dto.getStatus());

        RoadHandover handover = new RoadHandover();
        applyDto(handover, dto);
        if (StringUtils.isBlank(handover.getStatus())) {
            handover.setStatus(HandoverStatus.defaultValue());
        }
        handover.setId(IdWorker.getIdStr());
        handover.setDelFlag(0);
        handover.setCreateBy(handoverSupport.currentUsername());
        handover.setCreateTime(new Date());
        handover.setArchiveCount(countRelatedArchives(handover.getFacilityId(), handover.getCrzdbh()));

        JeecgBootException lastError = null;
        for (int attempt = 0; attempt < NO_RETRY; attempt++) {
            String no = StringUtils.isNotBlank(dto.getHandoverNo())
                    ? dto.getHandoverNo().trim()
                    : nextHandoverNo(resolveYear(handover));
            checkHandoverNoUnique(no, null);
            handover.setHandoverNo(no);
            if (save(handover)) {
                lastError = null;
                break;
            }
            lastError = new JeecgBootException("移交事项保存失败，请重试");
        }
        if (lastError != null) {
            throw lastError;
        }
        log.info("新增道路移交事项成功：id={}, handoverNo={}, 道路={}, 操作人={}",
                handover.getId(), handover.getHandoverNo(), handover.getRoadName(), handover.getCreateBy());
        return handover.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateHandover(HandoverSaveDTO dto) {
        if (dto == null || StringUtils.isBlank(dto.getId())) {
            throw new JeecgBootException("缺少移交事项ID");
        }
        RoadHandover old = getById(dto.getId());
        if (old == null) {
            throw new JeecgBootException("移交事项不存在或已被删除");
        }
        if (StringUtils.isBlank(dto.getRoadName())) {
            throw new JeecgBootException("道路名称不能为空");
        }
        checkStatus(dto.getStatus());
        if (StringUtils.isNotBlank(dto.getHandoverNo())) {
            checkHandoverNoUnique(dto.getHandoverNo(), old.getId());
        }

        RoadHandover update = new RoadHandover();
        update.setId(old.getId());
        applyDto(update, dto);
        update.setHandoverNo(StringUtils.isNotBlank(dto.getHandoverNo())
                ? dto.getHandoverNo().trim() : old.getHandoverNo());
        update.setStatus(StringUtils.isNotBlank(dto.getStatus()) ? dto.getStatus().trim() : old.getStatus());
        update.setArchiveCount(countRelatedArchives(update.getFacilityId(), update.getCrzdbh()));
        update.setUpdateBy(handoverSupport.currentUsername());
        update.setUpdateTime(new Date());

        if (baseMapper.updateHandoverAll(update) <= 0) {
            throw new JeecgBootException("移交事项更新失败（记录可能已被删除）");
        }
        log.info("编辑道路移交事项成功：id={}, handoverNo={}, 协议编号={}",
                update.getId(), update.getHandoverNo(), update.getAgreementNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(String id, String status) {
        if (StringUtils.isBlank(id)) {
            throw new JeecgBootException("缺少移交事项ID");
        }
        if (!HandoverStatus.isValid(status)) {
            throw new JeecgBootException("状态「" + status + "」不合法，允许值："
                    + String.join("/", HandoverStatus.allValues()));
        }
        RoadHandover old = getById(id);
        if (old == null) {
            throw new JeecgBootException("移交事项不存在或已被删除");
        }
        if (baseMapper.updateStatus(id, status.trim(), handoverSupport.currentUsername(), new Date()) <= 0) {
            throw new JeecgBootException("状态变更失败（记录可能已被删除）");
        }
        log.info("移交事项状态变更：id={}, {} → {}", id, old.getStatus(), status.trim());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteHandover(String id) {
        if (StringUtils.isBlank(id)) {
            throw new JeecgBootException("缺少移交事项ID");
        }
        RoadHandover handover = getById(id);
        if (handover == null) {
            throw new JeecgBootException("移交事项不存在或已被删除");
        }
        removeById(id);
        log.info("删除道路移交事项成功：id={}, handoverNo={}", id, handover.getHandoverNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteHandovers(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (String id : ids) {
            if (StringUtils.isNotBlank(id)) {
                deleteHandover(id.trim());
            }
        }
    }

    // ==================================================================
    // 移交编号
    // ==================================================================

    @Override
    public String generateHandoverNo(Integer year) {
        int targetYear = year == null || year < 1900 || year > 2999
                ? Calendar.getInstance().get(Calendar.YEAR) : year;
        return nextHandoverNo(targetYear);
    }

    @Override
    public void checkHandoverNoUnique(String handoverNo, String excludeId) {
        if (StringUtils.isBlank(handoverNo)) {
            throw new JeecgBootException("移交编号不能为空");
        }
        if (baseMapper.countByHandoverNo(handoverNo.trim(), excludeId) > 0) {
            throw new JeecgBootException("移交编号「" + handoverNo.trim() + "」已存在，请更换");
        }
    }

    private String nextHandoverNo(int year) {
        String prefix = NO_PREFIX + year + "-";
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
        RoadHandover handover = getById(id);
        if (handover == null) {
            return new ArrayList<>();
        }
        return queryRelatedArchivesByEntity(handover, resolveArchiveLimit(limit));
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
            throw new JeecgBootException("缺少移交事项ID");
        }
        if (StringUtils.isBlank(archiveId)) {
            throw new JeecgBootException("请选择要关联的档案");
        }
        RoadHandover handover = getById(id);
        if (handover == null) {
            throw new JeecgBootException("移交事项不存在或已被删除");
        }
        if (baseMapper.countArchiveById(archiveId.trim()) <= 0) {
            throw new JeecgBootException("档案不存在或已被删除，无法关联");
        }
        int rows = baseMapper.updateArchiveLink(handover.getId(), archiveId.trim(),
                countRelatedArchives(handover.getFacilityId(), handover.getCrzdbh()),
                handoverSupport.currentUsername(), new Date());
        if (rows <= 0) {
            throw new JeecgBootException("关联档案失败（记录可能已被删除）");
        }
        log.info("移交事项关联档案：id={}, handoverNo={}, archiveId={}", handover.getId(),
                handover.getHandoverNo(), archiveId.trim());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlinkArchive(String id) {
        if (StringUtils.isBlank(id)) {
            throw new JeecgBootException("缺少移交事项ID");
        }
        RoadHandover handover = getById(id);
        if (handover == null) {
            throw new JeecgBootException("移交事项不存在或已被删除");
        }
        // archiveId 传 null 即为取消关联（显式 SQL 能写 NULL）
        int rows = baseMapper.updateArchiveLink(handover.getId(), null,
                countRelatedArchives(handover.getFacilityId(), handover.getCrzdbh()),
                handoverSupport.currentUsername(), new Date());
        if (rows <= 0) {
            throw new JeecgBootException("取消关联失败（记录可能已被删除）");
        }
        log.info("移交事项取消关联档案：id={}, 原 archiveId={}", handover.getId(), handover.getArchiveId());
    }

    // ==================================================================
    // 私有工具
    // ==================================================================

    /** 条件归一化，返回**独立副本**（避免统计时清 status 污染列表条件） */
    private HandoverQueryDTO normalize(HandoverQueryDTO query) {
        HandoverQueryDTO condition = new HandoverQueryDTO();
        if (query != null) {
            BeanUtils.copyProperties(query, condition);
        }
        return condition;
    }

    /** 各状态计数：固定返回 3 项，数量为 0 的也返回 */
    private List<HandoverStatVO.StatusCount> statusCounts(HandoverQueryDTO condition) {
        Map<String, Long> counted = new LinkedHashMap<>();
        List<HandoverStatVO.StatusCount> rows = baseMapper.selectCountGroupByStatus(condition);
        if (rows != null) {
            for (HandoverStatVO.StatusCount row : rows) {
                if (row != null && StringUtils.isNotBlank(row.getStatus())) {
                    counted.put(row.getStatus().trim(), row.getCount());
                }
            }
        }
        List<HandoverStatVO.StatusCount> result = new ArrayList<>(HandoverStatus.values().length);
        for (String status : HandoverStatus.allValues()) {
            Long count = counted.get(status);
            result.add(new HandoverStatVO.StatusCount(status, count == null ? 0L : count));
        }
        return result;
    }

    private List<RelatedArchiveVO> queryRelatedArchivesByEntity(RoadHandover handover, int limit) {
        if (handover == null) {
            return new ArrayList<>();
        }
        String facilityId = trimToNull(handover.getFacilityId());
        String crzdbh = trimToNull(handover.getCrzdbh());
        if (facilityId == null && crzdbh == null) {
            return new ArrayList<>();
        }
        List<RelatedArchiveVO> archives = baseMapper.selectRelatedArchives(facilityId, crzdbh, limit);
        return archives == null ? new ArrayList<>() : archives;
    }

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

    private static void fillSeq(List<RoadHandover> rows, int offset) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        int seq = offset + 1;
        for (RoadHandover row : rows) {
            if (row == null) {
                continue;
            }
            row.setSeq(seq++);
        }
    }

    /** 状态必须是固定 3 值之一 */
    private void checkStatus(String status) {
        String value = trimToNull(status);
        if (value == null) {
            return;
        }
        if (!HandoverStatus.isValid(value)) {
            throw new JeecgBootException("状态「" + value + "」不合法，允许值："
                    + String.join("/", HandoverStatus.allValues()));
        }
    }

    /** DTO → 实体（新增与编辑共用） */
    private void applyDto(RoadHandover handover, HandoverSaveDTO dto) {
        handover.setRoadName(trimToNull(dto.getRoadName()));
        handover.setRoadCode(trimToNull(dto.getRoadCode()));
        handover.setDldj(trimToNull(dto.getDldj()));
        handover.setStartPoint(trimToNull(dto.getStartPoint()));
        handover.setEndPoint(trimToNull(dto.getEndPoint()));
        handover.setLengthM(dto.getLengthM());
        handover.setRedLineWidth(dto.getRedLineWidth());
        handover.setXzqh(trimToNull(dto.getXzqh()));
        handover.setGnq(trimToNull(dto.getGnq()));
        handover.setPtsslb(trimToNull(dto.getPtsslb()));

        handover.setCrzdbh(trimToNull(dto.getCrzdbh()));
        handover.setLandId(trimToNull(dto.getLandId()));
        handover.setDkmc(trimToNull(dto.getDkmc()));
        handover.setFacilityId(trimToNull(dto.getFacilityId()));
        // 配套项目名称与道路名称同值（保留两列是为了以后允许「一条道路拆多个配套项目」）
        handover.setPtxmmc(StringUtils.isNotBlank(dto.getPtxmmc())
                ? dto.getPtxmmc().trim() : trimToNull(dto.getRoadName()));

        handover.setHandoverType(trimToNull(dto.getHandoverType()));
        handover.setAgreementNo(trimToNull(dto.getAgreementNo()));
        handover.setAgreementName(trimToNull(dto.getAgreementName()));
        handover.setAgreementDate(dto.getAgreementDate());
        handover.setBuildUnit(trimToNull(dto.getBuildUnit()));
        handover.setReceiveUnit(trimToNull(dto.getReceiveUnit()));
        handover.setHandoverDate(dto.getHandoverDate());
        handover.setMaintenanceStart(dto.getMaintenanceStart());
        handover.setMaintenanceEnd(dto.getMaintenanceEnd());

        handover.setStatus(trimToNull(dto.getStatus()));
        handover.setRemark(trimToNull(dto.getRemark()));
        // ★ 不处理 sourceFacilityId / delFlag / archiveId（迁移幂等键、删除标记、档案指针不由表单改）
    }

    /** 编号年度：优先按协议签订日期，其次按移交日期，最后按当前年 */
    private int resolveYear(RoadHandover handover) {
        Date date = handover.getAgreementDate() != null ? handover.getAgreementDate()
                : (handover.getHandoverDate() != null ? handover.getHandoverDate() : new Date());
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar.get(Calendar.YEAR);
    }

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
        return handoverSupport.currentUsername();
    }
}
