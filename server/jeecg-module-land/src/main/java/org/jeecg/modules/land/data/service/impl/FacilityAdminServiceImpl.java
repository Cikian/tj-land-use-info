package org.jeecg.modules.land.data.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.data.dto.FacilitySaveDTO;
import org.jeecg.modules.land.data.dto.LandAdminQueryDTO;
import org.jeecg.modules.land.data.entity.Facility;
import org.jeecg.modules.land.data.entity.Land;
import org.jeecg.modules.land.data.fieldconfig.DataFieldLabels;
import org.jeecg.modules.land.data.mapper.FacilityMapper;
import org.jeecg.modules.land.data.oplog.entity.DataChangeLog;
import org.jeecg.modules.land.data.oplog.support.ChangeLogSupport;
import org.jeecg.modules.land.data.process.entity.FacilityProcess;
import org.jeecg.modules.land.data.process.entity.ProcessConfig;
import org.jeecg.modules.land.data.process.enums.ProcessStatus;
import org.jeecg.modules.land.data.process.mapper.FacilityProcessMapper;
import org.jeecg.modules.land.data.process.mapper.ProcessConfigMapper;
import org.jeecg.modules.land.data.process.support.WorkdayCalculator;
import org.jeecg.modules.land.data.service.IFacilityAdminService;
import org.jeecg.modules.land.data.service.ILandService;
import org.jeecg.modules.land.data.support.DataSupport;
import org.jeecg.modules.land.data.vo.FacilityProcessTreeVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @Description: 数据管理 · 配套地块数据录入（1 宗地 N 配套 + 29 环节进度）Service 实现
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p><b>★ 配套表 {@code t_supporting_facilities} 的三条结构差异必须牢记</b>
 * （都来自「列结构沿用旧系统配套表」这一决定，写错任何一条都会导致
 * 「查不到数据」或「删不掉」）：
 * <ol>
 *   <li><b>软删列是驼峰 {@code delFlag}，且类型是 varchar('0'/'1')</b>，
 *       与宗地表的下划线 tinyint {@code del_flag} 完全不同。
 *       因此本 Service 的所有查询都<b>手写 {@code delFlag = '0'} 条件</b>，
 *       而不是依赖 {@code @TableLogic}（Facility 实体上并没有这个注解）。</li>
 *   <li><b>没有唯一键</b>：旧表本来就没有。「同一宗地下配套项目名称不重复」这条
 *       只能靠 Service 校验，数据库不会兜底 —— 所以校验必须扎实。</li>
 *   <li><b>创建时间列是 {@code createTime}（驼峰）</b>，实体上已用
 *       {@code @TableField} 显式映射，见 {@link Facility}。</li>
 * </ol>
 *
 * <p><b>★ 相对旧系统的两处收紧</b>：
 * <ol>
 *   <li><b>必须挂到真实存在的宗地</b>。旧 {@code createSpecial} 只校验 {@code crzdbh}
 *       非空，不校验宗地是否存在，于是产生了设计文档实测的
 *       <b>60 行「孤儿配套」</b>（配套表里有、宗地表里查不到）——
 *       档案/收发文按宗地关联时这些配套永远挂不上。这里在写入前硬校验。</li>
 *   <li><b>预计结束时间按工作日推算</b>，且算法重写（见
 *       {@link WorkdayCalculator} 的类注释：旧递归算法在长假跨区间时会滚雪球）。</li>
 * </ol>
 */
@Slf4j
@Service
public class FacilityAdminServiceImpl extends ServiceImpl<FacilityMapper, Facility>
        implements IFacilityAdminService {

    private static final String PATTERN_DATE = "yyyy-MM-dd";
    private static final int MAX_PAGE_SIZE = 500;
    private static final int DEFAULT_HISTORY_LIMIT = 50;

    /** 允许的合法环节情况（与 sys_dict 的 land_process_status 逐字一致） */
    private static final Set<String> VALID_PROCESS_STATUS = new HashSet<>(ProcessStatus.allValues());

    @Autowired
    private FacilityMapper facilityMapper;

    @Autowired
    private ILandService landService;

    @Autowired
    private ProcessConfigMapper processConfigMapper;

    @Autowired
    private FacilityProcessMapper facilityProcessMapper;

    @Autowired
    private ChangeLogSupport changeLogSupport;

    @Autowired
    private WorkdayCalculator workdayCalculator;

    @Autowired
    private DataSupport dataSupport;

    // ==================================================================
    // 一、查询
    // ==================================================================

    @Override
    public IPage<Facility> queryPage(LandAdminQueryDTO query) {
        LandAdminQueryDTO condition = query == null ? new LandAdminQueryDTO() : query;
        QueryWrapper<Facility> wrapper = new QueryWrapper<>();
        // ★ 手写软删条件：Facility 没有 @TableLogic，不写就会把已移除的配套也查出来
        wrapper.eq("delFlag", "0");

        if (StringUtils.isNotBlank(condition.getCrzdbh())) {
            wrapper.like("crzdbh", condition.getCrzdbh().trim());
        }
        if (StringUtils.isNotBlank(condition.getDkmc())) {
            wrapper.like("dkmc", condition.getDkmc().trim());
        }
        if (StringUtils.isNotBlank(condition.getXzqh())) {
            wrapper.eq("xzqh", condition.getXzqh().trim());
        }
        if (StringUtils.isNotBlank(condition.getXmfl())) {
            wrapper.eq("xmfl", condition.getXmfl().trim());
        }
        if (StringUtils.isNotBlank(condition.getSrr())) {
            // 受让人字段在配套表里没有，这里复用它承载「配套项目名称」的模糊条件（前端同一个输入框）
            wrapper.like("ptxmmc", condition.getSrr().trim());
        }
        if (StringUtils.isNotBlank(condition.getFacilityKeyword())) {
            String keyword = condition.getFacilityKeyword().trim();
            wrapper.and(w -> w.like("ptxmmc", keyword).or().like("crzdbh", keyword));
        }

        wrapper.orderByDesc("createTime").orderByAsc("crzdbh");
        int pageNo = condition.getPageNo() == null || condition.getPageNo() < 1 ? 1 : condition.getPageNo();
        int pageSize = condition.getPageSize() == null || condition.getPageSize() < 1
                ? 10 : Math.min(condition.getPageSize(), MAX_PAGE_SIZE);
        return page(new Page<>(pageNo, pageSize), wrapper);
    }

    @Override
    public Facility queryDetail(String id) {
        if (StringUtils.isBlank(id)) {
            return null;
        }
        Facility facility = facilityMapper.selectById(id);
        if (facility == null || "1".equals(facility.getDelFlag())) {
            return null;
        }
        return facility;
    }

    @Override
    public List<Map<String, Object>> queryByLand(String crzdbh) {
        String code = DataSupport.clean(crzdbh);
        if (code == null) {
            return Collections.emptyList();
        }
        QueryWrapper<Facility> wrapper = new QueryWrapper<>();
        wrapper.eq("delFlag", "0").eq("crzdbh", code).orderByAsc("ptxmmc");
        List<Facility> facilities = list(wrapper);
        if (facilities.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> ptIds = new ArrayList<>(facilities.size());
        for (Facility facility : facilities) {
            ptIds.add(facility.getId());
        }
        // 一次拿全部环节进度，按配套分桶 —— 避免「每个配套查一次」（N+1）
        Map<String, List<FacilityProcess>> byPt = groupByPt(facilityProcessMapper.selectByFacilities(ptIds));

        List<Map<String, Object>> result = new ArrayList<>(facilities.size());
        for (Facility facility : facilities) {
            List<FacilityProcess> processes = byPt.get(facility.getId());
            if (processes == null) {
                processes = Collections.emptyList();
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", facility.getId());
            item.put("crzdbh", facility.getCrzdbh());
            item.put("ptxmmc", facility.getPtxmmc());
            item.put("dkmc", facility.getDkmc());
            item.put("ptsslb", facility.getPtsslb());
            item.put("xzqh", facility.getXzqh());
            item.put("xmfl", facility.getXmfl());
            item.put("jsdw", facility.getJsdw());
            item.put("tzgs", facility.getTzgs());
            item.put("sfkg", facility.getSfkg());
            item.put("sfjg", facility.getSfjg());
            item.put("sfyj", facility.getSfyj());
            item.put("lrr", facility.getLrr());
            item.put("createTime", facility.getCreateTime());
            // 环节进度汇总
            item.put("processStat", summarize(processes));
            // 阶段状态（六大阶段各自的汇总）
            item.put("stageStatus", stageStatusOf(processes));
            result.add(item);
        }
        return result;
    }

    // ==================================================================
    // 二、新增 / 编辑 / 移除
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createFacility(FacilitySaveDTO dto, HttpServletRequest request) {
        if (dto == null) {
            throw new JeecgBootException("提交内容为空");
        }
        String crzdbh = DataSupport.clean(dto.getCrzdbh());
        String ptxmmc = DataSupport.clean(dto.getPtxmmc());
        if (crzdbh == null) {
            throw new JeecgBootException("出让宗地编号不能为空（配套项目必须挂到已有宗地）");
        }
        if (ptxmmc == null) {
            throw new JeecgBootException("配套项目名称不能为空");
        }
        // ★ 宗地存在性硬校验：旧系统不校验，造出了 60 行「孤儿配套」
        Land land = landService.queryByCrzdbh(crzdbh);
        if (land == null) {
            throw new JeecgBootException("出让宗地编号「" + crzdbh
                    + "」在系统中不存在，无法挂接配套项目。请先在「经营性用地信息录入」里录入该宗地，"
                    + "或核对编号是否写错（注意全角/半角括号与末尾的「号」字）");
        }
        Facility duplicated = findByName(crzdbh, ptxmmc, null);
        if (duplicated != null) {
            throw new JeecgBootException("同一宗地下的配套项目名称「" + ptxmmc + "」已存在");
        }

        Facility facility = new Facility();
        copyBusinessFields(dto, facility);
        facility.setCrzdbh(crzdbh);
        facility.setPtxmmc(ptxmmc);
        if (StringUtils.isBlank(facility.getDkmc())) {
            // 地块名称留空时用宗地表的，省用户一次输入（旧录入页面也是这么联动的）
            facility.setDkmc(land.getDkmc());
        }
        if (StringUtils.isBlank(facility.getXzqh())) {
            facility.setXzqh(land.getXzqh());
        }
        if (StringUtils.isBlank(facility.getXmfl())) {
            facility.setXmfl(land.getXmfl());
        }
        facility.setDelFlag("0");
        facility.setCreateAccount(dataSupport.currentAccount());
        facility.setCreateTime(new Date());
        save(facility);

        changeLogSupport.collector(DataSupport.BIZ_FACILITY, facility.getId(), ptxmmc)
                .diff(null, facility, DataFieldLabels.facilityLabels())
                .save(DataChangeLog.ACTION_CREATE, "新增配套项目「" + ptxmmc + "」", request);

        log.info("新增配套项目成功：id={}, crzdbh={}, ptxmmc={}, 操作人={}",
                facility.getId(), crzdbh, ptxmmc, DataSupport.nameOrPlaceholder(dataSupport.currentRealname()));
        return facility.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateFacility(FacilitySaveDTO dto, HttpServletRequest request) {
        if (dto == null || StringUtils.isBlank(dto.getId())) {
            throw new JeecgBootException("缺少配套项目主键，无法编辑");
        }
        Facility before = queryDetail(dto.getId());
        if (before == null) {
            throw new JeecgBootException("未找到对应的配套项目（可能已被移除）");
        }
        String crzdbh = DataSupport.clean(dto.getCrzdbh());
        String ptxmmc = DataSupport.clean(dto.getPtxmmc());
        if (crzdbh == null) {
            throw new JeecgBootException("出让宗地编号不能为空");
        }
        if (ptxmmc == null) {
            throw new JeecgBootException("配套项目名称不能为空");
        }
        if (landService.queryByCrzdbh(crzdbh) == null) {
            throw new JeecgBootException("出让宗地编号「" + crzdbh + "」在系统中不存在，无法挂接");
        }
        if (!crzdbh.equals(before.getCrzdbh()) || !ptxmmc.equals(before.getPtxmmc())) {
            Facility duplicated = findByName(crzdbh, ptxmmc, before.getId());
            if (duplicated != null) {
                throw new JeecgBootException("同一宗地下的配套项目名称「" + ptxmmc + "」已被其他记录占用");
            }
        }

        Facility after = new Facility();
        copyBusinessFields(dto, after);
        after.setId(before.getId());
        after.setCrzdbh(crzdbh);
        after.setPtxmmc(ptxmmc);
        // ★ 审计字段从旧记录覆盖回来：挡掉客户端「塞 delFlag 想复活记录」这类越权写入
        after.setDelFlag(before.getDelFlag());
        after.setCreateAccount(before.getCreateAccount());
        after.setCreateTime(before.getCreateTime());

        ChangeLogSupport.Collector collector = changeLogSupport
                .collector(DataSupport.BIZ_FACILITY, before.getId(), ptxmmc)
                .diff(before, after, DataFieldLabels.facilityLabels());

        updateById(after);
        collector.save(DataChangeLog.ACTION_UPDATE, null, request);

        log.info("编辑配套项目成功：id={}, ptxmmc={}, 变更字段数={}, 操作人={}",
                before.getId(), ptxmmc, collector.size(),
                DataSupport.nameOrPlaceholder(dataSupport.currentRealname()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeFacility(String id, String reason, HttpServletRequest request) {
        if (StringUtils.isBlank(id)) {
            throw new JeecgBootException("缺少配套项目主键，无法移除");
        }
        Facility facility = queryDetail(id);
        if (facility == null) {
            throw new JeecgBootException("未找到对应的配套项目（可能已被移除）");
        }
        // ★ 软删：本表 delFlag 是 varchar('0'/'1')，不能用 removeById
        //   （Facility 没有 @TableLogic，removeById 会**物理删除** ——
        //    而配套项目被环节进度、附件、档案多处按 id 引用，物理删会留下孤儿数据）
        Facility patch = new Facility();
        patch.setId(facility.getId());
        patch.setDelFlag("1");
        updateById(patch);

        String summary = "移除配套项目「" + facility.getPtxmmc() + "」"
                + (StringUtils.isNotBlank(reason) ? "，原因：" + reason.trim() : "");
        changeLogSupport.collector(DataSupport.BIZ_FACILITY, facility.getId(), facility.getPtxmmc())
                .save(DataChangeLog.ACTION_DELETE, summary, request);

        log.info("移除配套项目成功：id={}, ptxmmc={}, 原因={}, 操作人={}",
                id, facility.getPtxmmc(), reason, DataSupport.nameOrPlaceholder(dataSupport.currentRealname()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> removeFacilityBatch(List<String> ids, String reason, HttpServletRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        int success = 0;
        List<Map<String, Object>> failures = new ArrayList<>();
        if (ids != null) {
            for (String id : ids) {
                try {
                    removeFacility(id, reason, request);
                    success++;
                } catch (Exception e) {
                    Map<String, Object> failure = new LinkedHashMap<>();
                    failure.put("id", id);
                    failure.put("reason", e.getMessage());
                    failures.add(failure);
                }
            }
        }
        result.put("successCount", success);
        result.put("failCount", failures.size());
        result.put("failures", failures);
        return result;
    }

    // ==================================================================
    // 三、29 环节进度
    // ==================================================================

    @Override
    public FacilityProcessTreeVO queryProcessTree(String ptId) {
        Facility facility = queryDetail(ptId);
        if (facility == null) {
            throw new JeecgBootException("未找到对应的配套项目（可能已被移除）");
        }
        // ① 环节骨架：全部启用中的阶段 + 事项
        List<ProcessConfig> all = processConfigMapper.selectEnabledAll();
        List<ProcessConfig> stages = new ArrayList<>();
        Map<String, List<ProcessConfig>> itemsByStage = new LinkedHashMap<>();
        for (ProcessConfig config : all) {
            if (config.isStage()) {
                stages.add(config);
                itemsByStage.put(config.getId(), new ArrayList<ProcessConfig>());
            }
        }
        for (ProcessConfig config : all) {
            List<ProcessConfig> bucket = itemsByStage.get(config.getParentId());
            if (bucket != null) {
                bucket.add(config);
            }
        }
        // ② 进度数据：一次查全，按 lcId 建索引
        List<FacilityProcess> progresses = facilityProcessMapper.selectByFacility(ptId);
        Map<String, FacilityProcess> byLc = new LinkedHashMap<>();
        for (FacilityProcess progress : progresses) {
            if (progress.getLcId() != null) {
                byLc.put(progress.getLcId(), progress);
            }
        }

        FacilityProcessTreeVO tree = new FacilityProcessTreeVO();
        tree.setPtId(ptId);
        tree.setCrzdbh(facility.getCrzdbh());
        tree.setFacilityName(facility.getPtxmmc());

        Date today = new Date();
        for (ProcessConfig stage : stages) {
            FacilityProcessTreeVO.StageNode stageNode = new FacilityProcessTreeVO.StageNode();
            stageNode.setStageId(stage.getId());
            stageNode.setStageName(stage.getName());
            stageNode.setPath(stage.getPath());
            stageNode.setProcessTime(stage.getProcessTime());

            List<String> childStatuses = new ArrayList<>();
            for (ProcessConfig item : itemsByStage.get(stage.getId())) {
                FacilityProcess progress = byLc.get(item.getId());
                FacilityProcessTreeVO.ItemNode itemNode = toItemNode(item, progress, today);
                childStatuses.add(itemNode.getLcqk());
                stageNode.getItems().add(itemNode);
                if (Boolean.TRUE.equals(itemNode.getFilled())) {
                    stageNode.setFilledCount(stageNode.getFilledCount() + 1);
                }
                if (Boolean.TRUE.equals(itemNode.getOverdue())) {
                    stageNode.setOverdueCount(stageNode.getOverdueCount() + 1);
                }
            }
            stageNode.setItemCount(stageNode.getItems().size());
            stageNode.setStatus(ProcessStatus.rollUpStageStatus(childStatuses));
            stageNode.setPercent(ProcessStatus.stagePercent(childStatuses));
            tree.getStages().add(stageNode);

            // 顶层累计
            tree.setTotalItems(tree.getTotalItems() + stageNode.getItemCount());
            tree.setFilledItems(tree.getFilledItems() + stageNode.getFilledCount());
            tree.setOverdueItems(tree.getOverdueItems() + stageNode.getOverdueCount());
            for (FacilityProcessTreeVO.ItemNode itemNode : stageNode.getItems()) {
                ProcessStatus status = ProcessStatus.of(itemNode.getLcqk());
                if (status == ProcessStatus.FINISHED) {
                    tree.setFinishedItems(tree.getFinishedItems() + 1);
                } else if (status == ProcessStatus.RUNNING) {
                    tree.setRunningItems(tree.getRunningItems() + 1);
                } else if (status == ProcessStatus.NOT_INVOLVED) {
                    tree.setNotInvolvedItems(tree.getNotInvolvedItems() + 1);
                } else {
                    tree.setNotStartedItems(tree.getNotStartedItems() + 1);
                }
            }
        }
        // 顶层完成度：把所有事项的环节情况拉平后按同一口径算（剔除「不涉及」）
        List<String> allStatuses = new ArrayList<>();
        for (FacilityProcessTreeVO.StageNode stageNode : tree.getStages()) {
            for (FacilityProcessTreeVO.ItemNode itemNode : stageNode.getItems()) {
                allStatuses.add(itemNode.getLcqk());
            }
        }
        tree.setPercent(ProcessStatus.stagePercent(allStatuses));
        return tree;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String saveProcess(String ptId, String lcId, Map<String, Object> payload,
                              HttpServletRequest request) {
        Facility facility = queryDetail(ptId);
        if (facility == null) {
            throw new JeecgBootException("未找到对应的配套项目（可能已被移除）");
        }
        ProcessConfig config = processConfigMapper.selectById(lcId);
        if (config == null || Integer.valueOf(1).equals(config.getDelFlag())) {
            throw new JeecgBootException("环节「" + lcId + "」不存在或已停用");
        }
        if (config.isStage()) {
            throw new JeecgBootException("「" + config.getName()
                    + "」是阶段汇总节点，不能直接录入进度；请录入该阶段下的具体事项");
        }
        Map<String, Object> data = payload == null ? Collections.<String, Object>emptyMap() : payload;

        FacilityProcess existing = findProcess(ptId, lcId);
        FacilityProcess target = new FacilityProcess();
        if (existing != null) {
            target.setId(existing.getId());
        }        target.setPtId(ptId);
        target.setCrzdbh(facility.getCrzdbh());
        target.setLcId(lcId);
        target.setLcPath(config.getPath());
        target.setLcName(config.getName());
        target.setStageId(config.getParentId());

        String lcqk = DataSupport.clean(asString(data.get("lcqk")));
        if (lcqk == null) {
            // 未指定状态时：有实际结束时间视为已完成，否则视为进行中
            lcqk = DataSupport.clean(asString(data.get("lcjssj"))) != null
                    ? ProcessStatus.FINISHED.getValue() : ProcessStatus.RUNNING.getValue();
        }
        if (!VALID_PROCESS_STATUS.contains(lcqk)) {
            throw new JeecgBootException("环节情况「" + lcqk + "」不合法，只能填："
                    + ProcessStatus.allowedText());
        }
        target.setLcqk(lcqk);
        target.setLckssj(parseDate(asString(data.get("lckssj"))));
        target.setLcjssj(parseDate(asString(data.get("lcjssj"))));
        target.setCzwtlx(DataSupport.cleanAndCap(asString(data.get("czwtlx")), 30));
        target.setJtwt(DataSupport.cleanAndCap(asString(data.get("jtwt")), 1000));
        target.setGzjy(DataSupport.cleanAndCap(asString(data.get("gzjy")), 1000));
        target.setLrdw(DataSupport.cleanAndCap(asString(data.get("lrdw")), 100));
        target.setLrr(DataSupport.cleanAndCap(asString(data.get("lrr")), 100));
        target.setLxdh(DataSupport.cleanAndCap(asString(data.get("lxdh")), 100));

        // 预计结束时间：前端没传时按「开始日 + 环节标准时长(工作日)」自动算
        Date expectEnd = parseDate(asString(data.get("yjjssj")));
        if (expectEnd == null && target.getLckssj() != null) {
            Map<String, Object> calc = workdayCalculator.plusWorkdays(
                    format(target.getLckssj()), config.getProcessTime() == null
                            ? null : config.getProcessTime().intValue());
            expectEnd = parseDate(asString(calc.get("endDate")));
        }
        target.setYjjssj(expectEnd);

        // 业务校验：状态与时间必须自洽（旧系统这里完全没有校验，
        // 于是库里存在「已完成但没有结束时间」「结束时间早于开始时间」这类脏数据）
        if (ProcessStatus.FINISHED.getValue().equals(lcqk) && target.getLcjssj() == null) {
            throw new JeecgBootException("环节情况为「已完成」时必须填写环节结束时间");
        }
        if (target.getLckssj() != null && target.getLcjssj() != null
                && target.getLcjssj().before(target.getLckssj())) {
            throw new JeecgBootException("环节结束时间不能早于开始时间");
        }
        if (ProcessStatus.NOT_INVOLVED.getValue().equals(lcqk) && target.getLckssj() != null) {
            // 不涉及还填了开始时间，多半是选错了状态 —— 提示但不阻断（业务上可能有历史留痕需求）
            log.info("环节「{}」标记为不涉及但仍填写了开始时间 {}，已按用户输入保存",
                    config.getName(), format(target.getLckssj()));
        }

        if (existing == null) {
            target.setCreateAccount(dataSupport.currentAccount());
            target.setCreateTime(new Date());
            facilityProcessMapper.insert(target);
            changeLogSupport.collector(DataSupport.BIZ_PROCESS, target.getId(),
                            facility.getPtxmmc() + " / " + config.getName())
                    .diff(null, target, DataFieldLabels.processLabels())
                    .save(DataChangeLog.ACTION_CREATE,
                            "录入环节进度：" + facility.getPtxmmc() + " - " + config.getName(), request);
        } else {
            // ★ 复用上面已经查出来的 existing 作为「改前快照」，
            //   而不是再查一次库：查两次不仅有性能浪费，更要紧的是
            //   两次查询之间若有人改了这条记录，diff 就会拿错基准
            //   （记出「从 A 改成 A」这种无意义履历）。
            target.setUpdateBy(dataSupport.currentEditor());
            target.setUpdateTime(new Date());
            ChangeLogSupport.Collector collector = changeLogSupport
                    .collector(DataSupport.BIZ_PROCESS, existing.getId(),
                            facility.getPtxmmc() + " / " + config.getName())
                    .diff(existing, target, DataFieldLabels.processLabels());
            facilityProcessMapper.updateById(target);
            collector.save(DataChangeLog.ACTION_UPDATE,
                    "更新环节进度：" + facility.getPtxmmc() + " - " + config.getName(), request);
        }
        log.info("保存环节进度成功：ptId={}, lcId={}, lcName={}, lcqk={}, 操作人={}",
                ptId, lcId, config.getName(), lcqk,
                DataSupport.nameOrPlaceholder(dataSupport.currentRealname()));
        return target.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveProcessBatch(String ptId, List<Map<String, Object>> items,
                                                HttpServletRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        int success = 0;
        List<Map<String, Object>> failures = new ArrayList<>();
        if (items != null) {
            for (Map<String, Object> item : items) {
                String lcId = asString(item.get("lcId"));
                if (StringUtils.isBlank(lcId)) {
                    continue;
                }
                try {
                    saveProcess(ptId, lcId, item, request);
                    success++;
                } catch (Exception e) {
                    Map<String, Object> failure = new LinkedHashMap<>();
                    failure.put("lcId", lcId);
                    failure.put("lcName", item.get("lcName"));
                    failure.put("reason", e.getMessage());
                    failures.add(failure);
                }
            }
        }
        result.put("successCount", success);
        result.put("failCount", failures.size());
        result.put("failures", failures);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeProcess(String ptId, String lcId, HttpServletRequest request) {
        FacilityProcess existing = findProcess(ptId, lcId);
        if (existing == null) {
            throw new JeecgBootException("该环节还没有录入进度，无需删除");
        }
        // 环节进度是「过程记录」，用户清空它就是「填错了，撤回」，物理删除即可 ——
        // 与业务主数据（宗地/配套）不同，它没有被其它表按 id 引用。
        facilityProcessMapper.deleteById(existing.getId());
        changeLogSupport.collector(DataSupport.BIZ_PROCESS, existing.getId(),
                        (existing.getLcName() == null ? "" : existing.getLcName()))
                .save(DataChangeLog.ACTION_DELETE,
                        "撤回环节进度：" + existing.getLcName(), request);
    }

    @Override
    public List<Map<String, Object>> queryProcessList(String ptId) {
        FacilityProcessTreeVO tree = queryProcessTree(ptId);
        List<Map<String, Object>> rows = new ArrayList<>();
        int index = 0;
        for (FacilityProcessTreeVO.StageNode stage : tree.getStages()) {
            for (FacilityProcessTreeVO.ItemNode item : stage.getItems()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("index", ++index);
                row.put("stageId", stage.getStageId());
                row.put("stageName", stage.getStageName());
                row.put("stageStatus", stage.getStatus());
                row.put("stagePercent", stage.getPercent());
                row.put("lcId", item.getLcId());
                row.put("lcName", item.getLcName());
                row.put("zgbm", item.getZgbm());
                row.put("processTime", item.getProcessTime());
                row.put("lcqk", item.getLcqk());
                row.put("lckssj", item.getLckssj());
                row.put("yjjssj", item.getYjjssj());
                row.put("lcjssj", item.getLcjssj());
                row.put("czwtlx", item.getCzwtlx());
                row.put("jtwt", item.getJtwt());
                row.put("gzjy", item.getGzjy());
                row.put("lrr", item.getLrr());
                row.put("lxdh", item.getLxdh());
                row.put("overdue", item.getOverdue());
                row.put("overdueDays", item.getOverdueDays());
                row.put("filled", item.getFilled());
                row.put("attachmentCount", item.getAttachmentCount());
                rows.add(row);
            }
        }
        return rows;
    }

    // ==================================================================
    // 四、校验与辅助
    // ==================================================================

    @Override
    public Map<String, Object> calcExpectEnd(String startDate, Integer days) {
        return workdayCalculator.plusWorkdays(startDate, days);
    }

    @Override
    public Map<String, Object> checkPtxmmc(String crzdbh, String ptxmmc, String excludeId) {
        Map<String, Object> result = new LinkedHashMap<>();
        String code = DataSupport.clean(crzdbh);
        String name = DataSupport.clean(ptxmmc);
        if (name == null) {
            result.put("available", false);
            result.put("message", "配套项目名称不能为空");
            return result;
        }
        // 宗地存在性一起给前端（表单里选完宗地就能立刻知道编号对不对）
        if (code == null) {
            result.put("available", false);
            result.put("message", "请先选择出让宗地");
            return result;
        }
        if (landService.queryByCrzdbh(code) == null) {
            result.put("available", false);
            result.put("message", "出让宗地编号「" + code + "」不存在，请先录入该宗地");
            return result;
        }
        Facility hit = findByName(code, name, excludeId);
        if (hit != null) {
            result.put("available", false);
            result.put("message", "该宗地下已存在同名配套项目");
            result.put("occupiedBy", hit.getId());
            return result;
        }
        result.put("available", true);
        result.put("message", "该名称可以使用");
        return result;
    }

    @Override
    public List<Map<String, Object>> queryProcessConfig() {
        List<ProcessConfig> all = processConfigMapper.selectEnabledAll();
        List<Map<String, Object>> stages = new ArrayList<>();
        Map<String, Map<String, Object>> stageById = new LinkedHashMap<>();
        for (ProcessConfig config : all) {
            if (!config.isStage()) {
                continue;
            }
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("stageId", config.getId());
            node.put("stageName", config.getName());
            node.put("path", config.getPath());
            node.put("processTime", config.getProcessTime());
            node.put("zgbm", config.getZgbm());
            node.put("items", new ArrayList<Map<String, Object>>());
            stages.add(node);
            stageById.put(config.getId(), node);
        }
        for (ProcessConfig config : all) {
            if (config.isStage()) {
                continue;
            }
            Map<String, Object> parent = stageById.get(config.getParentId());
            if (parent == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("lcId", config.getId());
            item.put("lcName", config.getName());
            item.put("aliasName", config.getAliasName());
            item.put("path", config.getPath());
            item.put("processTime", config.getProcessTime());
            item.put("zgbm", config.getZgbm());
            item.put("isParallel", config.getIsParallel());
            item.put("location", config.getLocation());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) parent.get("items");
            items.add(item);
        }
        return stages;
    }

    // ==================================================================
    // 五、内部工具
    // ==================================================================

    /**
     * 业务字段搬运：DTO → 实体。
     *
     * <p>这里用 Spring 的 {@code BeanUtils.copyProperties}（53 个字段逐个抄是无效劳动），
     * 但<b>随后把 id 与审计字段清空</b>：copyProperties 会把 DTO 里的
     * {@code id} / {@code delFlag} / {@code createTime} / {@code createAccount}
     * 一并拷过来，而客户端传什么是不可信的（见 {@link FacilitySaveDTO} 的类注释）。
     *
     * <p>★ 用 Spring 的 BeanUtils 而不是 commons-beanutils 的同名方法：
     * 两者<b>参数顺序相反</b>（Spring 是 {@code (源, 目标)}，commons 是 {@code (目标, 源)}），
     * 混用会让「字段一个都没拷过去」这种问题静默发生 —— 保存成功但数据全空。
     */
    private void copyBusinessFields(FacilitySaveDTO dto, Facility facility) {
        BeanUtils.copyProperties(dto, facility);
        // 剥掉客户端不可信的字段（由 Service 显式决定）
        facility.setId(null);
        facility.setDelFlag(null);
        facility.setCreateTime(null);
        facility.setCreateAccount(null);
        // 文本列宽截断（列宽取自旧表结构；超长会被 MySQL 在非严格模式下静默截断，
        // 显式截断至少是可预期的）
        facility.setPtxmmc(DataSupport.cleanAndCap(facility.getPtxmmc(), 100));
        facility.setDkmc(DataSupport.cleanAndCap(facility.getDkmc(), 255));
        facility.setCrzdbh(DataSupport.cleanAndCap(facility.getCrzdbh(), 100));
        facility.setPtsslb(DataSupport.cleanAndCap(facility.getPtsslb(), 100));
        facility.setXzqh(DataSupport.cleanAndCap(facility.getXzqh(), 100));
        facility.setXmfl(DataSupport.cleanAndCap(facility.getXmfl(), 100));
        facility.setJtwt(DataSupport.cleanAndCap(facility.getJtwt(), 2500));
        facility.setZlqsnrjsm(DataSupport.cleanAndCap(facility.getZlqsnrjsm(), 2500));
        facility.setBz(DataSupport.cleanAndCap(facility.getBz(), 100));
    }

    /** 按「宗地 + 名称」查一条有效配套（排除指定 id） */
    private Facility findByName(String crzdbh, String ptxmmc, String excludeId) {
        QueryWrapper<Facility> wrapper = new QueryWrapper<>();
        wrapper.eq("delFlag", "0").eq("crzdbh", crzdbh).eq("ptxmmc", ptxmmc);
        if (StringUtils.isNotBlank(excludeId)) {
            wrapper.ne("id", excludeId);
        }
        wrapper.last("LIMIT 1");
        return getOne(wrapper, false);
    }

    /** 取某配套某环节的进度（唯一键保证最多一条） */
    private FacilityProcess findProcess(String ptId, String lcId) {
        QueryWrapper<FacilityProcess> wrapper = new QueryWrapper<>();
        wrapper.eq("pt_id", ptId).eq("lc_id", lcId).last("LIMIT 1");
        return facilityProcessMapper.selectOne(wrapper);
    }

    /** 把一个环节配置 + 进度记录组装成树节点 */
    private FacilityProcessTreeVO.ItemNode toItemNode(ProcessConfig config, FacilityProcess progress,
                                                      Date today) {
        FacilityProcessTreeVO.ItemNode node = new FacilityProcessTreeVO.ItemNode();
        node.setLcId(config.getId());
        node.setLcName(config.getName());
        node.setPath(config.getPath());
        node.setZgbm(config.getZgbm());
        node.setProcessTime(config.getProcessTime());
        node.setLocation(config.getLocation());
        node.setIsParallel(config.getIsParallel());
        if (progress == null) {
            node.setLcqk(ProcessStatus.defaultValue());
            node.setFilled(false);
            node.setOverdue(false);
            node.setOverdueDays(0);
            node.setAttachmentCount(0);
            return node;
        }
        node.setProgressId(progress.getId());
        node.setLcqk(StringUtils.isBlank(progress.getLcqk())
                ? ProcessStatus.defaultValue() : progress.getLcqk());
        node.setLckssj(format(progress.getLckssj()));
        node.setYjjssj(format(progress.getYjjssj()));
        node.setLcjssj(format(progress.getLcjssj()));
        node.setCzwtlx(progress.getCzwtlx());
        node.setJtwt(progress.getJtwt());
        node.setGzjy(progress.getGzjy());
        node.setLrdw(progress.getLrdw());
        node.setLrr(progress.getLrr());
        node.setLxdh(progress.getLxdh());
        node.setFilled(true);
        // 逾期判定与旧系统一致：拿「实际结束时间（没结束就是今天）」与预计结束时间比
        Date compareBase = progress.getLcjssj() != null ? progress.getLcjssj() : today;
        Date expect = progress.getYjjssj();
        if (expect != null && compareBase.after(expect)
                && !ProcessStatus.NOT_INVOLVED.getValue().equals(node.getLcqk())) {
            node.setOverdue(true);
            node.setOverdueDays(daysBetween(expect, compareBase));
        } else {
            node.setOverdue(false);
            node.setOverdueDays(0);
        }
        return node;
    }

    /** 环节进度汇总（列表页每个配套一格） */
    private Map<String, Object> summarize(List<FacilityProcess> processes) {
        Map<String, Object> stat = new LinkedHashMap<>();
        List<String> statuses = new ArrayList<>();
        int overdue = 0;
        Date today = new Date();
        if (processes != null) {
            for (FacilityProcess progress : processes) {
                statuses.add(progress.getLcqk());
                Date compareBase = progress.getLcjssj() != null ? progress.getLcjssj() : today;
                if (progress.getYjjssj() != null && compareBase.after(progress.getYjjssj())
                        && !ProcessStatus.NOT_INVOLVED.getValue().equals(progress.getLcqk())) {
                    overdue++;
                }
            }
        }
        int totalItems = processConfigMapper.selectEnabledAll().size();
        stat.put("filled", statuses.size());
        stat.put("finished", countOf(statuses, ProcessStatus.FINISHED.getValue()));
        stat.put("running", countOf(statuses, ProcessStatus.RUNNING.getValue()));
        stat.put("notInvolved", countOf(statuses, ProcessStatus.NOT_INVOLVED.getValue()));
        stat.put("overdue", overdue);
        stat.put("status", ProcessStatus.rollUpStageStatus(statuses));
        stat.put("percent", ProcessStatus.stagePercent(statuses));
        return stat;
    }

    /** 各阶段状态（列表页展示六个阶段的小标签） */
    private List<Map<String, Object>> stageStatusOf(List<FacilityProcess> processes) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ProcessConfig stage : processConfigMapper.selectStages()) {
            List<String> statuses = new ArrayList<>();
            if (processes != null) {
                for (FacilityProcess progress : processes) {
                    if (stage.getId().equals(progress.getStageId())) {
                        statuses.add(progress.getLcqk());
                    }
                }
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("stageId", stage.getId());
            item.put("stageName", stage.getName());
            item.put("status", statuses.isEmpty()
                    ? ProcessStatus.defaultValue() : ProcessStatus.rollUpStageStatus(statuses));
            item.put("percent", ProcessStatus.stagePercent(statuses));
            item.put("filledCount", statuses.size());
            result.add(item);
        }
        return result;
    }

    private static int countOf(List<String> statuses, String value) {
        int count = 0;
        for (String status : statuses) {
            if (value.equals(status)) {
                count++;
            }
        }
        return count;
    }

    /** 两个日期相差的天数（不足一天按 1 天算，避免显示「逾期 0 天」） */
    private static int daysBetween(Date from, Date to) {
        if (from == null || to == null) {
            return 0;
        }
        long diff = to.getTime() - from.getTime();
        int days = (int) (diff / (24L * 60 * 60 * 1000));
        return days <= 0 ? 1 : days;
    }

    /** 值转字符串（Map 取参统一入口，null → null） */
    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date) {
            return format((Date) value);
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static Date parseDate(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String value = text.trim().replace('/', '-').replace('.', '-');
        for (String pattern : new String[]{"yyyy-MM-dd", "yyyy-M-d", "yyyyMMdd"}) {
            try {
                SimpleDateFormat formatter = new SimpleDateFormat(pattern);
                formatter.setLenient(false);
                return formatter.parse(value);
            } catch (ParseException ignored) {
                // 试下一种
            }
        }
        throw new JeecgBootException("日期「" + text + "」格式不正确，请用 yyyy-MM-dd");
    }

    private static String format(Date date) {
        return date == null ? null : new SimpleDateFormat(PATTERN_DATE).format(date);
    }

    /** 把环节进度按配套项目ID分桶（供 queryByLand 一次取全，避免 N+1） */
    private static Map<String, List<FacilityProcess>> groupByPt(List<FacilityProcess> rows) {
        Map<String, List<FacilityProcess>> map = new LinkedHashMap<>();
        if (rows == null) {
            return map;
        }
        for (FacilityProcess row : rows) {
            String key = row.getPtId();
            List<FacilityProcess> bucket = map.get(key);
            if (bucket == null) {
                bucket = new ArrayList<>();
                map.put(key, bucket);
            }
            bucket.add(row);
        }
        return map;
    }
}
