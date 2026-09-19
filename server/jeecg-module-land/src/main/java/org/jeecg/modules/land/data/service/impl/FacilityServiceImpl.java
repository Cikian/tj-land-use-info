package org.jeecg.modules.land.data.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.jeecg.modules.land.data.entity.Facility;
import org.jeecg.modules.land.data.mapper.FacilityMapper;
import org.jeecg.modules.land.data.service.IFacilityService;
import org.jeecg.modules.land.data.vo.FacilityOptionVO;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * @Description: 市政配套项目 Service 实现
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 */
@Slf4j
@Service
public class FacilityServiceImpl extends ServiceImpl<FacilityMapper, Facility> implements IFacilityService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    @Override
    public List<FacilityOptionVO> queryOptions(String crzdbh, String keyword, Integer limit) {
        if (StringUtils.isBlank(crzdbh)) {
            // 宗地没选就不该列配套项目；返回空列表而不是全表，避免前端误用
            return Collections.emptyList();
        }
        int size = normalizeLimit(limit);
        return baseMapper.selectOptions(crzdbh.trim(), trimToNull(keyword), size);
    }

    @Override
    public List<FacilityOptionVO> searchOptions(String keyword, Integer limit) {
        return baseMapper.searchOptions(trimToNull(keyword), normalizeLimit(limit));
    }

    @Override
    public Facility queryById(String id) {
        if (StringUtils.isBlank(id)) {
            return null;
        }
        return getById(id);
    }

    @Override
    public Facility queryByPtxmmc(String ptxmmc) {
        if (StringUtils.isBlank(ptxmmc)) {
            return null;
        }
        return baseMapper.selectByPtxmmc(ptxmmc.trim());
    }

    @Override
    public long countByCrzdbh(String crzdbh) {
        if (StringUtils.isBlank(crzdbh)) {
            return 0L;
        }
        return baseMapper.countByCrzdbh(crzdbh.trim());
    }

    private static int normalizeLimit(Integer limit) {
        return limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
