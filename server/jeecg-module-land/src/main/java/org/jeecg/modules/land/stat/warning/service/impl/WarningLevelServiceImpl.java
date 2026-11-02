package org.jeecg.modules.land.stat.warning.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.stat.warning.entity.ProcessWarningLevel;
import org.jeecg.modules.land.stat.warning.mapper.ProcessWarningLevelMapper;
import org.jeecg.modules.land.stat.warning.service.IWarningLevelService;
import org.jeecg.modules.land.stat.warning.vo.WarningLevelVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @Description: 分级预警 · 预警等级维护实现
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-11-02
 * @Version V1.0
 *
 * <p>校验顺序与理由（顺序本身也是设计的一部分：
 * 先报「字段没填」再报「区间不对」再报「跟别人冲突」，
 * 用户改一次就能过，不用来回试）：
 * <ol>
 *   <li>非空与格式 —— 最便宜的检查放最前；</li>
 *   <li>区间自身是否合法（{@code 0 <= min <= max}）；</li>
 *   <li>编辑时 id 是否存在 —— <b>必须在冲突检查之前</b>，
 *       否则「编辑一个已被删的等级」会被报成「与某等级区间重叠」，
 *       用户会去改区间，改了还是不行；</li>
 *   <li>levelCode 唯一、区间不重叠 —— 这两条都要和「其它生效等级」比，
 *       所以放在最后（需要先把候选集合取出来）。</li>
 * </ol>
 *
 * <p><b>★ 不做缓存</b>：等级表只有 4 行，每次匹配查一次的成本可以忽略；
 * 缓存换来的「管理端改完还在按旧阈值算」是实实在在的投诉（契约 §5.6 第 5 条）。
 */
@Slf4j
@Service
public class WarningLevelServiceImpl implements IWarningLevelService {

    /**
     * levelCode 允许的字符集：小写字母、数字、下划线、连字符，2~20 位。
     *
     * <p>为什么限制得这么死：{@code levelCode} 是**前端着色令牌**，
     * 前端按它去 {@code --screen-level-*} 取色，写个中文或大写在样式里匹配不上，
     * 表现是「等级显示出来了但没有颜色」，且没有任何报错。宁可保存时报错。
     */
    private static final Pattern CODE_PATTERN = Pattern.compile("[a-z0-9_-]{2,20}");

    @Autowired
    private ProcessWarningLevelMapper warningLevelMapper;

    @Override
    public List<WarningLevelVO> list() {
        List<ProcessWarningLevel> levels = warningLevelMapper.selectEnabled();
        List<WarningLevelVO> result = new ArrayList<>(levels == null ? 0 : levels.size());
        if (levels == null) {
            return result;
        }
        for (ProcessWarningLevel level : levels) {
            result.add(toVO(level));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String save(WarningLevelVO vo) {
        if (vo == null) {
            throw new JeecgBootException("预警等级内容为空，请检查提交数据");
        }
        // ① 字段非空与格式
        String levelName = trimToNull(vo.getLevelName());
        if (levelName == null) {
            throw new JeecgBootException("预警等级名称不能为空");
        }
        String levelCode = trimToNull(vo.getLevelCode());
        if (levelCode == null) {
            throw new JeecgBootException("预警等级别名不能为空");
        }
        if (!CODE_PATTERN.matcher(levelCode).matches()) {
            throw new JeecgBootException("预警等级别名「" + levelCode
                    + "」不合法：只能用小写字母、数字、下划线、连字符，长度 2~20（它同时是前端着色令牌）");
        }
        // ② 区间自身
        Integer minDays = vo.getMinDays();
        Integer maxDays = vo.getMaxDays();
        if (minDays == null || maxDays == null) {
            throw new JeecgBootException("预警等级的逾期天数区间不能为空");
        }
        if (minDays < 0 || maxDays < 0) {
            throw new JeecgBootException("逾期天数区间不能为负数");
        }
        if (minDays > maxDays) {
            throw new JeecgBootException("逾期天数区间的下界（" + minDays
                    + "）不能大于上界（" + maxDays + "）");
        }

        List<ProcessWarningLevel> levels = warningLevelMapper.selectEnabled();
        if (levels == null) {
            levels = new ArrayList<>();
        }

        // ③ 编辑时 id 必须存在（顺序见类注释）
        String id = trimToNull(vo.getId());
        ProcessWarningLevel existing = null;
        if (id != null) {
            existing = findById(levels, id);
            if (existing == null) {
                throw new JeecgBootException("要修改的预警等级不存在或已被删除（id = " + id + "），请刷新后重试");
            }
        }

        // ④-1 levelCode 唯一（大小写不敏感：Blue 与 blue 在前端是同一个颜色令牌）
        for (ProcessWarningLevel other : levels) {
            if (existing != null && existing.getId().equals(other.getId())) {
                continue;
            }
            if (levelCode.equalsIgnoreCase(trimToNull(other.getLevelCode()))) {
                throw new JeecgBootException("预警等级别名「" + levelCode + "」已被等级「"
                        + other.getLevelName() + "」占用，请换一个");
            }
        }
        // ④-2 区间不重叠：重叠会让匹配产生歧义，必须拒绝并指出冲突方
        for (ProcessWarningLevel other : levels) {
            if (existing != null && existing.getId().equals(other.getId())) {
                continue;
            }
            Integer otherMin = other.getMinDays();
            Integer otherMax = other.getMaxDays();
            if (otherMin == null || otherMax == null) {
                // 区间缺失的历史行不参与冲突判断（它自己也匹配不到任何天数）
                continue;
            }
            // 闭区间相交：!(新上界 < 旧下界 || 新下界 > 旧上界)
            if (!(maxDays < otherMin || minDays > otherMax)) {
                throw new JeecgBootException("逾期天数区间 [" + minDays + ", " + maxDays
                        + "] 与等级「" + other.getLevelName() + "」的区间 [" + otherMin + ", "
                        + otherMax + "] 重叠，请调整后再保存");
            }
        }

        Date now = new Date();
        if (existing == null) {
            ProcessWarningLevel entity = new ProcessWarningLevel();
            entity.setLevelName(levelName);
            entity.setLevelCode(levelCode);
            entity.setMinDays(minDays);
            entity.setMaxDays(maxDays);
            entity.setSortNo(vo.getSortNo() == null ? nextSortNo(levels) : vo.getSortNo());
            entity.setRemark(trimToNull(vo.getRemark()));
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            entity.setDelFlag(0);
            warningLevelMapper.insert(entity);
            log.info("新增预警等级：{}（{}），区间 [{}, {}]", levelName, levelCode, minDays, maxDays);
            return entity.getId();
        }

        // 编辑：只改可改的列，del_flag / create_* 原样保留（重算与列表都依赖它们）
        existing.setLevelName(levelName);
        existing.setLevelCode(levelCode);
        existing.setMinDays(minDays);
        existing.setMaxDays(maxDays);
        existing.setSortNo(vo.getSortNo() == null ? existing.getSortNo() : vo.getSortNo());
        existing.setRemark(trimToNull(vo.getRemark()));
        existing.setUpdateTime(now);
        warningLevelMapper.updateById(existing);
        log.info("修改预警等级：{}（{}），区间 [{}, {}]", levelName, levelCode, minDays, maxDays);
        return existing.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        String levelId = trimToNull(id);
        if (levelId == null) {
            throw new JeecgBootException("请指定要删除的预警等级");
        }
        int affected = warningLevelMapper.softDelete(levelId);
        if (affected <= 0) {
            // 报错而不是静默成功：管理端列表里点删除却没变化，用户会以为「系统坏了」
            throw new JeecgBootException("要删除的预警等级不存在或已被删除，请刷新后重试");
        }
        log.info("删除预警等级：id={}（软删；已生成的预警行仍保留 level_id，下次重算会改写或删除它们）", levelId);
    }

    // ==================================================================
    // 内部工具
    // ==================================================================

    private static ProcessWarningLevel findById(List<ProcessWarningLevel> levels, String id) {
        for (ProcessWarningLevel level : levels) {
            if (level != null && id.equals(level.getId())) {
                return level;
            }
        }
        return null;
    }

    /**
     * 新增时的默认排序号：排在最后。
     *
     * <p>不默认插在中间：等级是「由轻到重」的阶梯，新加的一般是最重的一档；
     * 真要插在中间，用户在维护页改一下 sortNo 即可。
     */
    private static int nextSortNo(List<ProcessWarningLevel> levels) {
        int max = 0;
        for (ProcessWarningLevel level : levels) {
            if (level != null && level.getSortNo() != null && level.getSortNo() > max) {
                max = level.getSortNo();
            }
        }
        return max + 1;
    }

    private static WarningLevelVO toVO(ProcessWarningLevel level) {
        WarningLevelVO vo = new WarningLevelVO();
        vo.setId(level.getId());
        vo.setLevelName(level.getLevelName());
        vo.setLevelCode(level.getLevelCode());
        vo.setMinDays(level.getMinDays());
        vo.setMaxDays(level.getMaxDays());
        vo.setSortNo(level.getSortNo());
        vo.setRemark(level.getRemark());
        return vo;
    }

    private static String trimToNull(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return value.trim();
    }
}
