package org.jeecg.modules.land.data.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.jeecg.modules.land.data.entity.Land;
import org.jeecg.modules.land.data.mapper.LandMapper;
import org.jeecg.modules.land.data.service.ILandService;
import org.jeecg.modules.land.data.vo.LandOptionVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
        return baseMapper.selectByCrzdbh(crzdbh.trim());
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

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
