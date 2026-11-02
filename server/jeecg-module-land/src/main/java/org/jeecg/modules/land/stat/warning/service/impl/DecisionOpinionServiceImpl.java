package org.jeecg.modules.land.stat.warning.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.stat.warning.entity.DecisionOpinion;
import org.jeecg.modules.land.stat.warning.entity.ProcessWarningLevel;
import org.jeecg.modules.land.stat.warning.mapper.DecisionOpinionMapper;
import org.jeecg.modules.land.stat.warning.mapper.ProcessWarningLevelMapper;
import org.jeecg.modules.land.stat.warning.service.IDecisionOpinionService;
import org.jeecg.modules.land.stat.warning.support.WarningFields;
import org.jeecg.modules.land.stat.warning.vo.DecisionOpinionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * @Description: 分级预警 · 决策意见维护实现
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-11-02
 * @Version V1.0
 *
 * <p>校验与 {@code WarningLevelServiceImpl} 同一套路（先便宜的检查、再需要查库的检查），
 * 差异只有「没有区间重叠这个概念」。
 *
 * <p><b>★ 编辑时 {@code (problemType, levelId)} 的唯一性要排除自己</b>：
 * 只改 opinion 不改类型和等级时，不排除自己就会报「已存在，请直接编辑」——
 * 而用户正在编辑的就是那一条，属于自己跟自己冲突。
 *
 * <p><b>★ {@code levelName} 由服务端按 levelId 回填</b>：
 * 不让前端传。前端传的话，一旦下拉数据是旧的（页面开着没刷新），
 * 就会把过期等级名写进冗余列，列表里显示「（旧名）」而实际等级已改名。
 */
@Slf4j
@Service
public class DecisionOpinionServiceImpl implements IDecisionOpinionService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 200;

    @Autowired
    private DecisionOpinionMapper decisionOpinionMapper;

    @Autowired
    private ProcessWarningLevelMapper warningLevelMapper;

    @Override
    public IPage<DecisionOpinionVO> list(String levelId, String problemType,
                                         Integer pageNo, Integer pageSize) {
        int current = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        // ★ problemType 原样传下去：null = 全部，"" = 只看通用意见（两种语义必须区分）
        return decisionOpinionMapper.selectOpinionPage(new Page<>(current, size),
                trimToNull(levelId), problemType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String save(DecisionOpinionVO vo) {
        if (vo == null) {
            throw new JeecgBootException("决策意见内容为空，请检查提交数据");
        }
        String levelId = trimToNull(vo.getLevelId());
        if (levelId == null) {
            throw new JeecgBootException("请选择预警等级");
        }
        String opinion = trimToNull(vo.getOpinion());
        if (opinion == null) {
            throw new JeecgBootException("决策意见正文不能为空");
        }
        if (opinion.length() > WarningFields.OPINION_MAX_LENGTH) {
            // 不截断而是报错：被截掉半句的处置意见比报错更难发现
            throw new JeecgBootException("决策意见正文最长 " + WarningFields.OPINION_MAX_LENGTH
                    + " 字，当前 " + opinion.length() + " 字，请精简后再保存");
        }
        // 等级必须存在且未删：指向已删等级的意见永远不会被用到（静默无效配置）
        ProcessWarningLevel level = warningLevelMapper.selectEnabledById(levelId);
        if (level == null) {
            throw new JeecgBootException("预警等级不存在或已被删除，请刷新后重新选择");
        }
        // ★ 问题类型：null 归一成空串（= 通用兜底），与表列 NOT NULL DEFAULT '' 一致
        String problemType = vo.getProblemType() == null ? WarningFields.OPINION_GENERIC_TYPE
                : vo.getProblemType().trim();

        String id = trimToNull(vo.getId());
        Date now = new Date();
        if (id == null) {
            DecisionOpinion duplicated = decisionOpinionMapper.selectByTypeAndLevel(problemType, levelId);
            if (duplicated != null) {
                throw new JeecgBootException("该等级下的「" + problemTypeLabel(problemType)
                        + "」决策意见已存在，请直接编辑");
            }
            DecisionOpinion entity = new DecisionOpinion();
            entity.setProblemType(problemType);
            entity.setLevelId(levelId);
            entity.setLevelName(level.getLevelName());
            entity.setOpinion(opinion);
            entity.setSortNo(vo.getSortNo() == null ? defaultSortNo(level) : vo.getSortNo());
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            entity.setDelFlag(0);
            decisionOpinionMapper.insert(entity);
            log.info("新增决策意见：等级={}，问题类型={}", level.getLevelName(), problemTypeLabel(problemType));
            return entity.getId();
        }

        // 编辑：id 必须存在（先查后判，报错文案才不会误导向「重复」）
        DecisionOpinion existing = decisionOpinionMapper.selectById(id);
        if (existing == null || Integer.valueOf(1).equals(existing.getDelFlag())) {
            throw new JeecgBootException("要修改的决策意见不存在或已被删除，请刷新后重试");
        }
        DecisionOpinion duplicated = decisionOpinionMapper.selectByTypeAndLevel(problemType, levelId);
        if (duplicated != null && !duplicated.getId().equals(id)) {
            throw new JeecgBootException("该等级下的「" + problemTypeLabel(problemType)
                    + "」决策意见已存在，请直接编辑");
        }
        existing.setProblemType(problemType);
        existing.setLevelId(levelId);
        existing.setLevelName(level.getLevelName());
        existing.setOpinion(opinion);
        existing.setSortNo(vo.getSortNo() == null ? existing.getSortNo() : vo.getSortNo());
        existing.setUpdateTime(now);
        decisionOpinionMapper.updateById(existing);
        log.info("修改决策意见：等级={}，问题类型={}", level.getLevelName(), problemTypeLabel(problemType));
        return existing.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        String opinionId = trimToNull(id);
        if (opinionId == null) {
            throw new JeecgBootException("请指定要删除的决策意见");
        }
        int affected = decisionOpinionMapper.softDelete(opinionId);
        if (affected <= 0) {
            throw new JeecgBootException("要删除的决策意见不存在或已被删除，请刷新后重试");
        }
        log.info("删除决策意见：id={}（软删；已生成的预警行里保留的是当时的意见正文快照）", opinionId);
    }

    // ==================================================================
    // 内部工具
    // ==================================================================

    /** 问题类型展示名：空串在界面上叫「通用（所有问题类型）」 */
    private static String problemTypeLabel(String problemType) {
        return StringUtils.isBlank(problemType) ? "通用（所有问题类型）" : problemType;
    }

    /**
     * 默认排序号：直接用等级的 sortNo。
     *
     * <p>这样列表天然按「由轻到重」分组，而不是按录入先后 ——
     * 维护页打开时要一眼看到「蓝黄橙红」四组，而不是一堆乱序行。
     */
    private static Integer defaultSortNo(ProcessWarningLevel level) {
        return level.getSortNo() == null ? 0 : level.getSortNo();
    }

    private static String trimToNull(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return value.trim();
    }
}
