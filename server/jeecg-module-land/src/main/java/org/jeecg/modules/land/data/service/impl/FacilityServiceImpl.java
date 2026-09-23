package org.jeecg.modules.land.data.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.land.data.entity.Facility;
import org.jeecg.modules.land.data.entity.Land;
import org.jeecg.modules.land.data.service.ILandService;
import org.jeecg.modules.land.data.mapper.FacilityMapper;
import org.jeecg.modules.land.data.service.IFacilityService;
import org.jeecg.modules.land.data.vo.FacilityDashboardVO;
import org.jeecg.modules.land.data.vo.FacilityOptionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    @Autowired
    private ILandService landService;

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
        return getOne(new QueryWrapper<Facility>().eq("id", id).eq("delFlag", "0"), false);
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

    @Override
    public FacilityDashboardVO queryDashboard() {
        Map<String, Object> overview = baseMapper.selectDashboardOverview();
        FacilityDashboardVO result = new FacilityDashboardVO();
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

        List<FacilityDashboardVO.RankItem> ranks = new ArrayList<>();
        for (Map<String, Object> row : baseMapper.selectDashboardRanks()) {
            FacilityDashboardVO.RankItem item = new FacilityDashboardVO.RankItem();
            item.setProjectType(toString(row, "projectType"));
            item.setName(toString(row, "name"));
            item.setValue(toLong(row, "value"));
            item.setCompletedCount(toLong(row, "completedCount"));
            ranks.add(item);
        }
        result.setRanks(ranks);

        Map<String, List<FacilityDashboardVO.WarningItem>> warnings = new LinkedHashMap<>();
        warnings.put("city", toWarnings(baseMapper.selectWarningByProjectType("市级项目")));
        warnings.put("district", emptyDistrictWarnings(baseMapper.selectWarningByProjectType("区级项目")));
        warnings.put("plot", toWarnings(baseMapper.selectLandWarnings()));
        result.setWarnings(warnings);
        return result;
    }

    @Override
    public void createFacility(Facility facility) {
        prepareFacility(facility, true);
        if (queryByPtxmmc(facility.getPtxmmc()) != null) {
            throw new JeecgBootException("创建失败！当前项目名称已创建!");
        }
        facility.setId(null);
        facility.setDelFlag("0");
        facility.setCreateAccount(currentUsername());
        facility.setCreateTime(new Date());
        save(facility);
    }

    @Override
    public void updateFacility(Facility facility) {
        prepareFacility(facility, false);
        if (queryById(facility.getId()) == null) {
            throw new JeecgBootException("未找到对应的配套项目");
        }
        Facility duplicated = queryByPtxmmc(facility.getPtxmmc());
        if (duplicated != null && !duplicated.getId().equals(facility.getId())) {
            throw new JeecgBootException("配套项目名称不能重复");
        }
        updateById(facility);
    }

    @Override
    public List<Facility> queryWarningDetails(String projectType, String district) {
        if (StringUtils.isBlank(projectType) || StringUtils.isBlank(district)) {
            return Collections.emptyList();
        }
        String projectLabel = "city".equals(projectType) ? "市级项目" : "district".equals(projectType) ? "区级项目" : projectType;
        return baseMapper.selectWarningDetails(projectLabel, district.trim());
    }

    private void prepareFacility(Facility facility, boolean creating) {
        if (facility == null) {
            throw new JeecgBootException(creating ? "配套项目不能为空" : "配套项目主键不能为空");
        }
        if (!creating && StringUtils.isBlank(facility.getId())) {
            throw new JeecgBootException("配套项目主键不能为空");
        }
        if (StringUtils.isBlank(facility.getCrzdbh())) {
            throw new JeecgBootException("出让宗地编号不能为空");
        }
        if (StringUtils.isBlank(facility.getPtxmmc())) {
            throw new JeecgBootException("配套项目名称不能为空");
        }
        facility.setCrzdbh(facility.getCrzdbh().trim());
        facility.setPtxmmc(facility.getPtxmmc().trim());
        Land land = landService.queryByCrzdbh(facility.getCrzdbh());
        if (land == null) {
            throw new JeecgBootException("未找到对应的出让宗地");
        }
        if (StringUtils.isBlank(facility.getDkmc())) {
            facility.setDkmc(land.getDkmc());
        }
        if (StringUtils.isBlank(facility.getXzqh())) {
            facility.setXzqh(land.getXzqh());
        }
        if (StringUtils.isBlank(facility.getXmfl())) {
            facility.setXmfl(land.getXmfl());
        }
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

    private List<FacilityDashboardVO.WarningItem> emptyDistrictWarnings(List<Map<String, Object>> rows) {
        List<FacilityDashboardVO.WarningItem> result = new ArrayList<>();
        List<Map<String, Object>> districts = baseMapper.selectDistrictGroups();
        Map<String, Map<String, Object>> counted = new LinkedHashMap<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                counted.put(toString(row, "district"), row);
            }
        }
        for (Map<String, Object> district : districts) {
            String name = toString(district, "district");
            result.addAll(toWarnings(Collections.singletonList(
                    counted.containsKey(name) ? counted.get(name) : unreported(name))));
        }
        return result;
    }

    private static Map<String, Object> unreported(String district) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "区级项目-" + district);
        row.put("district", district);
        row.put("unreported", Boolean.TRUE);
        return row;
    }

    private static List<FacilityDashboardVO.WarningItem> toWarnings(List<Map<String, Object>> rows) {
        List<FacilityDashboardVO.WarningItem> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (Map<String, Object> row : rows) {
            FacilityDashboardVO.WarningItem item = new FacilityDashboardVO.WarningItem();
            item.setId(toString(row, "id"));
            item.setDistrict(toString(row, "district"));
            item.setUnreported(Boolean.TRUE.equals(findValue(row, "unreported")));
            item.setLandNo(toString(row, "landNo"));
            item.setPlotName(toString(row, "plotName"));
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
