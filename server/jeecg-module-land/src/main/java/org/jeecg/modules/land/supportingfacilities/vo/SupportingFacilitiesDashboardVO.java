package org.jeecg.modules.land.supportingfacilities.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 配套设施首页统计与预警。 */
@Data
public class SupportingFacilitiesDashboardVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 至少有一个待落实配套项目的宗地数。 */
    private long landTotal;
    private long cityLandCount;
    private long districtLandCount;
    private long facilityTotal;
    private long completedFacilityCount;
    private BigDecimal completionRate = BigDecimal.ZERO;
    private List<RankItem> ranks = new ArrayList<>();
    private Map<String, List<WarningItem>> warnings = new LinkedHashMap<>();

    @Data
    public static class RankItem implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private long value;
        private long completedCount;
    }

    @Data
    public static class WarningItem implements Serializable {
        private static final long serialVersionUID = 1L;
        private String id;
        private String district;
        private BigDecimal landAcquisition = BigDecimal.ZERO;
        private BigDecimal feasibility = BigDecimal.ZERO;
        private BigDecimal preliminaryDesign = BigDecimal.ZERO;
        private BigDecimal fund = BigDecimal.ZERO;
        private BigDecimal notStarted = BigDecimal.ZERO;
        private BigDecimal notCompleted = BigDecimal.ZERO;
        private BigDecimal notHandedOver = BigDecimal.ZERO;
    }
}
