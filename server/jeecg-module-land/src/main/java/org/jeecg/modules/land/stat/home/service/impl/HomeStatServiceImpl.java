package org.jeecg.modules.land.stat.home.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.land.stat.home.mapper.HomeStatMapper;
import org.jeecg.modules.land.stat.home.service.IHomeStatService;
import org.jeecg.modules.land.stat.home.vo.LandOverviewVO;
import org.jeecg.modules.land.stat.home.vo.PendingFacilityVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 首页看板统计 Service 实现
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-19
 * @Version V1.0
 *
 * <p>只做「SQL 结果 → VO」的组装，统计口径全部在
 * {@code HomeStatMapper.xml} 里，避免 Java 侧再算一遍导致两处口径不一致。
 *
 * <p>从 data 域 {@code LandServiceImpl / FacilityServiceImpl} 的 dashboard 方法迁入，
 * 迁入后 data 域的方法已删除（单一实现）。
 */
@Slf4j
@Service
public class HomeStatServiceImpl implements IHomeStatService {

    /** 完成率保留 2 位小数（与旧系统 DecimalFormat("#.00") 的展示口径一致） */
    private static final int RATE_SCALE = 2;

    @Autowired
    private HomeStatMapper homeStatMapper;

    @Override
    public LandOverviewVO queryLandOverview() {
        Map<String, Object> overview = homeStatMapper.selectLandOverview();
        LandOverviewVO result = new LandOverviewVO();
        result.setTotal(toLong(overview, "total"));
        result.setCityCount(toLong(overview, "cityCount"));
        result.setDistrictCount(toLong(overview, "districtCount"));
        result.setRoadCount(toLong(overview, "roadCount"));

        List<LandOverviewVO.RankItem> ranks = new ArrayList<>();
        for (Map<String, Object> row : homeStatMapper.selectLandOverviewRanks()) {
            LandOverviewVO.RankItem item = new LandOverviewVO.RankItem();
            item.setProjectType(toString(row, "projectType"));
            item.setName(toString(row, "name"));
            item.setValue(toLong(row, "value"));
            item.setRoadCount(toLong(row, "roadCount"));
            ranks.add(item);
        }
        result.setRanks(ranks);
        return result;
    }

    @Override
    public PendingFacilityVO queryPendingFacility() {
        PendingFacilityVO result = new PendingFacilityVO();

        // 一、宗地口径：至少有一个「需落实」配套的宗地
        Map<String, Object> overview = homeStatMapper.selectPendingFacilityOverview();
        result.setLandTotal(toLong(overview, "landTotal"));
        result.setCityLandCount(toLong(overview, "cityLandCount"));
        result.setDistrictLandCount(toLong(overview, "districtLandCount"));

        // 二、配套口径：按 ptxmmc 去重后的总数 / 完成数
        Map<String, Object> totals = homeStatMapper.selectPendingFacilityTotals();
        result.setFacilityTotal(toLong(totals, "facilityTotal"));
        result.setCompletedFacilityCount(toLong(totals, "completedFacilityCount"));
        result.setCompletionRate(rate(result.getCompletedFacilityCount(), result.getFacilityTotal()));

        // 三、行政区排行
        List<PendingFacilityVO.RankItem> ranks = new ArrayList<>();
        for (Map<String, Object> row : homeStatMapper.selectPendingFacilityRanks()) {
            PendingFacilityVO.RankItem item = new PendingFacilityVO.RankItem();
            item.setProjectType(toString(row, "projectType"));
            item.setName(toString(row, "name"));
            item.setValue(toLong(row, "value"));
            item.setFacilityCount(toLong(row, "facilityCount"));
            item.setCompletedCount(toLong(row, "completedCount"));
            ranks.add(item);
        }
        result.setRanks(ranks);

        // 四、预警面板：结构与字段名必须与前端 HomeAttrPanel 依赖的完全一致
        Map<String, List<PendingFacilityVO.WarningItem>> warnings = new LinkedHashMap<>();
        warnings.put("city", toWarnings(homeStatMapper.selectWarningByProjectType("市级项目")));
        warnings.put("district", emptyDistrictWarnings(homeStatMapper.selectWarningByProjectType("区级项目")));
        warnings.put("plot", toWarnings(homeStatMapper.selectLandWarnings()));
        result.setWarnings(warnings);
        return result;
    }

    @Override
    public List<PendingFacilityVO.WarningItem> queryWarningLands(String projectType, String district) {
        // 参数非法时返回空列表而不是报错：首页面板点开一个空区不该弹错误框
        if (district == null || district.trim().isEmpty()) {
            return new ArrayList<>();
        }
        String xmfl = toProjectLabel(projectType);
        if (xmfl == null) {
            return new ArrayList<>();
        }
        return toWarnings(homeStatMapper.selectWarningLands(xmfl, district.trim()));
    }

    /**
     * 前端的 {@code city / district} 与库里的 {@code 市级项目 / 区级项目} 互转。
     *
     * <p>两套写法都要认：前端走 {@code HomePlotModal} 时传的是 tab key（city/district），
     * 而历史深链或手工调接口时可能直接传中文分类。认不出来就返回 null（= 空结果），
     * 不做「猜一个默认值」—— 猜错会让区级数字出现在市级口径下。
     */
    private static String toProjectLabel(String projectType) {
        if (projectType == null) {
            return null;
        }
        String value = projectType.trim();
        if ("city".equalsIgnoreCase(value) || "市级项目".equals(value)) {
            return "市级项目";
        }
        if ("district".equalsIgnoreCase(value) || "区级项目".equals(value)) {
            return "区级项目";
        }
        return null;
    }

    /**
     * 完成率：completed / total * 100，保留 2 位。
     *
     * <p>分母为 0 时直接返回 0 而不是抛除零异常 —— 首页在「库里还没有配套数据」的
     * 新环境也必须能打开（旧系统用 DecimalFormat 输出 "NaN%" 或 "∞"，更难处理）。
     */
    private static BigDecimal rate(long completed, long total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(RATE_SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(completed)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), RATE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 区级预警补「未上报」行。
     *
     * <p>旧系统要求区级面板把「库里存在、但一条配套都没上报」的行政区也列出来
     * （显示 0% 并标注未上报），否则那些区会从面板里消失、看起来像统计漏了。
     * 这里先取 xzqh2 全量分组，再用已统计出的行去匹配，缺的补一条全 0 行。
     */
    private List<PendingFacilityVO.WarningItem> emptyDistrictWarnings(List<Map<String, Object>> rows) {
        List<PendingFacilityVO.WarningItem> result = new ArrayList<>();
        List<Map<String, Object>> districts = homeStatMapper.selectDistrictGroups();
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

    /** 构造一条「该区一条配套都没上报」的预警行（比率字段在 toWarnings 里会落成 0） */
    private static Map<String, Object> unreported(String district) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "区级项目-" + district);
        row.put("district", district);
        row.put("projectType", "区级项目");
        row.put("unreported", Boolean.TRUE);
        return row;
    }

    private static List<PendingFacilityVO.WarningItem> toWarnings(List<Map<String, Object>> rows) {
        List<PendingFacilityVO.WarningItem> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (Map<String, Object> row : rows) {
            PendingFacilityVO.WarningItem item = new PendingFacilityVO.WarningItem();
            item.setId(toString(row, "id"));
            item.setDistrict(toString(row, "district"));
            item.setProjectType(toString(row, "projectType"));
            item.setUnreported(Boolean.TRUE.equals(findValue(row, "unreported")));
            item.setLandNo(toString(row, "landNo"));
            item.setPlotName(toString(row, "plotName"));
            item.setFacilityCount(toLong(row, "facilityCount"));
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

    /**
     * 从 Map 里按列名取值（大小写不敏感）。
     *
     * <p>为什么要忽略大小写：MySQL 会按建表/别名的原始大小写返回列名，
     * 而不同驱动版本对 {@code AS cityCount} 的大小写处理并不完全一致；
     * 忽略大小写比在 SQL 里到处加双引号更稳。
     */
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
}
