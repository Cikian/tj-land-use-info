package org.jeecg.modules.land.data.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.land.data.entity.Land;
import org.jeecg.modules.land.data.mapper.LandMapper;
import org.jeecg.modules.land.data.service.ILandService;
import org.jeecg.modules.land.data.support.DataSupport;
import org.jeecg.modules.land.data.vo.LandOptionVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 经营性用地（宗地）Service 实现
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 */
@Slf4j
@Service
public class LandServiceImpl extends ServiceImpl<LandMapper, Land> implements ILandService {

    /** 下拉默认返回条数 */
    private static final int DEFAULT_LIMIT = 50;
    /** 下拉最大返回条数（防止前端一次性拉全表 847 条） */
    private static final int MAX_LIMIT = 200;

    @Override
    public List<LandOptionVO> queryOptions(String keyword, String xzqh, Integer limit) {
        int size = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return baseMapper.selectOptions(trimToNull(keyword), trimToNull(xzqh), size);
    }

    @Override
    public Land queryById(String id) {
        if (StringUtils.isBlank(id)) {
            return null;
        }
        return getById(id);
    }

    @Override
    public Land queryByCrzdbh(String crzdbh) {
        if (StringUtils.isBlank(crzdbh)) {
            return null;
        }
        // 查询侧也归一：用户/导入表里填的编号可能没带末尾「号」、或用半角括号，
        // 而库里存的是规范写法。不归一就会出现「明明有这个编号却查不到宗地」，
        // 配套录入与批量导入的「宗地必须已存在」校验都会误报。
        String code = DataSupport.normalizeCode(crzdbh).getCode();
        if (StringUtils.isBlank(code)) {
            return null;
        }
        return baseMapper.selectByCrzdbh(code);
    }

    @Override
    public List<Map<String, Object>> queryXzqhOptions() {
        List<Land> rows = list(new QueryWrapper<Land>()
                .select("DISTINCT xzqh")
                .isNotNull("xzqh")
                .ne("xzqh", "")
                .orderByAsc("xzqh"));
        List<Map<String, Object>> options = new ArrayList<>(rows.size());
        for (Land row : rows) {
            if (StringUtils.isBlank(row.getXzqh())) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>(4);
            item.put("label", row.getXzqh());
            item.put("value", row.getXzqh());
            options.add(item);
        }
        return options;
    }

    @Override
    public void createLand(Land land) {
        if (land == null || StringUtils.isBlank(land.getCrzdbh())) {
            throw new JeecgBootException("出让宗地编号不能为空");
        }
        normalizeFields(land);
        if (queryByCrzdbh(land.getCrzdbh()) != null) {
            throw new JeecgBootException("创建失败！当前出让宗地编号已创建!");
        }
        land.setId(null);
        land.setDelFlag(0);
        land.setCreateBy(currentUsername());
        land.setCreateTime(new Date());
        save(land);
    }

    @Override
    public void updateLand(Land land) {
        if (land == null || StringUtils.isBlank(land.getId())) {
            throw new JeecgBootException("宗地主键不能为空");
        }
        if (StringUtils.isBlank(land.getCrzdbh())) {
            throw new JeecgBootException("出让宗地编号不能为空");
        }
        Land current = queryById(land.getId());
        if (current == null) {
            throw new JeecgBootException("未找到对应的出让宗地");
        }
        normalizeFields(land);
        Land duplicated = queryByCrzdbh(land.getCrzdbh());
        if (duplicated != null && !duplicated.getId().equals(land.getId())) {
            throw new JeecgBootException("出让宗地编号不能重复");
        }
        land.setUpdateBy(currentUsername());
        land.setUpdateTime(new Date());
        updateById(land);
    }

    /**
     * 与 {@code LandAdminServiceImpl} 同一口径：名称归一（括号/连字符/空白）、
     * 编号去【城更】标记（记备注）并补「号」。
     *
     * <p>这条是旧接口 {@code /land/data/land/add|edit} 的写入路径，
     * 与单条录入页走的是不同 Service，所以两边都要归一，否则从旧接口进来的
     * 编号仍会缺「号」，配套又挂不上。
     */
    private void normalizeFields(Land land) {
        DataSupport.CodeInfo code = DataSupport.normalizeCode(land.getCrzdbh());
        if (StringUtils.isBlank(code.getCode())) {
            throw new JeecgBootException("出让宗地编号不能为空");
        }
        land.setCrzdbh(code.getCode());
        land.setBeizhu(DataSupport.appendRemark(land.getBeizhu(), code.getMarkers()));
        land.setDkmc(DataSupport.normalizeName(land.getDkmc(), 255));
        land.setSrr(DataSupport.normalizeName(land.getSrr(), 100));
        land.setLpmc(DataSupport.normalizeName(land.getLpmc(), 100));
    }

    private static String currentUsername() {
        try {
            Object principal = SecurityUtils.getSubject().getPrincipal();
            if (principal instanceof LoginUser) {
                return ((LoginUser) principal).getUsername();
            }
        } catch (Exception ignored) {
            // 未登录场景不阻断写入，审计字段留空
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
