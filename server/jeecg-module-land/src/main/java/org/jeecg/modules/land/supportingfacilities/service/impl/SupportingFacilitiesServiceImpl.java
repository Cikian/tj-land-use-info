package org.jeecg.modules.land.supportingfacilities.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.jeecg.modules.land.supportingfacilities.entity.SupportingFacilities;
import org.jeecg.modules.land.supportingfacilities.mapper.SupportingFacilitiesMapper;
import org.jeecg.modules.land.supportingfacilities.service.ISupportingFacilitiesService;
import org.jeecg.modules.land.supportingfacilities.vo.SupportingFacilitiesOptionVO;
import org.jeecg.modules.land.supportingfacilities.vo.SupportingFacilitiesDashboardVO;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * @Description: 市政配套项目 Service 实现
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 */
@Slf4j
@Service
public class SupportingFacilitiesServiceImpl extends ServiceImpl<SupportingFacilitiesMapper, SupportingFacilities> implements ISupportingFacilitiesService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    @Override
    public List<SupportingFacilitiesOptionVO> queryOptions(String crzdbh, String keyword, Integer limit) {
        if (StringUtils.isBlank(crzdbh)) {
            // 宗地没选就不该列配套项目；返回空列表而不是全表，避免前端误用
            return Collections.emptyList();
        }
        int size = normalizeLimit(limit);
        return baseMapper.selectOptions(crzdbh.trim(), trimToNull(keyword), size);
    }

    @Override
    public List<SupportingFacilitiesOptionVO> searchOptions(String keyword, Integer limit) {
        return baseMapper.searchOptions(trimToNull(keyword), normalizeLimit(limit));
    }

    @Override
    public SupportingFacilities queryById(String id) {
        if (StringUtils.isBlank(id)) {
            return null;
        }
        return getById(id);
    }

    @Override
    public SupportingFacilities queryByPtxmmc(String ptxmmc) {
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

    @Override
    public SupportingFacilitiesDashboardVO queryDashboard() {
        Map<String, Object> overview = baseMapper.selectDashboardOverview();
        SupportingFacilitiesDashboardVO result = new SupportingFacilitiesDashboardVO();
        result.setLandTotal(toLong(overview, "landTotal"));
        result.setCityLandCount(toLong(overview, "cityLandCount"));
        result.setDistrictLandCount(toLong(overview, "districtLandCount"));
        result.setFacilityTotal(toLong(overview, "facilityTotal"));
        result.setCompletedFacilityCount(toLong(overview, "completedFacilityCount"));
        if (result.getFacilityTotal() > 0) {
            result.setCompletionRate(BigDecimal.valueOf(result.getCompletedFacilityCount())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(result.getFacilityTotal()), 2, RoundingMode.HALF_UP));
        }

        List<SupportingFacilitiesDashboardVO.RankItem> ranks = new ArrayList<>();
        for (Map<String, Object> row : baseMapper.selectDashboardRanks()) {
            SupportingFacilitiesDashboardVO.RankItem item = new SupportingFacilitiesDashboardVO.RankItem();
            item.setName(toString(row, "name"));
            item.setValue(toLong(row, "value"));
            item.setCompletedCount(toLong(row, "completedCount"));
            ranks.add(item);
        }
        result.setRanks(ranks);

        Map<String, List<SupportingFacilitiesDashboardVO.WarningItem>> warnings = new LinkedHashMap<>();
        warnings.put("city", toWarnings(baseMapper.selectWarningByProjectType("市级项目")));
        warnings.put("district", toWarnings(baseMapper.selectWarningByProjectType("区级项目")));
        warnings.put("plot", toWarnings(baseMapper.selectLandWarnings()));
        result.setWarnings(warnings);
        return result;
    }

    private static List<SupportingFacilitiesDashboardVO.WarningItem> toWarnings(List<Map<String, Object>> rows) {
        List<SupportingFacilitiesDashboardVO.WarningItem> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            SupportingFacilitiesDashboardVO.WarningItem item = new SupportingFacilitiesDashboardVO.WarningItem();
            item.setId(toString(row, "id"));
            item.setDistrict(toString(row, "district"));
            item.setLandAcquisition(toDecimal(row, "landAcquisition"));
            item.setFeasibility(toDecimal(row, "feasibility"));
            item.setPreliminaryDesign(toDecimal(row, "preliminaryDesign"));
            item.setFund(toDecimal(row, "fund"));
            item.setNotStarted(toDecimal(row, "notStarted"));
            item.setNotCompleted(toDecimal(row, "notCompleted"));
            item.setNotHandedOver(toDecimal(row, "notHandedOver"));
            result.add(item);
        }
        return result;
    }

    private static long toLong(Map<String, Object> row, String key) {
        Object value = findValue(row, key);
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private static BigDecimal toDecimal(Map<String, Object> row, String key) {
        Object value = findValue(row, key);
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        return value instanceof Number ? BigDecimal.valueOf(((Number) value).doubleValue()) : BigDecimal.ZERO;
    }

    private static String toString(Map<String, Object> row, String key) {
        Object value = findValue(row, key);
        return value == null ? "" : String.valueOf(value);
    }

    private static Object findValue(Map<String, Object> row, String key) {
        if (row == null) {
            return null;
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
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
